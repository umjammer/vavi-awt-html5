/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.render;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import vavi.awt.html5.Html5Screen;
import vavi.awt.html5.protocol.MessageWriter;
import vavi.awt.html5.transport.SessionManager;


/**
 * Detects screen changes by diffing the framebuffer against the previously
 * sent frame in fixed tiles, and ships changed regions as PNG blits ~30
 * times per second.
 * <p>
 * Framebuffer diffing (rather than intercepting draw operations) is used
 * deliberately: cacio composits windows through {@code SunGraphics2D}'s
 * {@code constrain()} offset, which is not visible in the graphics
 * transform, so device coordinates cannot be reconstructed reliably from a
 * {@code Graphics2D} wrapper. Diffing sees the real pixels wherever they
 * land.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public class FramePump extends Thread {

    private static final Logger logger = Logger.getLogger(FramePump.class.getName());

    private static final long TICK_MS = 33;
    private static final int TILE = 64;

    private final Html5Screen screen;
    private final SessionManager sessions;
    private volatile boolean running = true;
    private long frameSeq;

    /** the previously sent framebuffer contents, tile-diffed against the live one */
    private int[] prev;
    private MessageWriter lastWriter;

    public FramePump(Html5Screen screen, SessionManager sessions) {
        super("html5-frame-pump");
        setDaemon(true);
        this.screen = screen;
        this.sessions = sessions;
    }

    public void shutdown() {
        running = false;
        interrupt();
    }

    @Override
    public void run() {
        while (running) {
            try {
                Thread.sleep(TICK_MS);
            } catch (InterruptedException e) {
                continue;
            }
            try {
                tick();
            } catch (IOException e) {
                logger.log(Level.INFO, "send failed, detaching session: " + e);
                sessions.detach(lastWriter);
                lastWriter = null;
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "frame pump error", e);
            }
        }
    }

    private void tick() throws IOException {
        MessageWriter writer = sessions.current();
        if (writer == null) {
            lastWriter = null;
            return;
        }

        BufferedImage fb = screen.getFramebuffer();
        int w = fb.getWidth();
        int h = fb.getHeight();
        int[] cur = ((DataBufferInt) fb.getRaster().getDataBuffer()).getData();

        boolean newSession = writer != lastWriter;
        lastWriter = writer;

        if (newSession || prev == null || prev.length != cur.length) {
            // first frame for this connection: send the whole screen
            prev = cur.clone();
            sendBlit(writer, 0, 0, w, h, prev);
            writer.writeFrameEnd(frameSeq++);
            return;
        }

        List<Rectangle> dirty = diffTiles(prev, cur, w, h);
        if (dirty.isEmpty()) {
            return;
        }
        for (Rectangle r : dirty) {
            copyInto(cur, prev, w, r);
            sendBlit(writer, r.x, r.y, r.width, r.height, extract(cur, w, r));
        }
        writer.writeFrameEnd(frameSeq++);
        if (Boolean.getBoolean("vavi.awt.html5.debug")) {
            logger.info("frame " + (frameSeq - 1) + ": " + dirty.size() + " region(s)");
        }
    }

    /** changed tiles, merged into per-tile-row rectangles */
    private static List<Rectangle> diffTiles(int[] prev, int[] cur, int w, int h) {
        int tilesX = (w + TILE - 1) / TILE;
        List<Rectangle> out = new ArrayList<>();
        for (int ty = 0; ty < h; ty += TILE) {
            int th = Math.min(TILE, h - ty);
            int runStart = -1;
            for (int tx = 0; tx <= w; tx += TILE) {
                boolean dirty = tx < w && tileDiffers(prev, cur, w, tx, ty, Math.min(TILE, w - tx), th);
                if (dirty && runStart < 0) {
                    runStart = tx;
                } else if (!dirty && runStart >= 0) {
                    out.add(new Rectangle(runStart, ty, Math.min(tx, w) - runStart, th));
                    runStart = -1;
                }
            }
        }
        return out;
    }

    private static boolean tileDiffers(int[] prev, int[] cur, int w, int x, int y, int tw, int th) {
        for (int row = 0; row < th; row++) {
            int base = (y + row) * w + x;
            for (int col = 0; col < tw; col++) {
                if (prev[base + col] != cur[base + col]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void copyInto(int[] src, int[] dst, int w, Rectangle r) {
        for (int row = 0; row < r.height; row++) {
            int off = (r.y + row) * w + r.x;
            System.arraycopy(src, off, dst, off, r.width);
        }
    }

    private static int[] extract(int[] src, int w, Rectangle r) {
        int[] out = new int[r.width * r.height];
        for (int row = 0; row < r.height; row++) {
            System.arraycopy(src, (r.y + row) * w + r.x, out, row * r.width, r.width);
        }
        return out;
    }

    private void sendBlit(MessageWriter writer, int x, int y, int w, int h, int[] argb) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, argb, 0, w);
        writer.writeBlit(x, y, w, h, encodePng(img));
    }

    public static byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024);
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
