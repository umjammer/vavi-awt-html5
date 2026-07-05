/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.render;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import vavi.awt.html5.Html5Screen;
import vavi.awt.html5.protocol.MessageWriter;
import vavi.awt.html5.transport.SessionManager;


/**
 * Converts accumulated damage into PNG blit messages at ~30 fps. When a
 * send fails the connection is detached and the damage is re-added, so the
 * next connection starts from a consistent state.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public class FramePump extends Thread {

    private static final Logger logger = Logger.getLogger(FramePump.class.getName());

    private static final long TICK_MS = 33;

    private final Html5Screen screen;
    private final SessionManager sessions;
    private volatile boolean running = true;
    private long frameSeq;

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
            MessageWriter writer = sessions.current();
            if (writer == null || !screen.getDamageTracker().hasDamage()) {
                continue;
            }
            List<ScreenUpdate> updates = screen.getDamageTracker().flush(screen.getFramebuffer());
            if (updates.isEmpty()) {
                continue;
            }
            try {
                for (ScreenUpdate u : updates) {
                    switch (u) {
                    case BlitUpdate b -> writer.writeBlit(b.x(), b.y(), b.width(), b.height(), encodePng(b.image()));
                    case CopyAreaUpdate c -> writer.writeCopyArea(c.x(), c.y(), c.width(), c.height(), c.dx(), c.dy());
                    }
                }
                writer.writeFrameEnd(frameSeq++);
            } catch (IOException e) {
                logger.log(Level.INFO, "send failed, detaching session: " + e);
                sessions.detach(writer);
                // make sure the next connection gets the lost regions again
                screen.getDamageTracker().damageAll();
            }
        }
    }

    public static byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024);
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
