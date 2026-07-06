/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5;

import java.awt.Insets;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import vavi.awt.html5.protocol.MessageWriter;
import vavi.awt.html5.protocol.Protocol;
import vavi.awt.html5.render.FramePump;
import vavi.awt.html5.transport.SessionManager;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * The frame pump turns a block-move hint into a {@code COPY_AREA} message so
 * the browser reuses pixels it already holds instead of receiving a fresh PNG.
 */
class CopyAreaTest {

    record Frame(int opcode, byte[] body) {
    }

    private static List<Frame> parse(byte[] buf) {
        List<Frame> out = new ArrayList<>();
        int off = 0;
        while (buf.length - off >= 4) {
            int len = ((buf[off] & 0xff) << 24) | ((buf[off + 1] & 0xff) << 16)
                    | ((buf[off + 2] & 0xff) << 8) | (buf[off + 3] & 0xff);
            if (buf.length - off - 4 < len) {
                break;
            }
            int opcode = buf[off + 4] & 0xff;
            byte[] body = new byte[len - 1];
            System.arraycopy(buf, off + 5, body, 0, len - 1);
            out.add(new Frame(opcode, body));
            off += 4 + len;
        }
        return out;
    }

    private static int u16(byte[] b, int i) {
        return ((b[i] & 0xff) << 8) | (b[i + 1] & 0xff);
    }

    @Test
    void hintBecomesCopyAreaMessage() throws Exception {
        ToolkitInstaller.install();

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        MessageWriter writer = new MessageWriter(captured);
        SessionManager sessions = new SessionManager();
        FramePump pump = new FramePump(Html5Screen.getInstance(), sessions);
        pump.start();
        try {
            // attach so the pump sends the initial full frame, then wait for it
            sessions.attach(writer);
            long deadline = System.currentTimeMillis() + 5_000;
            while (parse(captured.toByteArray()).stream().noneMatch(f -> f.opcode() == Protocol.OP_BLIT)
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            int before = captured.size();

            // a block move: source (100,100,50,40) shifted by (12,-8)
            Html5Screen.getInstance().hintCopyArea(100, 100, 50, 40, 12, -8);

            Frame copy = null;
            deadline = System.currentTimeMillis() + 5_000;
            while (copy == null && System.currentTimeMillis() < deadline) {
                for (Frame f : parse(captured.toByteArray())) {
                    if (f.opcode() == Protocol.OP_COPY_AREA) {
                        copy = f;
                        break;
                    }
                }
                if (copy == null) {
                    Thread.sleep(50);
                }
            }

            assertNotNull(copy, "no COPY_AREA emitted for the block-move hint");
            byte[] b = copy.body();
            assertTrue(b.length >= 12, "short COPY_AREA body");
            org.junit.jupiter.api.Assertions.assertEquals(100, u16(b, 0), "x");
            org.junit.jupiter.api.Assertions.assertEquals(100, u16(b, 2), "y");
            org.junit.jupiter.api.Assertions.assertEquals(50, u16(b, 4), "w");
            org.junit.jupiter.api.Assertions.assertEquals(40, u16(b, 6), "h");
            org.junit.jupiter.api.Assertions.assertEquals(12, (short) u16(b, 8), "dx");
            org.junit.jupiter.api.Assertions.assertEquals(-8, (short) u16(b, 10), "dy");
            assertTrue(captured.size() > before, "nothing was sent after the hint");
        } finally {
            pump.shutdown();
            sessions.detach(writer);
        }
    }

    @Test
    void titleBarDragEmitsCopyAreaOverThePump() throws Exception {
        ToolkitInstaller.install();

        AtomicReference<JFrame> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame("copy drag");
            f.getContentPane().add(new JLabel("body"));
            f.setSize(320, 220);
            f.setLocation(120, 90);
            f.setVisible(true);
            ref.set(f);
        });
        JFrame frame = ref.get();
        Thread.sleep(1200);

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        MessageWriter writer = new MessageWriter(captured);
        SessionManager sessions = new SessionManager();
        FramePump pump = new FramePump(Html5Screen.getInstance(), sessions);
        pump.start();
        try {
            sessions.attach(writer);
            long deadline = System.currentTimeMillis() + 5_000;
            while (parse(captured.toByteArray()).stream().noneMatch(f -> f.opcode() == Protocol.OP_BLIT)
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }

            AtomicReference<Point> loc = new AtomicReference<>();
            AtomicReference<Insets> ins = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                loc.set(frame.getLocationOnScreen());
                ins.set(frame.getInsets());
            });
            int tx = loc.get().x + 160;
            int ty = loc.get().y + Math.max(6, ins.get().top / 2);

            Html5EventSource ev = Html5EventSource.getInstance();
            ev.postMouseEvent(MouseEvent.MOUSE_PRESSED, tx, ty,
                    MouseEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1_DOWN_MASK, 1);
            for (int i = 1; i <= 8; i++) {
                ev.postMouseEvent(MouseEvent.MOUSE_DRAGGED, tx + i * 6, ty + i * 3,
                        MouseEvent.BUTTON1_DOWN_MASK, MouseEvent.NOBUTTON, 0);
                Thread.sleep(40);
            }
            ev.postMouseEvent(MouseEvent.MOUSE_RELEASED, tx + 48, ty + 24,
                    MouseEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1_DOWN_MASK, 1);

            boolean sawCopy = false;
            deadline = System.currentTimeMillis() + 5_000;
            while (!sawCopy && System.currentTimeMillis() < deadline) {
                sawCopy = parse(captured.toByteArray()).stream()
                        .anyMatch(f -> f.opcode() == Protocol.OP_COPY_AREA);
                if (!sawCopy) {
                    Thread.sleep(50);
                }
            }
            assertTrue(sawCopy, "title-bar drag did not produce a COPY_AREA on the wire");
        } finally {
            pump.shutdown();
            sessions.detach(writer);
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }
}
