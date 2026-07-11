/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.client;

import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.KeyboardEvent;
import org.teavm.jso.dom.events.MouseEvent;
import org.teavm.jso.dom.events.WheelEvent;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.dom.html.HTMLDocument;


/**
 * Captures browser input on the canvas and forwards it as protocol
 * messages. Buttons: JS 0/1/2 map to protocol 1/2/3 (left/middle/right).
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
final class InputCapture {

    private InputCapture() {
    }

    private static int mods(boolean shift, boolean ctrl, boolean alt, boolean meta) {
        int m = 0;
        if (shift) m |= ClientProtocol.MOD_SHIFT;
        if (ctrl) m |= ClientProtocol.MOD_CTRL;
        if (alt) m |= ClientProtocol.MOD_ALT;
        if (meta) m |= ClientProtocol.MOD_META;
        return m;
    }

    private static int mouseMods(MouseEvent e) {
        return mods(e.getShiftKey(), e.getCtrlKey(), e.getAltKey(), e.getMetaKey());
    }

    private static int keyMods(KeyboardEvent e) {
        return mods(e.isShiftKey(), e.isCtrlKey(), e.isAltKey(), e.isMetaKey());
    }

    /** JS MouseEvent.buttons bitfield (1 left, 4 middle, 2 right) to protocol bits (1 left, 2 middle, 4 right) */
    private static int buttonsState(MouseEvent e) {
        int js = e.getButtons();
        int m = 0;
        if ((js & 1) != 0) m |= 1;
        if ((js & 4) != 0) m |= 2;
        if ((js & 2) != 0) m |= 4;
        return m;
    }

    private static int button(MouseEvent e) {
        return switch (e.getButton()) {
            case 0 -> 1;
            case 1 -> 2;
            case 2 -> 3;
            default -> 0;
        };
    }

    static void install(HTMLCanvasElement canvas, HTMLDocument document, MsgSender sender) {
        canvas.addEventListener("mousedown", evt -> {
            MouseEvent e = (MouseEvent) evt;
            e.preventDefault();
            canvas.focus();
            sender.mouse(ClientProtocol.MOUSE_PRESS, e.getOffsetX(), e.getOffsetY(),
                    button(e), buttonsState(e), mouseMods(e), e.getDetail());
        });
        canvas.addEventListener("mouseup", evt -> {
            MouseEvent e = (MouseEvent) evt;
            e.preventDefault();
            sender.mouse(ClientProtocol.MOUSE_RELEASE, e.getOffsetX(), e.getOffsetY(),
                    button(e), buttonsState(e), mouseMods(e), e.getDetail());
        });
        canvas.addEventListener("mousemove", evt -> {
            MouseEvent e = (MouseEvent) evt;
            sender.mouse(ClientProtocol.MOUSE_MOVE, e.getOffsetX(), e.getOffsetY(),
                    0, buttonsState(e), mouseMods(e), 0);
        });
        canvas.addEventListener("contextmenu", Event::preventDefault);
        canvas.addEventListener("wheel", evt -> {
            WheelEvent e = (WheelEvent) evt;
            e.preventDefault();
            int dy = e.getDeltaY() > 0 ? 1 : e.getDeltaY() < 0 ? -1 : 0;
            if (dy != 0) {
                sender.wheel(((MouseEvent) evt).getOffsetX(), ((MouseEvent) evt).getOffsetY(),
                        dy, mouseMods((MouseEvent) evt));
            }
        });

        document.addEventListener("keydown", evt -> {
            KeyboardEvent e = (KeyboardEvent) evt;
            e.preventDefault();
            sender.key(ClientProtocol.KEY_PRESS, e.getKeyCode(), (char) 0, keyMods(e), e.getLocation());
            String key = e.getKey();
            // printable characters also produce a typed event
            if (key != null && key.length() == 1 && !e.isCtrlKey() && !e.isMetaKey()) {
                sender.key(ClientProtocol.KEY_TYPED, 0, key.charAt(0), keyMods(e), e.getLocation());
            }
            if ("Enter".equals(key)) {
                sender.key(ClientProtocol.KEY_TYPED, 0, '\n', keyMods(e), e.getLocation());
            }
        });
        document.addEventListener("keyup", evt -> {
            KeyboardEvent e = (KeyboardEvent) evt;
            e.preventDefault();
            sender.key(ClientProtocol.KEY_RELEASE, e.getKeyCode(), (char) 0, keyMods(e), e.getLocation());
        });
    }
}
