/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.sound;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import vavi.awt.html5.protocol.MessageWriter;
import vavi.awt.html5.transport.SessionManager;


/**
 * Ships captured audio to the active browser connection. With no browser
 * attached the chunk is dropped; a send failure is logged and dropped too —
 * the transport layer notices the dead connection on its own, and audio must
 * never take the producing line down.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-11 nsano initial version <br>
 */
public class SessionAudioSink implements AudioSink {

    private static final Logger logger = Logger.getLogger(SessionAudioSink.class.getName());

    private final SessionManager sessions;

    public SessionAudioSink(SessionManager sessions) {
        this.sessions = sessions;
    }

    @Override
    public void audioData(int streamId, int sampleRate, int channels, byte[] pcm, int off, int len) {
        MessageWriter writer = sessions.current();
        if (writer == null) {
            return;
        }
        try {
            writer.writeAudio(streamId, sampleRate, channels, pcm, off, len);
        } catch (IOException e) {
            logger.log(Level.FINE, "audio send failed: " + e);
        }
    }

    @Override
    public void audioStop(int streamId) {
        MessageWriter writer = sessions.current();
        if (writer == null) {
            return;
        }
        try {
            writer.writeAudioStop(streamId);
        } catch (IOException e) {
            logger.log(Level.FINE, "audio stop send failed: " + e);
        }
    }
}
