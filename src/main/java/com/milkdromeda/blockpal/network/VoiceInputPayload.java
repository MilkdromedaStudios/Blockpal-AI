package com.milkdromeda.blockpal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: the transcribed text of a push-to-talk voice message. The
 * client records the microphone while the talk key is held, transcribes it
 * (Whisper large-v3-turbo by default) on the player's own machine, and sends
 * only the resulting <b>text</b> — raw audio never crosses the wire, which
 * keeps the packet tiny and the server logic identical to typed chat.
 *
 * <p>The server routes it to the sender's own companion only (it is never
 * public chat), re-checking ownership/trust server-side like every other order.
 */
public record VoiceInputPayload(String text) implements CustomPacketPayload {

    public static final Type<VoiceInputPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("blockpal", "voice_input"));

    public static final StreamCodec<FriendlyByteBuf, VoiceInputPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeUtf(p.text == null ? "" : p.text),
                    buf -> new VoiceInputPayload(buf.readUtf()));

    @Override
    public Type<VoiceInputPayload> type() {
        return TYPE;
    }
}
