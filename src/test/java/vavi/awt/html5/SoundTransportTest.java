/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import vavi.awt.html5.protocol.Protocol;
import vavi.awt.html5.sound.Html5AudioSystem;
import vavi.awt.html5.sound.SessionAudioSink;
import vavi.awt.html5.transport.SessionManager;
import vavi.awt.html5.transport.WebSocketTransportServer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Audio end-to-end over the real WebSocket transport: javax.sound playback
 * on the server arrives at the (browser-stand-in) client as AUDIO frames.
 */
class SoundTransportTest {

    static int wsPort = 40000 + new Random().nextInt(20000);
    static WebSocketTransportServer server;
    static SessionManager sessions;

    @BeforeAll
    static void setUp() throws Exception {
        ToolkitInstaller.install();
        sessions = new SessionManager();
        server = new WebSocketTransportServer(wsPort, sessions);
        server.start();
        Html5AudioSystem.install(new SessionAudioSink(sessions));
        Html5AudioSystem.registerDefaultProviders();
    }

    @AfterAll
    static void tearDown() throws Exception {
        Html5AudioSystem.install(null);
        server.stop();
    }

    record Frame(int opcode, byte[] body) {
    }

    @Test
    void audioReachesTheClient() throws Exception {
        ConcurrentLinkedQueue<Frame> frames = new ConcurrentLinkedQueue<>();
        CountDownLatch gotInit = new CountDownLatch(1);
        CountDownLatch gotAudioStop = new CountDownLatch(1);

        WebSocketClient client = new WebSocketClient(URI.create("ws://localhost:" + wsPort + "/")) {
            @Override
            public void onOpen(ServerHandshake handshake) {
            }

            @Override
            public void onMessage(ByteBuffer bytes) {
                // the server sends each protocol frame as one ws message
                int len = bytes.getInt();
                int opcode = bytes.get() & 0xff;
                byte[] body = new byte[len - 1];
                bytes.get(body);
                frames.add(new Frame(opcode, body));
                if (opcode == Protocol.OP_INIT) {
                    gotInit.countDown();
                }
                if (opcode == Protocol.OP_AUDIO_STOP) {
                    gotAudioStop.countDown();
                }
            }

            @Override
            public void onMessage(String message) {
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
            }

            @Override
            public void onError(Exception ex) {
            }
        };
        assertTrue(client.connectBlocking(10, TimeUnit.SECONDS), "ws connect failed");

        // HELLO: u32 length, u8 opcode, u16 version, u16 viewW, u16 viewH
        ByteBuffer hello = ByteBuffer.allocate(11);
        hello.putInt(7).put((byte) Protocol.OP_HELLO)
                .putShort((short) Protocol.VERSION).putShort((short) 1024).putShort((short) 768);
        client.send(hello.array());
        assertTrue(gotInit.await(10, TimeUnit.SECONDS), "no INIT received");

        // play a tone through plain javax.sound on the server side
        AudioFormat fmt = new AudioFormat(8000f, 16, 1, true, true);
        int toneFrames = 1600; // 200 ms
        byte[] tone = new byte[toneFrames * 2];
        for (int i = 0; i < toneFrames; i++) {
            short v = (short) (Math.sin(2 * Math.PI * 440 * i / 8000) * 12000);
            tone[2 * i] = (byte) (v >> 8);
            tone[2 * i + 1] = (byte) v;
        }
        SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
        line.open(fmt);
        line.start();
        line.write(tone, 0, tone.length);
        line.drain();
        line.close();

        assertTrue(gotAudioStop.await(10, TimeUnit.SECONDS), "no AUDIO_STOP received");

        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        for (Frame f : frames) {
            if (f.opcode() != Protocol.OP_AUDIO) {
                continue;
            }
            ByteBuffer b = ByteBuffer.wrap(f.body());
            b.get(); // streamId
            assertEquals(8000, b.getInt(), "sample rate");
            assertEquals(1, b.get() & 0xff, "channels");
            byte[] chunk = new byte[b.getInt()];
            b.get(chunk);
            pcm.write(chunk);
        }
        assertArrayEquals(tone, pcm.toByteArray(), "PCM must arrive intact over the wire");

        client.closeBlocking();
    }
}
