package com.milkdromeda.aiassistant.client;

import com.milkdromeda.aiassistant.ModEntities;
import com.milkdromeda.aiassistant.client.gui.AiConfigScreen;
import com.milkdromeda.aiassistant.client.render.AiAssistantEntityModel;
import com.milkdromeda.aiassistant.client.render.AiAssistantEntityRenderer;
import com.milkdromeda.aiassistant.network.ConfigRequestPayload;
import com.milkdromeda.aiassistant.network.ConfigSyncPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

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

        // Keybind to open the config menu (default K; rebind in Controls).
        KeyMapping openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.ai-assistant.open_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "key.categories.ai-assistant"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.consumeClick()) {
                if (client.player != null && ClientPlayNetworking.canSend(ConfigRequestPayload.TYPE)) {
                    // Ask the server for the current settings; it replies with a
                    // sync packet that opens the menu with fresh values.
                    ClientPlayNetworking.send(new ConfigRequestPayload());
                }
            }
        });

        // Server sent us the current config — open the menu seeded with it.
        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() ->
                        context.client().setScreen(new AiConfigScreen(payload.data()))));
    }
}
