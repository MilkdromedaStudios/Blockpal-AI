package com.milkdromeda.blockpal.client;

import com.milkdromeda.blockpal.ModEntities;
import com.milkdromeda.blockpal.client.gui.AdminScreen;
import com.milkdromeda.blockpal.client.gui.AiConfigScreen;
import com.milkdromeda.blockpal.client.gui.HostScreen;
import com.milkdromeda.blockpal.client.gui.PlayerSettingsScreen;
import com.milkdromeda.blockpal.client.gui.TutorialScreen;
import com.milkdromeda.blockpal.client.host.HostManager;
import com.milkdromeda.blockpal.client.render.AiAssistantEntityModel;
import com.milkdromeda.blockpal.client.render.AiAssistantEntityRenderer;
import com.milkdromeda.blockpal.client.render.RuntimeSkins;
import com.milkdromeda.blockpal.network.AdminSyncPayload;
import com.milkdromeda.blockpal.network.ConfigSyncPayload;
import com.milkdromeda.blockpal.network.OpenTutorialPayload;
import com.milkdromeda.blockpal.network.PlayerPrefsSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

import java.util.Set;

public class AiAssistantClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ModelLayerRegistry.registerModelLayer(
                AiAssistantEntityModel.LAYER,
                AiAssistantEntityModel::createModelData
        );

        EntityRendererRegistry.register(
                ModEntities.AI_ASSISTANT,
                AiAssistantEntityRenderer::new
        );

        // Server sent us the current config (via /ai menu) — open the settings screen.
        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    FpsGuardian.setPreset(payload.data().performancePreset());
                    context.client().setScreenAndShow(new AiConfigScreen(payload.data()));
                }));

        // Server sent an admin snapshot (via /ai admin menu, or after an action) —
        // open/refresh the admin panel. Only admins ever receive this packet.
        ClientPlayNetworking.registerGlobalReceiver(AdminSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() ->
                        context.client().setScreenAndShow(new AdminScreen(payload.data()))));

        // Server sent the personal preferences snapshot (via /ai mymenu, or after a
        // save) — open/refresh the per-player settings screen.
        ClientPlayNetworking.registerGlobalReceiver(PlayerPrefsSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() ->
                        context.client().setScreenAndShow(new PlayerSettingsScreen(payload))));

        // Server asked us to open the how-to tutorial (first join, or /ai tutorial).
        ClientPlayNetworking.registerGlobalReceiver(OpenTutorialPayload.TYPE, (payload, context) ->
                context.client().execute(() ->
                        context.client().setScreenAndShow(new TutorialScreen())));

        // Extreme frame-rate watchdog: auto-disable the mod if FPS collapses.
        FpsGuardian.register();

        // Make the custom-skins folder (config/blockpal/skins/) and scan it.
        RuntimeSkins.init();

        // Client-side helper command to list / reload skins dropped into that folder.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("aiskins")
                        .executes(ctx -> listSkins(ctx.getSource()))
                        .then(ClientCommands.literal("list")
                                .executes(ctx -> listSkins(ctx.getSource())))
                        .then(ClientCommands.literal("reload")
                                .executes(ctx -> {
                                    RuntimeSkins.reload();
                                    return listSkins(ctx.getSource());
                                }))));

        // "Host with Blockpal": open a server (Minecraft + Fabric + latest Geyser +
        // Floodgate) so Bedrock friends can join your Java world. Client-only, so a
        // Bedrock player has no way to host — they can only join. Reachable from the
        // pause menu (singleplayer) and via /aihost.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("aihost")
                        .executes(ctx -> openHost(ctx.getSource()))
                        .then(ClientCommands.literal("status").executes(ctx -> hostStatus(ctx.getSource())))
                        .then(ClientCommands.literal("stop").executes(ctx -> {
                            HostManager.get().stop();
                            ctx.getSource().sendFeedback(Component.literal("§eStopping the Blockpal host…"));
                            return 1;
                        }))));

        // Add a "Host with Blockpal" button to the pause menu, but only in singleplayer
        // (hosting a separate server from inside someone else's server makes no sense).
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof PauseScreen && client.hasSingleplayerServer()) {
                Button host = Button.builder(Component.literal("Host with Blockpal"),
                                b -> client.setScreenAndShow(new HostScreen(screen)))
                        .bounds(scaledWidth / 2 - 102, scaledHeight - 52, 204, 20)
                        .build();
                Screens.getWidgets(screen).add(host);
            }
        });
    }

    private static int openHost(FabricClientCommandSource src) {
        src.getClient().execute(() -> src.getClient().setScreenAndShow(new HostScreen(null)));
        return 1;
    }

    private static int hostStatus(FabricClientCommandSource src) {
        HostManager h = HostManager.get();
        StringBuilder sb = new StringBuilder("§6Blockpal host: §f" + h.phase().label + " §7— " + h.status());
        if (h.isRunning()) {
            sb.append("\n§eJava: §f").append(h.localIp()).append(":").append(h.javaPort())
                    .append(" §7(LAN) / §f").append(h.publicIp()).append(":").append(h.javaPort()).append(" §7(internet)");
            sb.append("\n§eBedrock: §f").append(h.localIp()).append(" port ").append(h.bedrockPort())
                    .append(" §7(LAN) / §f").append(h.publicIp()).append(" port ").append(h.bedrockPort()).append(" §7(internet)");
        }
        final String out = sb.toString();
        src.sendFeedback(Component.literal(out));
        return 1;
    }

    private static int listSkins(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource src) {
        Set<String> names = RuntimeSkins.names();
        if (names.isEmpty()) {
            src.sendFeedback(Component.literal(
                    "§eNo custom skins yet. §7Drop a 64×64 PNG into:\n§f" + RuntimeSkins.SKIN_DIR
                            + "\n§7then run §f/aiskins reload§7, and apply it with §f/ai skin <name>§7."));
        } else {
            src.sendFeedback(Component.literal(
                    "§aCustom skins (" + names.size() + "): §f" + String.join("§7, §f", names)
                            + "\n§7Apply one with §f/ai skin <name>§7."));
        }
        return 1;
    }
}
