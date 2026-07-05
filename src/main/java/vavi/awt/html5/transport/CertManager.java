/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.transport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;


/**
 * Generates and caches a self-signed ECDSA P-256 certificate for the
 * WebTransport endpoint. Browsers accept such a certificate through the
 * WebTransport {@code serverCertificateHashes} option provided it is valid
 * for at most 14 days, so validity is kept at 10 days and the certificate
 * is regenerated when less than a day remains.
 * <p>
 * Generation runs {@code keytool} from {@code java.home} (pure JDK
 * toolchain, no library dependency); the resulting PKCS12 keystore is
 * handed to kwik as-is.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public class CertManager {

    private static final Logger logger = Logger.getLogger(CertManager.class.getName());

    private static final String ALIAS = "wt";
    private static final String STOREPASS = "changeit";
    private static final int VALIDITY_DAYS = 10;

    /** keystore for kwik plus the base64 SHA-256 certificate hash for the browser */
    public record ServerCert(KeyStore keyStore, String alias, char[] password, String sha256Base64) {
    }

    private final Path dir;

    public CertManager(Path dir) {
        this.dir = dir;
    }

    public ServerCert ensureCert() throws IOException {
        try {
            Path keystorePath = dir.resolve("wt-cert.p12");
            Files.createDirectories(dir);

            KeyStore ks = null;
            if (Files.exists(keystorePath)) {
                ks = load(keystorePath);
                X509Certificate cert = (X509Certificate) ks.getCertificate(ALIAS);
                if (cert == null
                        || Instant.now().plus(Duration.ofDays(1)).isAfter(cert.getNotAfter().toInstant())) {
                    ks = null; // missing or expires too soon, regenerate
                }
            }
            if (ks == null) {
                generate(keystorePath);
                ks = load(keystorePath);
            }

            X509Certificate cert = (X509Certificate) ks.getCertificate(ALIAS);
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
            String hashB64 = Base64.getEncoder().encodeToString(hash);
            logger.fine(() -> "webtransport cert hash: " + hashB64);
            return new ServerCert(ks, ALIAS, STOREPASS.toCharArray(), hashB64);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("cannot prepare webtransport certificate", e);
        }
    }

    private static KeyStore load(Path keystorePath) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(keystorePath)) {
            ks.load(in, STOREPASS.toCharArray());
        }
        return ks;
    }

    private void generate(Path keystore) throws IOException, InterruptedException {
        Files.deleteIfExists(keystore);
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        Process p = new ProcessBuilder(
                keytool, "-genkeypair",
                "-alias", ALIAS,
                "-keyalg", "EC",
                "-groupname", "secp256r1",
                "-sigalg", "SHA256withECDSA",
                "-dname", "CN=localhost",
                "-validity", String.valueOf(VALIDITY_DAYS),
                "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-storetype", "PKCS12",
                "-keystore", keystore.toString(),
                "-storepass", STOREPASS)
                .redirectErrorStream(true)
                .start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(60, TimeUnit.SECONDS) || p.exitValue() != 0) {
            p.destroyForcibly();
            throw new IOException("keytool failed: " + output);
        }
    }
}
