package com.milkdromeda.blockpal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: an action from the Bots panel on a specific bot, identified by
 * its network {@code entityId}. {@code action} is one of come/follow/stay/stop/
 * dismiss/rename/skin/personality; {@code arg} carries the new name / skin /
 * personality id where relevant (else blank).
 *
 * <p>The server always re-checks the sender's permission for that bot before acting
 * — the panel only greys buttons cosmetically, so a modified client can't bypass it.
 */
public record BotActionPayload(int entityId, String action, String arg) implements CustomPacketPayload {

    public static final Type<BotActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("blockpal", "bot_action"));

    public static final StreamCodec<FriendlyByteBuf, BotActionPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeInt(p.entityId);
                        buf.writeUtf(p.action == null ? "" : p.action, 32);
                        buf.writeUtf(p.arg == null ? "" : p.arg, 256);
                    },
                    buf -> new BotActionPayload(buf.readInt(), buf.readUtf(32), buf.readUtf(256)));

    @Override
    public Type<BotActionPayload> type() {
        return TYPE;
    }
}
