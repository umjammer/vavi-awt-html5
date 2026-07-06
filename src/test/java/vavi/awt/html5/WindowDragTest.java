/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5;

import java.awt.Insets;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;


/** Reproduces: dragging the decorated window's title bar must move it. */
class WindowDragTest {

    @Test
    void titleBarDragMovesWindow() throws Exception {
        ToolkitInstaller.install();

        AtomicReference<JFrame> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame("drag me");
            f.getContentPane().add(new JLabel("body"));
            f.setSize(300, 200);
            f.setLocation(200, 150);
            f.setVisible(true);
            ref.set(f);
        });
        JFrame frame = ref.get();
        Thread.sleep(1500);

        AtomicReference<Point> start = new AtomicReference<>();
        AtomicReference<Insets> ins = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            start.set(frame.getLocationOnScreen());
            ins.set(frame.getInsets());
        });
        Point p0 = start.get();
        // a point on the title bar: horizontally centered, vertically in the middle of the top inset
        int titleY = p0.y + Math.max(6, ins.get().top / 2);
        int titleX = p0.x + 150;

        Html5EventSource ev = Html5EventSource.getInstance();
        ev.postMouseEvent(MouseEvent.MOUSE_MOVED, titleX, titleY, 0, MouseEvent.NOBUTTON, 0);
        ev.postMouseEvent(MouseEvent.MOUSE_PRESSED, titleX, titleY,
                MouseEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1_DOWN_MASK, 1);
        for (int i = 1; i <= 10; i++) {
            ev.postMouseEvent(MouseEvent.MOUSE_DRAGGED, titleX + i * 5, titleY + i * 4,
                    MouseEvent.BUTTON1_DOWN_MASK, MouseEvent.NOBUTTON, 0);
            Thread.sleep(30);
        }
        ev.postMouseEvent(MouseEvent.MOUSE_RELEASED, titleX + 50, titleY + 40,
                MouseEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1_DOWN_MASK, 1);

        Thread.sleep(500);
        AtomicReference<Point> end = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> end.set(frame.getLocation()));
        Point p1 = end.get();
        assertTrue(p1.x > p0.x + 20 && p1.y > p0.y + 20,
                "window did not move: " + p0 + " -> " + p1);
    }
}
