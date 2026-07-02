package com.milkdromeda.blockpal.party;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side registry of all live {@link Party parties} and pending invites. It's
 * the social layer the hosted world and the minigames build on: a leader gathers a
 * group (up to {@link #MAX_PARTY}), and everyone — Java or Bedrock — joins through
 * chat/commands, so it works for any client.
 *
 * <p>All of this runs on the server thread (commands + connection events), so plain
 * maps are safe. State is in-memory and not persisted across restarts.
 */
public final class PartyManager {

    private PartyManager() {}

    /** Largest party allowed. */
    public static final int MAX_PARTY = 100;
    private static final long INVITE_TTL_MS = 120_000;   // invites lapse after 2 minutes

    /** Every player that's in a party maps to that (shared) Party instance. */
    private static final Map<UUID, Party> byPlayer = new HashMap<>();
    /** invitee UUID -> who invited them + when it lapses. */
    private static final Map<UUID, Invite> invites = new HashMap<>();

    private record Invite(UUID leader, String inviterName, long expiresAt) {}

    /** The party {@code player} is in, or null. */
    public static Party partyOf(ServerPlayer player) {
        return byPlayer.get(player.getUUID());
    }

    // ── actions (each replies to the player; the command layer is a thin wrapper) ──

    public static void invite(ServerPlayer inviter, ServerPlayer target) {
        if (inviter.getUUID().equals(target.getUUID())) { msg(inviter, "§cYou can't invite yourself."); return; }

        Party party = byPlayer.get(inviter.getUUID());
        if (party == null) {                              // inviting auto-creates your party
            party = new Party(inviter);
            byPlayer.put(inviter.getUUID(), party);
        }
        if (!party.isLeader(inviter.getUUID())) { msg(inviter, "§cOnly the party leader can invite players."); return; }
        if (party.isMember(target.getUUID())) { msg(inviter, "§e" + target.getName().getString() + " is already in your party."); return; }
        if (byPlayer.containsKey(target.getUUID())) { msg(inviter, "§c" + target.getName().getString() + " is already in another party."); return; }
        if (party.size() >= MAX_PARTY) { msg(inviter, "§cYour party is full (" + MAX_PARTY + ")."); return; }

        invites.put(target.getUUID(), new Invite(inviter.getUUID(), inviter.getName().getString(),
                System.currentTimeMillis() + INVITE_TTL_MS));
        msg(inviter, "§aInvited §f" + target.getName().getString() + "§a to your party.");
        msg(target, "§e" + inviter.getName().getString() + " invited you to their party — type "
                + "§f/party accept§e or §f/party deny§e (expires in 2 min).");
    }

    public static void accept(ServerPlayer target) {
        Invite inv = invites.remove(target.getUUID());
        if (inv == null || inv.expiresAt() < System.currentTimeMillis()) {
            msg(target, "§cYou have no pending party invite."); return;
        }
        if (byPlayer.containsKey(target.getUUID())) { msg(target, "§cLeave your current party first with §f/party leave§c."); return; }
        Party party = byPlayer.get(inv.leader());
        if (party == null || !party.isLeader(inv.leader())) { msg(target, "§cThat party no longer exists."); return; }
        if (party.size() >= MAX_PARTY) { msg(target, "§cThat party is now full."); return; }

        party.add(target);
        byPlayer.put(target.getUUID(), party);
        broadcast(target.level().getServer(), party, "§a" + target.getName().getString()
                + " joined the party §7(" + party.size() + "/" + MAX_PARTY + ")");
    }

    public static void deny(ServerPlayer target) {
        Invite inv = invites.remove(target.getUUID());
        if (inv == null) { msg(target, "§7No pending party invite."); return; }
        msg(target, "§7Declined the party invite from " + inv.inviterName() + ".");
        ServerPlayer leader = target.level().getServer() == null ? null
                : target.level().getServer().getPlayerList().getPlayer(inv.leader());
        if (leader != null) msg(leader, "§7" + target.getName().getString() + " declined your party invite.");
    }

    public static void leave(ServerPlayer player) {
        Party party = byPlayer.remove(player.getUUID());
        if (party == null) { msg(player, "§7You're not in a party."); return; }
        removeFrom(player.level().getServer(), party, player.getUUID(), player.getName().getString(), "left");
        msg(player, "§7You left the party.");
    }

    public static void kick(ServerPlayer leader, String targetName) {
        Party party = byPlayer.get(leader.getUUID());
        if (party == null || !party.isLeader(leader.getUUID())) { msg(leader, "§cOnly the party leader can kick."); return; }
        UUID found = null;
        for (UUID u : party.memberUuids()) {
            if (targetName.equalsIgnoreCase(party.nameOf(u)) && !u.equals(leader.getUUID())) { found = u; break; }
        }
        if (found == null) { msg(leader, "§c" + targetName + " isn't in your party."); return; }
        byPlayer.remove(found);
        ServerPlayer kicked = leader.level().getServer().getPlayerList().getPlayer(found);
        if (kicked != null) msg(kicked, "§cYou were removed from " + party.leaderName() + "'s party.");
        removeFrom(leader.level().getServer(), party, found, party.nameOf(found) == null ? targetName : party.nameOf(found), "was kicked");
    }

    public static void disband(ServerPlayer leader) {
        Party party = byPlayer.get(leader.getUUID());
        if (party == null || !party.isLeader(leader.getUUID())) { msg(leader, "§cOnly the party leader can disband."); return; }
        broadcast(leader.level().getServer(), party, "§7The party was disbanded by " + leader.getName().getString() + ".");
        for (UUID u : party.memberUuids()) byPlayer.remove(u);
    }

    public static void list(ServerPlayer player) {
        Party party = byPlayer.get(player.getUUID());
        if (party == null) { msg(player, "§7You're not in a party. Start one with §f/party invite <player>§7."); return; }
        StringBuilder sb = new StringBuilder("§6=== Party (" + party.size() + "/" + MAX_PARTY + ") ===");
        for (UUID u : party.memberUuids()) {
            sb.append("\n§f  ").append(party.nameOf(u)).append(party.isLeader(u) ? " §6(leader)" : "");
        }
        msg(player, sb.toString());
    }

    /** Called from the player-disconnect event: drop them from their party quietly. */
    public static void handleDisconnect(ServerPlayer player) {
        Party party = byPlayer.remove(player.getUUID());
        invites.remove(player.getUUID());
        if (party != null) {
            removeFrom(player.level().getServer(), party, player.getUUID(), player.getName().getString(), "disconnected");
        }
    }

    // ── helpers ──

    /** Removes {@code uuid} from {@code party}, handing off leadership or disbanding as needed. */
    private static void removeFrom(MinecraftServer server, Party party, UUID uuid, String name, String verb) {
        boolean wasLeader = party.isLeader(uuid);
        party.remove(uuid);
        if (party.size() == 0) {
            return;   // empty party just evaporates
        }
        if (wasLeader) {
            UUID next = party.anyMemberExcept(uuid);
            if (next != null) {
                party.setLeader(next);
                broadcast(server, party, "§e" + name + " " + verb + "; " + party.nameOf(next) + " is now the leader.");
                return;
            }
        }
        broadcast(server, party, "§7" + name + " " + verb + " §7(" + party.size() + "/" + MAX_PARTY + ")");
    }

    private static void broadcast(MinecraftServer server, Party party, String message) {
        if (server == null) return;
        Component c = Component.literal(message);
        for (UUID u : party.memberUuids()) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) p.sendSystemMessage(c);
        }
    }

    private static void msg(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
    }
}
