/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.render;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;


/**
 * Accumulates dirty rectangles reported by {@link TrackingGraphics2D} and
 * coalesces them until the frame pump flushes. Rectangles closer than
 * {@link #MERGE_SLOP} pixels are unioned; the pending set is capped at
 * {@link #MAX_RECTS} by unioning everything (bandwidth beats precision
 * beyond that point).
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public class DamageTracker {

    private static final int MERGE_SLOP = 8;
    private static final int MAX_RECTS = 64;

    private final Rectangle screenBounds;
    private final List<Rectangle> pending = new ArrayList<>();

    public DamageTracker(Rectangle screenBounds) {
        this.screenBounds = new Rectangle(screenBounds);
    }

    public synchronized void trackRect(Rectangle r) {
        Rectangle clipped = r.intersection(screenBounds);
        if (clipped.isEmpty()) {
            return;
        }
        Rectangle grown = new Rectangle(clipped.x - MERGE_SLOP, clipped.y - MERGE_SLOP,
                clipped.width + 2 * MERGE_SLOP, clipped.height + 2 * MERGE_SLOP);
        // union with any overlapping rect, repeating because the union may
        // newly overlap others
        Rectangle acc = clipped;
        boolean merged = true;
        while (merged) {
            merged = false;
            for (int i = pending.size() - 1; i >= 0; i--) {
                Rectangle p = pending.get(i);
                if (grown.intersects(p)) {
                    pending.remove(i);
                    acc = acc.union(p);
                    grown = new Rectangle(acc.x - MERGE_SLOP, acc.y - MERGE_SLOP,
                            acc.width + 2 * MERGE_SLOP, acc.height + 2 * MERGE_SLOP);
                    merged = true;
                }
            }
        }
        pending.add(acc);
        if (pending.size() > MAX_RECTS) {
            Rectangle all = pending.getFirst();
            for (Rectangle p : pending) {
                all = all.union(p);
            }
            pending.clear();
            pending.add(all);
        }
    }

    /**
     * v1 treats copyArea as plain damage of the destination area; the
     * COPY_AREA protocol opcode is reserved for a later optimization.
     */
    public synchronized void trackCopyArea(int x, int y, int width, int height, int dx, int dy) {
        trackRect(new Rectangle(x + dx, y + dy, width, height));
    }

    public synchronized void damageAll() {
        pending.clear();
        pending.add(new Rectangle(screenBounds));
    }

    public synchronized boolean hasDamage() {
        return !pending.isEmpty();
    }

    /** returns and clears the pending dirty rectangles */
    public synchronized List<Rectangle> flushRects() {
        List<Rectangle> out = new ArrayList<>(pending);
        pending.clear();
        return out;
    }

    /**
     * Flushes pending damage as blit updates carrying deep copies of the
     * corresponding framebuffer regions (decoupled from concurrent rendering).
     */
    public List<ScreenUpdate> flush(BufferedImage framebuffer) {
        List<Rectangle> rects = flushRects();
        List<ScreenUpdate> updates = new ArrayList<>(rects.size());
        for (Rectangle r : rects) {
            BufferedImage copy = new BufferedImage(r.width, r.height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = copy.createGraphics();
            g.drawImage(framebuffer,
                    0, 0, r.width, r.height,
                    r.x, r.y, r.x + r.width, r.y + r.height,
                    null);
            g.dispose();
            updates.add(new BlitUpdate(r.x, r.y, r.width, r.height, copy));
        }
        return updates;
    }

    public Rectangle getScreenBounds() {
        return new Rectangle(screenBounds);
    }
}
