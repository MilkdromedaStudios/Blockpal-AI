package com.milkdromeda.blockpal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: "open the how-to tutorial screen." Carries no data — the
 * tutorial content is static on the client. Sent on a player's first join after
 * a fresh install, or on demand via {@code /ai tutorial}.
 */
public record OpenTutorialPayload() implements CustomPacketPayload {

    public static final Type<OpenTutorialPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("blockpal", "open_tutorial"));

    public static final StreamCodec<FriendlyByteBuf, OpenTutorialPayload> CODEC =
            StreamCodec.unit(new OpenTutorialPayload());

    @Override
    public Type<OpenTutorialPayload> type() {
        return TYPE;
    }
}
