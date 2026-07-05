/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.protocol;

import java.awt.event.KeyEvent;
import java.util.Map;


/**
 * Maps JavaScript legacy {@code keyCode} values to AWT virtual key codes.
 * Letters, digits, function and navigation keys are identical; the table
 * covers the exceptions.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public final class KeyCodeMapper {

    private static final Map<Integer, Integer> EXCEPTIONS = Map.ofEntries(
            Map.entry(13, KeyEvent.VK_ENTER),          // 13 -> 10
            Map.entry(46, KeyEvent.VK_DELETE),         // 46 -> 127
            Map.entry(45, KeyEvent.VK_INSERT),         // 45 -> 155
            Map.entry(91, KeyEvent.VK_META),           // left meta
            Map.entry(92, KeyEvent.VK_META),
            Map.entry(93, KeyEvent.VK_CONTEXT_MENU),
            Map.entry(186, KeyEvent.VK_SEMICOLON),
            Map.entry(187, KeyEvent.VK_EQUALS),
            Map.entry(188, KeyEvent.VK_COMMA),
            Map.entry(189, KeyEvent.VK_MINUS),
            Map.entry(190, KeyEvent.VK_PERIOD),
            Map.entry(191, KeyEvent.VK_SLASH),
            Map.entry(219, KeyEvent.VK_OPEN_BRACKET),
            Map.entry(220, KeyEvent.VK_BACK_SLASH),
            Map.entry(221, KeyEvent.VK_CLOSE_BRACKET),
            Map.entry(222, KeyEvent.VK_QUOTE),
            Map.entry(173, KeyEvent.VK_MINUS),         // firefox
            Map.entry(61, KeyEvent.VK_EQUALS),         // firefox
            Map.entry(59, KeyEvent.VK_SEMICOLON));     // firefox

    private KeyCodeMapper() {
    }

    public static int toVK(int jsKeyCode) {
        return EXCEPTIONS.getOrDefault(jsKeyCode, jsKeyCode);
    }
}
