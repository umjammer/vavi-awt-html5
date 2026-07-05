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
import java.security.PrivateKey;
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
 * toolchain, no library dependency), then exports the PEM files kwik needs
 * and the SHA-256 hash the browser needs.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public class CertManager {

    private static final Logger logger = Logger.getLogger(CertManager.class.getName());

    private static final String STOREPASS = "changeit";
    private static final int VALIDITY_DAYS = 10;

    /** PEM files for kwik plus the base64 SHA-256 certificate hash for the browser */
    public record ServerCert(Path certPem, Path keyPem, String sha256Base64) {
    }

    private final Path dir;

    public CertManager(Path dir) {
        this.dir = dir;
    }

    public ServerCert ensureCert() throws IOException {
        try {
            Path keystore = dir.resolve("wt-cert.p12");
            Path certPem = dir.resolve("wt-cert.pem");
            Path keyPem = dir.resolve("wt-key.pem");
            Files.createDirectories(dir);

            X509Certificate cert = null;
            if (Files.exists(keystore) && Files.exists(certPem) && Files.exists(keyPem)) {
                cert = loadCert(keystore);
                Instant expiry = cert.getNotAfter().toInstant();
                if (Instant.now().plus(Duration.ofDays(1)).isAfter(expiry)) {
                    cert = null; // expires too soon, regenerate
                }
            }
            if (cert == null) {
                generate(keystore);
                KeyStore ks = KeyStore.getInstance("PKCS12");
                try (var in = Files.newInputStream(keystore)) {
                    ks.load(in, STOREPASS.toCharArray());
                }
                cert = (X509Certificate) ks.getCertificate("wt");
                PrivateKey key = (PrivateKey) ks.getKey("wt", STOREPASS.toCharArray());
                writePem(certPem, "CERTIFICATE", cert.getEncoded());
                writePem(keyPem, "PRIVATE KEY", key.getEncoded());
            }

            byte[] hash = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
            String hashB64 = Base64.getEncoder().encodeToString(hash);
            logger.fine(() -> "webtransport cert hash: " + hashB64);
            return new ServerCert(certPem, keyPem, hashB64);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("cannot prepare webtransport certificate", e);
        }
    }

    private static X509Certificate loadCert(Path keystore) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(keystore)) {
            ks.load(in, STOREPASS.toCharArray());
        }
        return (X509Certificate) ks.getCertificate("wt");
    }

    private void generate(Path keystore) throws IOException, InterruptedException {
        Files.deleteIfExists(keystore);
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        Process p = new ProcessBuilder(
                keytool, "-genkeypair",
                "-alias", "wt",
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

    private static void writePem(Path file, String type, byte[] der) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN ").append(type).append("-----\n");
        String b64 = Base64.getEncoder().encodeToString(der);
        for (int i = 0; i < b64.length(); i += 64) {
            sb.append(b64, i, Math.min(i + 64, b64.length())).append('\n');
        }
        sb.append("-----END ").append(type).append("-----\n");
        Files.writeString(file, sb.toString());
    }
}
