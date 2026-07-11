/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.SourceDataLine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import vavi.awt.html5.sound.AudioSink;
import vavi.awt.html5.sound.Html5AudioSystem;
import vavi.awt.html5.sound.Html5Clip;
import vavi.awt.html5.sound.Html5SourceDataLine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * The javax.sound capture path: default-device lookup finds the HTML5
 * mixer, written PCM reaches the sink as s16 big-endian, clips loop.
 */
class SoundTest {

    /** collects everything the mixer ships, keyed by nothing — one test, one stream */
    static class RecordingSink implements AudioSink {

        final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        final List<Integer> stops = new ArrayList<>();
        volatile int sampleRate;
        volatile int channels;

        @Override
        public synchronized void audioData(int streamId, int sampleRate, int channels, byte[] data, int off, int len) {
            this.sampleRate = sampleRate;
            this.channels = channels;
            pcm.write(data, off, len);
        }

        @Override
        public synchronized void audioStop(int streamId) {
            stops.add(streamId);
        }
    }

    RecordingSink sink;

    @BeforeAll
    static void defaults() {
        Html5AudioSystem.registerDefaultProviders();
    }

    @BeforeEach
    void setUp() {
        sink = new RecordingSink();
        Html5AudioSystem.install(sink);
    }

    @AfterEach
    void tearDown() {
        Html5AudioSystem.install(null);
    }

    @Test
    void defaultLookupFindsHtml5Mixer() throws Exception {
        AudioFormat fmt = new AudioFormat(8000f, 16, 1, true, true);
        assertInstanceOf(Html5SourceDataLine.class, AudioSystem.getSourceDataLine(fmt));
        assertInstanceOf(Html5Clip.class, AudioSystem.getClip());
    }

    @Test
    void sourceDataLinePassesS16beThrough() throws Exception {
        AudioFormat fmt = new AudioFormat(8000f, 16, 1, true, true);
        SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
        int frames = 800; // 100 ms, inside the pacing lead: no blocking
        byte[] buf = new byte[frames * 2];
        for (int i = 0; i < frames; i++) {
            buf[2 * i] = (byte) (i >> 8);
            buf[2 * i + 1] = (byte) i;
        }
        line.open(fmt);
        line.start();
        assertEquals(buf.length, line.write(buf, 0, buf.length));
        line.drain();
        line.close();

        assertEquals(8000, sink.sampleRate);
        assertEquals(1, sink.channels);
        assertArrayEquals(buf, sink.pcm.toByteArray(), "s16be must pass through unchanged");
        assertEquals(1, sink.stops.size(), "close must end the stream");
    }

    @Test
    void convertsUnsigned8LittleEndianWorld() throws Exception {
        AudioFormat fmt = new AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, 8000f, 8, 1, 1, 8000f, false);
        SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
        line.open(fmt);
        line.start();
        line.write(new byte[] {0, (byte) 128, (byte) 255}, 0, 3);
        line.close();

        assertArrayEquals(new byte[] {
                (byte) 0x80, 0x00,          // 0   -> -32768
                0x00, 0x00,                 // 128 -> 0
                0x7f, 0x00,                 // 255 -> 32512
        }, sink.pcm.toByteArray());
    }

    @Test
    void convertsSigned16LittleEndian() throws Exception {
        AudioFormat fmt = new AudioFormat(8000f, 16, 1, true, false);
        SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
        line.open(fmt);
        line.start();
        line.write(new byte[] {0x34, 0x12, (byte) 0xfe, (byte) 0xff}, 0, 4);
        line.close();

        assertArrayEquals(new byte[] {0x12, 0x34, (byte) 0xff, (byte) 0xfe}, sink.pcm.toByteArray());
    }

    @Test
    void clipLoopsOnce() throws Exception {
        AudioFormat fmt = new AudioFormat(8000f, 16, 1, true, true);
        int frames = 400; // 50 ms
        byte[] buf = new byte[frames * 2];
        for (int i = 0; i < frames; i++) {
            buf[2 * i] = (byte) (i >> 8);
            buf[2 * i + 1] = (byte) i;
        }
        Clip clip = AudioSystem.getClip();
        clip.open(fmt, buf, 0, buf.length);
        assertEquals(frames, clip.getFrameLength());
        clip.loop(1); // play twice in total

        long deadline = System.currentTimeMillis() + 5000;
        while (clip.isRunning() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        clip.close();

        assertEquals(2L * buf.length, sink.pcm.size(), "loop(1) must play the clip twice");
        assertTrue(sink.stops.size() >= 1);
    }
}
