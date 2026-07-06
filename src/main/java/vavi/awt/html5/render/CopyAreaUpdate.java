/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.render;


/**
 * Copy of the source rect (x, y, width, height) shifted by (dx, dy) —
 * a canvas self-copy on the browser side. Used as a copyArea hint: block
 * moves such as a title-bar window drag are queued on {@code Html5Screen}
 * and turned into {@code COPY_AREA} messages by the frame pump.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public record CopyAreaUpdate(int x, int y, int width, int height, int dx, int dy) implements ScreenUpdate {
}
