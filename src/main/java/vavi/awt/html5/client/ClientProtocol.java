/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.client;


/**
 * Client-side mirror of the wire protocol constants (deliberately not
 * shared with the server sources: the client is compiled for TeaVM at a
 * lower class-file level).
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
final class ClientProtocol {

    static final int VERSION = 1;

    // server -> client
    static final int OP_INIT = 0x01;
    static final int OP_BLIT = 0x02;
    static final int OP_COPY_AREA = 0x03;
    static final int OP_FRAME_END = 0x04;
    static final int OP_RESIZE = 0x05;
    static final int OP_PONG = 0x0e;

    // client -> server
    static final int OP_HELLO = 0x81;
    static final int OP_MOUSE = 0x82;
    static final int OP_WHEEL = 0x83;
    static final int OP_KEY = 0x84;
    static final int OP_CLIENT_RESIZE = 0x85;
    static final int OP_PING = 0x8e;

    static final int MOUSE_MOVE = 0;
    static final int MOUSE_PRESS = 1;
    static final int MOUSE_RELEASE = 2;

    static final int KEY_PRESS = 0;
    static final int KEY_RELEASE = 1;
    static final int KEY_TYPED = 2;

    static final int MOD_SHIFT = 1;
    static final int MOD_CTRL = 2;
    static final int MOD_ALT = 4;
    static final int MOD_META = 8;

    private ClientProtocol() {
    }
}
