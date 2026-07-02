package com.milkdromeda.blockpal.client.host;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional <b>no-port-forwarding</b> tunnel for the hosted server, via the
 * <a href="https://playit.gg">playit.gg</a> agent — the one relay that tunnels both
 * Java (TCP) and Bedrock (UDP), so friends can join without the host forwarding ports
 * or sharing their real IP.
 *
 * <p>It downloads the official playit agent for the OS and runs it as a child process.
 * On first run the agent prints a one-time <b>setup link</b> (a {@code https://playit.gg/…}
 * URL) that we surface in the UI; the host visits it once (free account) to claim the
 * tunnel and map the Java/Bedrock ports. After that the playit dashboard shows the public
 * address friends use.
 *
 * <p>Client-only and opt-in. Like the rest of the host engine, the live download/run path
 * needs a real machine to verify (it can't run in CI).
 */
public final class TunnelManager {

    public enum Phase {
        IDLE("Tunnel off"),
        DOWNLOADING("Downloading tunnel agent"),
        STARTING("Starting tunnel"),
        RUNNING("Tunnel running"),
        ERROR("Tunnel error");
        public final String label;
        Phase(String label) { this.label = label; }
    }

    private static final TunnelManager INSTANCE = new TunnelManager();
    public static TunnelManager get() { return INSTANCE; }
    private TunnelManager() {}

    private static final Pattern PLAYIT_URL = Pattern.compile("https://playit\\.gg/\\S+");

    private volatile Phase phase = Phase.IDLE;
    private volatile String status = "Not running.";
    private volatile String claimUrl = "";
    private final Deque<String> log = new ArrayDeque<>();
    private Process process;

    public Phase phase() { return phase; }
    public String status() { return status; }
    /** The one-time playit setup link, or "" until the agent prints one. */
    public String claimUrl() { return claimUrl; }
    public boolean isRunning() { return phase == Phase.RUNNING || phase == Phase.STARTING; }
    public boolean isBusy() { return phase == Phase.DOWNLOADING || phase == Phase.STARTING; }

    public synchronized List<String> recentLog() { return new ArrayList<>(log); }
    private synchronized void log(String line) { log.addLast(line); while (log.size() > 200) log.removeFirst(); }
    private void set(Phase p, String s) { phase = p; status = s; log("[" + p.label + "] " + s); }

    public synchronized void start() {
        if (isRunning() || isBusy()) return;
        claimUrl = "";
        Thread t = new Thread(this::run, "blockpal-tunnel");
        t.setDaemon(true);
        t.start();
    }

    private void run() {
        try {
            set(Phase.DOWNLOADING, "Fetching the playit.gg tunnel agent…");
            Path bin = ensureAgent();

            set(Phase.STARTING, "Starting the tunnel — a setup link will appear below…");
            ProcessBuilder pb = new ProcessBuilder(bin.toAbsolutePath().toString());
            pb.directory(HostPaths.tunnelDir().toFile());
            pb.redirectErrorStream(true);
            process = pb.start();

            Thread reader = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        log(line);
                        if (claimUrl.isEmpty()) {
                            Matcher m = PLAYIT_URL.matcher(line);
                            if (m.find()) {
                                claimUrl = m.group();
                                set(Phase.RUNNING, "Open the setup link to finish (free, one-time).");
                            }
                        }
                    }
                } catch (IOException ignored) {
                    // stream closed on stop
                }
                if (phase != Phase.ERROR) set(Phase.IDLE, "Tunnel stopped.");
            }, "blockpal-tunnel-log");
            reader.setDaemon(true);
            reader.start();

            // If the agent's already claimed it won't print a link, so mark it running.
            if (phase == Phase.STARTING) {
                set(Phase.RUNNING, "Tunnel running — see your address on the playit.gg dashboard.");
            }
        } catch (Exception e) {
            set(Phase.ERROR, "Tunnel failed: " + e.getMessage());
        }
    }

    public synchronized void stop() {
        if (process != null && process.isAlive()) process.destroy();
        set(Phase.IDLE, "Tunnel stopped.");
    }

    /** Downloads the playit agent for this OS/arch (once) and returns its path. */
    private Path ensureAgent() throws Exception {
        Path bin = HostPaths.playitBinary();
        if (Files.exists(bin)) return bin;
        Http.download(resolveAgentUrl(), bin, null);
        try { bin.toFile().setExecutable(true); } catch (Exception ignored) { /* Windows / no-op */ }
        return bin;
    }

    /** Picks the right playit agent asset from the latest GitHub release for this platform. */
    private String resolveAgentUrl() throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        boolean windows = os.contains("win");
        boolean mac = os.contains("mac") || os.contains("darwin");
        String osKey = windows ? "windows" : mac ? "darwin" : "linux";
        boolean arm = arch.contains("aarch64") || arch.contains("arm");

        JsonObject release = JsonParser.parseString(Http.getString(
                "https://api.github.com/repos/playit-cloud/playit-agent/releases/latest")).getAsJsonObject();
        JsonArray assets = release.getAsJsonArray("assets");
        String anyOsMatch = null;
        String archMatch = null;
        for (JsonElement el : assets) {
            JsonObject a = el.getAsJsonObject();
            String name = a.get("name").getAsString().toLowerCase();
            String url = a.get("browser_download_url").getAsString();
            if (!name.contains(osKey)) continue;
            if (name.endsWith(".deb") || name.endsWith(".rpm") || name.contains("sha256") || name.contains(".sig")) continue;
            anyOsMatch = url;
            boolean isArm = name.contains("aarch64") || name.contains("arm64") || name.contains("arm");
            if (arm == isArm) archMatch = url;
        }
        String url = archMatch != null ? archMatch : anyOsMatch;
        if (url == null) throw new IllegalStateException("No playit agent build for this platform (" + osKey + "/" + arch + ")");
        return url;
    }
}
