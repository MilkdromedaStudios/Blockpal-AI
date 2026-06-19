package com.milkdromeda.blockpal.client;

import com.milkdromeda.blockpal.ModEntities;
import com.milkdromeda.blockpal.client.gui.AiConfigScreen;
import com.milkdromeda.blockpal.client.render.AiAssistantEntityModel;
import com.milkdromeda.blockpal.client.render.AiAssistantEntityRenderer;
import com.milkdromeda.blockpal.client.render.RuntimeSkins;
import com.milkdromeda.blockpal.network.ConfigSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
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
