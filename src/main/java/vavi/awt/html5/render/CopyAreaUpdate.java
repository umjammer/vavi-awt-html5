/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.render;


/**
 * Copy of the source rect (x, y, width, height) shifted by (dx, dy) —
 * a canvas self-copy on the browser side. Reserved for the copyArea
 * optimization; v1 sends blits instead.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public record CopyAreaUpdate(int x, int y, int width, int height, int dx, int dy) implements ScreenUpdate {
}
