package com.milkdromeda.blockpal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: the client's frame-rate guardian asks the server to flip the
 * emergency kill switch. {@code disabled} carries the requested state and
 * {@code fps} the frame-rate that triggered it (for the notification message).
 */
public record EmergencyDisablePayload(boolean disabled, int fps) implements CustomPacketPayload {

    public static final Type<EmergencyDisablePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("blockpal", "emergency_disable"));

    public static final StreamCodec<FriendlyByteBuf, EmergencyDisablePayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> { buf.writeBoolean(p.disabled); buf.writeInt(p.fps); },
                    buf -> new EmergencyDisablePayload(buf.readBoolean(), buf.readInt()));

    @Override
    public Type<EmergencyDisablePayload> type() {
        return TYPE;
    }
}
