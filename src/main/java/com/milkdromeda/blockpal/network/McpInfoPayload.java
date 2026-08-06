package com.milkdromeda.blockpal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: everything a player needs to connect their own AI app to this world,
 * plus a request to open the setup guide screen.
 *
 * <p>The access token really is sent to the client here, unlike the API key — but only to
 * a player the server has already checked is allowed to see it, and it is <i>their</i>
 * token for connecting <i>their</i> AI app to <i>this</i> world. Showing it is the whole
 * point: you cannot copy a credential you're never allowed to look at.
 */
public record McpInfoPayload(boolean running, String endpoint, String sseEndpoint,
                             String token, String status) implements CustomPacketPayload {

    public static final Type<McpInfoPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("blockpal", "mcp_info"));

    public static final StreamCodec<FriendlyByteBuf, McpInfoPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBoolean(p.running);
                        buf.writeUtf(p.endpoint == null ? "" : p.endpoint);
                        buf.writeUtf(p.sseEndpoint == null ? "" : p.sseEndpoint);
                        buf.writeUtf(p.token == null ? "" : p.token);
                        buf.writeUtf(p.status == null ? "" : p.status);
                    },
                    buf -> new McpInfoPayload(buf.readBoolean(), buf.readUtf(), buf.readUtf(),
                            buf.readUtf(), buf.readUtf()));

    @Override
    public Type<McpInfoPayload> type() {
        return TYPE;
    }
}
