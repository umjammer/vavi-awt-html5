/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.sound;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.Control;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;


/**
 * A {@code Clip} that plays its preloaded data through an internal
 * {@link Html5SourceDataLine} on a daemon thread; the line's write pacing
 * provides the real-time behavior, including sane loop timing.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-11 nsano initial version <br>
 */
public class Html5Clip implements Clip {

    /** frames per write; small enough that stop() reacts quickly */
    private static final int CHUNK_FRAMES = 2 * 1024;

    private final Html5SourceDataLine line;
    private final List<LineListener> listeners = new CopyOnWriteArrayList<>();

    private byte[] data;
    private AudioFormat format;
    private int frameLength;

    private volatile int framePosition;
    private volatile int loopStart;
    /** exclusive; frameLength means "to the end" */
    private volatile int loopEnd;
    private volatile int loopsRemaining;
    private volatile boolean playing;
    private Thread player;

    Html5Clip(Html5Mixer mixer) {
        this.line = new Html5SourceDataLine(mixer);
    }

    private void fire(LineEvent.Type type) {
        LineEvent event = new LineEvent(this, type, framePosition);
        for (LineListener l : listeners) {
            l.update(event);
        }
    }

    // ---- Clip ----

    @Override
    public synchronized void open(AudioFormat format, byte[] data, int offset, int bufferSize)
            throws LineUnavailableException {
        if (isOpen()) {
            return;
        }
        PcmCodec.requireSupported(format);
        this.format = format;
        this.data = new byte[bufferSize];
        System.arraycopy(data, offset, this.data, 0, bufferSize);
        this.frameLength = bufferSize / format.getFrameSize();
        this.framePosition = 0;
        this.loopStart = 0;
        this.loopEnd = frameLength;
        line.open(format);
        fire(LineEvent.Type.OPEN);
    }

    @Override
    public void open(AudioInputStream stream) throws LineUnavailableException, IOException {
        byte[] bytes = stream.readAllBytes();
        open(stream.getFormat(), bytes, 0, bytes.length);
    }

    @Override
    public void open() throws LineUnavailableException {
        throw new IllegalArgumentException("open() without data is not supported on a Clip");
    }

    @Override
    public int getFrameLength() {
        return frameLength;
    }

    @Override
    public long getMicrosecondLength() {
        return format == null ? 0 : (long) (frameLength * 1_000_000.0 / format.getSampleRate());
    }

    @Override
    public void setFramePosition(int frames) {
        framePosition = Math.max(0, Math.min(frames, frameLength));
    }

    @Override
    public void setMicrosecondPosition(long microseconds) {
        if (format != null) {
            setFramePosition((int) (microseconds / 1_000_000.0 * format.getSampleRate()));
        }
    }

    @Override
    public void setLoopPoints(int start, int end) {
        int e = end == -1 ? frameLength : end + 1; // API end point is inclusive
        if (start < 0 || e > frameLength || start >= e) {
            throw new IllegalArgumentException("invalid loop points: " + start + ".." + end);
        }
        loopStart = start;
        loopEnd = e;
    }

    @Override
    public void loop(int count) {
        loopsRemaining = count;
        start();
    }

    @Override
    public synchronized void start() {
        if (!isOpen() || playing) {
            return;
        }
        if (framePosition >= frameLength) {
            framePosition = 0;
        }
        playing = true;
        player = new Thread(this::play, "html5-clip");
        player.setDaemon(true);
        player.start();
    }

    private void play() {
        line.start();
        fire(LineEvent.Type.START);
        int frameSize = format.getFrameSize();
        while (playing) {
            boolean looping = loopsRemaining != 0;
            int end = looping ? loopEnd : frameLength;
            if (framePosition >= end) {
                break;
            }
            int frames = Math.min(CHUNK_FRAMES, end - framePosition);
            int written = line.write(data, framePosition * frameSize, frames * frameSize);
            framePosition += written / frameSize;
            if (written < frames * frameSize) {
                break; // interrupted by stop()
            }
            if (framePosition >= end && looping) {
                if (loopsRemaining > 0) {
                    loopsRemaining--;
                }
                framePosition = loopStart;
            }
        }
        if (playing) {
            line.drain();
        }
        playing = false;
        line.stop();
        fire(LineEvent.Type.STOP);
    }

    @Override
    public synchronized void stop() {
        if (!playing) {
            return;
        }
        playing = false;
        loopsRemaining = 0;
        if (player != null) {
            player.interrupt(); // aborts a pacing sleep inside line.write
            player = null;
        }
    }

    // ---- DataLine ----

    @Override
    public void drain() {
        line.drain();
    }

    @Override
    public void flush() {
        line.flush();
    }

    @Override
    public boolean isRunning() {
        return playing;
    }

    @Override
    public boolean isActive() {
        return playing;
    }

    @Override
    public AudioFormat getFormat() {
        return format == null ? line.getFormat() : format;
    }

    @Override
    public int getBufferSize() {
        return data == null ? 0 : data.length;
    }

    @Override
    public int available() {
        return 0; // a Clip takes no writes
    }

    @Override
    public int getFramePosition() {
        return framePosition;
    }

    @Override
    public long getLongFramePosition() {
        return framePosition;
    }

    @Override
    public long getMicrosecondPosition() {
        return format == null ? 0 : (long) (framePosition * 1_000_000.0 / format.getSampleRate());
    }

    @Override
    public float getLevel() {
        return AudioSystem.NOT_SPECIFIED;
    }

    // ---- Line ----

    @Override
    public Line.Info getLineInfo() {
        return Html5Mixer.CLIP_INFO;
    }

    @Override
    public synchronized void close() {
        if (!isOpen()) {
            return;
        }
        stop();
        line.close();
        data = null;
        fire(LineEvent.Type.CLOSE);
    }

    @Override
    public boolean isOpen() {
        return line.isOpen();
    }

    @Override
    public Control[] getControls() {
        return new Control[0];
    }

    @Override
    public boolean isControlSupported(Control.Type control) {
        return false;
    }

    @Override
    public Control getControl(Control.Type control) {
        throw new IllegalArgumentException("unsupported control: " + control);
    }

    @Override
    public void addLineListener(LineListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeLineListener(LineListener listener) {
        listeners.remove(listener);
    }
}
