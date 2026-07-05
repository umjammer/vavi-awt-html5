/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.client;


/**
 * Reassembles length-prefixed protocol frames from arbitrary stream chunks
 * and dispatches them to the renderer.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
class FrameParser {

    private final CanvasRenderer renderer;

    private byte[] buffer = new byte[64 * 1024];
    private int size;

    FrameParser(CanvasRenderer renderer) {
        this.renderer = renderer;
    }

    void feed(byte[] chunk, int length) {
        if (size + length > buffer.length) {
            int newCap = buffer.length;
            while (newCap < size + length) {
                newCap *= 2;
            }
            byte[] grown = new byte[newCap];
            System.arraycopy(buffer, 0, grown, 0, size);
            buffer = grown;
        }
        System.arraycopy(chunk, 0, buffer, size, length);
        size += length;

        int off = 0;
        while (size - off >= 4) {
            int len = ((buffer[off] & 0xff) << 24) | ((buffer[off + 1] & 0xff) << 16)
                    | ((buffer[off + 2] & 0xff) << 8) | (buffer[off + 3] & 0xff);
            if (size - off - 4 < len) {
                break;
            }
            dispatch(buffer[off + 4] & 0xff, off + 5, len - 1);
            off += 4 + len;
        }
        if (off > 0) {
            System.arraycopy(buffer, off, buffer, 0, size - off);
            size -= off;
        }
    }

    private int u16(int off) {
        return ((buffer[off] & 0xff) << 8) | (buffer[off + 1] & 0xff);
    }

    private void dispatch(int opcode, int off, int len) {
        switch (opcode) {
        case ClientProtocol.OP_INIT: {
            int width = u16(off + 2);
            int height = u16(off + 4);
            renderer.resize(width, height);
            Js.status("connected " + width + "x" + height);
            break;
        }
        case ClientProtocol.OP_RESIZE: {
            renderer.resize(u16(off), u16(off + 2));
            break;
        }
        case ClientProtocol.OP_BLIT: {
            int x = u16(off);
            int y = u16(off + 2);
            int pngLen = ((buffer[off + 8] & 0xff) << 24) | ((buffer[off + 9] & 0xff) << 16)
                    | ((buffer[off + 10] & 0xff) << 8) | (buffer[off + 11] & 0xff);
            byte[] png = new byte[pngLen];
            System.arraycopy(buffer, off + 12, png, 0, pngLen);
            renderer.blit(x, y, png);
            break;
        }
        case ClientProtocol.OP_COPY_AREA: {
            int x = u16(off);
            int y = u16(off + 2);
            int w = u16(off + 4);
            int h = u16(off + 6);
            int dx = (short) u16(off + 8);
            int dy = (short) u16(off + 10);
            renderer.copyArea(x, y, w, h, dx, dy);
            break;
        }
        case ClientProtocol.OP_FRAME_END:
        case ClientProtocol.OP_PONG:
            break;
        default:
            // forward compatible: skip unknown opcodes
        }
    }
}
