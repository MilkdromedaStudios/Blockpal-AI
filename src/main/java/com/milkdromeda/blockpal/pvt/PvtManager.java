package com.milkdromeda.blockpal.pvt;

import com.milkdromeda.blockpal.config.ModConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>The one place PVT lives.</b> Owns the recordings on disk, the trained policy in
 * memory, and the handful of players who have said their play may be watched.
 *
 * <p><b>Recording is opt-in, per player, always.</b> Not because the data is sensitive —
 * it is a list of which way somebody walked — but because "this mod quietly recorded how
 * you play" is a sentence no server owner should have to defend. {@code pvtAutoRecord}
 * only decides whether an opted-in player starts recording automatically when they join;
 * it never opts anybody in.
 */
public final class PvtManager {

    private PvtManager() {}

    private static final Path ROOT = FabricLoader.getInstance()
            .getConfigDir().resolve("blockpal").resolve("pvt");
    private static final Path DEMOS = ROOT.resolve("demos");
    private static final Path POLICY_FILE = ROOT.resolve("policy.bpn");
    private static final Path IDM_FILE = ROOT.resolve("idm.bpn");
    private static final Path OPTIN_FILE = ROOT.resolve("recording-consent.txt");

    /** Live recorders, one per subject being watched. */
    private static final Map<UUID, PvtRecorder> RECORDERS = new ConcurrentHashMap<>();

    /** Players who have agreed their play may be recorded. */
    private static final Set<UUID> CONSENTED = ConcurrentHashMap.newKeySet();

    private static PvtNet policy;
    private static boolean policyLoaded;
    private static PvtTrainer trainer;
    private static String lastMessage = "";

    public static Path root() { return ROOT; }
    public static Path demoFolder() { return DEMOS; }
    public static Path policyFile() { return POLICY_FILE; }

    // ── consent ─────────────────────────────────────────────────────────────────

    public static boolean hasConsented(UUID player) {
        loadConsent();
        return CONSENTED.contains(player);
    }

    /** @return true when this changed something */
    public static boolean setConsent(UUID player, boolean allowed) {
        loadConsent();
        boolean changed = allowed ? CONSENTED.add(player) : CONSENTED.remove(player);
        if (changed) saveConsent();
        return changed;
    }

    public static int consentedCount() {
        loadConsent();
        return CONSENTED.size();
    }

    private static boolean consentLoaded;

    private static void loadConsent() {
        if (consentLoaded) return;
        consentLoaded = true;
        try {
            if (!Files.exists(OPTIN_FILE)) return;
            for (String line : Files.readAllLines(OPTIN_FILE)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                try {
                    CONSENTED.add(UUID.fromString(trimmed));
                } catch (IllegalArgumentException ignored) {
                    // A hand-edited file shouldn't stop the rest loading.
                }
            }
        } catch (IOException ignored) {
            // No consent file simply means nobody has opted in yet.
        }
    }

    private static void saveConsent() {
        try {
            Files.createDirectories(ROOT);
            StringBuilder sb = new StringBuilder(
                    "# Players who agreed that Blockpal may record their play for PVT training.\n"
                    + "# Remove a line (or use /ai pvt watch off) to withdraw.\n");
            for (UUID id : CONSENTED) sb.append(id).append('\n');
            Files.writeString(OPTIN_FILE, sb.toString());
        } catch (IOException ignored) {
            // Consent still holds for this session even if the file can't be written.
        }
    }

    // ── recording ───────────────────────────────────────────────────────────────

    public static boolean isRecording(UUID id) {
        PvtRecorder r = RECORDERS.get(id);
        return r != null && !r.isClosed();
    }

    public static int recorderCount() { return RECORDERS.size(); }

    /**
     * Starts recording a subject's play.
     *
     * @return a message for the player, or "" when it started cleanly
     */
    public static String startRecording(LivingEntity subject) {
        if (!ModConfig.get().pvtEnabled) return "PVT is switched off on this server.";
        UUID id = subject.getUUID();
        if (isRecording(id)) return "Already recording.";
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String who = safeName(subject);
        Path file = DEMOS.resolve(who + "-" + stamp + ".bpd");
        RECORDERS.put(id, new PvtRecorder(subject, file));
        return "";
    }

    /** Stops recording and returns a summary, or "" when nothing was running. */
    public static String stopRecording(UUID id) {
        PvtRecorder r = RECORDERS.remove(id);
        if (r == null) return "";
        r.close();
        // An episode of a handful of frames is noise; don't keep it.
        if (r.frames() < 20) {
            try {
                Files.deleteIfExists(r.file());
            } catch (IOException ignored) {
                // Leaving a tiny file behind is harmless.
            }
            return "Stopped — too short to keep (" + r.frames() + " frames).";
        }
        PvtDataset.prune(DEMOS, ModConfig.get().pvtMaxFrames);
        return "Stopped. Banked " + r.frames() + " frames of your play.";
    }

    public static void stopAll() {
        for (UUID id : RECORDERS.keySet().toArray(new UUID[0])) stopRecording(id);
    }

    /** Ticks every live recorder. Called once per server tick. */
    public static void tick(MinecraftServer server) {
        if (RECORDERS.isEmpty()) return;
        for (Map.Entry<UUID, PvtRecorder> e : RECORDERS.entrySet()) {
            PvtRecorder r = e.getValue();
            if (r.isClosed() || !r.subject().isAlive()) {
                stopRecording(e.getKey());
                continue;
            }
            r.tick();
        }
    }

    /** Starts recording an opted-in player who has just joined, when auto-record is on. */
    public static void onPlayerJoin(ServerPlayer player) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.pvtEnabled || !cfg.pvtAutoRecord) return;
        if (!hasConsented(player.getUUID())) return;
        startRecording(player);
    }

    public static void onPlayerLeave(ServerPlayer player) {
        stopRecording(player.getUUID());
    }

    // ── the policy ──────────────────────────────────────────────────────────────

    /** The trained policy, or null when there isn't one yet. */
    public static PvtNet policy() {
        if (!policyLoaded) {
            policyLoaded = true;
            policy = readNet(POLICY_FILE, PvtObservation.SIZE);
        }
        return policy;
    }

    public static boolean hasPolicy() { return policy() != null; }

    /** Forces the policy to be re-read from disk (after training, or on demand). */
    public static void reloadPolicy() {
        policyLoaded = false;
        policy = null;
        policy();
    }

    private static PvtNet readNet(Path file, int expectedInputs) {
        try {
            if (!Files.exists(file)) return null;
            try (DataInputStream in = new DataInputStream(
                    new BufferedInputStream(Files.newInputStream(file), 1 << 16))) {
                PvtNet net = PvtNet.read(in);
                // A policy trained against a different observation layout would drive the
                // bot from numbers that no longer mean what it learned they meant.
                if (net != null && net.inputSize() != expectedInputs) return null;
                return net;
            }
        } catch (IOException e) {
            return null;
        }
    }

    // ── training ────────────────────────────────────────────────────────────────

    public static PvtTrainer trainer() {
        if (trainer == null) trainer = new PvtTrainer(DEMOS, POLICY_FILE, IDM_FILE);
        return trainer;
    }

    public static boolean isTraining() { return trainer != null && trainer.isRunning(); }

    /**
     * Kicks off a training run.
     *
     * @param server  used to hop back onto the server thread when it finishes
     * @param onDone  given the finished trainer, on the server thread
     * @return false when a run is already going
     */
    public static boolean train(MinecraftServer server,
                                java.util.function.Consumer<PvtTrainer> onDone) {
        PvtTrainer t = trainer();
        return t.start(finished -> {
            lastMessage = finished.describe();
            Runnable apply = () -> {
                reloadPolicy();
                if (onDone != null) onDone.accept(finished);
            };
            if (server != null) server.execute(apply);
            else apply.run();
        });
    }

    // ── status ──────────────────────────────────────────────────────────────────

    /** A multi-line status report for {@code /ai pvt status}. */
    public static String status() {
        StringBuilder sb = new StringBuilder();
        ModConfig cfg = ModConfig.get();
        sb.append("PVT (pre-video training) — ").append(cfg.pvtEnabled ? "on" : "off").append('\n');

        PvtNet net = policy();
        if (net == null) {
            sb.append("Policy: none trained yet.\n");
        } else {
            sb.append(String.format("Policy: %,d weights, trained on %,d frames.%n",
                    net.parameterCount(), net.framesTrained()));
        }

        long bytes = folderSize(DEMOS);
        int frames = (int) (bytes / Math.max(1, PvtDataset.frameBytes()));
        sb.append(String.format("Recordings: ~%,d frames (%.1f MB) in %s%n",
                frames, bytes / 1_048_576.0, DEMOS));
        sb.append("Recording now: ").append(RECORDERS.size())
          .append("; opted in: ").append(consentedCount()).append('\n');

        if (isTraining()) {
            sb.append(trainer().describe());
        } else if (!lastMessage.isEmpty()) {
            sb.append("Last run: ").append(lastMessage);
        } else {
            sb.append("Never trained. Record some play, then /ai pvt train.");
        }
        return sb.toString();
    }

    public static String lastMessage() { return lastMessage; }

    private static long folderSize(Path folder) {
        if (!Files.isDirectory(folder)) return 0;
        try (var stream = Files.list(folder)) {
            return stream.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0L;
                }
            }).sum();
        } catch (IOException e) {
            return 0;
        }
    }

    /** Deletes every recording (not the trained policy). @return files removed */
    public static int clearRecordings() {
        stopAll();
        if (!Files.isDirectory(DEMOS)) return 0;
        int n = 0;
        try (var stream = Files.list(DEMOS)) {
            for (Path p : stream.filter(p -> p.getFileName().toString().endsWith(".bpd")).toList()) {
                try {
                    Files.deleteIfExists(p);
                    n++;
                } catch (IOException ignored) {
                    // Skip anything locked; the rest still go.
                }
            }
        } catch (IOException ignored) {
            // Nothing to clear.
        }
        return n;
    }

    /** Files are named after the subject, so the name has to be filesystem-safe. */
    private static String safeName(LivingEntity subject) {
        String raw = subject.getName().getString();
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toCharArray()) {
            sb.append(Character.isLetterOrDigit(c) || c == '_' || c == '-' ? c : '_');
        }
        String s = sb.toString();
        return s.isEmpty() ? "subject" : s.substring(0, Math.min(24, s.length()));
    }

    /** Live recorder summaries, newest first — for the status readout. */
    public static Map<UUID, String> liveRecordings() {
        Map<UUID, String> out = new LinkedHashMap<>();
        RECORDERS.forEach((id, r) -> out.put(id, r.describe()));
        return out;
    }
}
