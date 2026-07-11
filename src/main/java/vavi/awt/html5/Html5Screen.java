/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5;

import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Window;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.imageio.ImageIO;

import vavi.awt.html5.transport.SessionManager;

import com.github.caciocavallosilano.cacio.peer.WindowClippedGraphics;
import com.github.caciocavallosilano.cacio.peer.managed.FullScreenWindowFactory;
import com.github.caciocavallosilano.cacio.peer.managed.PlatformScreen;

import vavi.awt.html5.render.CopyAreaUpdate;


/**
 * The virtual screen: a {@link BufferedImage} framebuffer all AWT windows
 * render into. The frame pump ({@code vavi.awt.html5.render.FramePump})
 * detects changes by diffing this framebuffer, so no draw interception is
 * needed here — the graphics handed to AWT is the plain framebuffer
 * graphics, wrapped only for window clipping (matching cacio's own CTC
 * reference backend).
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public class Html5Screen implements PlatformScreen {

    private static Html5Screen instance;

    public static synchronized Html5Screen getInstance() {
        if (instance == null) {
            instance = new Html5Screen();
        }
        return instance;
    }

    private SessionManager sessionManager;

    public synchronized void setSessionManager(SessionManager sm) {
        this.sessionManager = sm;
    }

    public synchronized SessionManager getSessionManager() {
        return sessionManager;
    }

    private volatile BufferedImage screenBuffer;

    private int clientWidth = -1;
    private int clientHeight = -1;

    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(Html5Screen.class.getName());

    /**
     * Pending copyArea hints (framebuffer/device coordinates) produced by
     * cheap block moves such as a title-bar window drag. The frame pump
     * drains these and turns them into {@code COPY_AREA} messages so the
     * browser reuses pixels it already has instead of receiving a fresh PNG.
     * Bounded: if no browser is attached the pump does not drain, so old
     * hints are dropped rather than accumulated without limit.
     */
    private final Queue<CopyAreaUpdate> copyHints = new ConcurrentLinkedQueue<>();
    private static final int MAX_COPY_HINTS = 256;

    private Html5Screen() {
        Dimension d = FullScreenWindowFactory.getScreenDimension();
        screenBuffer = new BufferedImage(d.width, d.height, BufferedImage.TYPE_INT_ARGB);

        // Listen for window resize/show events to dynamically adjust the screen size
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof java.awt.event.ComponentEvent ce && ce.getSource() instanceof Window w) {
                if (ce.getID() == java.awt.event.ComponentEvent.COMPONENT_RESIZED ||
                    ce.getID() == java.awt.event.ComponentEvent.COMPONENT_SHOWN) {
                    if (w.isVisible() && w.isShowing() && (w instanceof Frame || w instanceof Dialog)) {
                        adjustScreenSizeToFitWindows();
                    }
                }
            }
        }, java.awt.AWTEvent.COMPONENT_EVENT_MASK);
    }

    public synchronized void setClientViewportSize(int w, int h) {
        this.clientWidth = w;
        this.clientHeight = h;
        adjustScreenSizeToFitWindows();
    }

    private synchronized void adjustScreenSizeToFitWindows() {
        int maxW = clientWidth;
        int maxH = clientHeight;
        for (Window w : Window.getWindows()) {
            if (w.isVisible() && w.isShowing() && (w instanceof Frame || w instanceof Dialog)) {
                Rectangle bounds = w.getBounds();
                int right = bounds.x + bounds.width;
                int bottom = bounds.y + bounds.height;
                if (right > maxW) {
                    maxW = right;
                }
                if (bottom > maxH) {
                    maxH = bottom;
                }
            }
        }
        if (maxW > 0 && maxH > 0) {
            maxW = Math.max(maxW, 100);
            maxH = Math.max(maxH, 100);
            resize(maxW, maxH);
        }
    }

    public synchronized void resize(int width, int height) {
        BufferedImage fb = screenBuffer;
        if (fb.getWidth() == width && fb.getHeight() == height) {
            return;
        }
        BufferedImage newBuffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = newBuffer.createGraphics();
        g.drawImage(fb, 0, 0, null);
        g.dispose();
        screenBuffer = newBuffer;

        Html5GraphicsConfiguration.setScreenSize(new Dimension(width, height));
        logger.info("Resized virtual screen to " + width + "x" + height);
    }

    /**
     * Records a block move so the frame pump can emit it as a {@code COPY_AREA}
     * optimization. Coordinates are in framebuffer space; the region is the
     * source rectangle and {@code (dx, dy)} the shift. Callers may hint freely:
     * the pump's diff is the safety net, so an inaccurate hint only costs a few
     * extra blits, never correctness.
     */
    public void hintCopyArea(int x, int y, int width, int height, int dx, int dy) {
        if (width <= 0 || height <= 0 || (dx == 0 && dy == 0)) {
            return;
        }
        if (copyHints.size() >= MAX_COPY_HINTS) {
            copyHints.poll();
        }
        copyHints.add(new CopyAreaUpdate(x, y, width, height, dx, dy));
    }

    /** removes and returns the next pending copyArea hint, or null if none */
    public CopyAreaUpdate pollCopyHint() {
        return copyHints.poll();
    }

    /** drops all pending copyArea hints (e.g. when sending a full frame) */
    public void clearCopyHints() {
        copyHints.clear();
    }

    @Override
    public ColorModel getColorModel() {
        return screenBuffer.getColorModel();
    }

    @Override
    public GraphicsConfiguration getGraphicsConfiguration() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
    }

    @Override
    public Rectangle getBounds() {
        BufferedImage fb = screenBuffer;
        return new Rectangle(0, 0, fb.getWidth(), fb.getHeight());
    }

    @Override
    public Graphics2D getClippedGraphics(Color fg, Color bg, Font f, List<Rectangle> clipRects) {
        BufferedImage fb = screenBuffer;
        Graphics2D g2d = (Graphics2D) fb.getGraphics();
        if (clipRects != null && !clipRects.isEmpty()) {
            Area a = new Area(getBounds());
            for (Rectangle clip : clipRects) {
                a.subtract(new Area(clip));
            }
            g2d = new WindowClippedGraphics(g2d, a);
        }
        return g2d;
    }

    /** the shared framebuffer; read by the frame pump and the robot peer */
    public BufferedImage getFramebuffer() {
        return screenBuffer;
    }

    public int[] getRGBPixels(Rectangle bounds) {
        return screenBuffer.getRGB(bounds.x, bounds.y, bounds.width, bounds.height, null, 0, bounds.width);
    }

    /** writes the current framebuffer as png, synchronized against rendering */
    public void snapshotPng(OutputStream out) throws IOException {
        BufferedImage copy;
        BufferedImage fb = screenBuffer;
        synchronized (fb) {
            copy = new BufferedImage(fb.getWidth(), fb.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = copy.createGraphics();
            g.drawImage(fb, 0, 0, null);
            g.dispose();
        }
        ImageIO.write(copy, "png", out);
    }
}
