package com.milkdromeda.blockpal.voice;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who can HEAR whose agent. By default a companion's voice is private: only its
 * owner hears it. An owner can <b>share</b> their agent's voice with another
 * player (/ai voice share &lt;player&gt;), who then hears that agent too — and
 * sharing in either direction <b>links</b> the two agents into one conversation
 * group, which the {@link VoiceCoordinator} uses for turn-taking so linked
 * agents never talk over each other.
 *
 * <p>State is in-memory (like parties): shares reset on server restart. All
 * methods key by player UUID, so name changes and relogs within a session are
 * safe. The map is concurrent because shares are read from the coordinator on
 * the server tick and edited from command handlers.
 */
public final class VoiceLinkManager {

    private VoiceLinkManager() {}

    /** owner UUID → the players that owner has shared their agent's voice with. */
    private static final Map<UUID, Set<UUID>> SHARES = new ConcurrentHashMap<>();
    /** Last-known display names so /ai voice list reads well while someone is offline. */
    private static final Map<UUID, String> NAMES = new ConcurrentHashMap<>();

    /** Records {@code owner} sharing their agent's voice with {@code target}. @return false if already shared. */
    public static boolean share(ServerPlayer owner, ServerPlayer target) {
        if (owner == null || target == null || owner.getUUID().equals(target.getUUID())) return false;
        NAMES.put(owner.getUUID(), owner.getName().getString());
        NAMES.put(target.getUUID(), target.getName().getString());
        return SHARES.computeIfAbsent(owner.getUUID(), k -> ConcurrentHashMap.newKeySet())
                .add(target.getUUID());
    }

    /** Stops sharing with {@code target}. @return true if a share was removed. */
    public static boolean unshare(UUID owner, UUID target) {
        Set<UUID> set = SHARES.get(owner);
        return set != null && set.remove(target);
    }

    /** Removes every share the owner made. @return how many were removed. */
    public static int clearShares(UUID owner) {
        Set<UUID> set = SHARES.remove(owner);
        return set == null ? 0 : set.size();
    }

    /** The players {@code owner} currently shares their agent's voice with (never null). */
    public static Set<UUID> sharesOf(UUID owner) {
        Set<UUID> set = SHARES.get(owner);
        return set == null ? Set.of() : Collections.unmodifiableSet(set);
    }

    /** The owners currently sharing THEIR agent's voice with {@code listener}. */
    public static List<UUID> sharedTo(UUID listener) {
        List<UUID> out = new ArrayList<>();
        for (Map.Entry<UUID, Set<UUID>> e : SHARES.entrySet()) {
            if (e.getValue().contains(listener)) out.add(e.getKey());
        }
        return out;
    }

    /**
     * Everyone who should hear an agent owned by {@code owner}: the owner plus
     * whoever the owner shared with — online players only.
     */
    public static List<ServerPlayer> listenersFor(MinecraftServer server, UUID owner) {
        List<ServerPlayer> out = new ArrayList<>();
        if (server == null || owner == null) return out;
        ServerPlayer op = server.getPlayerList().getPlayer(owner);
        if (op != null) out.add(op);
        for (UUID id : sharesOf(owner)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && !out.contains(p)) out.add(p);
        }
        return out;
    }

    /**
     * The link group {@code owner} belongs to: the connected component of owners
     * over share edges in EITHER direction ("if two or more agents are shared and
     * linked"). Agents in one group take turns speaking instead of interrupting
     * each other. An unshared owner is a group of one.
     *
     * <p>The group is identified by its smallest member UUID so every member
     * computes the same key regardless of who is asking.
     */
    public static UUID groupKeyOf(UUID owner) {
        if (owner == null) return null;
        Set<UUID> seen = new HashSet<>();
        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(owner);
        seen.add(owner);
        while (!queue.isEmpty()) {
            UUID cur = queue.poll();
            // Outgoing shares link me to my listeners' agents…
            for (UUID next : sharesOf(cur)) {
                if (seen.add(next)) queue.add(next);
            }
            // …and incoming shares link me to agents shared to me.
            for (UUID next : sharedTo(cur)) {
                if (seen.add(next)) queue.add(next);
            }
        }
        return seen.stream().min(Comparator.naturalOrder()).orElse(owner);
    }

    /** A friendly, ordered name list for /ai voice list. */
    public static Map<String, Boolean> describeShares(MinecraftServer server, UUID owner) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (UUID id : sharesOf(owner)) {
            ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(id);
            String name = p != null ? p.getName().getString() : NAMES.getOrDefault(id, id.toString());
            out.put(name, p != null);
        }
        return out;
    }

    /** Remembers a player's current name (called opportunistically from commands). */
    public static void rememberName(ServerPlayer player) {
        if (player != null) NAMES.put(player.getUUID(), player.getName().getString());
    }

    public static String nameOf(UUID id) {
        return NAMES.getOrDefault(id, "someone");
    }
}
