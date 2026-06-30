package com.milkdromeda.blockpal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: "send me the current bot list" — answered with a
 * {@link BotListSyncPayload}. Used to open the Bots panel and to refresh it.
 */
public record BotListRequestPayload() implements CustomPacketPayload {

    public static final Type<BotListRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("blockpal", "bot_list_request"));

    public static final StreamCodec<FriendlyByteBuf, BotListRequestPayload> CODEC =
            StreamCodec.unit(new BotListRequestPayload());

    @Override
    public Type<BotListRequestPayload> type() {
        return TYPE;
    }
}
