package com.milkdromeda.blockpal.possession;

import com.milkdromeda.blockpal.EmergencyState;
import com.milkdromeda.blockpal.ai.HuggingFaceClient;
import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import com.milkdromeda.blockpal.network.AiNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Runs <b>possession mode</b>: a player hands control of their own character to their
 * nearby companion, which then drives their body from typed instructions. Entirely
 * server-side (planning + a per-tick driving loop), so it works on any server running
 * Blockpal and in singleplayer, with no client mod required to be <i>controlled</i>
 * (the console GUI is a Java-client nicety; Bedrock/vanilla steer it with
 * {@code /ai possess <instruction>}).
 *
 * <p>A player can only ever possess themselves, with a companion they own — so there
 * is no cross-player control and nothing to grief. Sessions are in-memory (like the
 * party/mini-game systems); a restart or the owning bot going away ends possession.
 */
public final class PossessionManager {

    private PossessionManager() {}

    private static final Map<UUID, PossessionSession> BY_PLAYER = new HashMap<>();
    private static final HuggingFaceClient CLIENT = new HuggingFaceClient();

    /** Wires the per-tick driving loop. Called once from the mod initializer. */
    public static void registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register(PossessionManager::tick);
    }

    public static boolean isPossessed(UUID id) {
        return id != null && BY_PLAYER.containsKey(id);
    }

    // ── start / stop / instruct ─────────────────────────────────────────────────

    /** Begins possessing {@code player} with their nearest owned companion. */
    public static void start(ServerPlayer player) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.allowPossession) {
            msg(player, "§cPossession mode is turned off on this server.");
            return;
        }
        if (EmergencyState.isDisabled()) {
            msg(player, "§cBlockpal is disabled right now — try again after §f/ai resume§c.");
            return;
        }
        if (BY_PLAYER.containsKey(player.getUUID())) {
            // Already possessed — just (re)open the console.
            AiNetworking.sendPossession(player, true, true, "§7Possession console reopened.");
            return;
        }
        AiAssistantEntity bot = AiAssistantEntity.findOwnedFor(player, 256);
        if (bot == null) {
            msg(player, "§cYou need one of your own companions nearby to possess you. "
                    + "Summon one with §f/ai summon§c.");
            return;
        }
        if (!bot.hasUsableApiKey()) {
            msg(player, cfg.requireOwnApiKey
                    ? "§cPossession needs your own API key — set it with §f/ai mykey <token>§c."
                    : "§cNo API key set for the AI yet — an admin can add one in §f/ai menu§c.");
            return;
        }
        PossessionSession session = new PossessionSession(player.getUUID(), bot.getId(), CLIENT);
        BY_PLAYER.put(player.getUUID(), session);
        bot.setPossessing(player.getUUID());
        AiNetworking.sendPossession(player, true, true,
                "§d" + bot.getAssistantName() + " is now possessing you. §7Tell it what to do.");
    }

    /** Ends {@code player}'s possession (if any), handing control back. */
    public static void stop(ServerPlayer player) {
        end(player.level().getServer(), player.getUUID(), true);
    }

    /** Queues an instruction, starting possession first if it isn't running yet. */
    public static void queue(ServerPlayer player, String instruction) {
        if (instruction == null || instruction.isBlank()) return;
        PossessionSession session = BY_PLAYER.get(player.getUUID());
        if (session == null) {
            start(player);
            session = BY_PLAYER.get(player.getUUID());
            if (session == null) return;   // start refused (disabled / no bot / no key)
        }
        session.queue(instruction.trim());
        AiNetworking.sendPossession(player, false, true, "§b> §f" + instruction.trim());
    }

    public static void handleDisconnect(ServerPlayer player) {
        end(player.level().getServer(), player.getUUID(), false);
    }

    // ── per-tick ────────────────────────────────────────────────────────────────

    private static void tick(MinecraftServer server) {
        if (BY_PLAYER.isEmpty()) return;
        ModConfig cfg = ModConfig.get();
        for (UUID id : new ArrayList<>(BY_PLAYER.keySet())) {
            PossessionSession session = BY_PLAYER.get(id);
            if (session == null) continue;

            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || !player.isAlive()) { end(server, id, false); continue; }

            if (!cfg.allowPossession || EmergencyState.isDisabled()) {
                end(server, id, true);
                continue;
            }

            AiAssistantEntity bot = findBot(server, session.getBotId());
            if (bot == null || !bot.isAlive()) {
                AiNetworking.sendPossession(player, false, false,
                        "§7Your companion is gone — possession ended.");
                end(server, id, false);
                continue;
            }

            session.tick(server, player, bot);

            String line;
            while ((line = session.drainStatus()) != null) {
                AiNetworking.sendPossession(player, false, true, line);
            }
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private static void end(MinecraftServer server, UUID id, boolean notify) {
        PossessionSession session = BY_PLAYER.remove(id);
        if (session == null || server == null) return;

        AiAssistantEntity bot = findBot(server, session.getBotId());
        if (bot != null) bot.clearPossession();

        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null) {
            player.setShiftKeyDown(false);
            if (notify) {
                AiNetworking.sendPossession(player, false, false,
                        "§7Possession ended — you have control again.");
            }
        }
    }

    private static AiAssistantEntity findBot(MinecraftServer server, int entityId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(entityId);
            if (e instanceof AiAssistantEntity ai && ai.isAlive()) return ai;
        }
        return null;
    }

    private static void msg(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
    }
}
