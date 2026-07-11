/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.sound;

import java.util.concurrent.atomic.AtomicInteger;


/**
 * Process-wide wiring between the Java Sound capture side
 * ({@link Html5Mixer} and friends, instantiated by {@code AudioSystem}
 * through the service loader) and the transport side (installed by
 * {@code Main}). Lines keep working with no sink installed: they consume
 * data in real time and discard it, like a muted device.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-11 nsano initial version <br>
 */
public final class Html5AudioSystem {

    private static volatile AudioSink sink;

    private static final AtomicInteger streamIds = new AtomicInteger();

    private Html5AudioSystem() {
    }

    /** installs the transport-backed sink; null uninstalls */
    public static void install(AudioSink newSink) {
        sink = newSink;
    }

    /** the installed sink, or null when audio is not wired */
    public static AudioSink sink() {
        return sink;
    }

    /** protocol stream ids are a u8; wrap-around is harmless (streams are short-lived) */
    static int nextStreamId() {
        return streamIds.getAndIncrement() & 0xff;
    }

    /**
     * Makes {@link Html5Mixer} the default device for {@code SourceDataLine}
     * and {@code Clip} lookups ({@code AudioSystem.getSourceDataLine},
     * {@code AudioSystem.getClip}), unless the user chose a device explicitly.
     */
    public static void registerDefaultProviders() {
        String value = Html5MixerProvider.class.getName() + "#" + Html5Mixer.NAME;
        System.getProperties().putIfAbsent("javax.sound.sampled.SourceDataLine", value);
        System.getProperties().putIfAbsent("javax.sound.sampled.Clip", value);
    }

    /** short tone for {@code Toolkit.beep()}, written straight to the sink */
    public static void beep() {
        AudioSink s = sink;
        if (s == null) {
            return;
        }
        Thread t = new Thread(() -> {
            int rate = 8000;
            int frames = rate * 150 / 1000;
            byte[] pcm = new byte[frames * 2];
            for (int i = 0; i < frames; i++) {
                double env = Math.min(1.0, (frames - i) / (frames * 0.3));
                short v = (short) (Math.sin(2 * Math.PI * 880 * i / rate) * 0.3 * 32767 * env);
                pcm[2 * i] = (byte) (v >> 8);
                pcm[2 * i + 1] = (byte) v;
            }
            int id = nextStreamId();
            s.audioData(id, rate, 1, pcm, 0, pcm.length);
            s.audioStop(id);
        }, "html5-beep");
        t.setDaemon(true);
        t.start();
    }
}
