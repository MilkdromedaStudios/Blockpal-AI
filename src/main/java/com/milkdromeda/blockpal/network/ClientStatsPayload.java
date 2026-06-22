package com.milkdromeda.blockpal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: a lightweight client stat report (currently the frame-rate),
 * sent about once a second so the admin menu can show how each player's game is
 * running. Purely informational; the server just records the latest value.
 */
public record ClientStatsPayload(int fps) implements CustomPacketPayload {

    public static final Type<ClientStatsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("blockpal", "client_stats"));

    public static final StreamCodec<FriendlyByteBuf, ClientStatsPayload> CODEC =
            StreamCodec.of((buf, p) -> buf.writeInt(p.fps), buf -> new ClientStatsPayload(buf.readInt()));

    @Override
    public Type<ClientStatsPayload> type() {
        return TYPE;
    }
}
