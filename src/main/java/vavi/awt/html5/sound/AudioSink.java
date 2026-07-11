/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.sound;


/**
 * Destination for captured audio. PCM is always s16 big-endian interleaved;
 * the format is carried with every chunk so a receiver that attaches
 * mid-stream can still play it.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-11 nsano initial version <br>
 */
public interface AudioSink {

    /** one chunk of s16 big-endian interleaved PCM */
    void audioData(int streamId, int sampleRate, int channels, byte[] pcm, int off, int len);

    /** the stream has ended; the receiver may drop its per-stream state */
    void audioStop(int streamId);
}
