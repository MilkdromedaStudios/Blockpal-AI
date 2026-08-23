package com.milkdromeda.blockpal.localai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <b>Works out which llama.cpp build this machine needs, and where to get it.</b>
 *
 * <p>llama.cpp publishes a separate binary per platform <i>and per GPU backend</i>, so
 * the difference between "runs on your graphics card" and "grinds along on the CPU" is
 * entirely a matter of picking the right asset. The names are resolved from the release
 * API at runtime rather than hard-coded, for the same reason
 * {@code client/host/TunnelManager} does it for the playit agent: the project publishes
 * often, and a pinned filename becomes a broken download the moment they rename anything.
 *
 * <p><b>What is available, verified against llama.cpp's own release workflow:</b>
 * <ul>
 *   <li><b>Windows</b> — {@code bin-win-cuda-<ver>-x64} (NVIDIA), {@code bin-win-vulkan-<arch>}
 *       (any modern GPU), {@code bin-win-cpu-<arch>}. The Windows CPU backend files are
 *       merged into every other Windows zip by their release job, so one download is
 *       self-contained — except CUDA, which also needs its {@code cudart-} companion.</li>
 *   <li><b>Linux</b> — {@code bin-ubuntu-vulkan-x64} or {@code bin-ubuntu-<arch>}.
 *       <b>There is no Linux CUDA release</b>, so Vulkan is the GPU path on Linux even on
 *       an NVIDIA card (their Vulkan driver handles it).</li>
 *   <li><b>macOS</b> — {@code bin-macos-arm64} has Metal compiled in, so an Apple Silicon
 *       Mac gets GPU acceleration from the ordinary build with nothing extra.</li>
 * </ul>
 */
public final class LlamaRelease {

    private LlamaRelease() {}

    private static final String RELEASES_URL =
            "https://api.github.com/repos/ggml-org/llama.cpp/releases/latest";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** A chosen build: what to download, how big it is, and how it will run. */
    public record Choice(String assetName, String url, long bytes,
                         String companionName, String companionUrl, long companionBytes,
                         Backend backend, String tag) {

        public boolean hasCompanion() {
            return companionUrl != null && !companionUrl.isEmpty();
        }

        /** Total bytes the runtime download will cost, companion included. */
        public long totalBytes() {
            return bytes + (hasCompanion() ? companionBytes : 0);
        }

        public String sizeText() {
            return String.format(Locale.ROOT, "%.0f MB", totalBytes() / 1_048_576.0);
        }
    }

    /** How the model will actually be executed. */
    public enum Backend {
        CUDA("NVIDIA GPU (CUDA)", true),
        VULKAN("GPU (Vulkan)", true),
        METAL("Apple GPU (Metal)", true),
        CPU("CPU only — slow", false);

        private final String display;
        private final boolean gpu;

        Backend(String display, boolean gpu) {
            this.display = display;
            this.gpu = gpu;
        }

        public String display() { return display; }
        public boolean isGpu() { return gpu; }
    }

    /**
     * Picks the best asset the current machine can use.
     *
     * @return the chosen build, or null when nothing suitable is published for this
     *         platform (a 32-bit or exotic system) — the caller says so rather than
     *         downloading something that cannot run
     * @throws IOException if the release list can't be fetched
     */
    public static Choice resolve() throws IOException {
        JsonObject release = fetchLatest();
        String tag = release.has("tag_name") ? release.get("tag_name").getAsString() : "latest";
        JsonArray assets = release.getAsJsonArray("assets");
        if (assets == null) throw new IOException("the llama.cpp release has no downloads listed");

        for (Candidate candidate : candidates()) {
            JsonObject asset = findAsset(assets, candidate.needles, candidate.suffix);
            if (asset == null) continue;

            String companionName = null, companionUrl = null;
            long companionBytes = 0;
            if (candidate.companionNeedles != null) {
                JsonObject companion = findAsset(assets, candidate.companionNeedles, candidate.suffix);
                // A CUDA build without its runtime companion would start and immediately
                // fail on a missing DLL, so fall through to the next candidate instead.
                if (companion == null) continue;
                companionName = companion.get("name").getAsString();
                companionUrl = companion.get("browser_download_url").getAsString();
                companionBytes = companion.get("size").getAsLong();
            }

            return new Choice(
                    asset.get("name").getAsString(),
                    asset.get("browser_download_url").getAsString(),
                    asset.get("size").getAsLong(),
                    companionName, companionUrl, companionBytes,
                    candidate.backend, tag);
        }
        return null;
    }

    private static JsonObject fetchLatest() throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASES_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Blockpal")
                .GET()
                .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("GitHub answered " + response.statusCode()
                        + " when asked for the latest llama.cpp release");
            }
            JsonElement parsed = JsonParser.parseString(response.body());
            if (!parsed.isJsonObject()) throw new IOException("unexpected release data");
            return parsed.getAsJsonObject();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while contacting GitHub");
        }
    }

    /** First asset whose name contains every needle and ends with the right extension. */
    private static JsonObject findAsset(JsonArray assets, String[] needles, String suffix) {
        for (JsonElement element : assets) {
            if (!element.isJsonObject()) continue;
            JsonObject asset = element.getAsJsonObject();
            if (!asset.has("name") || !asset.has("browser_download_url") || !asset.has("size")) continue;
            String name = asset.get("name").getAsString().toLowerCase(Locale.ROOT);
            if (!name.endsWith(suffix)) continue;
            boolean all = true;
            for (String needle : needles) {
                if (!name.contains(needle)) { all = false; break; }
            }
            // "cudart-llama-..." also contains "bin-win-cuda", so a plain runtime lookup
            // must not accidentally match the CUDA runtime archive.
            if (all && !isCompanionName(name, needles)) return asset;
        }
        return null;
    }

    private static boolean isCompanionName(String name, String[] needles) {
        boolean wantsCompanion = false;
        for (String needle : needles) {
            if (needle.startsWith("cudart")) wantsCompanion = true;
        }
        return !wantsCompanion && name.startsWith("cudart");
    }

    private record Candidate(String[] needles, String[] companionNeedles,
                             String suffix, Backend backend) {}

    /**
     * Preference order for this machine: fastest usable build first, CPU last.
     */
    private static List<Candidate> candidates() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean arm = arch.contains("aarch64") || arch.contains("arm");
        List<Candidate> out = new ArrayList<>();

        if (os.contains("win")) {
            String a = arm ? "arm64" : "x64";
            if (!arm && GpuProbe.hasNvidia()) {
                out.add(new Candidate(new String[]{"bin-win-cuda", "-" + a},
                        new String[]{"cudart", "-" + a}, ".zip", Backend.CUDA));
            }
            out.add(new Candidate(new String[]{"bin-win-vulkan-" + a}, null, ".zip", Backend.VULKAN));
            out.add(new Candidate(new String[]{"bin-win-cpu-" + a}, null, ".zip", Backend.CPU));
        } else if (os.contains("mac") || os.contains("darwin")) {
            // Metal is compiled into the standard macOS build; Apple Silicon uses the GPU
            // with no extra download, and an Intel Mac falls back to its CPU.
            String a = arm ? "arm64" : "x64";
            out.add(new Candidate(new String[]{"bin-macos-" + a}, null, ".tar.gz",
                    arm ? Backend.METAL : Backend.CPU));
        } else {
            String a = arm ? "arm64" : "x64";
            if (!arm) {
                out.add(new Candidate(new String[]{"bin-ubuntu-vulkan-" + a}, null,
                        ".tar.gz", Backend.VULKAN));
            }
            out.add(new Candidate(new String[]{"bin-ubuntu-" + a}, null, ".tar.gz", Backend.CPU));
        }
        return out;
    }

    /** The platform in words, for status messages. */
    public static String platformText() {
        return System.getProperty("os.name", "?") + " " + System.getProperty("os.arch", "?");
    }
}
