package com.milkdromeda.blockpal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: "your companion (or one shared with you) just said this —
 * speak it out loud." The client synthesizes the audio locally (text-to-speech
 * with the given voice id) and plays it privately, so only players this packet
 * was addressed to ever hear the agent. The server has already done the
 * turn-taking ({@link com.milkdromeda.blockpal.voice.VoiceCoordinator}), so
 * utterances from linked agents arrive one at a time, never overlapping.
 *
 * <ul>
 *   <li>{@code speaker} — the agent's display name (for the caption/log);</li>
 *   <li>{@code voice} — the agent's TTS voice id ("" = the client's default);</li>
 *   <li>{@code text} — what to say.</li>
 * </ul>
 */
public record VoiceSpeakPayload(String speaker, String voice, String text) implements CustomPacketPayload {

    public static final Type<VoiceSpeakPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("blockpal", "voice_speak"));

    public static final StreamCodec<FriendlyByteBuf, VoiceSpeakPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUtf(p.speaker == null ? "" : p.speaker);
                        buf.writeUtf(p.voice == null ? "" : p.voice);
                        buf.writeUtf(p.text == null ? "" : p.text);
                    },
                    buf -> new VoiceSpeakPayload(buf.readUtf(), buf.readUtf(), buf.readUtf()));

    @Override
    public Type<VoiceSpeakPayload> type() {
        return TYPE;
    }
}
