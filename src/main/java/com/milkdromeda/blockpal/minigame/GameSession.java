package com.milkdromeda.blockpal.minigame;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One running mini-game: its {@link GameMode}, who's playing (party members at start),
 * the dimension it runs in, and any mode-specific state (the One Block position).
 * Sessions live in memory in {@link MinigameManager}; leaving/ending removes you.
 *
 * <p>Note: a game runs <b>in the current world</b> (One Block builds a sky platform).
 * The "each game is its own resumeable world" vision is a future enhancement — see the
 * Minigames wiki page.
 */
public final class GameSession {

    public final GameMode mode;
    public final UUID leader;
    public final Set<UUID> players = new LinkedHashSet<>();
    public final ResourceKey<Level> dimension;

    /** For ONE_BLOCK: the block that regenerates when broken. Null for other modes. */
    public BlockPos oneBlockPos;
    /** How many times the One Block has been mined (drives the block-type cycle). */
    public int oneBlockBreaks;
    public boolean ended;

    GameSession(GameMode mode, UUID leader, ResourceKey<Level> dimension) {
        this.mode = mode;
        this.leader = leader;
        this.dimension = dimension;
    }
}
