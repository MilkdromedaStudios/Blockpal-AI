package com.milkdromeda.blockpal.minigame.village;

import com.milkdromeda.blockpal.ai.HuggingFaceClient;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One running "Growth" game: an AI-run village the player watches (and can join a role in).
 * The village lives or dies on its own daily simulation — food, houses, knowledge, morale,
 * defence — driven by its AI villagers, each with its own role, personality and (small)
 * language model. Turns (village days) run at 2× speed.
 *
 * <p><b>Win / lose (per the design):</b> if the village <i>dies out</i> (population hits 0)
 * the <b>player wins</b> — they outlasted it. If it <i>grows as big as ever</i>
 * (peak population reaches the target) the player is offered the chance to
 * <b>surrender</b> and concede the village its triumph.
 */
public final class VillageGame {

    /** Per-villager metadata: its role, its assigned model + endpoint, and its name. */
    public record VillagerInfo(VillageRole role, HuggingFaceClient.ApiAuth auth, String name) {}

    public final UUID founder;
    public final String founderName;
    public final ResourceKey<Level> dimension;
    public final BlockPos center;

    /** Live villager entities: entity UUID → info. Pruned as villagers die/are removed. */
    public final Map<UUID, VillagerInfo> villagers = new LinkedHashMap<>();
    /** Players who joined a role (they contribute to the daily sim while online & nearby). */
    public final Set<UUID> participants = new HashSet<>();
    public final Map<UUID, VillageRole> playerRoles = new HashMap<>();

    // ── simulation state ────────────────────────────────────────────────────────────
    public int peakPopulation;
    public int day;
    public long simTicks;          // advances 2 per server tick → the "2× faster" clock
    public double food = 24;
    public double morale = 0.8;    // 0..1
    public int houses = 3;
    public int knowledge = 0;      // accumulates from teachers/scholars → efficiency
    public final int targetPopulation;
    public boolean ended;
    public boolean surrenderOffered;

    // ── round-robin cursors ─────────────────────────────────────────────────────────
    public int thinkerCursor;      // which villager narrates next
    public int modelCursor;        // which model the next spawned villager gets
    public int roleCursor;         // which role the next spawned villager gets
    public int nameCursor;         // which name the next spawned villager gets
    public int houseCursor;        // where the next hut is placed (ring around center)

    public VillageGame(UUID founder, String founderName, ResourceKey<Level> dimension,
                       BlockPos center, int targetPopulation) {
        this.founder = founder;
        this.founderName = founderName;
        this.dimension = dimension;
        this.center = center;
        this.targetPopulation = Math.max(2, targetPopulation);
    }

    /** Living villagers currently registered (entities are pruned each tick if gone). */
    public int population() {
        return villagers.size();
    }
}
