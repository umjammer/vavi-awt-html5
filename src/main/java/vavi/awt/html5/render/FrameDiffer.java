/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.render;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;


/**
 * Full-frame diff in fixed-size tiles. Safety net for
 * {@link TrackingGraphics2D}: an alternative damage source
 * ({@code -Dvavi.awt.html5.diff=full}) and the reference in tests proving
 * the tracker misses nothing.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public final class FrameDiffer {

    public static final int TILE = 64;

    private FrameDiffer() {
    }

    /** changed tiles between two same-sized ARGB images, row-merged */
    public static List<Rectangle> diff(BufferedImage prev, BufferedImage cur) {
        int w = cur.getWidth();
        int h = cur.getHeight();
        int[] prevRow = new int[w];
        int[] curRow = new int[w];
        int tilesX = (w + TILE - 1) / TILE;
        int tilesY = (h + TILE - 1) / TILE;
        boolean[][] dirty = new boolean[tilesY][tilesX];

        for (int y = 0; y < h; y++) {
            prev.getRGB(0, y, w, 1, prevRow, 0, w);
            cur.getRGB(0, y, w, 1, curRow, 0, w);
            int ty = y / TILE;
            for (int x = 0; x < w; x++) {
                if (prevRow[x] != curRow[x]) {
                    dirty[ty][x / TILE] = true;
                    x = (x / TILE + 1) * TILE - 1; // rest of this tile row is already dirty
                }
            }
        }

        List<Rectangle> out = new ArrayList<>();
        for (int ty = 0; ty < tilesY; ty++) {
            int runStart = -1;
            for (int tx = 0; tx <= tilesX; tx++) {
                boolean d = tx < tilesX && dirty[ty][tx];
                if (d && runStart < 0) {
                    runStart = tx;
                } else if (!d && runStart >= 0) {
                    int x = runStart * TILE;
                    int y = ty * TILE;
                    out.add(new Rectangle(x, y,
                            Math.min(tx * TILE, w) - x,
                            Math.min((ty + 1) * TILE, h) - y));
                    runStart = -1;
                }
            }
        }
        return out;
    }

    /** deep copy for use as the "previous" frame */
    public static BufferedImage snapshot(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = copy.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }
}
