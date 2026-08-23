package com.milkdromeda.blockpal.pvt;

import com.milkdromeda.blockpal.config.ModConfig;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * <b>Turns recorded play into a policy.</b> Runs entirely on a daemon thread — training
 * takes minutes, and a server tick has 50 milliseconds — and reports progress that
 * {@code /ai pvt status} and the finished-message can read.
 *
 * <p>The run has three phases, and the first two only happen when they are needed:
 *
 * <ol>
 *   <li><b>Inverse dynamics.</b> If the archive holds observation-only frames, a
 *       {@link PvtIdm} is trained on the properly-labelled ones (which know both what
 *       was seen and what was pressed) so it can read actions off consecutive views.</li>
 *   <li><b>Labelling.</b> The inverse dynamics model fills in the missing actions. Labels
 *       it isn't confident about are dropped rather than guessed — a wrong label is worse
 *       than no label, because the policy has no way to tell it was wrong.</li>
 *   <li><b>Behaviour cloning.</b> The policy is trained on everything, with a held-out
 *       tenth kept back so the reported accuracy means something, and early stopping when
 *       that held-out loss stops improving.</li>
 * </ol>
 *
 * <p>The best weights seen are kept, not the last ones: the final epoch of an overfitting
 * run is usually worse than something ten epochs earlier.
 */
public final class PvtTrainer {

    /** Frames per gradient step. */
    private static final int BATCH = 64;
    /** Fraction of frames held back to measure honestly. */
    private static final double VALIDATION_SPLIT = 0.1;
    /** Stop when the held-out loss hasn't improved for this many epochs. */
    private static final int PATIENCE = 4;
    /** Below this the inverse dynamics model's guess is discarded rather than used. */
    private static final double MIN_IDM_CONFIDENCE = 0.5;

    private final Path demoFolder;
    private final Path policyFile;
    private final Path idmFile;

    private volatile boolean running;
    private volatile boolean cancelled;
    private volatile String phase = "idle";
    private volatile int percent;
    private volatile String result = "";
    private volatile String error = "";
    private volatile double trainLoss;
    private volatile double valLoss;
    private volatile double valAccuracy;
    private volatile int framesUsed;
    private volatile int inferredLabels;

    private Thread thread;

    public PvtTrainer(Path demoFolder, Path policyFile, Path idmFile) {
        this.demoFolder = demoFolder;
        this.policyFile = policyFile;
        this.idmFile = idmFile;
    }

    public boolean isRunning() { return running; }
    public String phase() { return phase; }
    public int percent() { return percent; }
    public String result() { return result; }
    public String error() { return error; }
    public double valAccuracy() { return valAccuracy; }
    public double valLoss() { return valLoss; }
    public double trainLoss() { return trainLoss; }
    public int framesUsed() { return framesUsed; }
    public int inferredLabels() { return inferredLabels; }

    public void cancel() { cancelled = true; }

    /** A one-line progress report for chat. */
    public String describe() {
        if (running) {
            return "PVT training — " + phase + " " + percent + "%"
                    + (trainLoss > 0 ? String.format(" (loss %.3f)", trainLoss) : "");
        }
        if (!error.isEmpty()) return "PVT training failed: " + error;
        return result.isEmpty() ? "PVT training is not running." : result;
    }

    /**
     * Starts a training run.
     *
     * @param onFinished called from the training thread when it ends — the caller is
     *                   responsible for hopping back onto the server thread
     * @return false when a run is already in progress
     */
    public boolean start(java.util.function.Consumer<PvtTrainer> onFinished) {
        if (running) return false;
        running = true;
        cancelled = false;
        error = "";
        result = "";
        percent = 0;
        thread = new Thread(() -> {
            try {
                run();
            } catch (Throwable t) {
                error = String.valueOf(t.getMessage());
            } finally {
                running = false;
                phase = "done";
                if (onFinished != null) {
                    try {
                        onFinished.accept(this);
                    } catch (Exception ignored) {
                        // A broken callback must not mask a successful training run.
                    }
                }
            }
        }, "blockpal-pvt-trainer");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);   // never compete with the server thread
        thread.start();
        return true;
    }

    // ── the run ─────────────────────────────────────────────────────────────────

    private void run() throws IOException {
        ModConfig cfg = ModConfig.get();

        phase = "loading";
        PvtDataset data = PvtDataset.load(demoFolder, cfg.pvtMaxFrames);
        if (data.size() < BATCH * 2) {
            error = "not enough recorded play yet — " + data.size() + " frames, need at least "
                    + (BATCH * 2) + ". Record some play with /ai pvt record.";
            return;
        }

        int unlabelled = data.size() - data.labelledCount();
        if (unlabelled > 0 && data.labelledCount() >= BATCH * 2) {
            trainInverseDynamics(data, cfg);
            if (cancelled) { error = "cancelled"; return; }
            labelWithInverseDynamics(data);
        }
        if (cancelled) { error = "cancelled"; return; }

        // Only frames with a real or confidently-inferred action can teach anything.
        PvtDataset usable = new PvtDataset();
        for (PvtFrame f : data.frames()) {
            if (f.labelled()) usable.add(f);
        }
        framesUsed = usable.size();
        if (usable.size() < BATCH * 2) {
            error = "only " + usable.size() + " usable frames after labelling — record more play.";
            return;
        }

        phase = "training";
        trainPolicy(usable, cfg);
    }

    private void trainInverseDynamics(PvtDataset data, ModConfig cfg) throws IOException {
        phase = "inverse dynamics";
        List<PvtDataset.Pair> pairs = data.idmPairs();
        if (pairs.size() < BATCH * 2) return;

        PvtIdm idm = PvtIdm.fresh(Math.max(64, cfg.pvtHiddenSize), 1234L);
        Random rng = new Random(4242);
        int epochs = Math.max(3, Math.min(30, cfg.pvtEpochs / 2));
        int threads = workerThreads();

        for (int epoch = 0; epoch < epochs && !cancelled; epoch++) {
            java.util.Collections.shuffle(pairs, rng);
            for (int at = 0; at + BATCH <= pairs.size() && !cancelled; at += BATCH) {
                float[][] xs = new float[BATCH][];
                int[][] ys = new int[BATCH][];
                for (int i = 0; i < BATCH; i++) {
                    xs[i] = pairs.get(at + i).input();
                    ys[i] = pairs.get(at + i).labels();
                }
                PvtNet.BatchResult r = idm.net().trainBatch(xs, ys, (float) cfg.pvtLearningRate, threads);
                trainLoss = r.loss();
            }
            percent = (int) (100.0 * (epoch + 1) / epochs);
        }
        idm.net().shutdown();
        this.idm = idm;
        writeNet(idmFile, idm.net());
    }

    private PvtIdm idm;

    private void labelWithInverseDynamics(PvtDataset data) {
        if (idm == null) return;
        phase = "labelling";
        List<PvtDataset.Pair> pairs = data.unlabelledPairs();
        int done = 0;
        for (PvtDataset.Pair pair : pairs) {
            if (cancelled) return;
            PvtNet.Prediction p = idm.predict(pair.input());
            // A label the model isn't sure of is worse than none: the policy would learn
            // it as ground truth with no way of knowing better.
            if (p.confidence() >= MIN_IDM_CONFIDENCE) {
                data.relabel(pair.frameIndex(), p.action().heads());
                inferredLabels++;
            }
            if (++done % 512 == 0) percent = (int) (100.0 * done / Math.max(1, pairs.size()));
        }
    }

    private void trainPolicy(PvtDataset data, ModConfig cfg) throws IOException {
        Random rng = new Random(97531L);
        data.shuffle(rng);

        int valCount = Math.max(BATCH, (int) (data.size() * VALIDATION_SPLIT));
        int trainCount = data.size() - valCount;
        if (trainCount < BATCH) {
            error = "not enough frames to train and still measure honestly.";
            return;
        }
        float[][] valX = data.observations(trainCount, data.size());
        int[][] valY = data.labels(trainCount, data.size());

        PvtNet net = new PvtNet(PvtObservation.SIZE, cfg.pvtHiddenSize, cfg.pvtHiddenSize,
                System.nanoTime());
        int epochs = Math.max(1, cfg.pvtEpochs);
        int threads = workerThreads();
        float lr = (float) cfg.pvtLearningRate;

        double best = Double.MAX_VALUE;
        byte[] bestWeights = null;
        int sinceImproved = 0;

        for (int epoch = 0; epoch < epochs && !cancelled; epoch++) {
            // Reshuffle only the training portion, so the held-out frames stay held out.
            List<Integer> order = new ArrayList<>(trainCount);
            for (int i = 0; i < trainCount; i++) order.add(i);
            java.util.Collections.shuffle(order, rng);

            double epochLoss = 0;
            int batches = 0;
            for (int at = 0; at + BATCH <= trainCount && !cancelled; at += BATCH) {
                float[][] xs = new float[BATCH][];
                int[][] ys = new int[BATCH][];
                for (int i = 0; i < BATCH; i++) {
                    int index = order.get(at + i);
                    xs[i] = data.frames().get(index).observation();
                    ys[i] = data.frames().get(index).labels();
                }
                PvtNet.BatchResult r = net.trainBatch(xs, ys, lr, threads);
                epochLoss += r.loss();
                batches++;
            }
            trainLoss = batches == 0 ? 0 : epochLoss / batches;

            PvtNet.BatchResult v = net.evaluate(valX, valY);
            valLoss = v.loss();
            valAccuracy = v.accuracy();
            percent = (int) (100.0 * (epoch + 1) / epochs);

            if (valLoss < best - 1e-4) {
                best = valLoss;
                bestWeights = snapshot(net);
                sinceImproved = 0;
            } else if (++sinceImproved >= PATIENCE) {
                // Held-out loss has stopped improving — more epochs would only memorise.
                break;
            }
        }
        net.shutdown();
        if (cancelled) { error = "cancelled"; return; }

        PvtNet finalNet = net;
        if (bestWeights != null) {
            PvtNet restored = restore(bestWeights);
            if (restored != null) finalNet = restored;
        }
        finalNet.addFramesTrained(framesUsed);
        writeNet(policyFile, finalNet);

        result = String.format(
                "PVT policy trained on %,d frames%s — held-out accuracy %.1f%%, loss %.3f.",
                framesUsed,
                inferredLabels > 0 ? " (" + String.format("%,d", inferredLabels)
                        + " labelled by the inverse dynamics model)" : "",
                valAccuracy * 100, valLoss);
    }

    /** Training should be slow and polite, not fast and stuttery — leave a core spare. */
    private static int workerThreads() {
        return Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
    }

    private static byte[] snapshot(PvtNet net) {
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bos)) {
                net.write(out);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private static PvtNet restore(byte[] bytes) {
        try (java.io.DataInputStream in = new java.io.DataInputStream(
                new java.io.ByteArrayInputStream(bytes))) {
            return PvtNet.read(in);
        } catch (IOException e) {
            return null;
        }
    }

    /** Writes a network beside its target then moves it into place, so a crash mid-write
     *  can never leave a half-a-policy file that would load as nonsense. */
    static void writeNet(Path file, PvtNet net) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(tmp), 1 << 16))) {
            net.write(out);
        }
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
