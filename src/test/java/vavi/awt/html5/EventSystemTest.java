/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * The event system must deliver AWT events to application listeners for the
 * window operations the browser can trigger: resize (border drag), move
 * (title-bar drag) and activation — just like a native toolkit would.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventSystemTest {

    static JFrame frame;

    static final CountDownLatch opened = new CountDownLatch(1);
    static final CountDownLatch activated = new CountDownLatch(1);
    static final CountDownLatch focusGained = new CountDownLatch(1);

    @BeforeAll
    static void setUp() throws Exception {
        ToolkitInstaller.install();

        SwingUtilities.invokeAndWait(() -> {
            frame = new JFrame("event system test");
            frame.getContentPane().add(new JLabel("events"));
            frame.addWindowListener(new WindowAdapter() {
                @Override public void windowOpened(WindowEvent e) {
                    opened.countDown();
                }
                @Override public void windowActivated(WindowEvent e) {
                    activated.countDown();
                }
                @Override public void windowGainedFocus(WindowEvent e) {
                    focusGained.countDown();
                }
            });
            frame.addWindowFocusListener(new WindowAdapter() {
                @Override public void windowGainedFocus(WindowEvent e) {
                    focusGained.countDown();
                }
            });
            frame.setSize(300, 200);
            frame.setLocation(100, 100);
            frame.setVisible(true);
        });
        Thread.sleep(1000);
    }

    @Test
    @Order(1)
    void windowOpenedAndActivatedFire() throws Exception {
        assertTrue(opened.await(5, TimeUnit.SECONDS), "windowOpened did not fire");
        // activation needs a click on the window (managed focus follows mouse press)
        AtomicReference<Point> p = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Point loc = frame.getLocationOnScreen();
            p.set(new Point(loc.x + 150, loc.y + 100));
        });
        Html5EventSource ev = Html5EventSource.getInstance();
        ev.postMouseEvent(MouseEvent.MOUSE_MOVED, p.get().x, p.get().y, 0, MouseEvent.NOBUTTON, 0);
        ev.postMouseEvent(MouseEvent.MOUSE_PRESSED, p.get().x, p.get().y,
                MouseEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1_DOWN_MASK, 1);
        ev.postMouseEvent(MouseEvent.MOUSE_RELEASED, p.get().x, p.get().y,
                MouseEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1_DOWN_MASK, 1);
        assertTrue(activated.await(5, TimeUnit.SECONDS), "windowActivated did not fire");
        assertTrue(focusGained.await(5, TimeUnit.SECONDS), "windowGainedFocus did not fire");
    }

    @Test
    @Order(2)
    void programmaticResizeFiresComponentResized() throws Exception {
        CountDownLatch resized = new CountDownLatch(1);
        AtomicReference<Dimension> seen = new AtomicReference<>();
        ComponentAdapter l = new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                seen.set(e.getComponent().getSize());
                resized.countDown();
            }
        };
        SwingUtilities.invokeAndWait(() -> {
            frame.addComponentListener(l);
            frame.setSize(400, 300);
        });
        try {
            assertTrue(resized.await(5, TimeUnit.SECONDS),
                    "componentResized did not fire on setSize");
            assertTrue(seen.get().width == 400 && seen.get().height == 300,
                    "unexpected size in event: " + seen.get());
        } finally {
            frame.removeComponentListener(l);
        }
    }

    @Test
    @Order(3)
    void borderDragResizeFiresComponentResized() throws Exception {
        CountDownLatch resized = new CountDownLatch(1);
        ComponentAdapter l = new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                resized.countDown();
            }
        };
        AtomicReference<Rectangle> b0 = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            frame.addComponentListener(l);
            b0.set(frame.getBounds());
        });
        Rectangle r = b0.get();
        // grab the right border, drag it 60px to the right
        int gx = r.x + r.width - 1;
        int gy = r.y + r.height / 2;

        Html5EventSource ev = Html5EventSource.getInstance();
        ev.postMouseEvent(MouseEvent.MOUSE_MOVED, gx, gy, 0, MouseEvent.NOBUTTON, 0);
        ev.postMouseEvent(MouseEvent.MOUSE_PRESSED, gx, gy,
                MouseEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1_DOWN_MASK, 1);
        for (int i = 1; i <= 6; i++) {
            ev.postMouseEvent(MouseEvent.MOUSE_DRAGGED, gx + i * 10, gy,
                    MouseEvent.BUTTON1_DOWN_MASK, MouseEvent.NOBUTTON, 0);
            Thread.sleep(30);
        }
        ev.postMouseEvent(MouseEvent.MOUSE_RELEASED, gx + 60, gy,
                MouseEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1_DOWN_MASK, 1);

        try {
            assertTrue(resized.await(5, TimeUnit.SECONDS),
                    "componentResized did not fire on border-drag resize");
            AtomicReference<Rectangle> b1 = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> b1.set(frame.getBounds()));
            assertTrue(b1.get().width >= r.width + 40,
                    "window did not grow: " + r + " -> " + b1.get());
        } finally {
            frame.removeComponentListener(l);
        }
    }

    @Test
    @Order(4)
    void titleBarDragFiresComponentMoved() throws Exception {
        CountDownLatch moved = new CountDownLatch(1);
        ComponentAdapter l = new ComponentAdapter() {
            @Override public void componentMoved(ComponentEvent e) {
                moved.countDown();
            }
        };
        AtomicReference<Point> p0 = new AtomicReference<>();
        AtomicReference<Insets> ins = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            frame.addComponentListener(l);
            p0.set(frame.getLocationOnScreen());
            ins.set(frame.getInsets());
        });
        int titleX = p0.get().x + frame.getWidth() / 2;
        int titleY = p0.get().y + Math.max(6, ins.get().top / 2);

        Html5EventSource ev = Html5EventSource.getInstance();
        ev.postMouseEvent(MouseEvent.MOUSE_MOVED, titleX, titleY, 0, MouseEvent.NOBUTTON, 0);
        ev.postMouseEvent(MouseEvent.MOUSE_PRESSED, titleX, titleY,
                MouseEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1_DOWN_MASK, 1);
        for (int i = 1; i <= 5; i++) {
            ev.postMouseEvent(MouseEvent.MOUSE_DRAGGED, titleX + i * 8, titleY + i * 6,
                    MouseEvent.BUTTON1_DOWN_MASK, MouseEvent.NOBUTTON, 0);
            Thread.sleep(30);
        }
        ev.postMouseEvent(MouseEvent.MOUSE_RELEASED, titleX + 40, titleY + 30,
                MouseEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1_DOWN_MASK, 1);

        try {
            assertTrue(moved.await(5, TimeUnit.SECONDS),
                    "componentMoved did not fire on title-bar drag");
        } finally {
            frame.removeComponentListener(l);
        }
    }
}
