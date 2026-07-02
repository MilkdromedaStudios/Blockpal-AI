package com.milkdromeda.blockpal;

import com.milkdromeda.blockpal.chat.ChatListener;
import com.milkdromeda.blockpal.command.AiCommands;
import com.milkdromeda.blockpal.command.PartyCommands;
import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import com.milkdromeda.blockpal.network.AiNetworking;
import com.milkdromeda.blockpal.party.PartyManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
        PartyCommands.register();
        ChatListener.register();
        registerFirstRunTutorial();
        // Keep parties tidy: drop a player from their party when they disconnect.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                PartyManager.handleDisconnect(handler.player));

        LOGGER.info("Blockpal mod initialized.");
        if (!ModConfig.get().hasApiToken()) {
            LOGGER.warn("No AI API token set yet. Set one in-game from /ai menu (AI tab), "
                    + "or via the BLOCKPAL_API_TOKEN environment variable.");
        }
    }

    /**
     * On the first player join after a fresh install (no config folder yet), greet
     * the player and open the how-to tutorial. {@code tutorialShown} makes this a
     * one-time thing; the config folder itself is created by {@link ModConfig} on load.
     */
    private void registerFirstRunTutorial() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (ModConfig.get().tutorialShown) return;
            ModConfig.get().tutorialShown = true;
            ModConfig.save();
            ServerPlayer player = handler.player;
            player.sendSystemMessage(Component.literal(
                    "§6Welcome to Blockpal! §7New here? Run §a/ai tutorial§7 for a quick guide, "
                            + "or §a/ai summon§7 to spawn your companion."));
            AiNetworking.openTutorialFor(player);
        });
    }
}
