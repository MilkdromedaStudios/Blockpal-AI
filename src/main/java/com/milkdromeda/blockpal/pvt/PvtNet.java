package com.milkdromeda.blockpal.pvt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <b>The network PVT actually learns.</b> A plain two-hidden-layer perceptron with a
 * factored output — one softmax per {@link PvtAction} head — trained by Adam on
 * cross-entropy against recorded human actions.
 *
 * <p><b>Why hand-rolled?</b> Because a Fabric mod cannot ship a tensor library. Adding
 * one would mean a hundred-megabyte native dependency, per-platform binaries, and a jar
 * that fails to load on somebody's ARM laptop. The model this needs is small — about
 * a hundred thousand weights — so plain float arrays run it in tens of microseconds,
 * comfortably inside a 50 ms tick, and train it on a background thread in minutes.
 *
 * <p>Everything here is deliberately ordinary: He initialisation, ReLU, Adam with the
 * usual constants, and gradient clipping so one strange frame cannot blow the weights
 * up. There is nothing clever to go wrong in a place nobody can debug.
 *
 * <p>The same class serves as the <i>policy</i> (observation → action) and as the
 * <i>inverse dynamics model</i> (two consecutive observations → the action between
 * them). They differ only in input width, which is why the input size is a field.
 */
public final class PvtNet {

    /** Bumped whenever the on-disk layout changes, so an old file is refused not misread. */
    private static final int FILE_MAGIC = 0x50565431;   // "PVT1"

    private final int inputs;
    private final int hidden1;
    private final int hidden2;

    // Weights are flat row-major float arrays: w1[j * inputs + i].
    private final float[] w1, b1, w2, b2, w3, b3;

    // Adam moments, one pair per weight array.
    private final float[] mW1, vW1, mB1, vB1, mW2, vW2, mB2, vB2, mW3, vW3, mB3, vB3;

    private int step;                       // Adam's time step, for bias correction
    private long framesTrained;             // provenance, shown in /ai pvt status

    private static final float BETA1 = 0.9f;
    private static final float BETA2 = 0.999f;
    private static final float EPS = 1e-8f;
    /** Any single gradient larger than this is clipped — one odd frame can't wreck it. */
    private static final float CLIP = 5f;

    public PvtNet(int inputs, int hidden1, int hidden2, long seed) {
        this.inputs = inputs;
        this.hidden1 = hidden1;
        this.hidden2 = hidden2;
        int outputs = PvtAction.LOGITS;

        w1 = new float[hidden1 * inputs]; b1 = new float[hidden1];
        w2 = new float[hidden2 * hidden1]; b2 = new float[hidden2];
        w3 = new float[outputs * hidden2]; b3 = new float[outputs];

        mW1 = new float[w1.length]; vW1 = new float[w1.length];
        mB1 = new float[b1.length]; vB1 = new float[b1.length];
        mW2 = new float[w2.length]; vW2 = new float[w2.length];
        mB2 = new float[b2.length]; vB2 = new float[b2.length];
        mW3 = new float[w3.length]; vW3 = new float[w3.length];
        mB3 = new float[b3.length]; vB3 = new float[b3.length];

        Random rng = new Random(seed);
        heInit(w1, inputs, rng);
        heInit(w2, hidden1, rng);
        heInit(w3, hidden2, rng);
    }

    private PvtNet(int inputs, int hidden1, int hidden2) {
        this(inputs, hidden1, hidden2, 1L);
    }

    private static void heInit(float[] w, int fanIn, Random rng) {
        double scale = Math.sqrt(2.0 / Math.max(1, fanIn));
        for (int i = 0; i < w.length; i++) w[i] = (float) (rng.nextGaussian() * scale);
    }

    public int inputSize() { return inputs; }
    public int hidden1Size() { return hidden1; }
    public int hidden2Size() { return hidden2; }
    public long framesTrained() { return framesTrained; }
    public void addFramesTrained(long n) { framesTrained += n; }

    /** Roughly how much memory the weights occupy, for the status readout. */
    public int parameterCount() {
        return w1.length + b1.length + w2.length + b2.length + w3.length + b3.length;
    }

    // ── forward ─────────────────────────────────────────────────────────────────

    /** Scratch buffers so a forward pass allocates nothing on the hot path. */
    public static final class Scratch {
        final float[] h1, h2, out;
        Scratch(int hidden1, int hidden2) {
            h1 = new float[hidden1];
            h2 = new float[hidden2];
            out = new float[PvtAction.LOGITS];
        }
    }

    public Scratch scratch() { return new Scratch(hidden1, hidden2); }

    /**
     * Runs one observation through the network.
     *
     * @return the scratch buffer's logits (not a copy — read them before the next call)
     */
    public float[] forward(float[] x, Scratch s) {
        dense(x, w1, b1, s.h1, inputs, hidden1, true);
        dense(s.h1, w2, b2, s.h2, hidden1, hidden2, true);
        dense(s.h2, w3, b3, s.out, hidden2, PvtAction.LOGITS, false);
        return s.out;
    }

    private static void dense(float[] in, float[] w, float[] b, float[] out,
                              int nIn, int nOut, boolean relu) {
        for (int j = 0; j < nOut; j++) {
            int base = j * nIn;
            float sum = b[j];
            for (int i = 0; i < nIn; i++) sum += w[base + i] * in[i];
            out[j] = relu && sum < 0 ? 0 : sum;
        }
    }

    /**
     * Picks the most likely class for each head, and reports how confident the network
     * was about the heads that matter for movement.
     */
    public Prediction predict(float[] x, Scratch s) {
        float[] logits = forward(x, s);
        int[] heads = new int[PvtAction.HEADS];
        double confidence = 0;
        int counted = 0;
        for (int h = 0; h < PvtAction.HEADS; h++) {
            int off = PvtAction.HEAD_OFFSETS[h];
            int size = PvtAction.HEAD_SIZES[h];
            float[] probs = softmax(logits, off, size);
            int best = 0;
            for (int c = 1; c < size; c++) if (probs[c] > probs[best]) best = c;
            heads[h] = best;
            // Confidence is the mean top-probability over the heads that steer the body.
            // The binary heads are nearly always "off" and would flatter the number.
            if (h == PvtAction.HEAD_FORWARD || h == PvtAction.HEAD_STRAFE
                    || h == PvtAction.HEAD_YAW || h == PvtAction.HEAD_PITCH) {
                confidence += probs[best];
                counted++;
            }
        }
        return new Prediction(new PvtAction(heads), counted == 0 ? 0 : confidence / counted);
    }

    /** What the policy decided, and how sure it was. */
    public record Prediction(PvtAction action, double confidence) {}

    private static float[] softmax(float[] logits, int off, int size) {
        float[] p = new float[size];
        float max = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < size; i++) max = Math.max(max, logits[off + i]);
        float total = 0;
        for (int i = 0; i < size; i++) {
            p[i] = (float) Math.exp(logits[off + i] - max);
            total += p[i];
        }
        if (total <= 0) {
            java.util.Arrays.fill(p, 1f / size);
            return p;
        }
        for (int i = 0; i < size; i++) p[i] /= total;
        return p;
    }

    // ── training ────────────────────────────────────────────────────────────────

    /** Accumulated gradients for one (possibly parallel) pass over a minibatch. */
    private final class Grad {
        final float[] gW1 = new float[w1.length], gB1 = new float[b1.length];
        final float[] gW2 = new float[w2.length], gB2 = new float[b2.length];
        final float[] gW3 = new float[w3.length], gB3 = new float[b3.length];
        final Scratch s = scratch();
        double loss;
        int correct;
        int counted;

        void clear() {
            java.util.Arrays.fill(gW1, 0); java.util.Arrays.fill(gB1, 0);
            java.util.Arrays.fill(gW2, 0); java.util.Arrays.fill(gB2, 0);
            java.util.Arrays.fill(gW3, 0); java.util.Arrays.fill(gB3, 0);
            loss = 0; correct = 0; counted = 0;
        }

        void add(Grad o) {
            addInto(gW1, o.gW1); addInto(gB1, o.gB1);
            addInto(gW2, o.gW2); addInto(gB2, o.gB2);
            addInto(gW3, o.gW3); addInto(gB3, o.gB3);
            loss += o.loss; correct += o.correct; counted += o.counted;
        }

        private void addInto(float[] a, float[] b) {
            for (int i = 0; i < a.length; i++) a[i] += b[i];
        }

        /** One sample's forward + backward pass, accumulating into this gradient. */
        void accumulate(float[] x, int[] labels) {
            float[] logits = forward(x, s);

            // dL/dlogits for a softmax + cross-entropy per head is simply (p - onehot).
            float[] dOut = new float[PvtAction.LOGITS];
            for (int h = 0; h < PvtAction.HEADS; h++) {
                int off = PvtAction.HEAD_OFFSETS[h];
                int size = PvtAction.HEAD_SIZES[h];
                float[] p = softmax(logits, off, size);
                int label = Math.max(0, Math.min(size - 1, labels[h]));
                loss += -Math.log(Math.max(1e-9f, p[label]));
                int best = 0;
                for (int c = 1; c < size; c++) if (p[c] > p[best]) best = c;
                if (best == label) correct++;
                counted++;
                for (int c = 0; c < size; c++) dOut[off + c] = p[c] - (c == label ? 1f : 0f);
            }

            // Layer 3 (no activation on the output).
            float[] dH2 = new float[hidden2];
            for (int j = 0; j < PvtAction.LOGITS; j++) {
                float d = dOut[j];
                if (d == 0) continue;
                int base = j * hidden2;
                gB3[j] += d;
                for (int i = 0; i < hidden2; i++) {
                    gW3[base + i] += d * s.h2[i];
                    dH2[i] += d * w3[base + i];
                }
            }
            // Layer 2 (ReLU).
            float[] dH1 = new float[hidden1];
            for (int j = 0; j < hidden2; j++) {
                float d = s.h2[j] > 0 ? dH2[j] : 0;
                if (d == 0) continue;
                int base = j * hidden1;
                gB2[j] += d;
                for (int i = 0; i < hidden1; i++) {
                    gW2[base + i] += d * s.h1[i];
                    dH1[i] += d * w2[base + i];
                }
            }
            // Layer 1 (ReLU).
            for (int j = 0; j < hidden1; j++) {
                float d = s.h1[j] > 0 ? dH1[j] : 0;
                if (d == 0) continue;
                int base = j * inputs;
                gB1[j] += d;
                for (int i = 0; i < inputs; i++) gW1[base + i] += d * x[i];
            }
        }
    }

    private Grad[] workers;
    private java.util.concurrent.ExecutorService pool;

    /** How a minibatch went. */
    public record BatchResult(double loss, double accuracy) {}

    /**
     * Trains on one minibatch and applies the update.
     *
     * <p>Samples inside a batch are independent, so they are split across a small worker
     * pool — a demonstration set of any size is otherwise a long single-threaded wait.
     *
     * @param xs      one observation per row
     * @param labels  one label per head, per row
     * @param lr      Adam step size
     * @param threads worker threads (1 = run inline)
     */
    public BatchResult trainBatch(float[][] xs, int[][] labels, float lr, int threads) {
        int n = xs.length;
        if (n == 0) return new BatchResult(0, 0);
        int workerCount = Math.max(1, Math.min(threads, n));

        if (workers == null || workers.length != workerCount) {
            shutdown();
            workers = new Grad[workerCount];
            for (int i = 0; i < workerCount; i++) workers[i] = new Grad();
            if (workerCount > 1) {
                pool = java.util.concurrent.Executors.newFixedThreadPool(workerCount, r -> {
                    Thread t = new Thread(r, "blockpal-pvt-train");
                    t.setDaemon(true);
                    return t;
                });
            }
        }
        for (Grad g : workers) g.clear();

        if (workerCount == 1) {
            for (int i = 0; i < n; i++) workers[0].accumulate(xs[i], labels[i]);
        } else {
            AtomicInteger cursor = new AtomicInteger();
            java.util.List<java.util.concurrent.Future<?>> jobs = new java.util.ArrayList<>();
            for (int w = 0; w < workerCount; w++) {
                Grad g = workers[w];
                jobs.add(pool.submit(() -> {
                    int i;
                    while ((i = cursor.getAndIncrement()) < n) g.accumulate(xs[i], labels[i]);
                }));
            }
            for (java.util.concurrent.Future<?> f : jobs) {
                try {
                    f.get();
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                    return new BatchResult(0, 0);
                }
            }
            for (int w = 1; w < workerCount; w++) workers[0].add(workers[w]);
        }

        Grad total = workers[0];
        float scale = 1f / n;
        step++;
        adam(w1, total.gW1, mW1, vW1, lr, scale);
        adam(b1, total.gB1, mB1, vB1, lr, scale);
        adam(w2, total.gW2, mW2, vW2, lr, scale);
        adam(b2, total.gB2, mB2, vB2, lr, scale);
        adam(w3, total.gW3, mW3, vW3, lr, scale);
        adam(b3, total.gB3, mB3, vB3, lr, scale);

        double accuracy = total.counted == 0 ? 0 : total.correct / (double) total.counted;
        return new BatchResult(total.loss / n, accuracy);
    }

    /** Releases the training worker pool — call when a training run finishes. */
    public void shutdown() {
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
        workers = null;
    }

    private void adam(float[] p, float[] g, float[] m, float[] v, float lr, float scale) {
        float bc1 = 1f - (float) Math.pow(BETA1, step);
        float bc2 = 1f - (float) Math.pow(BETA2, step);
        for (int i = 0; i < p.length; i++) {
            float grad = g[i] * scale;
            if (grad > CLIP) grad = CLIP;
            else if (grad < -CLIP) grad = -CLIP;
            m[i] = BETA1 * m[i] + (1 - BETA1) * grad;
            v[i] = BETA2 * v[i] + (1 - BETA2) * grad * grad;
            float mHat = m[i] / bc1;
            float vHat = v[i] / bc2;
            p[i] -= lr * mHat / ((float) Math.sqrt(vHat) + EPS);
        }
    }

    /** Cross-entropy and per-head accuracy on held-out frames — no weights change. */
    public BatchResult evaluate(float[][] xs, int[][] labels) {
        if (xs.length == 0) return new BatchResult(0, 0);
        Scratch s = scratch();
        double loss = 0;
        int correct = 0, counted = 0;
        for (int i = 0; i < xs.length; i++) {
            float[] logits = forward(xs[i], s);
            for (int h = 0; h < PvtAction.HEADS; h++) {
                int off = PvtAction.HEAD_OFFSETS[h];
                int size = PvtAction.HEAD_SIZES[h];
                float[] p = softmax(logits, off, size);
                int label = Math.max(0, Math.min(size - 1, labels[i][h]));
                loss += -Math.log(Math.max(1e-9f, p[label]));
                int best = 0;
                for (int c = 1; c < size; c++) if (p[c] > p[best]) best = c;
                if (best == label) correct++;
                counted++;
            }
        }
        return new BatchResult(loss / xs.length, counted == 0 ? 0 : correct / (double) counted);
    }

    // ── persistence ─────────────────────────────────────────────────────────────

    public void write(DataOutputStream out) throws IOException {
        out.writeInt(FILE_MAGIC);
        out.writeInt(inputs);
        out.writeInt(hidden1);
        out.writeInt(hidden2);
        out.writeInt(PvtAction.LOGITS);
        out.writeInt(step);
        out.writeLong(framesTrained);
        for (float[] a : new float[][]{w1, b1, w2, b2, w3, b3}) {
            for (float v : a) out.writeFloat(v);
        }
    }

    /**
     * Reads a network back. Returns null when the file is from a different layout —
     * an action space or observation encoding that changed makes old weights meaningless,
     * and quietly loading them would produce a bot that twitches for no visible reason.
     */
    public static PvtNet read(DataInputStream in) throws IOException {
        if (in.readInt() != FILE_MAGIC) return null;
        int inputs = in.readInt();
        int h1 = in.readInt();
        int h2 = in.readInt();
        int outputs = in.readInt();
        if (outputs != PvtAction.LOGITS) return null;
        if (inputs <= 0 || h1 <= 0 || h2 <= 0 || inputs > 100000 || h1 > 8192 || h2 > 8192) return null;
        PvtNet net = new PvtNet(inputs, h1, h2);
        net.step = in.readInt();
        net.framesTrained = in.readLong();
        for (float[] a : new float[][]{net.w1, net.b1, net.w2, net.b2, net.w3, net.b3}) {
            for (int i = 0; i < a.length; i++) a[i] = in.readFloat();
        }
        return net;
    }
}
