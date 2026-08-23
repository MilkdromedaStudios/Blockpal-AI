package com.milkdromeda.blockpal.combat;

import com.milkdromeda.blockpal.config.ModConfig;

import java.util.Locale;

/** How well a companion fights — the tuning behind the {@code combatSkill} setting. */
public enum CombatSkill {

    /** Walk at it and swing. What the mod did before, kept so nothing regresses. */
    BASIC("basic", "Basic", false, false, false, 0.30, 3.0),

    /** Keeps its distance, circles, raises a shield, backs off when hurt. */
    SKILLED("skilled", "Skilled", true, true, false, 0.35, 3.0),

    /** Adds crit timing, bow work at range and combat potions. */
    EXPERT("expert", "Expert", true, true, true, 0.40, 3.2);

    private final String id;
    private final String label;
    private final boolean strafes;
    private final boolean blocks;
    private final boolean crits;
    private final double retreatBelow;
    private final double preferredRange;

    CombatSkill(String id, String label, boolean strafes, boolean blocks, boolean crits,
                double retreatBelow, double preferredRange) {
        this.id = id;
        this.label = label;
        this.strafes = strafes;
        this.blocks = blocks;
        this.crits = crits;
        this.retreatBelow = retreatBelow;
        this.preferredRange = preferredRange;
    }

    public String id() { return id; }
    public String label() { return label; }

    /** Circle the target rather than standing in front of it. */
    public boolean strafes() { return strafes; }

    /** Raise a shield between swings. */
    public boolean blocks() { return blocks; }

    /** Jump before striking, so the hit lands as a critical, and use a bow at range. */
    public boolean crits() { return crits; }

    /** Health fraction below which it disengages. */
    public double retreatBelow() { return retreatBelow; }

    /** The distance it tries to hold in a melee, in blocks. */
    public double preferredRange() { return preferredRange; }

    public static CombatSkill byId(String id) {
        if (id == null) return null;
        String needle = id.trim().toLowerCase(Locale.ROOT);
        for (CombatSkill s : values()) {
            if (s.id.equals(needle)) return s;
        }
        return null;
    }

    /** The skill level in force, from the config (never null). */
    public static CombatSkill current() {
        CombatSkill s = byId(ModConfig.get().combatSkill);
        return s == null ? SKILLED : s;
    }

    public static String idList() {
        StringBuilder sb = new StringBuilder();
        for (CombatSkill s : values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s.id);
        }
        return sb.toString();
    }
}
