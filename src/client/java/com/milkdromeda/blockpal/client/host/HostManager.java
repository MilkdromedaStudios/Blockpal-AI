package com.milkdromeda.blockpal.client.host;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Drives the whole "host my world for Bedrock + Java friends" flow on the client:
 * downloads a Minecraft + Fabric + latest Geyser + Floodgate server, configures it,
 * launches it as a child process, and reports its status and connect addresses.
 *
 * <p><b>Only the Java client hosts.</b> This lives in the client source set, so a
 * Bedrock player (who has no Blockpal mod) never has a way to start it — they can
 * only join a Java host through Geyser.
 *
 * <p>Single instance ({@link #get()}); all the slow work runs on background threads,
 * and a tiny status snapshot is read by the UI each frame.
 */
public final class HostManager {

    public enum Phase {
        IDLE("Not hosting"),
        DOWNLOADING("Downloading server"),
        CONFIGURING("Configuring"),
        STARTING("Starting server"),
        RUNNING("Server running"),
        STOPPING("Stopping"),
        ERROR("Error");

        public final String label;
        Phase(String label) { this.label = label; }
    }

    /** Default Java port (vanilla) and Bedrock port (Geyser's default UDP port). */
    public static final int DEFAULT_JAVA_PORT = 25565;
    public static final int DEFAULT_BEDROCK_PORT = 19132;

    private static final HostManager INSTANCE = new HostManager();
    public static HostManager get() { return INSTANCE; }
    private HostManager() {}

    private volatile Phase phase = Phase.IDLE;
    private volatile String status = "Not hosting.";
    private volatile boolean eulaAccepted = false;
    private volatile int javaPort = DEFAULT_JAVA_PORT;
    private volatile int memoryMb = 2048;
    private volatile String localIp = "";
    private volatile String publicIp = "";
    private volatile boolean stopRequested = false;

    private final ServerProcess server = new ServerProcess();
    private final Deque<String> log = new ArrayDeque<>();

    // ── read-only snapshot for the UI / command ──
    public Phase phase()        { return phase; }
    public String status()      { return status; }
    public boolean eulaAccepted() { return eulaAccepted; }
    public int javaPort()       { return javaPort; }
    public int bedrockPort()    { return DEFAULT_BEDROCK_PORT; }
    public int memoryMb()       { return memoryMb; }
    public String localIp()     { return localIp; }
    public String publicIp()    { return publicIp; }
    public boolean isRunning()  { return phase == Phase.RUNNING; }
    /** True while setup/teardown is mid-flight (so the UI disables the buttons). */
    public boolean isBusy() {
        return phase == Phase.DOWNLOADING || phase == Phase.CONFIGURING
                || phase == Phase.STARTING || phase == Phase.STOPPING;
    }

    public void setEulaAccepted(boolean v) { eulaAccepted = v; }
    public void setJavaPort(int p) { if (p > 0 && p < 65536) javaPort = p; }
    public void setMemoryMb(int m) { if (m >= 1024) memoryMb = m; }

    public synchronized List<String> recentLog() { return new ArrayList<>(log); }

    private synchronized void log(String line) {
        log.addLast(line);
        while (log.size() > 200) log.removeFirst();
    }

    private void set(Phase p, String msg) {
        phase = p;
        status = msg;
        log("[" + p.label + "] " + msg);
    }

    /**
     * Kicks off download → configure → launch on a worker thread. No-op when already
     * busy or running, or if the EULA hasn't been accepted yet.
     */
    public synchronized void start() {
        if (isRunning() || isBusy()) return;
        if (!eulaAccepted) {
            set(Phase.ERROR, "You must accept the Minecraft EULA first.");
            return;
        }
        stopRequested = false;
        Thread worker = new Thread(this::run, "blockpal-host-setup");
        worker.setDaemon(true);
        worker.start();
    }

    private void run() {
        try {
            Path serverDir = HostPaths.serverDir();
            Files.createDirectories(HostPaths.modsDir());

            set(Phase.DOWNLOADING, "Resolving the latest components…");
            ComponentResolver.Artifact mcServer = ComponentResolver.mojangServer();
            String launcherUrl = ComponentResolver.fabricServerLauncherUrl();
            String fabricApiUrl = ComponentResolver.fabricApiUrl();

            set(Phase.DOWNLOADING, "Downloading Minecraft server " + ComponentResolver.MC_VERSION + "…");
            Http.download(mcServer.url(), HostPaths.serverJar(), mcServer.sha1());

            set(Phase.DOWNLOADING, "Downloading Fabric server…");
            Http.download(launcherUrl, HostPaths.fabricLauncher(), null);

            set(Phase.DOWNLOADING, "Downloading Fabric API…");
            Http.download(fabricApiUrl, HostPaths.modsDir().resolve("fabric-api.jar"), null);

            set(Phase.DOWNLOADING, "Downloading the latest Geyser…");
            Http.download(ComponentResolver.geyserFabricUrl(), HostPaths.modsDir().resolve("Geyser-Fabric.jar"), null);

            set(Phase.DOWNLOADING, "Downloading the latest Floodgate…");
            Http.download(ComponentResolver.floodgateFabricUrl(), HostPaths.modsDir().resolve("floodgate-fabric.jar"), null);

            set(Phase.CONFIGURING, "Writing configuration…");
            HostConfig.writeEula(serverDir, true);
            HostConfig.writeServerProperties(serverDir, javaPort,
                    "Blockpal world — Bedrock welcome via Geyser", "blockpal-world");
            try {
                HostConfig.copyBlockpalMod(HostPaths.modsDir());
            } catch (Exception e) {
                log("Note: couldn't add Blockpal to the server (" + e.getMessage() + ") — it'll run vanilla+Geyser.");
            }

            if (stopRequested) { set(Phase.IDLE, "Host setup cancelled."); return; }

            localIp = NetAddresses.localIp();
            set(Phase.STARTING, "Starting the server (first run downloads libraries — give it a minute)…");
            publicIp = NetAddresses.publicIp();

            server.start(serverDir, HostPaths.fabricLauncher(), memoryMb, new ServerProcess.Listener() {
                @Override public void onLine(String line) { log(line); }
                @Override public void onReady() { set(Phase.RUNNING, "Server running — share the address below."); }
                @Override public void onExit(int code) { set(Phase.IDLE, "Server stopped (exit code " + code + ")."); }
            });
        } catch (Exception e) {
            set(Phase.ERROR, "Host setup failed: " + e.getMessage());
        }
    }

    /** Stops the running server (or cancels a stuck launch) on a background thread. */
    public synchronized void stop() {
        if (phase == Phase.IDLE) return;
        stopRequested = true;
        set(Phase.STOPPING, "Stopping the server…");
        Thread t = new Thread(() -> {
            server.stop();
            if (phase != Phase.IDLE) set(Phase.IDLE, "Server stopped.");
        }, "blockpal-host-stop");
        t.setDaemon(true);
        t.start();
    }
}
