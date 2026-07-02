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

    // ── "host my CURRENT world" (copy → play → sync back → delete the copy) ──
    /** The singleplayer save to host, captured when the Host screen is opened in a world. */
    private volatile Path sourceWorldPath;
    private volatile String sourceWorldName = "";
    /** When true, start() copies the source world into the server instead of a fresh one. */
    private volatile boolean hostCurrentWorld = false;
    /** True while a played world copy is waiting to be synced back to the save. */
    private volatile boolean pendingSync = false;
    /** Guards syncBack so it runs at most once per hosting run. */
    private volatile boolean syncedThisRun = false;

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

    // ── host-current-world state (read by the Host screen) ──

    /** Remembers which singleplayer save the Host screen was opened from. */
    public void setSourceWorld(Path path, String name) {
        if (phase != Phase.IDLE || pendingSync) return;   // don't retarget mid-flight
        sourceWorldPath = path;
        sourceWorldName = name == null ? "" : name;
        hostCurrentWorld = path != null;                  // default ON when a world is known
    }

    public boolean hasSourceWorld() { return sourceWorldPath != null; }
    public String sourceWorldName() { return sourceWorldName; }
    public boolean hostCurrentWorld() { return hostCurrentWorld && sourceWorldPath != null; }
    public void setHostCurrentWorld(boolean v) { hostCurrentWorld = v; }

    /** True when a played world copy still needs syncing back to the save. */
    public boolean pendingSync() { return pendingSync; }

    /**
     * Crash recovery, called when the Host screen opens: if a previous session left a
     * hosted world copy behind (marker + folder exist), offer the sync-back again.
     */
    public void checkPendingSyncMarker() {
        if (phase != Phase.IDLE || pendingSync) return;
        Path source = WorldSync.readMarker();
        if (source != null && Files.isDirectory(HostPaths.hostedCopyDir())) {
            sourceWorldPath = source;
            sourceWorldName = source.getFileName() == null ? "" : source.getFileName().toString();
            pendingSync = true;
            set(Phase.IDLE, "A previous hosted world wasn't synced back — use \"Sync world back\".");
        } else if (source != null) {
            WorldSync.clearMarker();   // stale marker with no copy — nothing to recover
        }
    }

    /** Guards against double-clicking "Sync world back" while a sync is in flight. */
    private volatile boolean syncInFlight = false;

    /** Runs the deferred sync-back (from the Host screen button) on a worker thread. */
    public synchronized void syncNow() {
        if (!pendingSync || syncInFlight || isBusy() || isRunning()) return;
        syncInFlight = true;
        Thread t = new Thread(() -> {
            try {
                trySyncBack();
            } finally {
                syncInFlight = false;
            }
        }, "blockpal-host-sync");
        t.setDaemon(true);
        t.start();
    }

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
        // Freeze the copy-mode decision for this run; the toggle may change later.
        final boolean copyMode = hostCurrentWorld();
        final Path source = sourceWorldPath;
        syncedThisRun = false;
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
            if (copyMode) {
                // The Start click saved + left the world; wait for the integrated
                // server to fully close so every region file is flushed and unlocked.
                set(Phase.CONFIGURING, "Waiting for the world to finish saving…");
                waitForWorldClosed();
                set(Phase.CONFIGURING, "Copying \"" + sourceWorldName + "\" into the server…");
                WorldSync.copyWorld(source, HostPaths.hostedCopyDir());
                WorldSync.writeMarker(source);
                HostConfig.writeServerProperties(serverDir, javaPort,
                        sourceWorldName + " — hosted with Blockpal", HostPaths.HOSTED_COPY_NAME);
            } else {
                HostConfig.writeServerProperties(serverDir, javaPort,
                        "Blockpal world — Bedrock welcome via Geyser", "blockpal-world");
            }
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
                @Override public void onReady() {
                    set(Phase.RUNNING, copyMode
                            ? "\"" + sourceWorldName + "\" is live — you join via localhost:" + javaPort + "."
                            : "Server running — share the address below.");
                }
                @Override public void onExit(int code) {
                    set(Phase.IDLE, "Server stopped (exit code " + code + ").");
                    if (copyMode) trySyncBack();   // bring the played world home
                }
            });
        } catch (Exception e) {
            set(Phase.ERROR, "Host setup failed: " + e.getMessage());
            if (copyMode && Files.isDirectory(HostPaths.hostedCopyDir())) pendingSync = true;
        }
    }

    /** Blocks until the client's integrated server is gone (save flushed, lock released). */
    private static void waitForWorldClosed() throws InterruptedException {
        for (int i = 0; i < 120; i++) {   // up to ~60 s — saving big worlds takes a moment
            if (!net.minecraft.client.Minecraft.getInstance().hasSingleplayerServer()) {
                Thread.sleep(1000);        // grace tick for file handles to release
                return;
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("The singleplayer world didn't close — leave it, then try again");
    }

    /**
     * Copies the played world back over the singleplayer save (keeping a timestamped
     * backup) and deletes the server's copy. Deferred — with the "Sync world back"
     * button armed — if the save is currently open in singleplayer or the sync fails.
     */
    private void trySyncBack() {
        Path source = sourceWorldPath;
        if (source == null || syncedThisRun) return;
        if (!Files.isDirectory(HostPaths.hostedCopyDir())) { WorldSync.clearMarker(); return; }
        if (isSourceWorldOpen(source)) {
            pendingSync = true;
            set(Phase.IDLE, "World is open in singleplayer — leave it, then press \"Sync world back\".");
            return;
        }
        try {
            set(Phase.IDLE, "Saving your world's changes back…");
            Path backup = WorldSync.syncBack(HostPaths.hostedCopyDir(), source, HostPaths.backupsDir());
            WorldSync.clearMarker();
            syncedThisRun = true;
            pendingSync = false;
            set(Phase.IDLE, "\"" + sourceWorldName + "\" updated ✓ (pre-host backup: blockpal-host/backups/"
                    + backup.getFileName() + ")");
        } catch (Exception e) {
            pendingSync = true;
            set(Phase.ERROR, "Couldn't sync the world back: " + e.getMessage()
                    + " — the played copy is kept; use \"Sync world back\".");
        }
    }

    /** True if the client is currently playing that exact save (never sync over an open world). */
    private static boolean isSourceWorldOpen(Path source) {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (!mc.hasSingleplayerServer() || mc.getSingleplayerServer() == null) return false;
            Path open = mc.getSingleplayerServer()
                    .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .toAbsolutePath().normalize();
            return open.equals(source.toAbsolutePath().normalize());
        } catch (Exception e) {
            return true;   // can't tell → be safe, don't overwrite
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
