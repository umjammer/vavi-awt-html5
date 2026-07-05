/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.client;

import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.streams.ReadableStreamDefaultReader;
import org.teavm.jso.typedarrays.Int8Array;


/**
 * Browser entry point (compiled to WASM by TeaVM). Opens the WebTransport
 * session advertised by the page globals {@code WT_URL} and
 * {@code CERT_HASH}, establishes one bidirectional stream, then renders
 * incoming screen updates and forwards input events.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public final class ClientMain {

    private ClientMain() {
    }

    public static void main(String[] args) {
        HTMLDocument document = HTMLDocument.current();
        HTMLCanvasElement canvas = (HTMLCanvasElement) document.getElementById("screen");

        String wtUrl = Js.global("WT_URL");
        String certHash = Js.global("CERT_HASH");
        Js.status("connecting to " + wtUrl + " ...");

        Js.WebTransport transport = Js.createWebTransport(wtUrl, certHash);
        transport.getReady().then(ready -> {
            openStream(transport, canvas, document);
            return null;
        }, error -> {
            Js.status("webtransport connect failed");
            return null;
        });
        transport.getClosed().then(info -> {
            Js.status("session closed");
            return null;
        }, error -> {
            Js.status("session aborted");
            return null;
        });
    }

    private static void openStream(Js.WebTransport transport, HTMLCanvasElement canvas, HTMLDocument document) {
        transport.createBidirectionalStream().then(stream -> {
            MsgSender sender = new MsgSender(stream.getWritable().getWriter());
            CanvasRenderer renderer = new CanvasRenderer(canvas);
            FrameParser parser = new FrameParser(renderer);
            InputCapture.install(canvas, document, sender);

            sender.hello(canvas.getWidth(), canvas.getHeight());
            pump(stream.getReadable().getReader(), parser);
            return null;
        }, error -> {
            Js.status("cannot open stream");
            return null;
        });
    }

    private static void pump(ReadableStreamDefaultReader reader, FrameParser parser) {
        reader.read().then(result -> {
            if (result.isDone()) {
                Js.status("stream ended");
                return null;
            }
            Int8Array value = result.getValue();
            int length = value.getLength();
            byte[] chunk = new byte[length];
            for (int i = 0; i < length; i++) {
                chunk[i] = value.get(i);
            }
            parser.feed(chunk, length);
            pump(reader, parser);
            return null;
        }, error -> {
            Js.status("read failed");
            return null;
        });
    }
}
