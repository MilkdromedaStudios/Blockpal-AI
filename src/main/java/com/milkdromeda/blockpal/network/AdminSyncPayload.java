package com.milkdromeda.blockpal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → admin client: a fresh {@link AdminStatsData} snapshot. Receiving it
 * opens (or refreshes) the admin menu screen. Only ever sent to players the
 * server has already verified as admins.
 */
public record AdminSyncPayload(AdminStatsData data) implements CustomPacketPayload {

    public static final Type<AdminSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("blockpal", "admin_sync"));

    public static final StreamCodec<FriendlyByteBuf, AdminSyncPayload> CODEC =
            AdminStatsData.STREAM_CODEC.map(AdminSyncPayload::new, AdminSyncPayload::data);

    @Override
    public Type<AdminSyncPayload> type() {
        return TYPE;
    }
}
