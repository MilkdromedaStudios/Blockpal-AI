package com.milkdromeda.blockpal.minigame.village;

import java.util.Locale;
import java.util.Random;

/**
 * A job an AI villager (or a participating player) can hold in the {@link VillageGame}.
 * Each role drives a different lever of the village's daily simulation — food, houses,
 * knowledge, morale, defence — and carries a small pool of in-character "thought" lines
 * used to narrate what the villager is doing when no language model is reachable (with a
 * key/Ollama, the villager's own small model writes the line instead, so different
 * villagers "think differently").
 */
public enum VillageRole {
    BUILDER("builder", "Builder",
            "raises houses so the village can hold more people",
            new String[]{
                    "laying foundations — every family needs a roof before winter.",
                    "measuring out the next house; we grow by building, not waiting.",
                    "reinforcing these walls so they'll outlast a raid."
            }),
    FARMER("farmer", "Farmer",
            "grows food so nobody starves",
            new String[]{
                    "tilling a new row — a full granary is a growing village.",
                    "rotating the crops so the soil never tires out.",
                    "harvesting early; better a stocked barn than a spoiled field."
            }),
    TEACHER("teacher", "Teacher",
            "passes on knowledge so the village works smarter",
            new String[]{
                    "showing the young ones how to read the seasons.",
                    "sharing what I know — knowledge kept to yourself dies with you.",
                    "teaching the trades so no skill is ever lost."
            }),
    TRADER("trader", "Trader",
            "trades to keep spirits and supplies flowing",
            new String[]{
                    "swapping surplus grain for planks — everyone comes out ahead.",
                    "balancing the books; a fair trade lifts the whole village.",
                    "bartering for tools we can't make ourselves yet."
            }),
    GUARD("guard", "Guard",
            "defends the village through the night",
            new String[]{
                    "walking the perimeter — nothing gets past me tonight.",
                    "keeping watch so the builders can sleep easy.",
                    "sharpening my blade; the dark won't take anyone here."
            }),
    SCHOLAR("scholar", "Scholar",
            "studies and plans the village's next leap",
            new String[]{
                    "charting where the next quarter should rise.",
                    "puzzling out a smarter way to feed everyone.",
                    "reasoning through the numbers — growth has to be planned."
            });

    public final String id;
    public final String display;
    /** A short "does X" clause for day summaries. */
    public final String summary;
    private final String[] thoughts;

    VillageRole(String id, String display, String summary, String[] thoughts) {
        this.id = id;
        this.display = display;
        this.summary = summary;
        this.thoughts = thoughts;
    }

    /** A random in-character line for this role (the no-API fallback narration). */
    public String thought(Random rng) {
        return thoughts[rng.nextInt(thoughts.length)];
    }

    public static VillageRole byId(String id) {
        if (id == null) return null;
        String want = id.trim().toLowerCase(Locale.ROOT);
        for (VillageRole r : values()) if (r.id.equals(want)) return r;
        return null;
    }

    /** The role assigned to the {@code i}-th villager, cycling through the roles. */
    public static VillageRole forIndex(int i) {
        VillageRole[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
