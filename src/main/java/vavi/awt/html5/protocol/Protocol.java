/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.protocol;


/**
 * Binary wire protocol, version 1. All integers big-endian.
 * Frame layout (both directions): {@code u32 length, u8 opcode, body},
 * where length counts opcode + body. Unknown opcodes are skipped.
 *
 * <h2>server to client</h2>
 * <pre>
 * INIT       u16 version, u16 width, u16 height
 * BLIT       u16 x, u16 y, u16 w, u16 h, u32 pngLen, byte[pngLen]
 * COPY_AREA  u16 x, u16 y, u16 w, u16 h, s16 dx, s16 dy   (reserved)
 * FRAME_END  u32 frameSeq
 * RESIZE     u16 width, u16 height
 * PONG       u32 echo
 * </pre>
 *
 * <h2>client to server</h2>
 * <pre>
 * HELLO      u16 version, u16 viewW, u16 viewH
 * MOUSE      u8 kind (0 move, 1 press, 2 release), u16 x, u16 y,
 *            u8 button (0 none, 1 left, 2 middle, 3 right),
 *            u8 buttons (bit0 left, bit1 middle, bit2 right — state after event),
 *            u8 mods, u8 clickCount
 * WHEEL      u16 x, u16 y, s16 deltaY (positive scrolls down), u8 mods
 * KEY        u8 kind (0 press, 1 release, 2 typed), u16 jsKeyCode,
 *            u16 charUtf16, u8 mods, u8 location
 * RESIZE     u16 viewW, u16 viewH
 * PING       u32 nonce
 * </pre>
 *
 * mods bitmask: 1 shift, 2 ctrl, 4 alt, 8 meta.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public final class Protocol {

    public static final int VERSION = 1;

    // server -> client
    public static final int OP_INIT = 0x01;
    public static final int OP_BLIT = 0x02;
    public static final int OP_COPY_AREA = 0x03;
    public static final int OP_FRAME_END = 0x04;
    public static final int OP_RESIZE = 0x05;
    public static final int OP_PONG = 0x0e;

    // client -> server
    public static final int OP_HELLO = 0x81;
    public static final int OP_MOUSE = 0x82;
    public static final int OP_WHEEL = 0x83;
    public static final int OP_KEY = 0x84;
    public static final int OP_CLIENT_RESIZE = 0x85;
    public static final int OP_PING = 0x8e;

    public static final int MOUSE_MOVE = 0;
    public static final int MOUSE_PRESS = 1;
    public static final int MOUSE_RELEASE = 2;

    public static final int KEY_PRESS = 0;
    public static final int KEY_RELEASE = 1;
    public static final int KEY_TYPED = 2;

    public static final int MOD_SHIFT = 1;
    public static final int MOD_CTRL = 2;
    public static final int MOD_ALT = 4;
    public static final int MOD_META = 8;

    private Protocol() {
    }
}
