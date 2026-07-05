/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;


/**
 * Serves the browser client (index.html, TeaVM output) from classpath
 * resources under {@code web/} using the JDK's built-in HTTP server —
 * zero dependencies. {@code index.html} is templated with the WebTransport
 * URL and certificate hash.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public class StaticHttpServer {

    private static final Logger logger = Logger.getLogger(StaticHttpServer.class.getName());

    private static final Map<String, String> MIME = Map.of(
            "html", "text/html; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "wasm", "application/wasm",
            "png", "image/png",
            "ico", "image/x-icon");

    private final int port;
    private final String wtUrl;
    private final String certHashB64;
    private HttpServer server;

    public StaticHttpServer(int port, String wtUrl, String certHashB64) {
        this.port = port;
        this.wtUrl = wtUrl;
        this.certHashB64 = certHashB64;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        logger.info(() -> "http server listening on http://localhost:" + port + "/");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) {
            path = "/index.html";
        }
        if (path.contains("..")) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("web" + path)) {
            if (in == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] data = in.readAllBytes();
            if (path.equals("/index.html")) {
                String html = new String(data, StandardCharsets.UTF_8)
                        .replace("${WT_URL}", wtUrl)
                        .replace("${CERT_HASH_B64}", certHashB64);
                data = html.getBytes(StandardCharsets.UTF_8);
            }
            String ext = path.substring(path.lastIndexOf('.') + 1);
            exchange.getResponseHeaders().set("Content-Type",
                    MIME.getOrDefault(ext, "application/octet-stream"));
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(data);
            }
        }
    }
}
