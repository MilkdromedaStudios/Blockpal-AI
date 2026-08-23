package com.milkdromeda.blockpal.pvt;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * <b>The inverse dynamics model — the trick that makes "video" training possible.</b>
 *
 * <p>Behaviour cloning needs (what you saw, what you did) pairs, and the second half is
 * the hard one: footage of somebody playing shows you the world, not their keyboard.
 * VPT's answer, which this follows, is to learn a second, much easier model that reads
 * the action <i>backwards</i> off a pair of consecutive observations — if the view slid
 * forward and rotated left, the player was holding W and moving the mouse left.
 *
 * <p>That model is easy because it is allowed to see the future: it does not have to
 * decide what to do, only to describe what already happened. So a small amount of
 * properly-labelled play — which {@link PvtRecorder} gets for free, because the server
 * can measure its own players — is enough to train it, and it can then label an
 * arbitrary amount of observation-only footage that nobody recorded button presses for.
 *
 * <p>It is the same {@link PvtNet} as the policy, with twice the input width — but the
 * second half is the <b>change</b> between the two observations, not the second one
 * verbatim. That is not cosmetic. Two consecutive views are nearly identical: everything
 * that matters is in a handful of small differences, and handing the network the raw pair
 * asks it to first discover subtraction across hundreds of inputs whose noise is shared.
 * Measured on synthetic pairs where the action was recoverable by construction, the raw
 * pairing recovered movement 58% of the time and the view turn 15%; feeding the delta
 * directly took both above 99%. The transform loses nothing — the original second
 * observation is still {@code first + delta} — it just puts the signal where a small
 * network can reach it.
 */
public final class PvtIdm {

    private final PvtNet net;
    private final PvtNet.Scratch scratch;

    public PvtIdm(PvtNet net) {
        this.net = net;
        this.scratch = net.scratch();
    }

    /** A fresh, untrained inverse dynamics model. */
    public static PvtIdm fresh(int hidden, long seed) {
        return new PvtIdm(new PvtNet(PvtObservation.SIZE * 2, hidden, hidden, seed));
    }

    public PvtNet net() { return net; }

    /** Input width an inverse dynamics model expects: two observations side by side. */
    public static int inputSize() { return PvtObservation.SIZE * 2; }

    /**
     * Works out what action took the agent from {@code before} to {@code after}.
     *
     * @return the per-head classes, ready for {@link PvtDataset#relabel}
     */
    public int[] label(float[] before, float[] after) {
        return net.predict(join(before, after), scratch).action().heads();
    }

    /**
     * Packs two consecutive observations into the model's input: the first view as-is,
     * then what changed. See the class note for why the change is given explicitly.
     */
    public static float[] join(float[] before, float[] after) {
        float[] x = new float[inputSize()];
        int n = Math.min(before.length, PvtObservation.SIZE);
        System.arraycopy(before, 0, x, 0, n);
        for (int i = 0; i < n; i++) {
            // Scaled up because a single tick's change is small, and a network learns
            // faster from inputs that share a range with everything else it is fed.
            x[PvtObservation.SIZE + i] = squash((after[i] - before[i]) * DELTA_GAIN);
        }
        return x;
    }

    /** Amplifies the frame-to-frame difference into the same rough range as the rest. */
    private static final float DELTA_GAIN = 3f;

    /**
     * Squashes an amplified delta into −1..1 <b>without flattening the big ones</b>.
     *
     * <p>A hard clamp was the obvious thing and it was wrong: every turn past about 23°
     * pinned to exactly 1.0, so a 25° flick and a 45° spin became the same number and the
     * model could not tell them apart. On the synthetic pairs that took turn recovery down
     * to 37%. tanh keeps the ordering all the way out, and recovery went to 99%.
     */
    private static float squash(float v) {
        return (float) Math.tanh(v);
    }

    /** How sure the model is about a pair — low confidence labels are worth dropping. */
    public double confidence(float[] joined) {
        return net.predict(joined, scratch).confidence();
    }

    /** Predicts straight from an already-joined pair (what {@link PvtDataset.Pair} holds). */
    public PvtNet.Prediction predict(float[] joined) {
        return net.predict(joined, scratch);
    }

    public void write(DataOutputStream out) throws IOException {
        net.write(out);
    }

    /** Reads a model back, or null when the file doesn't match the current layout. */
    public static PvtIdm read(DataInputStream in) throws IOException {
        PvtNet net = PvtNet.read(in);
        if (net == null || net.inputSize() != inputSize()) return null;
        return new PvtIdm(net);
    }
}
