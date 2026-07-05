/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.protocol;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

import vavi.awt.html5.Html5EventSource;


/**
 * Turns client protocol messages into AWT events on the
 * {@link Html5EventSource} queue. One instance per connection (keeps the
 * modifier state of that client).
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public class InputEventDecoder implements MessageReader.Handler {

    private static final Logger logger = Logger.getLogger(InputEventDecoder.class.getName());

    /** connection-level callbacks beyond plain input */
    public interface Listener {

        void onHello(int version, int viewW, int viewH) throws IOException;

        void onPing(long nonce) throws IOException;

        void onClientResize(int viewW, int viewH) throws IOException;
    }

    private final Html5EventSource events;
    private final Listener listener;

    public InputEventDecoder(Html5EventSource events, Listener listener) {
        this.events = events;
        this.listener = listener;
    }

    private static int u16(ByteBuffer b) {
        return b.getShort() & 0xffff;
    }

    private static int s16(ByteBuffer b) {
        return b.getShort();
    }

    private static int keyModifiers(int mods) {
        int m = 0;
        if ((mods & Protocol.MOD_SHIFT) != 0) m |= InputEvent.SHIFT_DOWN_MASK;
        if ((mods & Protocol.MOD_CTRL) != 0) m |= InputEvent.CTRL_DOWN_MASK;
        if ((mods & Protocol.MOD_ALT) != 0) m |= InputEvent.ALT_DOWN_MASK;
        if ((mods & Protocol.MOD_META) != 0) m |= InputEvent.META_DOWN_MASK;
        return m;
    }

    private static int buttonsToDownMask(int buttons) {
        int m = 0;
        if ((buttons & 1) != 0) m |= InputEvent.BUTTON1_DOWN_MASK;
        if ((buttons & 2) != 0) m |= InputEvent.BUTTON2_DOWN_MASK;
        if ((buttons & 4) != 0) m |= InputEvent.BUTTON3_DOWN_MASK;
        return m;
    }

    private static int buttonToDownMask(int button) {
        return switch (button) {
            case 1 -> InputEvent.BUTTON1_DOWN_MASK;
            case 2 -> InputEvent.BUTTON2_DOWN_MASK;
            case 3 -> InputEvent.BUTTON3_DOWN_MASK;
            default -> 0;
        };
    }

    @Override
    public void onMessage(int opcode, ByteBuffer body) throws IOException {
        switch (opcode) {
        case Protocol.OP_HELLO -> {
            int version = u16(body);
            int w = u16(body);
            int h = u16(body);
            listener.onHello(version, w, h);
        }
        case Protocol.OP_MOUSE -> {
            int kind = body.get() & 0xff;
            int x = u16(body);
            int y = u16(body);
            int button = body.get() & 0xff;
            int buttons = body.get() & 0xff;
            int mods = body.get() & 0xff;
            int clicks = body.get() & 0xff;
            int modifiers = keyModifiers(mods) | buttonsToDownMask(buttons);
            switch (kind) {
            case Protocol.MOUSE_MOVE -> {
                int id = buttons == 0 ? MouseEvent.MOUSE_MOVED : MouseEvent.MOUSE_DRAGGED;
                events.postMouseEvent(id, x, y, modifiers, MouseEvent.NOBUTTON, 0);
            }
            case Protocol.MOUSE_PRESS -> events.postMouseEvent(MouseEvent.MOUSE_PRESSED, x, y,
                    modifiers | buttonToDownMask(button), buttonToDownMask(button), Math.max(1, clicks));
            case Protocol.MOUSE_RELEASE -> events.postMouseEvent(MouseEvent.MOUSE_RELEASED, x, y,
                    modifiers | buttonToDownMask(button), buttonToDownMask(button), Math.max(1, clicks));
            default -> logger.fine(() -> "unknown mouse kind: " + kind);
            }
        }
        case Protocol.OP_WHEEL -> {
            int x = u16(body);
            int y = u16(body);
            int deltaY = s16(body);
            int mods = body.get() & 0xff;
            events.postWheelEvent(x, y, keyModifiers(mods), deltaY < 0);
        }
        case Protocol.OP_KEY -> {
            int kind = body.get() & 0xff;
            int jsKeyCode = u16(body);
            char c = (char) u16(body);
            int mods = body.get() & 0xff;
            body.get(); // location, unused for now
            int modifiers = keyModifiers(mods);
            switch (kind) {
            case Protocol.KEY_PRESS -> events.postKeyEvent(KeyEvent.KEY_PRESSED,
                    KeyCodeMapper.toVK(jsKeyCode), c == 0 ? KeyEvent.CHAR_UNDEFINED : c, modifiers);
            case Protocol.KEY_RELEASE -> events.postKeyEvent(KeyEvent.KEY_RELEASED,
                    KeyCodeMapper.toVK(jsKeyCode), c == 0 ? KeyEvent.CHAR_UNDEFINED : c, modifiers);
            case Protocol.KEY_TYPED -> events.postKeyEvent(KeyEvent.KEY_TYPED,
                    KeyEvent.VK_UNDEFINED, c, modifiers);
            default -> logger.fine(() -> "unknown key kind: " + kind);
            }
        }
        case Protocol.OP_CLIENT_RESIZE -> {
            int w = u16(body);
            int h = u16(body);
            listener.onClientResize(w, h);
        }
        case Protocol.OP_PING -> listener.onPing(body.getInt() & 0xffffffffL);
        default -> logger.fine(() -> "skipping unknown opcode: 0x" + Integer.toHexString(opcode));
        }
    }
}
