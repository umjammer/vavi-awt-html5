/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5;

import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;


/**
 * Graphics configuration of the virtual screen. Bootstrap-injectable
 * (JDK references only), therefore it parses {@code cacio.managed.screensize}
 * itself instead of referencing cacio classes.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public class Html5GraphicsConfiguration extends GraphicsConfiguration {

    private static final Dimension screenSize;
    static {
        String size = System.getProperty("cacio.managed.screensize", "1024x768");
        int x = size.indexOf('x');
        screenSize = new Dimension(Integer.parseInt(size.substring(0, x)),
                                   Integer.parseInt(size.substring(x + 1)));
    }

    private final ColorModel model;
    private final Raster raster;
    private final Html5GraphicsDevice device;

    Html5GraphicsConfiguration(Html5GraphicsDevice dev) {
        BufferedImage bi = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        device = dev;
        model = bi.getColorModel();
        raster = bi.getRaster().createCompatibleWritableRaster(1, 1);
    }

    @Override
    public GraphicsDevice getDevice() {
        return device;
    }

    @Override
    public ColorModel getColorModel() {
        return ColorModel.getRGBdefault();
    }

    @Override
    public ColorModel getColorModel(int transparency) {
        return ColorModel.getRGBdefault();
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(0, 0, screenSize.width, screenSize.height);
    }

    @Override
    public BufferedImage createCompatibleImage(int width, int height) {
        WritableRaster wr = raster.createCompatibleWritableRaster(width, height);
        return new BufferedImage(model, wr, model.isAlphaPremultiplied(), null);
    }

    @Override
    public AffineTransform getDefaultTransform() {
        return new AffineTransform();
    }

    @Override
    public AffineTransform getNormalizingTransform() {
        return new AffineTransform();
    }
}
