package com.milkdromeda.blockpal.client.assist;

import com.milkdromeda.blockpal.ai.ActionPlan;
import com.milkdromeda.blockpal.ai.HuggingFaceClient;
import com.milkdromeda.blockpal.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Client-side entry point to the language model, used by the private assistant chat
 * box, the on-screen tips, and the off-server possession driver. It reuses the same
 * {@link HuggingFaceClient} and {@link HuggingFaceClient.ApiAuth} resolution as the
 * server — but resolved from the <b>local player's own</b> {@code config/blockpal}
 * settings, so it works on any server (or none) without the server having Blockpal.
 */
public final class ClientAi {

    private static final HuggingFaceClient CLIENT = new HuggingFaceClient();

    private ClientAi() {}

    /** Resolves the key/model/endpoint from this client's local config + player. */
    public static HuggingFaceClient.ApiAuth auth() {
        LocalPlayer p = Minecraft.getInstance().player;
        UUID id = p == null ? null : p.getUUID();
        String name = p == null ? "" : p.getName().getString();
        return HuggingFaceClient.ApiAuth.resolveFor(id, name);
    }

    /** True when the client has some way to reach the AI (a key, or the free fallback). */
    public static boolean available() {
        return auth().usable();
    }

    /** A conversational reply for the chat box. Never throws. */
    public static CompletableFuture<String> chat(List<String[]> history, String userMessage) {
        return CLIENT.requestChat(history, userMessage, auth());
    }

    /** A structured action plan for the off-server possession driver. Never throws. */
    public static CompletableFuture<ActionPlan> plan(String instruction, String context) {
        return CLIENT.requestPlan(instruction, context, auth());
    }

    /** Convenience: the label to show for the current AI source. */
    public static String sourceLabel() {
        HuggingFaceClient.ApiAuth a = auth();
        if (!a.usable()) return "no AI configured";
        if (a.free()) return "free AI (" + a.model() + ")";
        return "your key (" + a.model() + ")";
    }

    public static ModConfig cfg() {
        return ModConfig.get();
    }
}
