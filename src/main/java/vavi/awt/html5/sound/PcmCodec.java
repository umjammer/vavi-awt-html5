/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.sound;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;


/**
 * Converts the PCM variants the mixer accepts (signed/unsigned, 8/16-bit,
 * either endianness, 1–2 channels) to the single wire format: s16 big-endian
 * interleaved.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-11 nsano initial version <br>
 */
final class PcmCodec {

    private PcmCodec() {
    }

    static boolean isSupported(AudioFormat f) {
        AudioFormat.Encoding enc = f.getEncoding();
        boolean pcm = AudioFormat.Encoding.PCM_SIGNED.equals(enc)
                || AudioFormat.Encoding.PCM_UNSIGNED.equals(enc);
        int bits = f.getSampleSizeInBits();
        int channels = f.getChannels();
        return pcm && (bits == 8 || bits == 16) && (channels == 1 || channels == 2)
                && f.getSampleRate() > 0;
    }

    static void requireSupported(AudioFormat f) throws LineUnavailableException {
        if (!isSupported(f)) {
            throw new LineUnavailableException("unsupported format: " + f);
        }
    }

    /** converts {@code samples} samples (frames * channels) starting at {@code off} to s16be */
    static byte[] toS16be(byte[] src, int off, int samples, AudioFormat f) {
        boolean signed = AudioFormat.Encoding.PCM_SIGNED.equals(f.getEncoding());
        byte[] out = new byte[samples * 2];
        int p = off;
        if (f.getSampleSizeInBits() == 8) {
            for (int i = 0; i < samples; i++) {
                int v = signed ? src[p] << 8 : ((src[p] & 0xff) - 128) << 8;
                out[2 * i] = (byte) (v >> 8);
                out[2 * i + 1] = (byte) v;
                p++;
            }
        } else {
            boolean be = f.isBigEndian();
            for (int i = 0; i < samples; i++) {
                int b0 = src[p] & 0xff;
                int b1 = src[p + 1] & 0xff;
                int u = be ? (b0 << 8) | b1 : (b1 << 8) | b0;
                int v = signed ? (short) u : u - 32768;
                out[2 * i] = (byte) (v >> 8);
                out[2 * i + 1] = (byte) v;
                p += 2;
            }
        }
        return out;
    }
}
