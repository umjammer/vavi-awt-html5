/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.protocol;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;


/**
 * Blocking reader splitting a stream into length-prefixed protocol frames.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public final class MessageReader {

    /** guards against a corrupt or malicious length prefix */
    private static final int MAX_FRAME = 32 * 1024 * 1024;

    public interface Handler {

        /** @param body positioned after the opcode byte */
        void onMessage(int opcode, ByteBuffer body) throws IOException;
    }

    private MessageReader() {
    }

    /** reads frames until EOF; returns normally on clean EOF */
    public static void readLoop(InputStream in, Handler handler) throws IOException {
        while (true) {
            byte[] lenBytes = in.readNBytes(4);
            if (lenBytes.length == 0) {
                return; // clean EOF between frames
            }
            if (lenBytes.length < 4) {
                throw new EOFException("truncated frame length");
            }
            int len = ByteBuffer.wrap(lenBytes).getInt();
            if (len < 1 || len > MAX_FRAME) {
                throw new IOException("bad frame length: " + len);
            }
            byte[] frame = in.readNBytes(len);
            if (frame.length < len) {
                throw new EOFException("truncated frame");
            }
            ByteBuffer body = ByteBuffer.wrap(frame);
            int opcode = body.get() & 0xff;
            handler.onMessage(opcode, body);
        }
    }
}
