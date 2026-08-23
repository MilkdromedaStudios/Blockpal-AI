package com.milkdromeda.blockpal.localai;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;

/**
 * <b>Fetches a large file, visibly.</b>
 *
 * <p>A two-gigabyte download that gives no sign of progress is indistinguishable from a
 * hang, and someone will kill the server thinking it has frozen. So this reports as it
 * goes, and it writes to a {@code .part} file that is only moved into place once the
 * whole thing has arrived — an interrupted download leaves no half-file that would later
 * be mistaken for a working model.
 */
public final class Downloader {

    private Downloader() {}

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Called as bytes arrive; {@code total} is -1 when the server didn't say. */
    public interface Progress {
        void update(long done, long total);
    }

    /**
     * Downloads {@code url} to {@code target}, replacing whatever is there.
     *
     * @throws IOException on any network or disk failure, with the partial file removed
     */
    public static void download(String url, Path target, Progress progress) throws IOException {
        Files.createDirectories(target.getParent());
        Path part = target.resolveSibling(target.getFileName() + ".part");
        Files.deleteIfExists(part);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(60))
                .header("User-Agent", "Blockpal")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response =
                    HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("the download server answered " + response.statusCode());
            }
            long total = response.headers().firstValueAsLong("content-length").orElse(-1L);

            long done = 0;
            long lastReport = 0;
            byte[] buffer = new byte[1 << 16];
            try (InputStream in = response.body();
                 OutputStream out = Files.newOutputStream(part)) {
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                    done += n;
                    // Report about every 8 MB: often enough to look alive, rarely enough
                    // not to flood the log of a server nobody is watching.
                    if (progress != null && done - lastReport >= 8L * 1024 * 1024) {
                        lastReport = done;
                        progress.update(done, total);
                    }
                }
            }
            if (total > 0 && done != total) {
                throw new IOException("the download ended early ("
                        + bytes(done) + " of " + bytes(total) + ")");
            }
            if (progress != null) progress.update(done, total);
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("the download was interrupted");
        } finally {
            try {
                Files.deleteIfExists(part);
            } catch (IOException ignored) {
                // A leftover .part is harmless; the next attempt overwrites it.
            }
        }
    }

    /** "1.9 GB" / "412 MB" / "8.0 KB" */
    public static String bytes(long n) {
        if (n < 0) return "?";
        if (n >= 1_073_741_824L) return String.format(Locale.ROOT, "%.1f GB", n / 1_073_741_824.0);
        if (n >= 1_048_576L) return String.format(Locale.ROOT, "%.0f MB", n / 1_048_576.0);
        return String.format(Locale.ROOT, "%.1f KB", n / 1024.0);
    }

    /** "63% (1.2 GB of 1.9 GB)" for a status line. */
    public static String percent(long done, long total) {
        if (total <= 0) return bytes(done);
        return String.format(Locale.ROOT, "%d%% (%s of %s)",
                Math.round(100.0 * done / total), bytes(done), bytes(total));
    }
}
