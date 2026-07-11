/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;


/**
 * Writes length-prefixed protocol frames onto a stream. Each message is
 * assembled into one buffer and written with a single {@code write} call;
 * all writes are synchronized so the frame pump and control replies can
 * share a connection.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public class MessageWriter {

    private final OutputStream out;

    public MessageWriter(OutputStream out) {
        this.out = out;
    }

    private static void u16(ByteArrayOutputStream b, int v) {
        b.write((v >>> 8) & 0xff);
        b.write(v & 0xff);
    }

    private static void u32(ByteArrayOutputStream b, long v) {
        b.write((int) ((v >>> 24) & 0xff));
        b.write((int) ((v >>> 16) & 0xff));
        b.write((int) ((v >>> 8) & 0xff));
        b.write((int) (v & 0xff));
    }

    private synchronized void frame(int opcode, byte[] body) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(5 + body.length);
        u32(b, 1 + body.length);
        b.write(opcode);
        b.writeBytes(body);
        out.write(b.toByteArray());
        out.flush();
    }

    public void writeInit(int width, int height) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        u16(b, Protocol.VERSION);
        u16(b, width);
        u16(b, height);
        frame(Protocol.OP_INIT, b.toByteArray());
    }

    public void writeBlit(int x, int y, int w, int h, byte[] png) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(12 + png.length);
        u16(b, x);
        u16(b, y);
        u16(b, w);
        u16(b, h);
        u32(b, png.length);
        b.writeBytes(png);
        frame(Protocol.OP_BLIT, b.toByteArray());
    }

    public void writeCopyArea(int x, int y, int w, int h, int dx, int dy) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        u16(b, x);
        u16(b, y);
        u16(b, w);
        u16(b, h);
        u16(b, dx & 0xffff);
        u16(b, dy & 0xffff);
        frame(Protocol.OP_COPY_AREA, b.toByteArray());
    }

    public void writeFrameEnd(long frameSeq) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        u32(b, frameSeq);
        frame(Protocol.OP_FRAME_END, b.toByteArray());
    }

    public void writeResize(int width, int height) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        u16(b, width);
        u16(b, height);
        frame(Protocol.OP_RESIZE, b.toByteArray());
    }

    public void writeAudio(int streamId, int sampleRate, int channels, byte[] pcm, int off, int len) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(10 + len);
        b.write(streamId);
        u32(b, sampleRate);
        b.write(channels);
        u32(b, len);
        b.write(pcm, off, len);
        frame(Protocol.OP_AUDIO, b.toByteArray());
    }

    public void writeAudioStop(int streamId) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(streamId);
        frame(Protocol.OP_AUDIO_STOP, b.toByteArray());
    }

    public void writeCursor(int cursorType) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(cursorType);
        frame(Protocol.OP_CURSOR, b.toByteArray());
    }

    public void writePong(long echo) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        u32(b, echo);
        frame(Protocol.OP_PONG, b.toByteArray());
    }

    // client-side messages, used by the integration tests (the browser
    // client has its own mirror implementation)

    public void writeHello(int viewW, int viewH) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        u16(b, Protocol.VERSION);
        u16(b, viewW);
        u16(b, viewH);
        frame(Protocol.OP_HELLO, b.toByteArray());
    }

    public void writeMouse(int kind, int x, int y, int button, int buttons, int mods, int clickCount) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(kind);
        u16(b, x);
        u16(b, y);
        b.write(button);
        b.write(buttons);
        b.write(mods);
        b.write(clickCount);
        frame(Protocol.OP_MOUSE, b.toByteArray());
    }

    public void writeWheel(int x, int y, int deltaY, int mods) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        u16(b, x);
        u16(b, y);
        u16(b, deltaY & 0xffff);
        b.write(mods);
        frame(Protocol.OP_WHEEL, b.toByteArray());
    }

    public void writeKey(int kind, int jsKeyCode, char c, int mods, int location) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(kind);
        u16(b, jsKeyCode);
        u16(b, c);
        b.write(mods);
        b.write(location);
        frame(Protocol.OP_KEY, b.toByteArray());
    }

    public void writePing(long nonce) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        u32(b, nonce);
        frame(Protocol.OP_PING, b.toByteArray());
    }

    public void close() throws IOException {
        out.close();
    }
}
