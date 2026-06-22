package com.milkdromeda.blockpal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: the settings the player chose in the menu. The server
 * validates, applies, and saves them.
 */
public record ConfigUpdatePayload(ConfigData data) implements CustomPacketPayload {

    public static final Type<ConfigUpdatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("blockpal", "config_update"));

    public static final StreamCodec<FriendlyByteBuf, ConfigUpdatePayload> CODEC =
            ConfigData.STREAM_CODEC.map(ConfigUpdatePayload::new, ConfigUpdatePayload::data);

    @Override
    public Type<ConfigUpdatePayload> type() {
        return TYPE;
    }
}
