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
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;


/** Reproduces: dragging the decorated window's title bar must move it. */
class WindowDragTest {

    /** press-drag-release with the left button from (x, y) by (dx, dy) */
    private static void drag(int x, int y, int dx, int dy) throws Exception {
        Html5EventSource ev = Html5EventSource.getInstance();
        ev.postMouseEvent(MouseEvent.MOUSE_MOVED, x, y, 0, MouseEvent.NOBUTTON, 0);
        ev.postMouseEvent(MouseEvent.MOUSE_PRESSED, x, y,
                MouseEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1_DOWN_MASK, 1);
        for (int i = 1; i <= 10; i++) {
            ev.postMouseEvent(MouseEvent.MOUSE_DRAGGED, x + dx * i / 10, y + dy * i / 10,
                    MouseEvent.BUTTON1_DOWN_MASK, MouseEvent.NOBUTTON, 0);
            Thread.sleep(30);
        }
        ev.postMouseEvent(MouseEvent.MOUSE_RELEASED, x + dx, y + dy,
                MouseEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1_DOWN_MASK, 1);
        Thread.sleep(500);
    }

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

        drag(titleX, titleY, 50, 40);

        AtomicReference<Point> end = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> end.set(frame.getLocation()));
        Point p1 = end.get();
        assertTrue(p1.x > p0.x + 20 && p1.y > p0.y + 20,
                "window did not move: " + p0 + " -> " + p1);
        SwingUtilities.invokeAndWait(frame::dispose);
    }

    /**
     * A JFrame with a JMenuBar (the SwingSet2 case): the whole title bar must
     * drag the window — the Swing menu bar sits below the insets and must not
     * shrink the drag zone — while dragging on the menu bar itself must not
     * move the window.
     */
    @Test
    void titleBarDragWorksWithMenuBar() throws Exception {
        ToolkitInstaller.install();

        AtomicReference<JFrame> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame("menu drag");
            JMenuBar mb = new JMenuBar();
            mb.add(new JMenu("File"));
            mb.add(new JMenu("Edit"));
            f.setJMenuBar(mb);
            f.getContentPane().add(new JLabel("body"));
            f.setSize(300, 220);
            f.setLocation(150, 120);
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
        int top = ins.get().top;

        // near the BOTTOM of the title bar (just above the inset edge): this
        // failed when the menu bar height was wrongly subtracted from the zone
        drag(p0.x + 150, p0.y + top - 3, 60, 45);

        AtomicReference<Point> mid = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> mid.set(frame.getLocation()));
        Point p1 = mid.get();
        assertTrue(p1.x > p0.x + 30 && p1.y > p0.y + 20,
                "drag on lower title bar did not move the window: " + p0 + " -> " + p1);

        // on the menu bar (just below the insets): must NOT move the window
        drag(p1.x + 150, p1.y + top + 5, 60, 45);

        AtomicReference<Point> end = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> end.set(frame.getLocation()));
        Point p2 = end.get();
        assertTrue(p2.equals(p1), "drag on the menu bar moved the window: " + p1 + " -> " + p2);
        SwingUtilities.invokeAndWait(frame::dispose);
    }
}
