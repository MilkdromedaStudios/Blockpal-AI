package com.milkdromeda.blockpal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: "open the MCP setup guide for me."
 *
 * <p>Sent by the <b>Open setup guide</b> button on the Settings panel's AI &amp; API tab,
 * so the whole MCP flow is reachable with a mouse instead of needing {@code /ai mcp}
 * typed in chat.
 *
 * <p>The reply ({@link McpInfoPayload}) carries the access token, so the handler
 * re-checks that the sender is an admin — the button being visible is not the check.
 */
public record McpInfoRequestPayload() implements CustomPacketPayload {

    public static final Type<McpInfoRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("blockpal", "mcp_info_request"));

    public static final StreamCodec<FriendlyByteBuf, McpInfoRequestPayload> CODEC =
            StreamCodec.unit(new McpInfoRequestPayload());

    @Override
    public Type<McpInfoRequestPayload> type() {
        return TYPE;
    }
}
