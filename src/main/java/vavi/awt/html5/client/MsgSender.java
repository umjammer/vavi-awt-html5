/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.client;

import java.io.ByteArrayOutputStream;

import org.teavm.jso.typedarrays.Uint8Array;


/**
 * Builds length-prefixed protocol frames and hands them to a transport sink
 * (WebSocket or WebTransport stream).
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
class MsgSender {

    /** transport-agnostic byte sink */
    interface ByteSink {
        void send(Uint8Array bytes);
    }

    private final ByteSink sink;

    MsgSender(ByteSink sink) {
        this.sink = sink;
    }

    private static void u16(ByteArrayOutputStream b, int v) {
        b.write((v >>> 8) & 0xff);
        b.write(v & 0xff);
    }

    private void send(int opcode, ByteArrayOutputStream body) {
        int len = 1 + body.size();
        ByteArrayOutputStream frame = new ByteArrayOutputStream(4 + len);
        frame.write((len >>> 24) & 0xff);
        frame.write((len >>> 16) & 0xff);
        frame.write((len >>> 8) & 0xff);
        frame.write(len & 0xff);
        frame.write(opcode);
        byte[] bodyBytes = body.toByteArray();
        frame.write(bodyBytes, 0, bodyBytes.length);
        byte[] bytes = frame.toByteArray();
        Uint8Array arr = new Uint8Array(bytes.length);
        for (int i = 0; i < bytes.length; i++) {
            arr.set(i, (short) (bytes[i] & 0xff));
        }
        sink.send(arr);
    }

    void hello(int viewW, int viewH) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        u16(b, ClientProtocol.VERSION);
        u16(b, viewW);
        u16(b, viewH);
        send(ClientProtocol.OP_HELLO, b);
    }

    void mouse(int kind, int x, int y, int button, int buttons, int mods, int clicks) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(kind);
        u16(b, x);
        u16(b, y);
        b.write(button);
        b.write(buttons);
        b.write(mods);
        b.write(clicks);
        send(ClientProtocol.OP_MOUSE, b);
    }

    void wheel(int x, int y, int deltaY, int mods) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        u16(b, x);
        u16(b, y);
        u16(b, deltaY & 0xffff);
        b.write(mods);
        send(ClientProtocol.OP_WHEEL, b);
    }

    void key(int kind, int jsKeyCode, char c, int mods, int location) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(kind);
        u16(b, jsKeyCode);
        u16(b, c);
        b.write(mods);
        b.write(location);
        send(ClientProtocol.OP_KEY, b);
    }

    void resize(int viewW, int viewH) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        u16(b, viewW);
        u16(b, viewH);
        send(ClientProtocol.OP_CLIENT_RESIZE, b);
    }
}
