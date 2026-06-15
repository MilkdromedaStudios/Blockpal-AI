package com.milkdromeda.aiassistant.client;

import com.milkdromeda.aiassistant.ModEntities;
import com.milkdromeda.aiassistant.client.render.AiAssistantEntityModel;
import com.milkdromeda.aiassistant.client.render.AiAssistantEntityRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

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

        AiAssistantHud.register();
    }
}
