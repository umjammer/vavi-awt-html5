/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.client;

import java.util.HashMap;
import java.util.Map;

import org.teavm.jso.typedarrays.Float32Array;


/**
 * Plays incoming PCM chunks through Web Audio. Each stream keeps a schedule
 * cursor: chunks are queued back-to-back on the AudioContext clock, so
 * arrival jitter does not become audible gaps. While the context is still
 * suspended (browser autoplay policy: no user gesture yet) chunks are
 * dropped and the user is told to click; the gesture listeners installed by
 * {@link Js#createAudioContext} resume the context.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-11 nsano initial version <br>
 */
class AudioPlayer {

    /** initial scheduling margin, seconds; absorbs network and decode jitter */
    private static final double LEAD = 0.06;

    private static final class Stream {
        int sampleRate;
        int channels;
        double nextTime;
    }

    private final Map<Integer, Stream> streams = new HashMap<>();
    private Js.AudioCtx ctx;
    private boolean hinted;

    /** s16 big-endian interleaved PCM; the format rides in every chunk */
    void data(int streamId, int sampleRate, int channels, byte[] buf, int off, int len) {
        if (ctx == null) {
            ctx = Js.createAudioContext();
        }
        if (Js.audioSuspended(ctx)) {
            if (!hinted) {
                Js.status("click the canvas to enable audio");
                hinted = true;
            }
            streams.remove(streamId);
            return;
        }
        Stream s = streams.get(streamId);
        if (s == null || s.sampleRate != sampleRate || s.channels != channels) {
            s = new Stream();
            s.sampleRate = sampleRate;
            s.channels = channels;
            streams.put(streamId, s);
        }
        int frames = len / (2 * channels);
        if (frames == 0 || sampleRate <= 0) {
            return;
        }
        Float32Array ch0 = new Float32Array(frames);
        Float32Array ch1 = channels >= 2 ? new Float32Array(frames) : null;
        int p = off;
        for (int i = 0; i < frames; i++) {
            ch0.set(i, ((buf[p] << 8) | (buf[p + 1] & 0xff)) / 32768f);
            p += 2;
            if (ch1 != null) {
                ch1.set(i, ((buf[p] << 8) | (buf[p + 1] & 0xff)) / 32768f);
                p += 2;
            }
        }
        double now = Js.audioCurrentTime(ctx);
        if (s.nextTime < now + LEAD) {
            s.nextTime = now + LEAD;
        }
        Js.playPcm(ctx, ch0, ch1, sampleRate, s.nextTime);
        s.nextTime += (double) frames / sampleRate;
    }

    void stop(int streamId) {
        streams.remove(streamId);
    }
}
