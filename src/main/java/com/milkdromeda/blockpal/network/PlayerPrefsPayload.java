package com.milkdromeda.blockpal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: a player saving their own preferences from the personal
 * "{@code /ai mymenu}" screen — the model to use, optionally a new personal API
 * key ({@code token}; blank = leave unchanged), and whether to clear the key.
 *
 * <p>A player can only ever change <i>their own</i> settings through this, so it
 * needs no admin check — the server applies it to the sending player's UUID.
 */
public record PlayerPrefsPayload(String model, String token, boolean clearKey) implements CustomPacketPayload {

    public static final Type<PlayerPrefsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("blockpal", "player_prefs"));

    public static final StreamCodec<FriendlyByteBuf, PlayerPrefsPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUtf(p.model == null ? "" : p.model);
                        buf.writeUtf(p.token == null ? "" : p.token);
                        buf.writeBoolean(p.clearKey);
                    },
                    buf -> new PlayerPrefsPayload(buf.readUtf(), buf.readUtf(), buf.readBoolean()));

    @Override
    public Type<PlayerPrefsPayload> type() {
        return TYPE;
    }
}
