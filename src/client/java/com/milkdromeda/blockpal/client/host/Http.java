package com.milkdromeda.blockpal.client.host;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

/**
 * Tiny HTTP helper for the host setup: fetch JSON as text, and download a file
 * (following redirects, with optional SHA-1 verification). Used only by the
 * client-side {@link HostManager}; nothing here runs on the server.
 */
final class Http {

    private Http() {}

    static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)   // Geyser/Floodgate download URLs 302 to a CDN
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /** GETs a (small) resource as a UTF-8 string — used for the JSON metadata endpoints. */
    static String getString(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "Blockpal-Host")
                .timeout(Duration.ofSeconds(60))
                .GET().build();
        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + resp.statusCode() + " for " + url);
        }
        return resp.body();
    }

    /**
     * Downloads {@code url} to {@code dest} (atomically, via a .part file). If
     * {@code expectedSha1} is non-blank the download is rejected on mismatch.
     */
    static void download(String url, Path dest, String expectedSha1) throws IOException, InterruptedException {
        Files.createDirectories(dest.getParent());
        Path tmp = dest.resolveSibling(dest.getFileName() + ".part");
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "Blockpal-Host")
                .timeout(Duration.ofMinutes(10))
                .GET().build();
        HttpResponse<Path> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofFile(tmp));
        if (resp.statusCode() / 100 != 2) {
            Files.deleteIfExists(tmp);
            throw new IOException("HTTP " + resp.statusCode() + " for " + url);
        }
        if (expectedSha1 != null && !expectedSha1.isBlank()) {
            String actual = sha1(tmp);
            if (!actual.equalsIgnoreCase(expectedSha1)) {
                Files.deleteIfExists(tmp);
                throw new IOException("Checksum mismatch for " + url
                        + " (expected " + expectedSha1 + ", got " + actual + ")");
            }
        }
        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
    }

    static String sha1(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);   // SHA-1 is always present, but be explicit
        }
    }
}
