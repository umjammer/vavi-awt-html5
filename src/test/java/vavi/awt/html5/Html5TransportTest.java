/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5;

import java.awt.Point;
import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import tech.kwik.flupke.Http3Client;
import tech.kwik.flupke.Http3ClientBuilder;
import tech.kwik.flupke.webtransport.ClientSessionFactory;
import tech.kwik.flupke.webtransport.Session;
import tech.kwik.flupke.webtransport.WebTransportStream;
import vavi.awt.html5.protocol.MessageReader;
import vavi.awt.html5.protocol.MessageWriter;
import vavi.awt.html5.protocol.Protocol;
import vavi.awt.html5.render.FramePump;
import vavi.awt.html5.transport.CertManager;
import vavi.awt.html5.transport.Http3WebTransportServer;
import vavi.awt.html5.transport.SessionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Phase 2: full protocol round-trip over real WebTransport (kwik/flupke
 * client against our server) without a browser.
 */
class Html5TransportTest {

    static int wtPort = 40000 + new Random().nextInt(20000);
    static JFrame frame;
    static JButton button;
    static JTextField textField;
    static final CountDownLatch clicked = new CountDownLatch(1);
    static Http3WebTransportServer server;
    static FramePump pump;

    @BeforeAll
    static void setUp() throws Exception {
        ToolkitInstaller.install();

        SwingUtilities.invokeAndWait(() -> {
            frame = new JFrame("transport test");
            JPanel panel = new JPanel();
            button = new JButton("Remote");
            button.addActionListener(e -> clicked.countDown());
            textField = new JTextField(10);
            panel.add(button);
            panel.add(textField);
            frame.getContentPane().add(panel);
            frame.setSize(300, 200);
            frame.setLocation(500, 400);
            frame.setVisible(true);
        });

        CertManager.ServerCert cert = new CertManager(Path.of("target", "wt-test-cert")).ensureCert();
        SessionManager sessions = new SessionManager();
        server = new Http3WebTransportServer(wtPort, cert, sessions);
        server.start();
        pump = new FramePump(Html5Screen.getInstance(), sessions);
        pump.start();
    }

    @AfterAll
    static void tearDown() {
        if (pump != null) {
            pump.shutdown();
        }
        if (server != null) {
            server.stop();
        }
    }

    record Frame(int opcode, byte[] body) {
    }

    @Test
    void protocolRoundTrip() throws Exception {
        // wait until the button is painted so the full blit has content
        AtomicReference<Rectangle> buttonRect = new AtomicReference<>();
        long deadline = System.currentTimeMillis() + 10_000;
        while (buttonRect.get() == null && System.currentTimeMillis() < deadline) {
            SwingUtilities.invokeAndWait(() -> {
                if (button.isShowing()) {
                    Point p = button.getLocationOnScreen();
                    buttonRect.set(new Rectangle(p.x, p.y, button.getWidth(), button.getHeight()));
                }
            });
            Thread.sleep(100);
        }
        assertNotNull(buttonRect.get(), "button never showed");
        Thread.sleep(1000); // let painting settle

        URI uri = URI.create("https://localhost:" + wtPort + Http3WebTransportServer.PATH);
        Http3Client client = (Http3Client) ((Http3ClientBuilder) Http3Client.newBuilder())
                .disableCertificateCheck()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        ClientSessionFactory factory = ClientSessionFactory.newBuilder()
                .serverUri(uri)
                .httpClient(client)
                .build();
        Session session = factory.createSession(uri);
        session.open();
        WebTransportStream stream = session.createBidirectionalStream();

        MessageWriter out = new MessageWriter(stream.getOutputStream());

        ConcurrentLinkedQueue<Frame> frames = new ConcurrentLinkedQueue<>();
        CountDownLatch gotInit = new CountDownLatch(1);
        CountDownLatch gotFrameEnd = new CountDownLatch(1);
        Thread reader = new Thread(() -> {
            try {
                MessageReader.readLoop(stream.getInputStream(), (opcode, body) -> {
                    byte[] rest = new byte[body.remaining()];
                    body.get(rest);
                    frames.add(new Frame(opcode, rest));
                    if (opcode == Protocol.OP_INIT) {
                        gotInit.countDown();
                    }
                    if (opcode == Protocol.OP_FRAME_END) {
                        gotFrameEnd.countDown();
                    }
                });
            } catch (Exception e) {
                // connection teardown at test end
            }
        }, "test-reader");
        reader.setDaemon(true);
        reader.start();

        out.writeHello(1024, 768);

        assertTrue(gotInit.await(10, TimeUnit.SECONDS), "no INIT received");
        assertTrue(gotFrameEnd.await(10, TimeUnit.SECONDS), "no FRAME_END received");

        // validate INIT and reassemble the received blits into a screen image
        BufferedImage received = new BufferedImage(1024, 768, BufferedImage.TYPE_INT_ARGB);
        boolean sawBlit = false;
        for (Frame f : frames) {
            ByteBuffer b = ByteBuffer.wrap(f.body());
            if (f.opcode() == Protocol.OP_INIT) {
                assertEquals(Protocol.VERSION, b.getShort() & 0xffff);
                assertEquals(1024, b.getShort() & 0xffff);
                assertEquals(768, b.getShort() & 0xffff);
            } else if (f.opcode() == Protocol.OP_BLIT) {
                int x = b.getShort() & 0xffff;
                int y = b.getShort() & 0xffff;
                int w = b.getShort() & 0xffff;
                int h = b.getShort() & 0xffff;
                int len = b.getInt();
                byte[] png = new byte[len];
                b.get(png);
                BufferedImage tile = ImageIO.read(new ByteArrayInputStream(png));
                assertEquals(w, tile.getWidth());
                assertEquals(h, tile.getHeight());
                received.getGraphics().drawImage(tile, x, y, null);
                sawBlit = true;
            }
        }
        assertTrue(sawBlit, "no BLIT received");

        // the button area of the received image must contain non-white pixels
        Rectangle r = buttonRect.get();
        boolean nonWhite = false;
        for (int y = r.y; y < r.y + r.height && !nonWhite; y++) {
            for (int x = r.x; x < r.x + r.width && !nonWhite; x++) {
                int p = received.getRGB(x, y);
                nonWhite = (p >>> 24) != 0 && (p & 0xffffff) != 0xffffff;
            }
        }
        assertTrue(nonWhite, "received screen content is blank at the button");

        // remote click
        int cx = r.x + r.width / 2;
        int cy = r.y + r.height / 2;
        out.writeMouse(Protocol.MOUSE_MOVE, cx, cy, 0, 0, 0, 0);
        out.writeMouse(Protocol.MOUSE_PRESS, cx, cy, 1, 1, 0, 1);
        out.writeMouse(Protocol.MOUSE_RELEASE, cx, cy, 1, 0, 0, 1);
        assertTrue(clicked.await(10, TimeUnit.SECONDS), "remote click did not fire");

        // remote typing into the text field
        AtomicReference<Point> tf = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Point p = textField.getLocationOnScreen();
            tf.set(new Point(p.x + textField.getWidth() / 2, p.y + textField.getHeight() / 2));
        });
        out.writeMouse(Protocol.MOUSE_MOVE, tf.get().x, tf.get().y, 0, 0, 0, 0);
        out.writeMouse(Protocol.MOUSE_PRESS, tf.get().x, tf.get().y, 1, 1, 0, 1);
        out.writeMouse(Protocol.MOUSE_RELEASE, tf.get().x, tf.get().y, 1, 0, 0, 1);
        deadline = System.currentTimeMillis() + 5_000;
        while (!textField.isFocusOwner() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(textField.isFocusOwner(), "text field did not gain focus");

        out.writeKey(Protocol.KEY_PRESS, 'X', 'x', 0, 0);
        out.writeKey(Protocol.KEY_TYPED, 0, 'x', 0, 0);
        out.writeKey(Protocol.KEY_RELEASE, 'X', 'x', 0, 0);
        deadline = System.currentTimeMillis() + 5_000;
        while (!"x".equals(textField.getText()) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertEquals("x", textField.getText(), "typed char did not arrive over webtransport");

        session.close();
    }
}
