package com.milkdromeda.blockpal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: a player driving <b>possession mode</b> from the console screen
 * (or the {@code /ai possess} command). {@code action} is one of:
 *
 * <ul>
 *   <li>{@code "start"} — begin possession with the player's nearby owned bot;</li>
 *   <li>{@code "stop"} — end possession and hand control back;</li>
 *   <li>{@code "instruction"} — a free-text thing to do (in {@code text}).</li>
 * </ul>
 *
 * <p>A player can only ever possess <i>their own</i> character with <i>their own</i>
 * companion, so this needs no admin check — the server always acts on the sender.
 */
public record PossessionInputPayload(String action, String text) implements CustomPacketPayload {

    public static final Type<PossessionInputPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("blockpal", "possess_input"));

    public static final StreamCodec<FriendlyByteBuf, PossessionInputPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUtf(p.action == null ? "" : p.action);
                        buf.writeUtf(p.text == null ? "" : p.text);
                    },
                    buf -> new PossessionInputPayload(buf.readUtf(), buf.readUtf()));

    @Override
    public Type<PossessionInputPayload> type() {
        return TYPE;
    }
}
