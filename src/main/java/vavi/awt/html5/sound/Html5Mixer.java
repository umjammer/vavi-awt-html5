/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.sound;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.Control;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;


/**
 * Playback-only mixer whose lines forward PCM to the browser through the
 * installed {@link AudioSink}. Supports {@code SourceDataLine} and
 * {@code Clip} for 8/16-bit signed/unsigned PCM, mono or stereo, any sample
 * rate. There are no target (capture) lines.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-11 nsano initial version <br>
 */
public class Html5Mixer implements Mixer {

    public static final String NAME = "vavi HTML5 Audio";

    static final AudioFormat[] SUPPORTED_FORMATS = supportedFormats();

    private static final Mixer.Info INFO = new Info();

    private static class Info extends Mixer.Info {
        Info() {
            super(NAME, "vavi", "streams javax.sound playback to the browser", "0.0.3");
        }
    }

    private static AudioFormat[] supportedFormats() {
        List<AudioFormat> formats = new ArrayList<>();
        for (AudioFormat.Encoding enc : new AudioFormat.Encoding[] {
                AudioFormat.Encoding.PCM_SIGNED, AudioFormat.Encoding.PCM_UNSIGNED}) {
            for (int bits : new int[] {8, 16}) {
                for (int channels : new int[] {1, 2}) {
                    for (boolean bigEndian : new boolean[] {false, true}) {
                        if (bits == 8 && bigEndian) {
                            continue; // endianness is meaningless for one byte
                        }
                        formats.add(new AudioFormat(enc, AudioSystem.NOT_SPECIFIED, bits, channels,
                                bits / 8 * channels, AudioSystem.NOT_SPECIFIED, bigEndian));
                    }
                }
            }
        }
        return formats.toArray(AudioFormat[]::new);
    }

    static final DataLine.Info SOURCE_DATA_LINE_INFO =
            new DataLine.Info(SourceDataLine.class, SUPPORTED_FORMATS, 0, AudioSystem.NOT_SPECIFIED);
    static final DataLine.Info CLIP_INFO =
            new DataLine.Info(Clip.class, SUPPORTED_FORMATS, 0, AudioSystem.NOT_SPECIFIED);

    private final List<Line> openSourceLines = new CopyOnWriteArrayList<>();

    static Mixer.Info info() {
        return INFO;
    }

    void lineOpened(Line line) {
        openSourceLines.add(line);
    }

    void lineClosed(Line line) {
        openSourceLines.remove(line);
    }

    @Override
    public Mixer.Info getMixerInfo() {
        return INFO;
    }

    @Override
    public Line.Info[] getSourceLineInfo() {
        return new Line.Info[] {SOURCE_DATA_LINE_INFO, CLIP_INFO};
    }

    @Override
    public Line.Info[] getTargetLineInfo() {
        return new Line.Info[0];
    }

    @Override
    public Line.Info[] getSourceLineInfo(Line.Info info) {
        List<Line.Info> matches = new ArrayList<>();
        for (Line.Info our : getSourceLineInfo()) {
            if (info.matches(our)) {
                matches.add(our);
            }
        }
        return matches.toArray(Line.Info[]::new);
    }

    @Override
    public Line.Info[] getTargetLineInfo(Line.Info info) {
        return new Line.Info[0];
    }

    @Override
    public boolean isLineSupported(Line.Info info) {
        for (Line.Info our : getSourceLineInfo()) {
            if (info.matches(our)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Line getLine(Line.Info info) throws LineUnavailableException {
        Class<?> lineClass = info.getLineClass();
        if (lineClass == SourceDataLine.class && info.matches(SOURCE_DATA_LINE_INFO)) {
            return new Html5SourceDataLine(this);
        }
        if (lineClass == Clip.class && info.matches(CLIP_INFO)) {
            return new Html5Clip(this);
        }
        throw new LineUnavailableException("unsupported line: " + info);
    }

    @Override
    public int getMaxLines(Line.Info info) {
        return isLineSupported(info) ? AudioSystem.NOT_SPECIFIED : 0;
    }

    @Override
    public Line[] getSourceLines() {
        return openSourceLines.toArray(Line[]::new);
    }

    @Override
    public Line[] getTargetLines() {
        return new Line[0];
    }

    @Override
    public void synchronize(Line[] lines, boolean maintainSync) {
        throw new IllegalArgumentException("synchronization not supported");
    }

    @Override
    public void unsynchronize(Line[] lines) {
    }

    @Override
    public boolean isSynchronizationSupported(Line[] lines, boolean maintainSync) {
        return false;
    }

    // ---- Line (the mixer itself needs no open/close bookkeeping) ----

    @Override
    public Line.Info getLineInfo() {
        return new Line.Info(Mixer.class);
    }

    @Override
    public void open() {
    }

    @Override
    public void close() {
    }

    @Override
    public boolean isOpen() {
        return true;
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
    }

    @Override
    public void removeLineListener(LineListener listener) {
    }
}
