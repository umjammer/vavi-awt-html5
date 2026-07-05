/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;

import vavi.awt.html5.render.FramePump;
import vavi.awt.html5.transport.CertManager;
import vavi.awt.html5.transport.Http3WebTransportServer;
import vavi.awt.html5.transport.SessionManager;
import vavi.awt.html5.transport.StaticHttpServer;


/**
 * Launcher: installs the HTML5 toolkit, starts the HTTP and WebTransport
 * servers and the frame pump, then runs the target Swing application
 * unmodified.
 * <pre>
 * java &lt;jvm flags, see bin/run.sh&gt; vavi.awt.html5.Main com.example.App args...
 * </pre>
 * System properties: {@code vavi.awt.html5.httpPort} (8080),
 * {@code vavi.awt.html5.wtPort} (4433), {@code cacio.managed.screensize}
 * (1024x768), {@code vavi.awt.html5.certDir} (default {@code ./target} or tmp).
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: vavi.awt.html5.Main <app-main-class> [args...]");
            System.exit(1);
        }
        String mainClass = args[0];
        String[] appArgs = Arrays.copyOfRange(args, 1, args.length);

        int httpPort = Integer.getInteger("vavi.awt.html5.httpPort", 8080);
        int wtPort = Integer.getInteger("vavi.awt.html5.wtPort", 4433);
        Path certDir = Path.of(System.getProperty("vavi.awt.html5.certDir",
                System.getProperty("java.io.tmpdir") + "/vavi-awt-html5"));

        ToolkitInstaller.install();

        CertManager.ServerCert cert = new CertManager(certDir).ensureCert();

        SessionManager sessions = new SessionManager();
        Http3WebTransportServer wtServer = new Http3WebTransportServer(wtPort, cert, sessions);
        wtServer.start();

        String wtUrl = "https://localhost:" + wtPort + Http3WebTransportServer.PATH;
        StaticHttpServer httpServer = new StaticHttpServer(httpPort, wtUrl, cert.sha256Base64());
        httpServer.start();

        new FramePump(Html5Screen.getInstance(), sessions).start();

        System.out.println("open http://localhost:" + httpPort + "/ in a WebTransport-capable browser");

        Method main = Class.forName(mainClass).getMethod("main", String[].class);
        Thread appThread = new Thread(() -> {
            try {
                main.invoke(null, (Object) appArgs);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(2);
            }
        }, "app-main");
        appThread.start();
    }
}
