/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5;

import java.awt.GraphicsEnvironment;

import net.bytebuddy.implementation.bind.annotation.RuntimeType;


/**
 * Delegation target for the redefined
 * {@code GraphicsEnvironment.getLocalGraphicsEnvironment()}. Injected into
 * the bootstrap classloader together with the graphics environment classes.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public class Html5Interceptor {

    @RuntimeType
    public static GraphicsEnvironment intercept() {
        return Html5GraphicsEnvironment.getInstance();
    }
}
