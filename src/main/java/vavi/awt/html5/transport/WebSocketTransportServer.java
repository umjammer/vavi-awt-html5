/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.transport;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import vavi.awt.html5.Html5EventSource;
import vavi.awt.html5.Html5Screen;
import vavi.awt.html5.protocol.InputEventDecoder;
import vavi.awt.html5.protocol.MessageReader;
import vavi.awt.html5.protocol.MessageWriter;


/**
 * Browser transport over a plain {@code ws://} WebSocket (pure Java,
 * {@code org.java-websocket}). The same length-prefixed binary protocol
 * rides inside WebSocket binary messages: each server frame is one message,
 * inbound bytes are streamed into the shared {@link MessageReader} /
 * {@link InputEventDecoder}. Served insecure because the page origin is
 * {@code http://localhost}, for which browsers permit {@code ws://}.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public class WebSocketTransportServer extends WebSocketServer {

    private static final Logger logger = Logger.getLogger(WebSocketTransportServer.class.getName());

    /** per-connection outbound writer and the pipe feeding the inbound reader */
    private record Conn(MessageWriter writer, PipedOutputStream sink) {
    }

    private final SessionManager sessions;
    private final Map<WebSocket, Conn> conns = new ConcurrentHashMap<>();

    public WebSocketTransportServer(int port, SessionManager sessions) {
        super(new InetSocketAddress(port));
        this.sessions = sessions;
        setReuseAddr(true);
    }

    @Override
    public void onStart() {
        logger.info(() -> "websocket server listening on ws port " + getPort());
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        logger.info(() -> "websocket session from " + conn.getRemoteSocketAddress());
        MessageWriter writer = new MessageWriter(new WebSocketOutputStream(conn));

        PipedOutputStream sink = new PipedOutputStream();
        InputEventDecoder decoder = new InputEventDecoder(Html5EventSource.getInstance(),
                new InputEventDecoder.Listener() {
            @Override
            public void onHello(int version, int viewW, int viewH) throws IOException {
                // the browser viewport defines the virtual screen size
                Html5Screen.getInstance().setClientViewportSize(viewW, viewH);
                var bounds = Html5Screen.getInstance().getBounds();
                writer.writeInit(bounds.width, bounds.height);
                // the frame pump detects the new session and sends a full frame
                sessions.attach(writer);
            }

            @Override
            public void onPing(long nonce) throws IOException {
                writer.writePong(nonce);
            }

            @Override
            public void onClientResize(int viewW, int viewH) {
                Html5Screen.getInstance().setClientViewportSize(viewW, viewH);
            }
        });

        try {
            PipedInputStream source = new PipedInputStream(sink, 1 << 16);
            Thread reader = new Thread(() -> {
                try {
                    MessageReader.readLoop(source, decoder);
                } catch (IOException e) {
                    logger.log(Level.FINE, "ws read loop ended: " + e);
                }
            }, "html5-ws-reader");
            reader.setDaemon(true);
            reader.start();
        } catch (IOException e) {
            logger.log(Level.WARNING, "cannot start ws reader", e);
            conn.close();
            return;
        }

        conns.put(conn, new Conn(writer, sink));
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        Conn c = conns.get(conn);
        if (c == null) {
            return;
        }
        try {
            byte[] bytes = new byte[message.remaining()];
            message.get(bytes);
            c.sink().write(bytes);
            c.sink().flush();
        } catch (IOException e) {
            logger.log(Level.FINE, "ws inbound write failed: " + e);
            conn.close();
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        // text frames are not used by the protocol
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Conn c = conns.remove(conn);
        if (c != null) {
            sessions.detach(c.writer());
            try {
                c.sink().close();
            } catch (IOException e) {
                logger.log(Level.FINE, "closing ws pipe", e);
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        logger.log(Level.FINE, "websocket error", ex);
    }

    /** adapts one WebSocket connection to the OutputStream MessageWriter expects; each flush is one binary message */
    private static final class WebSocketOutputStream extends OutputStream {

        private final WebSocket conn;
        private java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream(64 * 1024);

        WebSocketOutputStream(WebSocket conn) {
            this.conn = conn;
        }

        @Override
        public synchronized void write(int b) {
            buffer.write(b);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            buffer.write(b, off, len);
        }

        @Override
        public synchronized void flush() throws IOException {
            if (buffer.size() == 0) {
                return;
            }
            try {
                conn.send(buffer.toByteArray());
            } catch (RuntimeException e) {
                throw new IOException("websocket send failed", e);
            }
            buffer.reset();
        }

        @Override
        public void close() {
            conn.close();
        }
    }
}
