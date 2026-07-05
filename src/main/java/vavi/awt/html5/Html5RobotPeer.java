/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5;

import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.peer.RobotPeer;


/**
 * Robot peer reading pixels from the framebuffer and posting synthetic
 * input events; enables java.awt.Robot based testing.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public class Html5RobotPeer implements RobotPeer {

    private int currentModifiers = 0;
    private int currentX = 0;
    private int currentY = 0;

    private static int buttonMaskToDownMask(int buttons) {
        int down = 0;
        if ((buttons & InputEvent.BUTTON1_MASK) != 0 || (buttons & InputEvent.BUTTON1_DOWN_MASK) != 0) {
            down |= InputEvent.BUTTON1_DOWN_MASK;
        }
        if ((buttons & InputEvent.BUTTON2_MASK) != 0 || (buttons & InputEvent.BUTTON2_DOWN_MASK) != 0) {
            down |= InputEvent.BUTTON2_DOWN_MASK;
        }
        if ((buttons & InputEvent.BUTTON3_MASK) != 0 || (buttons & InputEvent.BUTTON3_DOWN_MASK) != 0) {
            down |= InputEvent.BUTTON3_DOWN_MASK;
        }
        return down;
    }

    @Override
    public void mouseMove(int x, int y) {
        currentX = x;
        currentY = y;
        int id = currentModifiers == 0 ? MouseEvent.MOUSE_MOVED : MouseEvent.MOUSE_DRAGGED;
        Html5EventSource.getInstance().postMouseEvent(id, x, y, currentModifiers, MouseEvent.NOBUTTON, 0);
    }

    @Override
    public void mousePress(int buttons) {
        int down = buttonMaskToDownMask(buttons);
        currentModifiers |= down;
        Html5EventSource.getInstance().postMouseEvent(
                MouseEvent.MOUSE_PRESSED, currentX, currentY, currentModifiers, down, 1);
    }

    @Override
    public void mouseRelease(int buttons) {
        int down = buttonMaskToDownMask(buttons);
        Html5EventSource.getInstance().postMouseEvent(
                MouseEvent.MOUSE_RELEASED, currentX, currentY, currentModifiers, down, 1);
        currentModifiers &= ~down;
    }

    @Override
    public void mouseWheel(int wheelAmt) {
        Html5EventSource.getInstance().postWheelEvent(currentX, currentY, currentModifiers, wheelAmt < 0);
    }

    @Override
    public void keyPress(int keycode) {
        Html5EventSource.getInstance().postKeyEvent(KeyEvent.KEY_PRESSED, keycode, KeyEvent.CHAR_UNDEFINED, currentModifiers);
    }

    @Override
    public void keyRelease(int keycode) {
        Html5EventSource.getInstance().postKeyEvent(KeyEvent.KEY_RELEASED, keycode, KeyEvent.CHAR_UNDEFINED, currentModifiers);
    }

    @Override
    public int getRGBPixel(int x, int y) {
        return Html5Screen.getInstance().getFramebuffer().getRGB(x, y);
    }

    @Override
    public int[] getRGBPixels(Rectangle bounds) {
        return Html5Screen.getInstance().getRGBPixels(bounds);
    }
}
