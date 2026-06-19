package com.milkdromeda.blockpal;

import com.milkdromeda.blockpal.chat.ChatListener;
import com.milkdromeda.blockpal.command.AiCommands;
import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import com.milkdromeda.blockpal.network.AiNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AiAssistantMod implements ModInitializer {
    public static final String MOD_ID = "blockpal";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModConfig.load();
        ModEntities.register();
        FabricDefaultAttributeRegistry.register(ModEntities.AI_ASSISTANT, AiAssistantEntity.createAttributes());
        AiNetworking.registerPayloads();
        AiNetworking.registerServerReceivers();
        AiCommands.register();
        ChatListener.register();

        LOGGER.info("Blockpal mod initialized.");
        if (!ModConfig.get().hasApiToken()) {
            LOGGER.warn("No AI API token set. Use /ai token <token> in-game to enable AI tasks.");
        }
    }
}
