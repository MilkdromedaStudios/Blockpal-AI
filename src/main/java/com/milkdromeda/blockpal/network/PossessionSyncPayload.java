package com.milkdromeda.blockpal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: a possession-mode update for the console screen.
 *
 * <ul>
 *   <li>{@code open} — true asks the client to open (or focus) the possession
 *       console; false is a background status update for an already-open console;</li>
 *   <li>{@code active} — whether possession is currently running;</li>
 *   <li>{@code line} — an optional status/log line to append (may be blank).</li>
 * </ul>
 *
 * <p>The server sends these as the AI plans and acts, so the console shows a live
 * feed of what it's doing. On a client that can't receive custom packets
 * (Bedrock/vanilla), the server falls back to a plain chat message instead.
 */
public record PossessionSyncPayload(boolean open, boolean active, String line) implements CustomPacketPayload {

    public static final Type<PossessionSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("blockpal", "possess_sync"));

    public static final StreamCodec<FriendlyByteBuf, PossessionSyncPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBoolean(p.open);
                        buf.writeBoolean(p.active);
                        buf.writeUtf(p.line == null ? "" : p.line);
                    },
                    buf -> new PossessionSyncPayload(buf.readBoolean(), buf.readBoolean(), buf.readUtf()));

    @Override
    public Type<PossessionSyncPayload> type() {
        return TYPE;
    }
}
