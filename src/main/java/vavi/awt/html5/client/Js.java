/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.client;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.canvas.CanvasImageSource;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.core.JSUndefined;
import org.teavm.jso.streams.ReadableStream;
import org.teavm.jso.typedarrays.Uint8Array;


/**
 * Hand-written JS bindings for the APIs teavm-jso-apis does not cover:
 * WebTransport, WritableStream writer, createImageBitmap, page globals.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
final class Js {

    private Js() {
    }

    /** the WebTransport session object */
    interface WebTransport extends JSObject {

        @JSProperty("ready")
        JSPromise<JSUndefined> getReady();

        @JSProperty("closed")
        JSPromise<JSObject> getClosed();

        JSPromise<BidiStream> createBidirectionalStream();
    }

    interface BidiStream extends JSObject {

        @JSProperty("readable")
        ReadableStream getReadable();

        @JSProperty("writable")
        WritableStream getWritable();
    }

    interface WritableStream extends JSObject {

        StreamWriter getWriter();
    }

    interface StreamWriter extends JSObject {

        JSPromise<JSUndefined> write(JSObject chunk);

        JSPromise<JSUndefined> close();
    }

    /** result of createImageBitmap, drawable on a canvas */
    interface ImageBitmap extends JSObject, CanvasImageSource {
    }

    @JSBody(params = {"url", "hashB64"}, script = """
            return new WebTransport(url, {
                serverCertificateHashes: [{
                    algorithm: 'sha-256',
                    value: Uint8Array.from(atob(hashB64), function(c) { return c.charCodeAt(0); })
                }]
            });""")
    static native WebTransport createWebTransport(String url, String hashB64);

    @JSBody(params = {"bytes"}, script = "return createImageBitmap(new Blob([bytes], {type: 'image/png'}));")
    static native JSPromise<ImageBitmap> createImageBitmap(Uint8Array bytes);

    @JSBody(params = {"buffer"}, script = "return new Uint8Array(buffer);")
    static native Uint8Array asUint8Array(org.teavm.jso.typedarrays.ArrayBuffer buffer);

    @JSBody(params = {"name"}, script = "return window[name];")
    static native String global(String name);

    @JSBody(params = {"text"}, script = """
            var el = document.getElementById('status');
            if (el) el.textContent = text;
            console.log(text);""")
    static native void status(String text);
}
