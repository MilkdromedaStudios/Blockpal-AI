package com.milkdromeda.blockpal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: "open the AI Manual in-game wiki screen."
 * Carries no data — all content is static on the client. Sent whenever a player
 * right-clicks their AI Manual item.
 */
public record OpenManualPayload() implements CustomPacketPayload {

    public static final Type<OpenManualPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("blockpal", "open_manual"));

    public static final StreamCodec<FriendlyByteBuf, OpenManualPayload> CODEC =
            StreamCodec.unit(new OpenManualPayload());

    @Override
    public Type<OpenManualPayload> type() {
        return TYPE;
    }
}
