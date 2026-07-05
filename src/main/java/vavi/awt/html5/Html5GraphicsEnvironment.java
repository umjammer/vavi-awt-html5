/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5;

import java.awt.GraphicsDevice;

import sun.java2d.SunGraphicsEnvironment;


/**
 * Single-virtual-screen graphics environment.
 * <p>
 * Injected into the bootstrap classloader by {@link ToolkitInstaller}, so it
 * must only reference JDK classes and other bootstrap-injected classes
 * ({@link Html5GraphicsDevice}, {@link Html5GraphicsConfiguration}).
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public class Html5GraphicsEnvironment extends SunGraphicsEnvironment {

    private static final Html5GraphicsEnvironment INSTANCE = new Html5GraphicsEnvironment();

    public static Html5GraphicsEnvironment getInstance() {
        return INSTANCE;
    }

    public Html5GraphicsEnvironment() {
    }

    @Override
    protected int getNumScreens() {
        return 1;
    }

    @Override
    protected GraphicsDevice makeScreenDevice(int screennum) {
        return new Html5GraphicsDevice();
    }

    @Override
    public boolean isDisplayLocal() {
        return true;
    }
}
