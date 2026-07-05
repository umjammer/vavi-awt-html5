/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.transport;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import tech.kwik.core.server.ServerConnectionConfig;
import tech.kwik.core.server.ServerConnector;
import tech.kwik.flupke.server.HttpRequestHandler;
import tech.kwik.flupke.webtransport.Session;
import tech.kwik.flupke.webtransport.WebTransportHttp3ApplicationProtocolFactory;
import tech.kwik.flupke.webtransport.WebTransportStream;
import vavi.awt.html5.Html5EventSource;
import vavi.awt.html5.Html5Screen;
import vavi.awt.html5.protocol.InputEventDecoder;
import vavi.awt.html5.protocol.MessageReader;
import vavi.awt.html5.protocol.MessageWriter;
import vavi.awt.html5.render.FramePump;

import static tech.kwik.flupke.server.Http3ApplicationProtocolFactory.HTTP3_PROTOCOL_ID;


/**
 * WebTransport endpoint (HTTP/3 over QUIC, pure Java via kwik + flupke).
 * The browser opens one bidirectional stream per session; that stream
 * carries length-prefixed protocol frames in both directions: input events
 * inbound, screen updates outbound (written by the frame pump through
 * {@link SessionManager}).
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public class Http3WebTransportServer {

    private static final Logger logger = Logger.getLogger(Http3WebTransportServer.class.getName());

    public static final String PATH = "/wt";

    private final int port;
    private final CertManager.ServerCert cert;
    private final SessionManager sessions;
    private ServerConnector serverConnector;

    public Http3WebTransportServer(int port, CertManager.ServerCert cert, SessionManager sessions) {
        this.port = port;
        this.cert = cert;
        this.sessions = sessions;
    }

    public void start() throws Exception {
        ServerConnectionConfig config = ServerConnectionConfig.builder()
                .maxOpenPeerInitiatedUnidirectionalStreams(10)
                .maxOpenPeerInitiatedBidirectionalStreams(10)
                .build();

        serverConnector = ServerConnector.builder()
                .withPort(port)
                .withKeyStore(cert.keyStore(), cert.alias(), cert.password())
                .withConfiguration(config)
                .withLogger(Boolean.getBoolean("vavi.awt.html5.debug")
                        ? new tech.kwik.core.log.SysOutLogger()
                        : new tech.kwik.core.log.NullLogger())
                .build();

        HttpRequestHandler notFoundHandler = (request, response) -> {
            if (!request.path().equals(PATH)) {
                response.setStatus(404);
            }
        };

        WebTransportHttp3ApplicationProtocolFactory factory =
                new WebTransportHttp3ApplicationProtocolFactory(notFoundHandler);
        factory.registerWebTransportServer(PATH, this::handleSession);
        serverConnector.registerApplicationProtocol(HTTP3_PROTOCOL_ID, factory);
        serverConnector.start();
        logger.info(() -> "webtransport server listening on udp/" + port + PATH);
    }

    public void stop() {
        if (serverConnector != null) {
            serverConnector.close();
        }
    }

    private void handleSession(Session session) {
        logger.info(() -> "webtransport session " + session.getSessionId());
        session.registerSessionTerminatedEventListener((errorCode, message) ->
                logger.info(() -> "webtransport session " + session.getSessionId() + " closed: " + errorCode));
        session.setBidirectionalStreamReceiveHandler(stream -> handleStream(session, stream));
        session.open();
    }

    private void handleStream(Session session, WebTransportStream stream) {
        MessageWriter writer = new MessageWriter(stream.getOutputStream());
        InputEventDecoder decoder = new InputEventDecoder(Html5EventSource.getInstance(),
                new InputEventDecoder.Listener() {
            @Override
            public void onHello(int version, int viewW, int viewH) throws IOException {
                var bounds = Html5Screen.getInstance().getBounds();
                writer.writeInit(bounds.width, bounds.height);
                // full repaint for the new client, shipped by the frame pump
                Html5Screen.getInstance().getDamageTracker().damageAll();
                sessions.attach(writer);
            }

            @Override
            public void onPing(long nonce) throws IOException {
                writer.writePong(nonce);
            }

            @Override
            public void onClientResize(int viewW, int viewH) {
                logger.fine(() -> "client viewport " + viewW + "x" + viewH + " (ignored in v1)");
            }
        });

        Thread reader = new Thread(() -> {
            try {
                MessageReader.readLoop(stream.getInputStream(), decoder);
            } catch (IOException e) {
                logger.log(Level.FINE, "session read loop ended: " + e);
            } finally {
                sessions.detach(writer);
            }
        }, "html5-wt-reader-" + session.getSessionId());
        reader.setDaemon(true);
        reader.start();
    }

    /** convenience for FramePump wiring; see {@link FramePump} */
    public SessionManager getSessions() {
        return sessions;
    }
}
