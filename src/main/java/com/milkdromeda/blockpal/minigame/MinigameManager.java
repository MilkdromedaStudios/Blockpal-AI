package com.milkdromeda.blockpal.minigame;

import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import com.milkdromeda.blockpal.party.Party;
import com.milkdromeda.blockpal.party.PartyManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs the mini-games. A game is started on a {@link Party} and its participants are
 * the party's online players plus each player's owned bot(s) — so the bot really
 * plays with you. Everything is server-side (tick + death + block-break hooks), so
 * Java and Bedrock players share the same games.
 *
 * <p>Mechanics per {@link GameMode}: <b>Same Health</b> keeps every participant at the
 * group's lowest health and, on any death, ends the game with everyone; <b>Chained</b>
 * tethers participants to the leader (a tug back when they stray, a teleport if they
 * get very far); <b>One Block</b> drops everyone on a single regenerating block in the
 * sky; <b>Fusion</b> runs Chained and Same Health together.
 */
public final class MinigameManager {

    private MinigameManager() {}

    private static final double CHAIN_MAX = 12.0;   // start tugging past this many blocks
    private static final double CHAIN_HARD = 40.0;  // teleport back past this
    private static final double CHAIN_PULL = 0.35;  // tug strength

    /** Blocks the One Block cycles through as it's mined. */
    private static final Block[] ONE_BLOCK_CYCLE = {
            Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.OAK_LOG, Blocks.STONE,
            Blocks.COBBLESTONE, Blocks.IRON_ORE, Blocks.OAK_LEAVES, Blocks.SAND
    };

    private static final Set<GameSession> SESSIONS = new HashSet<>();
    private static final Map<UUID, GameSession> BY_PLAYER = new HashMap<>();
    private static int arenaCounter = 0;

    /** Wires the tick / death / block-break hooks. Called once from the mod initializer. */
    public static void registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register(MinigameManager::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> onDeath(entity));
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
            if (level instanceof ServerLevel sl) onBlockBreak(sl, pos);
        });
    }

    public static GameSession sessionOf(ServerPlayer player) {
        return BY_PLAYER.get(player.getUUID());
    }

    // ── start / stop ──────────────────────────────────────────────────────────────

    /** Starts {@code mode} for the leader's party (or just the leader if partyless). */
    public static void start(ServerPlayer leader, GameMode mode) {
        MinecraftServer server = leader.level().getServer();
        if (server == null) return;

        Party party = PartyManager.partyOf(leader);
        if (party != null && !party.isLeader(leader.getUUID())) {
            msg(leader, "§cOnly the party leader can start a game.");
            return;
        }

        List<ServerPlayer> players = new ArrayList<>();
        if (party == null) {
            players.add(leader);
        } else {
            for (UUID u : party.memberUuids()) {
                ServerPlayer p = server.getPlayerList().getPlayer(u);
                if (p != null) players.add(p);
            }
        }
        for (ServerPlayer p : players) {
            if (BY_PLAYER.containsKey(p.getUUID())) {
                msg(leader, "§c" + p.getName().getString() + " is already in a game — stop it first (/game stop).");
                return;
            }
        }

        GameSession session = new GameSession(mode, leader.getUUID(), leader.level().dimension());
        for (ServerPlayer p : players) {
            session.players.add(p.getUUID());
            BY_PLAYER.put(p.getUUID(), session);
        }
        SESSIONS.add(session);

        if (mode.oneBlock && leader.level() instanceof ServerLevel level) {
            setupOneBlock(level, session, players);
        }

        broadcast(server, session, "§6[Game] §f" + mode.display + " §astarted! §7" + mode.desc);
        if (mode.chained) broadcast(server, session, "§7You're tethered to " + leader.getName().getString() + " — stay close.");
        if (mode.sharedHealth) broadcast(server, session, "§7You share one health pool. Look out for each other.");
    }

    /** {@code /game stop}: the leader ends the whole game; anyone else just leaves it. */
    public static void stop(ServerPlayer player) {
        GameSession s = BY_PLAYER.get(player.getUUID());
        if (s == null) { msg(player, "§7You're not in a game."); return; }
        MinecraftServer server = player.level().getServer();
        if (s.leader.equals(player.getUUID())) {
            endSession(server, s, "§6[Game] §7" + player.getName().getString() + " ended the game.");
        } else {
            BY_PLAYER.remove(player.getUUID());
            s.players.remove(player.getUUID());
            msg(player, "§7You left the game.");
            broadcast(server, s, "§7" + player.getName().getString() + " left the game.");
        }
    }

    public static void handleDisconnect(ServerPlayer player) {
        GameSession s = BY_PLAYER.remove(player.getUUID());
        if (s != null) s.players.remove(player.getUUID());
    }

    // ── per-tick ───────────────────────────────────────────────────────────────────

    private static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        for (GameSession s : new ArrayList<>(SESSIONS)) {
            if (s.ended) continue;
            ServerLevel level = server.getLevel(s.dimension);
            if (level == null) continue;
            List<LivingEntity> parts = participants(server, s);
            if (parts.isEmpty()) { endSession(server, s, null); continue; }
            if (s.mode.sharedHealth) syncHealth(parts);
            if (s.mode.chained) applyChain(parts, s);
            if (s.mode.oneBlock && s.oneBlockPos != null) keepAboveVoid(parts, s);
        }
    }

    /** Clamps every participant to the group's lowest current health. */
    private static void syncHealth(List<LivingEntity> parts) {
        float min = Float.MAX_VALUE;
        for (LivingEntity e : parts) min = Math.min(min, e.getHealth());
        if (min < 0) min = 0;
        for (LivingEntity e : parts) {
            if (e.getHealth() > min) e.setHealth(min);
        }
    }

    /** Tugs stray participants back toward the leader; teleports them if they get very far. */
    private static void applyChain(List<LivingEntity> parts, GameSession s) {
        LivingEntity anchor = null;
        for (LivingEntity e : parts) {
            if (e instanceof ServerPlayer p && p.getUUID().equals(s.leader)) { anchor = e; break; }
        }
        if (anchor == null) anchor = parts.get(0);
        for (LivingEntity e : parts) {
            if (e == anchor) continue;
            Vec3 delta = anchor.position().subtract(e.position());
            double dist = delta.length();
            if (dist > CHAIN_HARD) {
                e.teleportTo(anchor.getX(), anchor.getY(), anchor.getZ());
            } else if (dist > CHAIN_MAX) {
                Vec3 pull = delta.normalize().scale(CHAIN_PULL);
                e.setDeltaMovement(e.getDeltaMovement().add(pull.x, Math.max(0, pull.y) * 0.5, pull.z));
                e.hurtMarked = true;   // sync the new velocity to the client
            }
        }
    }

    /** One Block safety net: if you fall off, drop you back on the block instead of the void. */
    private static void keepAboveVoid(List<LivingEntity> parts, GameSession s) {
        for (LivingEntity e : parts) {
            if (e.getY() < s.oneBlockPos.getY() - 40) {
                e.setDeltaMovement(Vec3.ZERO);
                e.teleportTo(s.oneBlockPos.getX() + 0.5, s.oneBlockPos.getY() + 1, s.oneBlockPos.getZ() + 0.5);
            }
        }
    }

    // ── events ───────────────────────────────────────────────────────────────────

    /** In a shared-health game, one death ends it for everyone. */
    private static void onDeath(LivingEntity dead) {
        GameSession s = sessionOfEntity(dead);
        if (s == null || s.ended || !s.mode.sharedHealth) return;
        MinecraftServer server = dead.level().getServer();
        if (server == null) return;
        s.ended = true;   // set before the killing blow so the re-entrant deaths are ignored
        for (LivingEntity e : participants(server, s)) {
            if (e != dead && e.isAlive()) e.setHealth(0.0f);
        }
        broadcast(server, s, "§6[Game] §c" + nameOf(dead) + " fell — and so did everyone. Same fate, same health!");
        cleanup(s);
    }

    /** One Block regenerates the moment it's mined (the drop already spawned for you). */
    private static void onBlockBreak(ServerLevel level, BlockPos pos) {
        for (GameSession s : SESSIONS) {
            if (s.ended || !s.mode.oneBlock || s.oneBlockPos == null) continue;
            if (s.dimension.equals(level.dimension()) && s.oneBlockPos.equals(pos)) {
                s.oneBlockBreaks++;
                Block next = ONE_BLOCK_CYCLE[s.oneBlockBreaks % ONE_BLOCK_CYCLE.length];
                level.setBlockAndUpdate(pos, next.defaultBlockState());
                return;
            }
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private static void setupOneBlock(ServerLevel level, GameSession session, List<ServerPlayer> players) {
        // A simple isolated sky platform per game, near the leader but spaced out so
        // concurrent games don't overlap.
        int n = arenaCounter++;
        BlockPos base = players.get(0).blockPosition();
        BlockPos pos = new BlockPos(base.getX() + n * 64, 240, base.getZ());
        level.setBlockAndUpdate(pos, ONE_BLOCK_CYCLE[0].defaultBlockState());
        session.oneBlockPos = pos;
        for (ServerPlayer p : players) {
            p.setDeltaMovement(Vec3.ZERO);
            p.teleportTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        }
    }

    private static List<LivingEntity> participants(MinecraftServer server, GameSession s) {
        List<LivingEntity> out = new ArrayList<>();
        ServerLevel level = server.getLevel(s.dimension);
        if (level == null) return out;
        for (UUID u : s.players) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null && p.isAlive() && p.level() == level) out.add(p);
        }
        for (AiAssistantEntity bot : AiAssistantEntity.all(server)) {
            if (bot.isAlive() && bot.level() == level
                    && bot.getOwnerUuid() != null && s.players.contains(bot.getOwnerUuid())) {
                out.add(bot);
            }
        }
        return out;
    }

    private static GameSession sessionOfEntity(LivingEntity e) {
        if (e instanceof ServerPlayer p) return BY_PLAYER.get(p.getUUID());
        if (e instanceof AiAssistantEntity bot && bot.getOwnerUuid() != null) return BY_PLAYER.get(bot.getOwnerUuid());
        return null;
    }

    private static void endSession(MinecraftServer server, GameSession s, String message) {
        if (s.ended) { cleanup(s); return; }
        s.ended = true;
        if (message != null && server != null) broadcast(server, s, message);
        cleanup(s);
    }

    private static void cleanup(GameSession s) {
        for (UUID u : s.players) BY_PLAYER.remove(u, s);
        SESSIONS.remove(s);
    }

    private static void broadcast(MinecraftServer server, GameSession s, String message) {
        if (server == null) return;
        Component c = Component.literal(message);
        for (UUID u : s.players) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) p.sendSystemMessage(c);
        }
    }

    private static String nameOf(LivingEntity e) {
        return e.getName().getString();
    }

    private static void msg(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
    }
}
