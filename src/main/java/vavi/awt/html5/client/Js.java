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
import org.teavm.jso.typedarrays.Float32Array;
import org.teavm.jso.typedarrays.Uint8Array;


/**
 * Hand-written JS bindings for the APIs teavm-jso-apis does not cover:
 * WebTransport, WritableStream writer, createImageBitmap, Web Audio,
 * page globals.
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

    /** an AudioContext (opaque; used through the audio* helpers below) */
    interface AudioCtx extends JSObject {
    }

    /**
     * Creates the context and arms one-shot user-gesture listeners that
     * resume it: browsers keep an AudioContext suspended until the page
     * receives a gesture (autoplay policy).
     */
    @JSBody(script = """
            var Ctx = window.AudioContext || window.webkitAudioContext;
            var ctx = new Ctx();
            var resume = function() {
                if (ctx.state === 'suspended') ctx.resume();
            };
            window.addEventListener('mousedown', resume, true);
            window.addEventListener('keydown', resume, true);
            window.addEventListener('touchstart', resume, true);
            return ctx;""")
    static native AudioCtx createAudioContext();

    @JSBody(params = {"ctx"}, script = "return ctx.state === 'suspended';")
    static native boolean audioSuspended(AudioCtx ctx);

    @JSBody(params = {"ctx"}, script = "return ctx.currentTime;")
    static native double audioCurrentTime(AudioCtx ctx);

    /** schedules one PCM chunk; ch1 is null for mono */
    @JSBody(params = {"ctx", "ch0", "ch1", "sampleRate", "when"}, script = """
            var buffer = ctx.createBuffer(ch1 ? 2 : 1, ch0.length, sampleRate);
            buffer.getChannelData(0).set(ch0);
            if (ch1) buffer.getChannelData(1).set(ch1);
            var src = ctx.createBufferSource();
            src.buffer = buffer;
            src.connect(ctx.destination);
            src.start(when);""")
    static native void playPcm(AudioCtx ctx, Float32Array ch0, Float32Array ch1, double sampleRate, double when);

    @JSBody(params = {"text"}, script = """
            var el = document.getElementById('status');
            if (el) el.textContent = text;
            console.log(text);""")
    static native void status(String text);

    @JSBody(script = "return window.innerWidth - 16;")
    static native int clientWidth();

    @JSBody(script = "return window.innerHeight - 40;")
    static native int clientHeight();

    @org.teavm.jso.JSFunctor
    interface ResizeListener extends JSObject {
        void onResize();
    }

    @JSBody(params = {"listener"}, script = "window.addEventListener('resize', listener);")
    static native void onResize(ResizeListener listener);
}
