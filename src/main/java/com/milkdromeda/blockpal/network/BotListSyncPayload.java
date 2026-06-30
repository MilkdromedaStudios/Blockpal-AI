package com.milkdromeda.blockpal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: a fresh {@link BotListData} snapshot. Receiving it opens (or
 * refreshes) the visual <b>Bots</b> manager panel. Open to any player — the per-bot
 * permission flags inside decide what they can actually do.
 */
public record BotListSyncPayload(BotListData data) implements CustomPacketPayload {

    public static final Type<BotListSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("blockpal", "bot_list_sync"));

    public static final StreamCodec<FriendlyByteBuf, BotListSyncPayload> CODEC =
            BotListData.STREAM_CODEC.map(BotListSyncPayload::new, BotListSyncPayload::data);

    @Override
    public Type<BotListSyncPayload> type() {
        return TYPE;
    }
}
