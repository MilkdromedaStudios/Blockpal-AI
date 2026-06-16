package com.milkdromeda.aiassistant.network;

import com.milkdromeda.aiassistant.config.ModConfig;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Wires up the client ⇄ server packets that back the in-game config menu.
 * Payload types are registered on both sides (this runs from the common mod
 * initializer); the server-bound receivers are registered here too.
 */
public final class AiNetworking {

    private AiNetworking() {}

    /** Registers the three payload types. Safe to call on client and server. */
    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(ConfigRequestPayload.TYPE, ConfigRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConfigUpdatePayload.TYPE, ConfigUpdatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ConfigSyncPayload.TYPE, ConfigSyncPayload.CODEC);
    }

    /** Registers the handlers that run on the (integrated or dedicated) server. */
    public static void registerServerReceivers() {
        // A client asked for the current config — reply with a sync so it can open the menu.
        ServerPlayNetworking.registerGlobalReceiver(ConfigRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.getServer();
            if (server == null) return;
            server.execute(() ->
                    ServerPlayNetworking.send(player, new ConfigSyncPayload(ConfigData.fromConfig())));
        });

        // A client saved settings from the menu — validate, apply, persist.
        ServerPlayNetworking.registerGlobalReceiver(ConfigUpdatePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.getServer();
            if (server == null) return;
            server.execute(() -> {
                payload.data().applyTo(ModConfig.get());
                ModConfig.save();
                player.sendSystemMessage(Component.literal("§a[AI] Settings saved ✓"));
            });
        });
    }
}
