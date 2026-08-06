package com.milkdromeda.blockpal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: "you just went creative and your companion is still walking."
 *
 * <p>Carries the companion's name so the warning screen can be about <i>your</i> bot
 * rather than a generic notice. Sent at most once per switch into a flying game mode —
 * see {@link com.milkdromeda.blockpal.CreativeWatch}.
 */
public record CreativeWarningPayload(String botName) implements CustomPacketPayload {

    public static final Type<CreativeWarningPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("blockpal", "creative_warning"));

    public static final StreamCodec<FriendlyByteBuf, CreativeWarningPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeUtf(p.botName == null ? "" : p.botName),
                    buf -> new CreativeWarningPayload(buf.readUtf()));

    @Override
    public Type<CreativeWarningPayload> type() {
        return TYPE;
    }
}
