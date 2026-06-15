package com.milkdromeda.aiassistant;

import com.milkdromeda.aiassistant.command.AiCommands;
import com.milkdromeda.aiassistant.config.ModConfig;
import com.milkdromeda.aiassistant.entity.AiAssistantEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AiAssistantMod implements ModInitializer {
    public static final String MOD_ID = "ai-assistant";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModConfig.load();
        ModEntities.register();
        FabricDefaultAttributeRegistry.register(ModEntities.AI_ASSISTANT, AiAssistantEntity.createAttributes());
        AiCommands.register();

        LOGGER.info("AI Assistant mod initialized.");
        if (!ModConfig.get().hasApiToken()) {
            LOGGER.warn("No HuggingFace API token set. Use /aiassistant config hf_token <token> in-game.");
        }
    }
}
