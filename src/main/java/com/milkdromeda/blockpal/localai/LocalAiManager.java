package com.milkdromeda.blockpal.localai;

import com.milkdromeda.blockpal.ai.LocalModel;
import com.milkdromeda.blockpal.config.ModConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * <b>A language model that runs on this machine's own graphics card.</b>
 *
 * <p>This replaces the free keyless internet service as Blockpal's "works without an API
 * key" option. The trade is a good one: the free service was small, rate-limited, shared
 * with everybody and occasionally simply down, and every prompt — including everything
 * the bot could see — left the machine. A local model is private, free forever, offline,
 * and answers in about a second.
 *
 * <p>The cost is a download, and <b>this class will not make it without being asked.</b>
 * Choosing the local connection produces a {@link Plan} describing exactly what would be
 * fetched, how big it is and where it goes; nothing is downloaded until
 * {@link #accept} is called. That is deliberate: a mod that quietly pulls two gigabytes
 * onto somebody's metered connection has done something rude, however good the feature.
 *
 * <p>What runs is {@code llama-server} from llama.cpp, which speaks the same
 * OpenAI-compatible API Blockpal already uses — so once it is up, nothing else in the mod
 * has to know it is local. It is bound to loopback and never exposed to the network.
 */
public final class LocalAiManager {

    private LocalAiManager() {}

    private static final Path ROOT = FabricLoader.getInstance()
            .getConfigDir().resolve("blockpal").resolve("localai");
    private static final Path RUNTIME_DIR = ROOT.resolve("runtime");
    private static final Path MODEL_DIR = ROOT.resolve("models");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** Where the local model is in its life. */
    public enum State {
        /** Not the chosen connection, or switched off. */
        OFF,
        /** Chosen, but the download hasn't been agreed to yet. */
        NEEDS_CONSENT,
        /** Fetching the runtime and/or the weights. */
        DOWNLOADING,
        /** Files are here; the server process is coming up. */
        STARTING,
        /** Answering requests. */
        READY,
        /** Something went wrong; see {@link #lastError()}. */
        FAILED
    }

    private static volatile State state = State.OFF;
    private static volatile String detail = "";
    private static volatile String lastError = "";
    private static Process process;
    private static Thread worker;
    private static final Deque<String> LOG = new ArrayDeque<>();

    public static State state() { return state; }
    public static String detail() { return detail; }
    public static String lastError() { return lastError; }
    public static boolean isReady() { return state == State.READY; }
    public static boolean isBusy() { return state == State.DOWNLOADING || state == State.STARTING; }

    public static Path root() { return ROOT; }

    /** The OpenAI-compatible endpoint the rest of the mod talks to. */
    public static String endpoint() {
        return "http://127.0.0.1:" + port() + "/v1/chat/completions";
    }

    private static int port() {
        int p = ModConfig.get().localPort;
        return p < 1024 || p > 65535 ? 8081 : p;
    }

    /** The model this server is set up to run. */
    public static LocalModel model() {
        LocalModel m = LocalModel.byId(ModConfig.get().localModelId);
        return m == null ? LocalModel.defaultModel() : m;
    }

    private static Path modelFile(LocalModel m) {
        return MODEL_DIR.resolve(m.fileName());
    }

    /** True when the weights are already on disk, so no download is needed. */
    public static boolean modelPresent(LocalModel m) {
        Path f = modelFile(m);
        try {
            // A partial file from a killed download would be smaller; treat it as absent.
            return Files.isRegularFile(f) && Files.size(f) >= m.bytes() - 1_048_576L;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * True when everything needed is on disk and somebody has agreed to it — so a bot may
     * plan with this connection. Being <i>set up</i> is not the same as being
     * <i>running</i>: llama-server may still be loading, and requests simply wait.
     */
    public static boolean isSetUp() {
        return ModConfig.get().localConsented && modelPresent(model()) && runtimeBinary() != null;
    }

    /** The unpacked llama-server binary, or null when the runtime isn't installed. */
    public static Path runtimeBinary() {
        try {
            return Archives.findExecutable(RUNTIME_DIR, "llama-server");
        } catch (IOException e) {
            return null;
        }
    }

    // ── the consent step ────────────────────────────────────────────────────────

    /**
     * What setting up would involve. Nothing is downloaded to work this out — the sizes
     * come from the catalogue and from the release listing.
     */
    public record Plan(LocalModel model, LlamaRelease.Choice runtime,
                       boolean modelPresent, boolean runtimePresent, String gpuNote) {

        /** Bytes that would actually be fetched, ignoring anything already here. */
        public long downloadBytes() {
            long total = 0;
            if (!modelPresent) total += model.bytes();
            if (!runtimePresent && runtime != null) total += runtime.totalBytes();
            return total;
        }

        public boolean needsDownload() { return downloadBytes() > 0; }

        /** The text a player is shown before agreeing. Plain, specific, no salesmanship. */
        public String consentText(Path root) {
            StringBuilder sb = new StringBuilder();
            sb.append("Blockpal would like to download an AI model and run it on this machine.\n\n");
            sb.append("  Model    ").append(model.display())
              .append("  (").append(model.sizeText()).append(")\n");
            sb.append("           ").append(model.blurb()).append('\n');
            if (runtime != null) {
                sb.append("  Runtime  llama.cpp ").append(runtime.tag())
                  .append("  (").append(runtime.sizeText()).append(")\n");
                sb.append("           will run on: ").append(runtime.backend().display()).append('\n');
            }
            sb.append("  Uses     ").append(model.vramText()).append(" of video memory\n");
            sb.append("  Saved to ").append(root).append('\n');
            sb.append("  ").append(gpuNote).append("\n\n");
            if (needsDownload()) {
                sb.append("Total download: ").append(Downloader.bytes(downloadBytes())).append('\n');
            } else {
                sb.append("Everything is already downloaded — this will just start it.\n");
            }
            sb.append("Nothing is downloaded until you say yes. Once it's running, no prompt "
                    + "or picture ever leaves this machine.");
            return sb.toString();
        }
    }

    /** The plan most recently shown to somebody, waiting on a yes or no. */
    private static volatile Plan pending;

    public static Plan pending() { return pending; }

    public static void setPending(Plan plan) {
        pending = plan;
        if (plan != null && state == State.OFF) state = State.NEEDS_CONSENT;
    }

    public static void clearPending() { pending = null; }

    /**
     * Works out what setting up the given model would take.
     *
     * @throws IOException when the llama.cpp release list can't be reached
     */
    public static Plan plan(LocalModel m) throws IOException {
        LocalModel target = m == null ? model() : m;
        LlamaRelease.Choice runtime = LlamaRelease.resolve();
        if (runtime == null) {
            throw new IOException("llama.cpp doesn't publish a build for "
                    + LlamaRelease.platformText() + ", so the local model can't run here.");
        }
        String note = runtime.backend().isGpu()
                ? "Graphics card: " + GpuProbe.describe()
                : "No usable GPU build for this machine — it would run on the CPU, which is "
                  + "much slower (expect ten seconds or more per reply).";
        return new Plan(target, runtime, modelPresent(target), runtimeBinary() != null, note);
    }

    // ── doing it ────────────────────────────────────────────────────────────────

    /**
     * Downloads whatever is missing and starts the server. Runs on its own thread and
     * returns immediately.
     *
     * @param onMessage progress lines, called from the worker thread
     * @return false when a setup is already running
     */
    public static synchronized boolean accept(Plan plan, MinecraftServer server,
                                              Consumer<String> onMessage) {
        if (isBusy()) return false;
        ModConfig cfg = ModConfig.get();
        cfg.localConsented = true;
        cfg.localModelId = plan.model().id();
        ModConfig.save();

        state = State.DOWNLOADING;
        lastError = "";
        worker = new Thread(() -> {
            try {
                install(plan, onMessage);
                startProcess(plan.model(), onMessage);
            } catch (Exception e) {
                fail(String.valueOf(e.getMessage()), onMessage);
            }
        }, "blockpal-localai-setup");
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    private static void install(Plan plan, Consumer<String> onMessage) throws IOException {
        Files.createDirectories(RUNTIME_DIR);
        Files.createDirectories(MODEL_DIR);

        if (!plan.runtimePresent() && plan.runtime() != null) {
            say(onMessage, "Downloading the AI runtime (" + plan.runtime().sizeText() + ")…");
            Path archive = ROOT.resolve(plan.runtime().assetName());
            Downloader.download(plan.runtime().url(), archive,
                    (done, total) -> progress(onMessage, "Runtime", done, total));
            say(onMessage, "Unpacking the runtime…");
            Archives.extract(archive, RUNTIME_DIR);
            Files.deleteIfExists(archive);

            if (plan.runtime().hasCompanion()) {
                // The Windows CUDA build ships its GPU runtime libraries separately; without
                // them llama-server starts and dies on a missing DLL.
                say(onMessage, "Downloading GPU support libraries…");
                Path companion = ROOT.resolve(plan.runtime().companionName());
                Downloader.download(plan.runtime().companionUrl(), companion,
                        (done, total) -> progress(onMessage, "GPU libraries", done, total));
                Archives.extract(companion, RUNTIME_DIR);
                Files.deleteIfExists(companion);
            }
            Path binary = runtimeBinary();
            if (binary == null) {
                throw new IOException("the runtime unpacked but llama-server wasn't in it");
            }
            Archives.makeExecutable(binary);
        }

        if (!plan.modelPresent()) {
            say(onMessage, "Downloading " + plan.model().display()
                    + " (" + plan.model().sizeText() + ") — this is the big one.");
            Downloader.download(plan.model().downloadUrl(), modelFile(plan.model()),
                    (done, total) -> progress(onMessage, plan.model().display(), done, total));
        }
    }

    private static long lastProgressAt;

    private static void progress(Consumer<String> onMessage, String what, long done, long total) {
        long now = System.currentTimeMillis();
        // Chat is not a progress bar: at most one line every 15 seconds.
        if (now - lastProgressAt < 15_000 && done != total) return;
        lastProgressAt = now;
        detail = what + " " + Downloader.percent(done, total);
        say(onMessage, "  " + detail);
    }

    /** Starts the server process against an already-downloaded model. */
    public static synchronized boolean start(MinecraftServer server, Consumer<String> onMessage) {
        if (isBusy() || state == State.READY) return false;
        LocalModel m = model();
        if (!modelPresent(m) || runtimeBinary() == null) {
            state = State.NEEDS_CONSENT;
            detail = "not downloaded yet";
            return false;
        }
        state = State.STARTING;
        worker = new Thread(() -> {
            try {
                startProcess(m, onMessage);
            } catch (Exception e) {
                fail(String.valueOf(e.getMessage()), onMessage);
            }
        }, "blockpal-localai-start");
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    private static void startProcess(LocalModel m, Consumer<String> onMessage) throws IOException {
        stopProcess();
        Path binary = runtimeBinary();
        if (binary == null) throw new IOException("the runtime isn't installed");
        Archives.makeExecutable(binary);

        state = State.STARTING;
        say(onMessage, "Starting " + m.display() + " on " + LlamaRelease.platformText() + "…");

        java.util.List<String> command = new java.util.ArrayList<>();
        command.add(binary.toAbsolutePath().toString());
        command.add("-m");
        command.add(modelFile(m).toAbsolutePath().toString());
        // Loopback only. This is a private brain for this server, not a service.
        command.add("--host");
        command.add("127.0.0.1");
        command.add("--port");
        command.add(String.valueOf(port()));
        command.add("-c");
        command.add(String.valueOf(Math.max(512, Math.min(32768, ModConfig.get().localContext))));
        int layers = ModConfig.get().localGpuLayers;
        if (layers >= 0) {
            // Otherwise llama-server's own default ("auto") decides, which is usually right.
            command.add("-ngl");
            command.add(String.valueOf(layers));
        }

        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(binary.getParent().toFile())
                .redirectErrorStream(true);
        process = builder.start();
        pumpOutput(process);

        // llama-server loads several gigabytes off disk before it answers anything, so
        // give it a generous window rather than declaring failure on a slow machine.
        long deadline = System.currentTimeMillis() + 180_000;
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                throw new IOException("the model server stopped straight away — " + tailLog());
            }
            if (ping()) {
                state = State.READY;
                detail = m.display() + " on " + endpoint();
                say(onMessage, "Local AI is ready: " + m.display() + " — no key, no internet, "
                        + "nothing leaves this machine.");
                return;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new IOException("the model server didn't answer within three minutes — " + tailLog());
    }

    /** True when llama-server is up and answering. */
    public static boolean ping() {
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + port() + "/v1/models"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() / 100 == 2;
        } catch (Exception e) {
            return false;
        }
    }

    private static void pumpOutput(Process p) {
        Thread pump = new Thread(() -> {
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (LOG) {
                        if (LOG.size() >= 40) LOG.removeFirst();
                        LOG.addLast(line);
                    }
                }
            } catch (IOException ignored) {
                // The process ended; nothing more to read.
            }
        }, "blockpal-localai-log");
        pump.setDaemon(true);
        pump.start();
    }

    private static String tailLog() {
        synchronized (LOG) {
            if (LOG.isEmpty()) return "it printed nothing.";
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (java.util.Iterator<String> it = LOG.descendingIterator();
                 it.hasNext() && shown < 3; shown++) {
                sb.insert(0, it.next() + " ");
            }
            return sb.toString().trim();
        }
    }

    /** The last lines the model server printed, for {@code /ai local log}. */
    public static String log() {
        synchronized (LOG) {
            return LOG.isEmpty() ? "(nothing logged yet)" : String.join("\n", LOG);
        }
    }

    private static void fail(String message, Consumer<String> onMessage) {
        state = State.FAILED;
        lastError = message == null ? "unknown problem" : message;
        detail = lastError;
        say(onMessage, "Local AI setup failed: " + lastError);
    }

    private static void say(Consumer<String> onMessage, String message) {
        if (onMessage != null) {
            try {
                onMessage.accept(message);
            } catch (Exception ignored) {
                // A broken listener must not abort a running download.
            }
        }
    }

    // ── stopping ────────────────────────────────────────────────────────────────

    /** Stops the model server. Safe to call when nothing is running. */
    public static synchronized void stop() {
        stopProcess();
        if (state != State.FAILED) {
            state = State.OFF;
            detail = "";
        }
    }

    private static void stopProcess() {
        Process p = process;
        process = null;
        if (p == null) return;
        p.destroy();
        try {
            if (!p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
        }
    }

    // ── lifecycle ───────────────────────────────────────────────────────────────

    /**
     * Brings the local model up or down to match the current settings. Called when the
     * server starts and whenever the connection is changed.
     */
    public static void sync(MinecraftServer server) {
        ModConfig cfg = ModConfig.get();
        boolean wanted = cfg.connection() == com.milkdromeda.blockpal.ai.AiConnection.LOCAL;
        if (!wanted) {
            stop();
            return;
        }
        if (isBusy() || state == State.READY) return;
        if (!cfg.localConsented || !modelPresent(model()) || runtimeBinary() == null) {
            state = State.NEEDS_CONSENT;
            detail = "run /ai local setup";
            return;
        }
        if (cfg.localAutoStart) start(server, message -> {});
    }

    /** A multi-line report for {@code /ai local}. */
    public static String status() {
        LocalModel m = model();
        StringBuilder sb = new StringBuilder();
        sb.append("Local AI — runs on this machine, no key, nothing leaves it\n");
        sb.append("  Model    ").append(m.display()).append(" (").append(m.sizeText()).append(")")
          .append(modelPresent(m) ? " — downloaded" : " — not downloaded").append('\n');
        Path binary = runtimeBinary();
        sb.append("  Runtime  ").append(binary == null ? "not installed" : "installed").append('\n');
        sb.append("  Hardware ").append(GpuProbe.describe()).append('\n');
        sb.append("  State    ").append(state.name().toLowerCase(Locale.ROOT));
        if (!detail.isEmpty()) sb.append(" — ").append(detail);
        sb.append('\n');
        if (state == State.READY) sb.append("  Endpoint ").append(endpoint()).append('\n');
        if (!lastError.isEmpty()) sb.append("  Last error: ").append(lastError).append('\n');
        return sb.toString();
    }
}
