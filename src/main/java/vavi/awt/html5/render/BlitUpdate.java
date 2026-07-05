/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.render;

import java.awt.image.BufferedImage;


/**
 * A rectangular framebuffer region to draw at (x, y); {@code image} is a
 * deep copy sized {@code width} x {@code height}.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public record BlitUpdate(int x, int y, int width, int height, BufferedImage image) implements ScreenUpdate {
}
