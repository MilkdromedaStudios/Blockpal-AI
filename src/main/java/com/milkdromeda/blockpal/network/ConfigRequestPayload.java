package com.milkdromeda.blockpal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: "send me the current config so I can open the menu."
 * Carries no data; the server replies with a {@link ConfigSyncPayload}.
 */
public record ConfigRequestPayload() implements CustomPacketPayload {

    public static final Type<ConfigRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("blockpal", "config_request"));

    public static final StreamCodec<FriendlyByteBuf, ConfigRequestPayload> CODEC =
            StreamCodec.unit(new ConfigRequestPayload());

    @Override
    public Type<ConfigRequestPayload> type() {
        return TYPE;
    }
}
