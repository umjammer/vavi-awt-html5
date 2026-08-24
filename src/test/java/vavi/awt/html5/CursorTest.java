/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5;

import java.awt.Cursor;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/** Reproduces: the cursor must change over a decorated window's edges. */
class CursorTest {

    /** the last cursor cacio asked the screen to show */
    private static int cursorAt(JFrame frame, int x, int y) throws Exception {
        Html5Screen.getInstance().resetShownCursorForTest();
        Html5EventSource.getInstance()
                .postMouseEvent(MouseEvent.MOUSE_MOVED, x, y, 0, MouseEvent.NOBUTTON, 0);
        Thread.sleep(400);
        return Html5Screen.getInstance().getShownCursorForTest();
    }

    @Test
    void cursorFollowsTheDecoration() throws Exception {
        ToolkitInstaller.install();

        AtomicReference<JFrame> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame("point at me");
            f.getContentPane().add(new JLabel("body"));
            f.setSize(300, 200);
            f.setLocation(200, 150);
            f.setVisible(true);
            ref.set(f);
        });
        JFrame frame = ref.get();
        Thread.sleep(1500);

        AtomicReference<Point> loc = new AtomicReference<>();
        AtomicReference<Insets> ins = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            loc.set(frame.getLocationOnScreen());
            ins.set(frame.getInsets());
        });
        Point p = loc.get();
        int w = frame.getWidth();
        int h = frame.getHeight();

        assertEquals(Cursor.E_RESIZE_CURSOR, cursorAt(frame, p.x + w - 2, p.y + h / 2),
                "right edge");
        assertEquals(Cursor.S_RESIZE_CURSOR, cursorAt(frame, p.x + w / 2, p.y + h - 2),
                "bottom edge");
        assertEquals(Cursor.SE_RESIZE_CURSOR, cursorAt(frame, p.x + w - 2, p.y + h - 2),
                "bottom right corner");
        assertEquals(Cursor.MOVE_CURSOR,
                cursorAt(frame, p.x + w / 2, p.y + Math.max(6, ins.get().top / 2)),
                "title bar");
        assertEquals(Cursor.DEFAULT_CURSOR, cursorAt(frame, p.x + w / 2, p.y + h / 2),
                "contents");

        SwingUtilities.invokeAndWait(frame::dispose);
    }
}
