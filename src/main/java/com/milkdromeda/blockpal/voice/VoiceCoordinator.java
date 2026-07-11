package com.milkdromeda.blockpal.voice;

import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import com.milkdromeda.blockpal.network.VoiceSpeakPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turn-taking for agent speech ("advanced talking"). Every line an agent says
 * becomes an utterance addressed to the players allowed to hear it (its owner
 * plus anyone the owner shared with — see {@link VoiceLinkManager}). Utterances
 * are queued <b>per link group</b>, and a group plays only one utterance at a
 * time: while one linked agent is speaking, the others wait their turn instead
 * of interrupting — just like people in a conversation.
 *
 * <p>The server can't hear the client's speakers, so "still speaking" is an
 * estimate from the text length at a natural speaking rate. That keeps the
 * whole scheme server-authoritative with no extra client round-trips; a client
 * additionally plays its own incoming utterances strictly one at a time, so
 * even a mis-estimate can never overlap audio locally.
 *
 * <p>Everything runs on the server thread (enqueued from entity/handler code,
 * drained from END_SERVER_TICK), so no synchronization is needed.
 */
public final class VoiceCoordinator {

    private VoiceCoordinator() {}

    private record Utterance(UUID owner, String speaker, String voice, String text) {}

    /** Per-link-group pending utterances, in arrival order. */
    private static final Map<UUID, Deque<Utterance>> QUEUES = new HashMap<>();
    /** Per-link-group game-tick until which the current utterance is estimated to last. */
    private static final Map<UUID, Long> BUSY_UNTIL = new HashMap<>();

    /** Cap so one rambling line can't block a whole group for ages (~13 s). */
    private static final int MAX_UTTERANCE_TICKS = 260;
    /** A group's queue is bounded; when full the oldest line is dropped, not the newest. */
    private static final int MAX_QUEUED = 8;

    private static long serverTicks = 0;

    public static void registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register(VoiceCoordinator::tick);
    }

    /**
     * Queues a line the agent just said so eligible listeners hear it spoken.
     * Called from {@link AiAssistantEntity#broadcastMessage} on the server; a
     * no-op when voice is disabled server-wide or the bot has no owner online.
     */
    public static void speak(AiAssistantEntity bot, String text) {
        if (bot == null || text == null || text.isBlank()) return;
        if (!ModConfig.get().allowVoice) return;
        UUID owner = bot.getOwnerUuid();
        if (owner == null) return;
        MinecraftServer server = bot.level().getServer();
        if (server == null) return;
        // No listener online with a modded client → nothing would play; skip early.
        boolean anyone = false;
        for (ServerPlayer p : VoiceLinkManager.listenersFor(server, owner)) {
            if (ServerPlayNetworking.canSend(p, VoiceSpeakPayload.TYPE)) { anyone = true; break; }
        }
        if (!anyone) return;

        UUID group = VoiceLinkManager.groupKeyOf(owner);
        Deque<Utterance> q = QUEUES.computeIfAbsent(group, k -> new ArrayDeque<>());
        if (q.size() >= MAX_QUEUED) q.pollFirst();
        q.addLast(new Utterance(owner, bot.getAssistantName(), bot.getVoiceId(), text));
    }

    /** Advances every group's conversation: when its current speaker finishes, the next talks. */
    private static void tick(MinecraftServer server) {
        serverTicks++;
        if (QUEUES.isEmpty()) return;
        Iterator<Map.Entry<UUID, Deque<Utterance>>> it = QUEUES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Deque<Utterance>> e = it.next();
            long busyUntil = BUSY_UNTIL.getOrDefault(e.getKey(), 0L);
            if (serverTicks < busyUntil) continue;   // a linked agent is still speaking
            Utterance u = e.getValue().pollFirst();
            if (u == null) {
                it.remove();
                BUSY_UNTIL.remove(e.getKey());
                continue;
            }
            deliver(server, u);
            BUSY_UNTIL.put(e.getKey(), serverTicks + estimateTicks(u.text()));
        }
    }

    /** Sends the utterance to every eligible online listener whose client can play it. */
    private static void deliver(MinecraftServer server, Utterance u) {
        List<ServerPlayer> listeners = new ArrayList<>(VoiceLinkManager.listenersFor(server, u.owner()));
        VoiceSpeakPayload payload = new VoiceSpeakPayload(u.speaker(), u.voice(), u.text());
        for (ServerPlayer p : listeners) {
            if (ServerPlayNetworking.canSend(p, VoiceSpeakPayload.TYPE)) {
                ServerPlayNetworking.send(p, payload);
            }
        }
    }

    /**
     * How long the line will roughly take to say. Natural speech runs ~14-16
     * characters/second; add a beat of lead-in for the TTS round-trip so
     * back-to-back turns don't butt against each other.
     */
    private static int estimateTicks(String text) {
        int talking = (int) Math.ceil(text.length() / 15.0 * 20.0);
        return Math.min(MAX_UTTERANCE_TICKS, 30 + talking);
    }
}
