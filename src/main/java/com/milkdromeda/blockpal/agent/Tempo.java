package com.milkdromeda.blockpal.agent;

import com.milkdromeda.blockpal.config.ModConfig;

import java.util.Locale;

/**
 * <b>How fast the companion is allowed to act.</b>
 *
 * <p>Every artificial pause in the mod used to be its own hard-coded number scattered
 * across a goal, a brain and a driver — a step delay here, a "reaction" jitter there, a
 * five-second think interval somewhere else. Stacked up they made the bot feel sluggish
 * even when nothing was actually busy: it would finish walking, then stand still for two
 * seconds because a cooldown started when the *previous* round began.
 *
 * <p>They all resolve through here now, from one setting:
 *
 * <ul>
 *   <li>{@link #INSTANT} — no politeness at all. Turns on a dime, thinks again the moment
 *       it finishes, spends a big instruction budget per tick. Best for building and for
 *       watching it work.</li>
 *   <li>{@link #FAST} — <b>the default.</b> Quick and responsive, but still turns its head
 *       at a believable rate and pauses a beat between steps.</li>
 *   <li>{@link #HUMAN} — the old 3.21-3.25 feel: visible reaction times, slow head turns,
 *       a think every five seconds.</li>
 * </ul>
 *
 * <p>Nothing here makes the bot <i>cheat</i> — mining still costs real block-hardness
 * time at every speed except {@link #INSTANT}, where it is doubled and says so. Speed
 * only removes waiting that the mod invented.
 */
public enum Tempo {

    /** Everything as fast as the tick loop allows. */
    INSTANT("instant", "Instant", 0, 180f, 20, 200L, 4000, 2.0f, 2, 3),
    /** Snappy but still believable — the shipping default. */
    FAST("fast", "Fast", 2, 55f, 40, 400L, 1500, 1.0f, 5, 8),
    /** Deliberate, human-paced reactions (what the mod used to do). */
    HUMAN("human", "Human", 8, 22f, 100, 900L, 400, 1.0f, 10, 14);

    private final String id;
    private final String label;
    private final int stepDelay;
    private final float turnRate;
    private final int thinkInterval;
    private final long visionIntervalMs;
    private final int opsPerTick;
    private final float miningMultiplier;
    private final int useCooldown;
    private final int swingCooldown;

    Tempo(String id, String label, int stepDelay, float turnRate, int thinkInterval,
          long visionIntervalMs, int opsPerTick, float miningMultiplier,
          int useCooldown, int swingCooldown) {
        this.id = id;
        this.label = label;
        this.stepDelay = stepDelay;
        this.turnRate = turnRate;
        this.thinkInterval = thinkInterval;
        this.visionIntervalMs = visionIntervalMs;
        this.opsPerTick = opsPerTick;
        this.miningMultiplier = miningMultiplier;
        this.useCooldown = useCooldown;
        this.swingCooldown = swingCooldown;
    }

    public String id() { return id; }
    public String label() { return label; }

    /** Ticks to pause between two steps of a plan (0 = none). */
    public int stepDelay() { return stepDelay; }

    /** Degrees the head may turn per tick. */
    public float turnRate() { return turnRate; }

    /** Ticks between two AI thinking rounds. */
    public int thinkInterval() { return thinkInterval; }

    /** Milliseconds between two renders of the bot's field of view. */
    public long visionIntervalMs() { return visionIntervalMs; }

    /** Script instructions executed per tick before yielding back to the game. */
    public int opsPerTick() { return opsPerTick; }

    /** Multiplier on block-breaking progress — 1.0 everywhere except {@link #INSTANT}. */
    public float miningMultiplier() { return miningMultiplier; }

    /** Ticks between two right-clicks while the use button is held. */
    public int useCooldown() { return useCooldown; }

    /** Ticks between two arm swings while mining. */
    public int swingCooldown() { return swingCooldown; }

    /** True when the bot should still add small randomised reaction pauses. */
    public boolean humanised() { return this == HUMAN; }

    public static Tempo byId(String id) {
        if (id == null) return null;
        String needle = id.trim().toLowerCase(Locale.ROOT);
        for (Tempo t : values()) {
            if (t.id.equals(needle)) return t;
        }
        return null;
    }

    /** The tempo in force right now, from the config (never null). */
    public static Tempo current() {
        Tempo t = byId(ModConfig.get().reactionSpeed);
        return t == null ? FAST : t;
    }

    /**
     * Ticks to wait between plan steps, honouring the legacy {@code actionTickDelay}
     * developer setting when it has been raised above what the tempo asks for.
     *
     * <p>The developer field is a <i>floor</i>, not a target: someone who deliberately
     * set a long delay to nurse a slow server keeps it, while everyone else gets the
     * tempo's much shorter pause instead of the old 8-tick default.
     */
    public static int stepDelayTicks() {
        ModConfig cfg = ModConfig.get();
        Tempo t = current();
        // Only respect a hand-raised developer delay; the shipped default no longer wins.
        int dev = cfg.actionTickDelay > DEFAULT_ACTION_TICK_DELAY ? cfg.actionTickDelay : 0;
        return Math.max(t.stepDelay(), dev);
    }

    /** What {@code actionTickDelay} ships as — anything above this was set on purpose. */
    public static final int DEFAULT_ACTION_TICK_DELAY = 2;

    /**
     * A small randomised "reaction" pause, in ticks, or 0 when the tempo doesn't want one.
     * Used where the bot would otherwise snatch an item the instant it arrives.
     */
    public static int reactionJitter(java.util.Random random, int max) {
        Tempo t = current();
        if (!t.humanised() && !ModConfig.get().humanizeActions) return 0;
        if (t == INSTANT) return 0;
        int cap = t == FAST ? Math.max(1, max / 3) : max;
        return random.nextInt(cap + 1);
    }

    /** A human-readable list for command feedback: {@code instant, fast, human}. */
    public static String idList() {
        StringBuilder sb = new StringBuilder();
        for (Tempo t : values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(t.id);
        }
        return sb.toString();
    }
}
