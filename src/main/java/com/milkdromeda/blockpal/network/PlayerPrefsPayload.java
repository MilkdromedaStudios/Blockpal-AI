package com.milkdromeda.blockpal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: a player saving their own preferences from the personal
 * "{@code /ai mymenu}" screen — the model to use, optionally a new personal API
 * key ({@code token}; blank = leave unchanged), whether to clear the key, and the
 * personality for their nearby bot (a built-in {@code personality} id, or a free
 * {@code customPersonality} text that the server safety-checks before applying).
 *
 * <p>A player can only ever change <i>their own</i> settings (and their own bot's
 * personality) through this, so it needs no admin check — the server applies it to
 * the sending player's UUID and their nearest owned bot.
 */
public record PlayerPrefsPayload(String model, String token, boolean clearKey,
                                 String personality, String customPersonality) implements CustomPacketPayload {

    public static final Type<PlayerPrefsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("blockpal", "player_prefs"));

    public static final StreamCodec<FriendlyByteBuf, PlayerPrefsPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUtf(p.model == null ? "" : p.model);
                        buf.writeUtf(p.token == null ? "" : p.token);
                        buf.writeBoolean(p.clearKey);
                        buf.writeUtf(p.personality == null ? "" : p.personality);
                        buf.writeUtf(p.customPersonality == null ? "" : p.customPersonality);
                    },
                    buf -> new PlayerPrefsPayload(buf.readUtf(), buf.readUtf(), buf.readBoolean(),
                            buf.readUtf(), buf.readUtf()));

    @Override
    public Type<PlayerPrefsPayload> type() {
        return TYPE;
    }
}
