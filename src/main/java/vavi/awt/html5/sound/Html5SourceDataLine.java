/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.sound;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Control;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;


/**
 * A {@code SourceDataLine} that forwards written PCM (converted to s16
 * big-endian) to the installed {@link AudioSink}. Since there is no real
 * device consuming at sample rate, {@link #write} paces itself: it blocks
 * once the caller gets more than {@link #LEAD_NANOS} ahead of wall-clock
 * playback, which is what streaming apps rely on a hardware line's buffer
 * back-pressure for. The browser does its own scheduling, so server-side
 * pacing only has to bound the rush, not be sample-accurate.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-11 nsano initial version <br>
 */
public class Html5SourceDataLine implements SourceDataLine {

    /** how far ahead of real time a writer may run before write() blocks */
    private static final long LEAD_NANOS = 250_000_000L;

    /** frames per protocol message, to keep transport messages reasonably sized */
    private static final int CHUNK_FRAMES = 8 * 1024;

    private static final AudioFormat DEFAULT_FORMAT =
            new AudioFormat(44100f, 16, 2, true, false);

    private final Html5Mixer mixer;
    private final List<LineListener> listeners = new CopyOnWriteArrayList<>();

    private AudioFormat format = DEFAULT_FORMAT;
    private int bufferSize;
    private int streamId;
    private volatile boolean open;
    private volatile boolean running;

    private long framesWritten;
    /** pacing clock origin; -1 until the first write */
    private long epochNanos = -1;

    Html5SourceDataLine(Html5Mixer mixer) {
        this.mixer = mixer;
    }

    private long framesToNanos(long frames) {
        return (long) (frames * 1_000_000_000.0 / format.getSampleRate());
    }

    private long elapsedNanos() {
        return epochNanos < 0 ? 0 : System.nanoTime() - epochNanos;
    }

    private void fire(LineEvent.Type type) {
        LineEvent event = new LineEvent(this, type, getLongFramePosition());
        for (LineListener l : listeners) {
            l.update(event);
        }
    }

    // ---- SourceDataLine ----

    @Override
    public synchronized void open(AudioFormat format, int bufferSize) throws LineUnavailableException {
        if (open) {
            return;
        }
        PcmCodec.requireSupported(format);
        this.format = format;
        int frameSize = format.getFrameSize();
        this.bufferSize = bufferSize > 0
                ? Math.max(frameSize, bufferSize / frameSize * frameSize)
                : (int) (format.getSampleRate() / 2) * frameSize;
        this.streamId = Html5AudioSystem.nextStreamId();
        this.framesWritten = 0;
        this.epochNanos = -1;
        this.open = true;
        mixer.lineOpened(this);
        fire(LineEvent.Type.OPEN);
    }

    @Override
    public void open(AudioFormat format) throws LineUnavailableException {
        open(format, AudioSystem.NOT_SPECIFIED);
    }

    @Override
    public void open() throws LineUnavailableException {
        open(DEFAULT_FORMAT);
    }

    @Override
    public int write(byte[] b, int off, int len) {
        if (!open) {
            throw new IllegalStateException("line is not open");
        }
        int frameSize = format.getFrameSize();
        if (len % frameSize != 0) {
            throw new IllegalArgumentException("len " + len + " is not an integral number of frames");
        }
        int channels = format.getChannels();
        int sampleRate = Math.round(format.getSampleRate());
        int written = 0;
        while (written < len && open) {
            int frames = Math.min((len - written) / frameSize, CHUNK_FRAMES);

            long now = System.nanoTime();
            if (epochNanos < 0) {
                epochNanos = now;
            }
            long sleepNanos = framesToNanos(framesWritten + frames) - (now - epochNanos) - LEAD_NANOS;
            if (sleepNanos > 0) {
                try {
                    Thread.sleep(sleepNanos / 1_000_000, (int) (sleepNanos % 1_000_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return written;
                }
            }

            AudioSink sink = Html5AudioSystem.sink();
            if (sink != null) {
                byte[] s16be = PcmCodec.toS16be(b, off + written, frames * channels, format);
                sink.audioData(streamId, sampleRate, channels, s16be, 0, s16be.length);
            }
            framesWritten += frames;
            written += frames * frameSize;
        }
        return written;
    }

    @Override
    public void drain() {
        long remaining = framesToNanos(framesWritten) - elapsedNanos();
        if (remaining > 0) {
            try {
                Thread.sleep(remaining / 1_000_000, (int) (remaining % 1_000_000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void flush() {
        // nothing is buffered locally; forget the accumulated lead so the
        // next write is not blocked on audio the caller just discarded
        if (epochNanos >= 0) {
            epochNanos = System.nanoTime() - framesToNanos(framesWritten);
        }
    }

    @Override
    public void start() {
        if (open && !running) {
            running = true;
            fire(LineEvent.Type.START);
        }
    }

    @Override
    public void stop() {
        if (running) {
            running = false;
            fire(LineEvent.Type.STOP);
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isActive() {
        return running;
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    @Override
    public int available() {
        if (!open) {
            return 0;
        }
        long aheadNanos = framesToNanos(framesWritten) - elapsedNanos();
        long roomNanos = LEAD_NANOS - aheadNanos;
        if (roomNanos <= 0) {
            return 0;
        }
        long roomBytes = (long) (roomNanos / 1_000_000_000.0 * format.getSampleRate()) * format.getFrameSize();
        return (int) Math.min(roomBytes, bufferSize);
    }

    @Override
    public int getFramePosition() {
        return (int) getLongFramePosition();
    }

    @Override
    public long getLongFramePosition() {
        long elapsedFrames = (long) (elapsedNanos() / 1_000_000_000.0 * format.getSampleRate());
        return Math.min(framesWritten, elapsedFrames);
    }

    @Override
    public long getMicrosecondPosition() {
        return (long) (getLongFramePosition() * 1_000_000.0 / format.getSampleRate());
    }

    @Override
    public float getLevel() {
        return AudioSystem.NOT_SPECIFIED;
    }

    // ---- Line ----

    @Override
    public Line.Info getLineInfo() {
        return Html5Mixer.SOURCE_DATA_LINE_INFO;
    }

    @Override
    public synchronized void close() {
        if (!open) {
            return;
        }
        stop();
        open = false;
        AudioSink sink = Html5AudioSystem.sink();
        if (sink != null) {
            sink.audioStop(streamId);
        }
        mixer.lineClosed(this);
        fire(LineEvent.Type.CLOSE);
    }

    @Override
    public boolean isOpen() {
        return open;
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
