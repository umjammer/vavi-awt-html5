/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import javax.imageio.ImageIO;

import com.github.caciocavallosilano.cacio.peer.WindowClippedGraphics;
import com.github.caciocavallosilano.cacio.peer.managed.FullScreenWindowFactory;
import com.github.caciocavallosilano.cacio.peer.managed.PlatformScreen;


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

    private final BufferedImage screenBuffer;

    private Html5Screen() {
        Dimension d = FullScreenWindowFactory.getScreenDimension();
        screenBuffer = new BufferedImage(d.width, d.height, BufferedImage.TYPE_INT_ARGB);
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
        return new Rectangle(0, 0, screenBuffer.getWidth(), screenBuffer.getHeight());
    }

    @Override
    public Graphics2D getClippedGraphics(Color fg, Color bg, Font f, List<Rectangle> clipRects) {
        Graphics2D g2d = (Graphics2D) screenBuffer.getGraphics();
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
        synchronized (screenBuffer) {
            copy = new BufferedImage(screenBuffer.getWidth(), screenBuffer.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = copy.createGraphics();
            g.drawImage(screenBuffer, 0, 0, null);
            g.dispose();
        }
        ImageIO.write(copy, "png", out);
    }
}
