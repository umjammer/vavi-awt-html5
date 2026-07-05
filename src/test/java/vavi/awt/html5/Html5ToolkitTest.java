/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Phase 1: the toolkit renders an unmodified Swing UI into the framebuffer
 * and the managed event pipeline delivers synthetic input.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Html5ToolkitTest {

    static JFrame frame;
    static JButton button;
    static JTextField textField;
    static final CountDownLatch clicked = new CountDownLatch(1);

    @BeforeAll
    static void setUpToolkit() throws Exception {
        ToolkitInstaller.install();

        SwingUtilities.invokeAndWait(() -> {
            frame = new JFrame("html5 test");
            JPanel panel = new JPanel();
            button = new JButton("Click me");
            button.addActionListener(e -> clicked.countDown());
            textField = new JTextField(15);
            panel.add(new JLabel("hello html5"));
            panel.add(button);
            panel.add(textField);
            frame.getContentPane().add(panel);
            frame.setSize(400, 300);
            frame.setLocation(50, 50);
            frame.setVisible(true);
        });
    }

    private static boolean framebufferNonBlank() {
        int[] px = Html5Screen.getInstance().getRGBPixels(new Rectangle(50, 50, 400, 300));
        for (int p : px) {
            if ((p & 0xffffff) != 0 && (p >>> 24) != 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean buttonPainted() throws Exception {
        AtomicReference<Rectangle> r = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            if (button.isShowing()) {
                Point p = button.getLocationOnScreen();
                r.set(new Rectangle(p.x, p.y, button.getWidth(), button.getHeight()));
            }
        });
        if (r.get() == null) {
            return false;
        }
        // the Metal button is not plain white; look for any non-white pixel
        int[] px = Html5Screen.getInstance().getRGBPixels(r.get());
        for (int p : px) {
            if ((p >>> 24) != 0 && (p & 0xffffff) != 0xffffff) {
                return true;
            }
        }
        return false;
    }

    @Test
    @Order(1)
    void rendersToFramebuffer() throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!(framebufferNonBlank() && buttonPainted()) && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertTrue(framebufferNonBlank(), "framebuffer stayed blank");
        assertTrue(buttonPainted(), "button never painted");

        Files.createDirectories(Path.of("target"));
        try (FileOutputStream out = new FileOutputStream("target/snapshot.png")) {
            Html5Screen.getInstance().snapshotPng(out);
        }

        List<Rectangle> rects = Html5Screen.getInstance().getDamageTracker().flushRects();
        assertFalse(rects.isEmpty(), "no damage tracked");
        Rectangle union = rects.getFirst();
        for (Rectangle r : rects) {
            union = union.union(r);
        }
        assertTrue(union.intersects(new Rectangle(50, 50, 400, 300)),
                "damage " + union + " does not cover the frame");
    }

    @Test
    @Order(2)
    void clickFiresActionListener() throws Exception {
        AtomicReference<Point> center = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Point p = button.getLocationOnScreen();
            center.set(new Point(p.x + button.getWidth() / 2, p.y + button.getHeight() / 2));
        });
        int cx = center.get().x;
        int cy = center.get().y;

        Html5EventSource events = Html5EventSource.getInstance();
        events.postMouseEvent(MouseEvent.MOUSE_MOVED, cx, cy, 0, MouseEvent.NOBUTTON, 0);
        events.postMouseEvent(MouseEvent.MOUSE_PRESSED, cx, cy,
                MouseEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1_DOWN_MASK, 1);
        events.postMouseEvent(MouseEvent.MOUSE_RELEASED, cx, cy,
                MouseEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1_DOWN_MASK, 1);

        assertTrue(clicked.await(5, TimeUnit.SECONDS), "button click did not fire");
    }

    @Test
    @Order(3)
    void typedKeyReachesTextField() throws Exception {
        AtomicReference<Point> center = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Point p = textField.getLocationOnScreen();
            center.set(new Point(p.x + textField.getWidth() / 2, p.y + textField.getHeight() / 2));
        });
        int cx = center.get().x;
        int cy = center.get().y;

        Html5EventSource events = Html5EventSource.getInstance();
        // click to focus the text field
        events.postMouseEvent(MouseEvent.MOUSE_MOVED, cx, cy, 0, MouseEvent.NOBUTTON, 0);
        events.postMouseEvent(MouseEvent.MOUSE_PRESSED, cx, cy,
                MouseEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1_DOWN_MASK, 1);
        events.postMouseEvent(MouseEvent.MOUSE_RELEASED, cx, cy,
                MouseEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1_DOWN_MASK, 1);

        long deadline = System.currentTimeMillis() + 5_000;
        while (!textField.isFocusOwner() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(textField.isFocusOwner(), "text field did not gain focus");

        events.postKeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_A, 'a', 0);
        events.postKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, 'a', 0);
        events.postKeyEvent(KeyEvent.KEY_RELEASED, KeyEvent.VK_A, 'a', 0);

        deadline = System.currentTimeMillis() + 5_000;
        while (!"a".equals(textField.getText()) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue("a".equals(textField.getText()), "typed char did not arrive: '" + textField.getText() + "'");
    }
}
