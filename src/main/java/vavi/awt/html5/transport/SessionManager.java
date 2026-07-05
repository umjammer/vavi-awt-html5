/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.transport;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import vavi.awt.html5.protocol.MessageWriter;


/**
 * v1 session policy: a single active browser connection. A new connection
 * replaces the previous one and receives a full frame.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public class SessionManager {

    private static final Logger logger = Logger.getLogger(SessionManager.class.getName());

    private volatile MessageWriter current;

    /** installs a new active connection, closing any previous one */
    public synchronized void attach(MessageWriter writer) {
        MessageWriter old = current;
        current = writer;
        if (old != null) {
            try {
                old.close();
            } catch (IOException e) {
                logger.log(Level.FINE, "closing replaced session", e);
            }
        }
    }

    /** removes the connection if it is still the active one */
    public synchronized void detach(MessageWriter writer) {
        if (current == writer) {
            current = null;
        }
    }

    /** the active connection, or null when no browser is attached */
    public MessageWriter current() {
        return current;
    }
}
