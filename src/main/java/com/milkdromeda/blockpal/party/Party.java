package com.milkdromeda.blockpal.party;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A live group of players who've teamed up — the unit that
 * {@link PartyManager} tracks and that the upcoming minigames will run on. A party
 * has one <b>leader</b> (who can invite/kick/disband) and a set of members
 * (including the leader). Membership is kept in memory only; it isn't persisted
 * across a server restart, like most party systems.
 */
public final class Party {

    private UUID leader;
    private final LinkedHashMap<UUID, String> members = new LinkedHashMap<>(); // uuid -> last-seen name, leader first

    Party(ServerPlayer leader) {
        this.leader = leader.getUUID();
        members.put(leader.getUUID(), leader.getName().getString());
    }

    public UUID leader() { return leader; }
    public boolean isLeader(UUID uuid) { return leader.equals(uuid); }
    public boolean isMember(UUID uuid) { return members.containsKey(uuid); }
    public int size() { return members.size(); }
    public String nameOf(UUID uuid) { return members.get(uuid); }
    public String leaderName() { return members.get(leader); }

    public Set<UUID> memberUuids() { return new LinkedHashSet<>(members.keySet()); }
    public Collection<String> memberNames() { return new ArrayList<>(members.values()); }

    void add(ServerPlayer player) { members.put(player.getUUID(), player.getName().getString()); }
    void remove(UUID uuid) { members.remove(uuid); }
    void setLeader(UUID uuid) { this.leader = uuid; }

    /** First member that isn't {@code exclude}, for leadership hand-off, or null. */
    UUID anyMemberExcept(UUID exclude) {
        for (UUID u : members.keySet()) if (!u.equals(exclude)) return u;
        return null;
    }
}
