/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;


/**
 * The virtual screen device. Bootstrap-injectable (JDK references only).
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public class Html5GraphicsDevice extends GraphicsDevice {

    private Html5GraphicsConfiguration defaultConfig;

    @Override
    public int getType() {
        return GraphicsDevice.TYPE_RASTER_SCREEN;
    }

    @Override
    public String getIDstring() {
        return "vavi-html5";
    }

    @Override
    public GraphicsConfiguration[] getConfigurations() {
        return new GraphicsConfiguration[] { getDefaultConfiguration() };
    }

    @Override
    public GraphicsConfiguration getDefaultConfiguration() {
        if (defaultConfig == null) {
            defaultConfig = new Html5GraphicsConfiguration(this);
        }
        return defaultConfig;
    }
}
