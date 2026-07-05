/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;


/**
 * Records the device-space bounding box of every draw operation into a
 * {@link DamageTracker} before delegating. Bounds are computed in user
 * space (intersected with the clip), then transformed to device space.
 * Operations whose bounds cannot be computed conservatively damage the
 * current clip (or the whole screen when unclipped).
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public class TrackingGraphics2D extends DelegatingGraphics2D {

    private final DamageTracker tracker;

    public TrackingGraphics2D(Graphics2D delegate, DamageTracker tracker) {
        super(delegate);
        this.tracker = tracker;
    }

    /** transforms user-space bounds to device space, clips, and records them */
    private void track(Rectangle2D userBounds) {
        if (userBounds == null || userBounds.isEmpty()) {
            return;
        }
        Rectangle clip = delegate.getClipBounds();
        Rectangle r = userBounds.getBounds();
        if (clip != null) {
            r = r.intersection(clip);
            if (r.isEmpty()) {
                return;
            }
        }
        AffineTransform t = delegate.getTransform();
        Rectangle dev = t.isIdentity() ? r : t.createTransformedShape(r).getBounds();
        // one pixel slop for anti-aliasing and rounding
        dev.grow(1, 1);
        tracker.trackRect(dev);
    }

    /** fallback for untrackable operations: damage the clip, or everything */
    private void trackClip() {
        Rectangle clip = delegate.getClipBounds();
        if (clip == null) {
            tracker.trackRect(tracker.getScreenBounds());
        } else {
            track(clip);
        }
    }

    private void trackStroked(Shape s) {
        Rectangle2D b = s.getBounds2D();
        int lw = 2;
        Stroke stroke = delegate.getStroke();
        if (stroke instanceof BasicStroke bs) {
            lw = (int) Math.ceil(bs.getLineWidth()) + 1;
        }
        track(new Rectangle2D.Double(b.getX() - lw, b.getY() - lw,
                b.getWidth() + 2 * lw, b.getHeight() + 2 * lw));
    }

    private void trackString(String str, float x, float y) {
        try {
            Rectangle2D b = delegate.getFontMetrics().getStringBounds(str, delegate);
            track(new Rectangle2D.Double(x + b.getX(), y + b.getY(), b.getWidth(), b.getHeight()));
        } catch (RuntimeException e) {
            trackClip();
        }
    }

    @Override public void draw(Shape s) { trackStroked(s); super.draw(s); }
    @Override public void fill(Shape s) { track(s.getBounds2D()); super.fill(s); }

    @Override public void drawLine(int x1, int y1, int x2, int y2) {
        trackStroked(new Rectangle(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1)));
        super.drawLine(x1, y1, x2, y2);
    }
    @Override public void fillRect(int x, int y, int width, int height) { track(new Rectangle(x, y, width, height)); super.fillRect(x, y, width, height); }
    @Override public void clearRect(int x, int y, int width, int height) { track(new Rectangle(x, y, width, height)); super.clearRect(x, y, width, height); }
    @Override public void drawRoundRect(int x, int y, int width, int height, int aw, int ah) { trackStroked(new Rectangle(x, y, width, height)); super.drawRoundRect(x, y, width, height, aw, ah); }
    @Override public void fillRoundRect(int x, int y, int width, int height, int aw, int ah) { track(new Rectangle(x, y, width, height)); super.fillRoundRect(x, y, width, height, aw, ah); }
    @Override public void drawOval(int x, int y, int width, int height) { trackStroked(new Rectangle(x, y, width, height)); super.drawOval(x, y, width, height); }
    @Override public void fillOval(int x, int y, int width, int height) { track(new Rectangle(x, y, width, height)); super.fillOval(x, y, width, height); }
    @Override public void drawArc(int x, int y, int width, int height, int sa, int aa) { trackStroked(new Rectangle(x, y, width, height)); super.drawArc(x, y, width, height, sa, aa); }
    @Override public void fillArc(int x, int y, int width, int height, int sa, int aa) { track(new Rectangle(x, y, width, height)); super.fillArc(x, y, width, height, sa, aa); }

    private static Rectangle polyBounds(int[] xs, int[] ys, int n) {
        if (n == 0) {
            return new Rectangle();
        }
        int minX = xs[0], maxX = xs[0], minY = ys[0], maxY = ys[0];
        for (int i = 1; i < n; i++) {
            minX = Math.min(minX, xs[i]); maxX = Math.max(maxX, xs[i]);
            minY = Math.min(minY, ys[i]); maxY = Math.max(maxY, ys[i]);
        }
        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }

    @Override public void drawPolyline(int[] xs, int[] ys, int n) { trackStroked(polyBounds(xs, ys, n)); super.drawPolyline(xs, ys, n); }
    @Override public void drawPolygon(int[] xs, int[] ys, int n) { trackStroked(polyBounds(xs, ys, n)); super.drawPolygon(xs, ys, n); }
    @Override public void fillPolygon(int[] xs, int[] ys, int n) { track(polyBounds(xs, ys, n)); super.fillPolygon(xs, ys, n); }

    @Override public void drawString(String str, int x, int y) { trackString(str, x, y); super.drawString(str, x, y); }
    @Override public void drawString(String str, float x, float y) { trackString(str, x, y); super.drawString(str, x, y); }
    @Override public void drawString(AttributedCharacterIterator it, int x, int y) { trackClip(); super.drawString(it, x, y); }
    @Override public void drawString(AttributedCharacterIterator it, float x, float y) { trackClip(); super.drawString(it, x, y); }
    @Override public void drawGlyphVector(GlyphVector g, float x, float y) {
        Rectangle2D b = g.getVisualBounds();
        track(new Rectangle2D.Double(x + b.getX() - 1, y + b.getY() - 1, b.getWidth() + 2, b.getHeight() + 2));
        super.drawGlyphVector(g, x, y);
    }

    @Override public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs) {
        if (img != null) {
            Rectangle2D b = new Rectangle2D.Double(0, 0, img.getWidth(obs), img.getHeight(obs));
            track(xform == null ? b : xform.createTransformedShape(b).getBounds2D());
        }
        return super.drawImage(img, xform, obs);
    }
    @Override public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) {
        if (img != null) {
            track(new Rectangle(x, y, img.getWidth(), img.getHeight()));
        }
        super.drawImage(img, op, x, y);
    }
    @Override public void drawRenderedImage(RenderedImage img, AffineTransform xform) { trackClip(); super.drawRenderedImage(img, xform); }
    @Override public void drawRenderableImage(RenderableImage img, AffineTransform xform) { trackClip(); super.drawRenderableImage(img, xform); }
    @Override public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
        if (img != null) {
            track(new Rectangle(x, y, img.getWidth(observer), img.getHeight(observer)));
        }
        return super.drawImage(img, x, y, observer);
    }
    @Override public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer) {
        track(new Rectangle(x, y, width, height));
        return super.drawImage(img, x, y, width, height, observer);
    }
    @Override public boolean drawImage(Image img, int x, int y, Color bgcolor, ImageObserver observer) {
        if (img != null) {
            track(new Rectangle(x, y, img.getWidth(observer), img.getHeight(observer)));
        }
        return super.drawImage(img, x, y, bgcolor, observer);
    }
    @Override public boolean drawImage(Image img, int x, int y, int width, int height, Color bgcolor, ImageObserver observer) {
        track(new Rectangle(x, y, width, height));
        return super.drawImage(img, x, y, width, height, bgcolor, observer);
    }
    @Override public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, ImageObserver observer) {
        track(new Rectangle(Math.min(dx1, dx2), Math.min(dy1, dy2), Math.abs(dx2 - dx1), Math.abs(dy2 - dy1)));
        return super.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer);
    }
    @Override public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, Color bgcolor, ImageObserver observer) {
        track(new Rectangle(Math.min(dx1, dx2), Math.min(dy1, dy2), Math.abs(dx2 - dx1), Math.abs(dy2 - dy1)));
        return super.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, bgcolor, observer);
    }

    @Override public void copyArea(int x, int y, int width, int height, int dx, int dy) {
        // device-space translation of the whole op's footprint
        Rectangle user = new Rectangle(x + dx, y + dy, width, height);
        track(user);
        super.copyArea(x, y, width, height, dx, dy);
    }

    @Override public Graphics create() {
        return new TrackingGraphics2D((Graphics2D) delegate.create(), tracker);
    }

    @Override public Graphics create(int x, int y, int width, int height) {
        return new TrackingGraphics2D((Graphics2D) delegate.create(x, y, width, height), tracker);
    }
}
