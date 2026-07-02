package com.milkdromeda.blockpal.minigame;

/**
 * The mini-game modes you can start on a {@link com.milkdromeda.blockpal.party.Party}.
 * Each mode is a combination of a few reusable mechanics (a tether, a shared health
 * pool, a one-block arena), so "Fusion" is literally a fusion of the others.
 */
public enum GameMode {
    CHAINED("chained", "Chained",
            "Everyone — players and their bots — is tethered together; stray too far and you're yanked back.",
            true, false, false),
    SAME_HEALTH("samehealth", "Same Health",
            "One shared health pool: hurt one, hurt all, and if anyone dies, everyone dies.",
            false, true, false),
    ONE_BLOCK("oneblock", "One Block",
            "Everyone starts on a single, endlessly-regenerating block, skyblock-style — build a world from nothing.",
            false, false, true),
    FUSION("fusion", "Fusion",
            "Chained AND Same Health at once — the ultimate co-op (or co-death).",
            true, true, false);

    public final String id;
    public final String display;
    public final String desc;
    /** Participants are tethered together. */
    public final boolean chained;
    /** Participants share one health pool (and one fate). */
    public final boolean sharedHealth;
    /** Participants start on a regenerating single block. */
    public final boolean oneBlock;

    GameMode(String id, String display, String desc, boolean chained, boolean sharedHealth, boolean oneBlock) {
        this.id = id;
        this.display = display;
        this.desc = desc;
        this.chained = chained;
        this.sharedHealth = sharedHealth;
        this.oneBlock = oneBlock;
    }

    public static GameMode byId(String id) {
        if (id == null) return null;
        for (GameMode m : values()) if (m.id.equalsIgnoreCase(id.trim())) return m;
        return null;
    }
}
