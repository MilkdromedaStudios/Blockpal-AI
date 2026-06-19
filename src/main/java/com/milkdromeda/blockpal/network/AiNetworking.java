package com.milkdromeda.blockpal.network;

import com.milkdromeda.blockpal.EmergencyState;
import com.milkdromeda.blockpal.config.ModConfig;
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
        PayloadTypeRegistry.serverboundPlay().register(ConfigRequestPayload.TYPE, ConfigRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ConfigUpdatePayload.TYPE, ConfigUpdatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(EmergencyDisablePayload.TYPE, EmergencyDisablePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ConfigSyncPayload.TYPE, ConfigSyncPayload.CODEC);
    }

    /** Sends the current config to a player so their client opens the settings menu. */
    public static void openMenuFor(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, ConfigSyncPayload.TYPE)) {
            ServerPlayNetworking.send(player, new ConfigSyncPayload(ConfigData.fromConfig()));
        }
    }

    /** Registers the handlers that run on the (integrated or dedicated) server. */
    public static void registerServerReceivers() {
        // A client asked for the current config — reply with a sync so it can open the menu.
        ServerPlayNetworking.registerGlobalReceiver(ConfigRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.level().getServer();
            if (server == null) return;
            server.execute(() ->
                    ServerPlayNetworking.send(player, new ConfigSyncPayload(ConfigData.fromConfig())));
        });

        // A client saved settings from the menu — validate, apply, persist.
        ServerPlayNetworking.registerGlobalReceiver(ConfigUpdatePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                payload.data().applyTo(ModConfig.get());
                ModConfig.save();
                player.sendSystemMessage(Component.literal("§a[AI] Settings saved ✓"));
            });
        });

        // The client's frame-rate guardian tripped (or cleared) the emergency kill switch.
        ServerPlayNetworking.registerGlobalReceiver(EmergencyDisablePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                boolean was = EmergencyState.isDisabled();
                EmergencyState.setDisabled(payload.disabled());
                if (payload.disabled() && !was) {
                    server.getPlayerList().broadcastSystemMessage(Component.literal(
                            "§c[AI] Frame-rate critically low (" + payload.fps()
                                    + " FPS) — AI assistant auto-disabled to protect the game. "
                                    + "Run §e/ai resume§c once things recover."), false);
                } else if (!payload.disabled() && was) {
                    server.getPlayerList().broadcastSystemMessage(Component.literal(
                            "§a[AI] AI assistant re-enabled."), false);
                }
            });
        });
    }
}
