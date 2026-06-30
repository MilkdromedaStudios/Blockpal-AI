package com.milkdromeda.blockpal.network;

import com.milkdromeda.blockpal.admin.AdminAccess;
import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A serializable snapshot of every Blockpal bot on the server, for the visual
 * <b>Bots</b> manager panel. Each row carries who owns the bot and what the
 * <i>viewing</i> player is allowed to do with it (so the GUI can enable/grey the
 * right buttons), letting players manage many bots individually instead of
 * "the nearest one".
 *
 * <p>Built server-side per viewer via {@link #gather(MinecraftServer, ServerPlayer)};
 * the permission flags are authoritative there and re-checked again when an action
 * actually runs (see {@code AiNetworking}). The list is capped so a busy server can't
 * produce an oversized packet.
 */
public record BotListData(List<BotInfo> bots) {

    /** One bot row. {@code entityId} is the network id used to target it in {@link BotActionPayload}. */
    public record BotInfo(
            int entityId,
            String name,
            String owner,
            boolean ownedByViewer,
            boolean canCommand,
            boolean canManage,
            String dim,
            int x, int y, int z,
            int health, int maxHealth,
            String mode,
            String personality,
            int trustedCount
    ) {}

    private static final int MAX_ROWS = 100;

    public static final StreamCodec<FriendlyByteBuf, BotListData> STREAM_CODEC =
            StreamCodec.of(BotListData::write, BotListData::read);

    /** Snapshots every bot on the server, with per-{@code viewer} permission flags. */
    public static BotListData gather(MinecraftServer server, ServerPlayer viewer) {
        boolean admin = AdminAccess.isAdmin(viewer);
        List<BotInfo> rows = new ArrayList<>();
        for (AiAssistantEntity ai : AiAssistantEntity.all(server)) {
            if (rows.size() >= MAX_ROWS) break;
            String dim = ai.level() instanceof ServerLevel sl ? sl.dimension().identifier().getPath() : "?";
            rows.add(new BotInfo(
                    ai.getId(),
                    ai.getAssistantName(),
                    ownerName(server, ai.getOwnerUuid(), ai.getOwnerName()),
                    ai.isOwnedBy(viewer),
                    ai.canCommand(viewer) || admin,
                    ai.isOwner(viewer) || admin,
                    dim,
                    ai.getBlockX(), ai.getBlockY(), ai.getBlockZ(),
                    (int) Math.ceil(ai.getHealth()), (int) Math.ceil(ai.getMaxHealth()),
                    ai.getMode().name(),
                    ai.getPersonalityLabel(),
                    ai.trustedCount()));
        }
        return new BotListData(rows);
    }

    private static String ownerName(MinecraftServer server, UUID uuid, String storedName) {
        if (uuid == null) return "—";
        ServerPlayer p = server.getPlayerList().getPlayer(uuid);
        if (p != null) return p.getName().getString();
        if (storedName != null && !storedName.isBlank()) return storedName;
        return uuid.toString().substring(0, 8);   // owner offline & no stored name — short id
    }

    private static void write(FriendlyByteBuf buf, BotListData d) {
        buf.writeInt(d.bots.size());
        for (BotInfo b : d.bots) {
            buf.writeInt(b.entityId());
            buf.writeUtf(b.name());
            buf.writeUtf(b.owner());
            buf.writeBoolean(b.ownedByViewer());
            buf.writeBoolean(b.canCommand());
            buf.writeBoolean(b.canManage());
            buf.writeUtf(b.dim());
            buf.writeInt(b.x());
            buf.writeInt(b.y());
            buf.writeInt(b.z());
            buf.writeInt(b.health());
            buf.writeInt(b.maxHealth());
            buf.writeUtf(b.mode());
            buf.writeUtf(b.personality());
            buf.writeInt(b.trustedCount());
        }
    }

    private static BotListData read(FriendlyByteBuf buf) {
        int n = buf.readInt();
        List<BotInfo> bots = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            bots.add(new BotInfo(
                    buf.readInt(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readUtf(),
                    buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readInt(), buf.readInt(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readInt()));
        }
        return new BotListData(bots);
    }
}
