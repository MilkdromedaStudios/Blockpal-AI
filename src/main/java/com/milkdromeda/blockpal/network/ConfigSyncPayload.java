package com.milkdromeda.blockpal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: the current settings, sent in response to a request or to
 * {@code /ai menu}. Receiving it opens the in-game config screen.
 */
public record ConfigSyncPayload(ConfigData data) implements CustomPacketPayload {

    public static final Type<ConfigSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("blockpal", "config_sync"));

    public static final StreamCodec<FriendlyByteBuf, ConfigSyncPayload> CODEC =
            ConfigData.STREAM_CODEC.map(ConfigSyncPayload::new, ConfigSyncPayload::data);

    @Override
    public Type<ConfigSyncPayload> type() {
        return TYPE;
    }
}
