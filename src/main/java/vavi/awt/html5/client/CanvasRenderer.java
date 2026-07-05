/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.client;

import java.util.ArrayDeque;

import org.teavm.jso.canvas.CanvasRenderingContext2D;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.typedarrays.Uint8Array;


/**
 * Draws screen updates onto the canvas. PNG decoding via
 * {@code createImageBitmap} is asynchronous, so operations run through a
 * serial queue that preserves arrival order.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
class CanvasRenderer {

    private final HTMLCanvasElement canvas;
    private final CanvasRenderingContext2D ctx;
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private boolean busy;

    CanvasRenderer(HTMLCanvasElement canvas) {
        this.canvas = canvas;
        this.ctx = (CanvasRenderingContext2D) canvas.getContext("2d");
    }

    private void enqueue(Runnable op) {
        queue.add(op);
        if (!busy) {
            next();
        }
    }

    private void next() {
        Runnable op = queue.poll();
        if (op == null) {
            busy = false;
            return;
        }
        busy = true;
        op.run();
    }

    void resize(int width, int height) {
        enqueue(() -> {
            canvas.setWidth(width);
            canvas.setHeight(height);
            next();
        });
    }

    void blit(int x, int y, byte[] png) {
        Uint8Array data = new Uint8Array(png.length);
        for (int i = 0; i < png.length; i++) {
            data.set(i, (short) (png[i] & 0xff));
        }
        enqueue(() -> Js.createImageBitmap(data).then(bitmap -> {
            ctx.drawImage(bitmap, x, y);
            next();
            return null;
        }, error -> {
            Js.status("blit decode failed");
            next();
            return null;
        }));
    }

    void copyArea(int x, int y, int w, int h, int dx, int dy) {
        enqueue(() -> {
            ctx.drawImage(canvas, x, y, w, h, x + dx, y + dy, w, h);
            next();
        });
    }
}
