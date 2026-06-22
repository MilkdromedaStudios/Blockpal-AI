package com.milkdromeda.blockpal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Admin client → server: an action triggered from the admin menu — e.g.
 * {@code killall}, {@code disable}, {@code enable}, {@code maxbots} (with a value)
 * or {@code refresh}. The server re-checks the sender's permission before acting
 * (never trust the client), then replies with a fresh {@link AdminSyncPayload}.
 */
public record AdminActionPayload(String action, int value) implements CustomPacketPayload {

    public static final Type<AdminActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("blockpal", "admin_action"));

    public static final StreamCodec<FriendlyByteBuf, AdminActionPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> { buf.writeUtf(p.action == null ? "" : p.action, 64); buf.writeInt(p.value); },
                    buf -> new AdminActionPayload(buf.readUtf(64), buf.readInt()));

    @Override
    public Type<AdminActionPayload> type() {
        return TYPE;
    }
}
