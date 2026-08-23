package com.milkdromeda.blockpal.pvt;

/**
 * <b>What a player pressed in one tick</b> — the thing PVT learns to predict.
 *
 * <p>The action space is <i>factored</i> into nine independent heads rather than one
 * giant list of every button combination. That is the same shape OpenAI's VPT used, and
 * for the same reason: a flat space would need 3×3×2×2×2×2×2×11×7 ≈ 44,000 classes, most
 * of which never occur, while nine small heads share their evidence — every frame where
 * somebody walked forward teaches the forward head, whatever their mouse was doing.
 *
 * <p>Movement is stored as a discrete key state, not a velocity, so a demonstration
 * recorded from a <i>player</i> replays as key presses on a <i>bot</i> even though the
 * two have different speeds, step heights and inertia.
 */
public final class PvtAction {

    // ── the heads, in the order they appear in the network's output ──────────────
    public static final int HEAD_FORWARD = 0;
    public static final int HEAD_STRAFE  = 1;
    public static final int HEAD_JUMP    = 2;
    public static final int HEAD_SNEAK   = 3;
    public static final int HEAD_SPRINT  = 4;
    public static final int HEAD_ATTACK  = 5;
    public static final int HEAD_USE     = 6;
    public static final int HEAD_YAW     = 7;
    public static final int HEAD_PITCH   = 8;

    /** How many classes each head chooses between. */
    public static final int[] HEAD_SIZES = {3, 3, 2, 2, 2, 2, 2, 11, 7};

    public static final int HEADS = HEAD_SIZES.length;

    /** Total logits the policy emits — the sum of every head. */
    public static final int LOGITS = sum(HEAD_SIZES);

    /** Where each head's logits start inside that flat output vector. */
    public static final int[] HEAD_OFFSETS = offsets(HEAD_SIZES);

    public static final String[] HEAD_NAMES = {
            "forward", "strafe", "jump", "sneak", "sprint", "attack", "use", "yaw", "pitch"
    };

    /**
     * Degrees of yaw turn each bin means. Spacing is deliberately non-linear: most ticks
     * of real play are a nudge of a degree or two, and the wide bins only matter when
     * somebody spins to face something behind them.
     */
    public static final float[] YAW_BINS = {-45, -20, -10, -5, -2, 0, 2, 5, 10, 20, 45};

    /** Pitch is used over a much smaller range than yaw, so it needs fewer bins. */
    public static final float[] PITCH_BINS = {-20, -8, -3, 0, 3, 8, 20};

    /** The class chosen for each head. */
    private final int[] heads;

    public PvtAction(int[] heads) {
        if (heads == null || heads.length != HEADS) {
            throw new IllegalArgumentException("an action needs exactly " + HEADS + " heads");
        }
        this.heads = new int[HEADS];
        for (int h = 0; h < HEADS; h++) {
            this.heads[h] = Math.max(0, Math.min(HEAD_SIZES[h] - 1, heads[h]));
        }
    }

    /** The do-nothing action: no keys, no turn. */
    public static PvtAction idle() {
        return new PvtAction(new int[]{1, 1, 0, 0, 0, 0, 0, binOf(YAW_BINS, 0), binOf(PITCH_BINS, 0)});
    }

    /**
     * Builds an action from what an agent was observed doing this tick.
     *
     * @param forward  local forward drive, roughly -1..1
     * @param strafe   local left drive, roughly -1..1
     * @param yawDelta degrees the view turned this tick
     */
    public static PvtAction of(double forward, double strafe, boolean jump, boolean sneak,
                               boolean sprint, boolean attack, boolean use,
                               double yawDelta, double pitchDelta) {
        int[] h = new int[HEADS];
        h[HEAD_FORWARD] = forward > MOVE_THRESHOLD ? 2 : forward < -MOVE_THRESHOLD ? 0 : 1;
        h[HEAD_STRAFE]  = strafe  > MOVE_THRESHOLD ? 2 : strafe  < -MOVE_THRESHOLD ? 0 : 1;
        h[HEAD_JUMP]    = jump   ? 1 : 0;
        h[HEAD_SNEAK]   = sneak  ? 1 : 0;
        h[HEAD_SPRINT]  = sprint ? 1 : 0;
        h[HEAD_ATTACK]  = attack ? 1 : 0;
        h[HEAD_USE]     = use    ? 1 : 0;
        h[HEAD_YAW]     = binOf(YAW_BINS, yawDelta);
        h[HEAD_PITCH]   = binOf(PITCH_BINS, pitchDelta);
        return new PvtAction(h);
    }

    /** Below this, a drift in velocity is noise rather than somebody holding a key. */
    private static final double MOVE_THRESHOLD = 0.045;

    public int head(int index) { return heads[index]; }

    public int[] heads() { return heads.clone(); }

    /** -1 (backwards), 0 or 1 (forwards). */
    public float forward() { return heads[HEAD_FORWARD] - 1f; }

    /** -1 (right), 0 or 1 (left). */
    public float strafe() { return heads[HEAD_STRAFE] - 1f; }

    public boolean jump()   { return heads[HEAD_JUMP] == 1; }
    public boolean sneak()  { return heads[HEAD_SNEAK] == 1; }
    public boolean sprint() { return heads[HEAD_SPRINT] == 1; }
    public boolean attack() { return heads[HEAD_ATTACK] == 1; }
    public boolean use()    { return heads[HEAD_USE] == 1; }

    public float yawDelta()   { return YAW_BINS[heads[HEAD_YAW]]; }
    public float pitchDelta() { return PITCH_BINS[heads[HEAD_PITCH]]; }

    /** True when this action presses nothing and turns nowhere. */
    public boolean isIdle() {
        return heads[HEAD_FORWARD] == 1 && heads[HEAD_STRAFE] == 1
                && !jump() && !attack() && !use()
                && yawDelta() == 0f && pitchDelta() == 0f;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (forward() > 0) sb.append("forward ");
        if (forward() < 0) sb.append("back ");
        if (strafe() > 0) sb.append("left ");
        if (strafe() < 0) sb.append("right ");
        if (jump()) sb.append("jump ");
        if (sneak()) sb.append("sneak ");
        if (sprint()) sb.append("sprint ");
        if (attack()) sb.append("attack ");
        if (use()) sb.append("use ");
        if (yawDelta() != 0) sb.append("turn ").append((int) yawDelta()).append("° ");
        if (pitchDelta() != 0) sb.append("look ").append((int) pitchDelta()).append("° ");
        return sb.length() == 0 ? "(still)" : sb.toString().trim();
    }

    /** The bin whose value is closest to {@code value}. */
    public static int binOf(float[] bins, double value) {
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < bins.length; i++) {
            double d = Math.abs(bins[i] - value);
            if (d < bestDist) { bestDist = d; best = i; }
        }
        return best;
    }

    private static int sum(int[] a) {
        int n = 0;
        for (int v : a) n += v;
        return n;
    }

    private static int[] offsets(int[] sizes) {
        int[] off = new int[sizes.length];
        int at = 0;
        for (int i = 0; i < sizes.length; i++) {
            off[i] = at;
            at += sizes[i];
        }
        return off;
    }
}
