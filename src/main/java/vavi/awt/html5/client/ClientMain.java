/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.client;

import org.teavm.jso.dom.events.MessageEvent;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.streams.ReadableStreamDefaultReader;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.Int8Array;
import org.teavm.jso.typedarrays.Uint8Array;
import org.teavm.jso.websocket.WebSocket;


/**
 * Browser entry point (compiled to WASM by TeaVM). Opens the transport
 * advertised by the page globals ({@code TRANSPORT} = "ws" or
 * "webtransport"), then renders incoming screen updates and forwards input
 * events. Both transports carry the identical length-prefixed binary
 * protocol.
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
        String transport = Js.global("TRANSPORT");

        if ("webtransport".equals(transport)) {
            startWebTransport(canvas, document);
        } else {
            startWebSocket(canvas, document);
        }
    }

    // ---- WebSocket transport (default) ----

    private static void startWebSocket(HTMLCanvasElement canvas, HTMLDocument document) {
        String url = Js.global("WS_URL");
        Js.status("connecting to " + url + " ...");
        WebSocket ws = new WebSocket(url);
        ws.setBinaryType("arraybuffer");

        CanvasRenderer renderer = new CanvasRenderer(canvas);
        FrameParser parser = new FrameParser(renderer, new AudioPlayer());
        MsgSender sender = new MsgSender(ws::send);

        ws.onOpen(e -> {
            InputCapture.install(canvas, document, sender);
            sender.hello(Js.clientWidth(), Js.clientHeight());
            Js.onResize(() -> sender.resize(Js.clientWidth(), Js.clientHeight()));
        });
        ws.onMessage((MessageEvent e) -> {
            ArrayBuffer buffer = e.getDataAsArray();
            Uint8Array u8 = Js.asUint8Array(buffer);
            int len = u8.getLength();
            byte[] chunk = new byte[len];
            for (int i = 0; i < len; i++) {
                chunk[i] = (byte) u8.get(i);
            }
            parser.feed(chunk, len);
        });
        ws.onClose(e -> Js.status("connection closed"));
        ws.onError(e -> Js.status("connection error"));
    }

    // ---- WebTransport transport (experimental) ----

    private static void startWebTransport(HTMLCanvasElement canvas, HTMLDocument document) {
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
    }

    private static void openStream(Js.WebTransport transport, HTMLCanvasElement canvas, HTMLDocument document) {
        transport.createBidirectionalStream().then(stream -> {
            Js.StreamWriter writer = stream.getWritable().getWriter();
            MsgSender sender = new MsgSender(writer::write);
            CanvasRenderer renderer = new CanvasRenderer(canvas);
            FrameParser parser = new FrameParser(renderer, new AudioPlayer());
            InputCapture.install(canvas, document, sender);

            sender.hello(Js.clientWidth(), Js.clientHeight());
            Js.onResize(() -> sender.resize(Js.clientWidth(), Js.clientHeight()));
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
