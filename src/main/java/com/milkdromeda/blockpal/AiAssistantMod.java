package com.milkdromeda.blockpal;

import com.milkdromeda.blockpal.chat.ChatListener;
import com.milkdromeda.blockpal.command.AiCommands;
import com.milkdromeda.blockpal.command.PartyCommands;
import com.milkdromeda.blockpal.command.VillageCommands;
import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import com.milkdromeda.blockpal.minigame.MinigameManager;
import com.milkdromeda.blockpal.minigame.village.VillageManager;
import com.milkdromeda.blockpal.network.AiNetworking;
import com.milkdromeda.blockpal.party.PartyManager;
import com.milkdromeda.blockpal.possession.PossessionManager;
import com.milkdromeda.blockpal.voice.VoiceCoordinator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
        // Mini-games are registered as a subcommand of /ai (/ai minigame …) by AiCommands.
        VillageCommands.register();
        MinigameManager.registerEvents();
        VillageManager.registerEvents();
        PossessionManager.registerEvents();
        VoiceCoordinator.registerEvents();
        ChatListener.register();
        registerFirstRunTutorial();
        registerMcpServer();
        CreativeWatch.register();
        registerPvt();
        registerLocalAi();
        // Keep parties/games/possession tidy: drop a player from each when they disconnect.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PartyManager.handleDisconnect(handler.player);
            MinigameManager.handleDisconnect(handler.player);
            VillageManager.handleDisconnect(handler.player);
            PossessionManager.handleDisconnect(handler.player);
            CreativeWatch.handleDisconnect(handler.player);
            com.milkdromeda.blockpal.pvt.PvtManager.onPlayerLeave(handler.player);
        });

        LOGGER.info("Blockpal mod initialized.");
        // Third-party launchers (e.g. Lunar) may use a game folder other than
        // .minecraft — log the real location so "where did my config go" is answerable.
        LOGGER.info("Blockpal config file: {}", ModConfig.configPath());
        if (!ModConfig.get().hasApiToken()) {
            if (ModConfig.get().freeAiFallback) {
                LOGGER.info("No AI API key set — using the free built-in AI ({}). Add a HuggingFace "
                        + "key in /ai menu (AI & API tab) or via BLOCKPAL_API_TOKEN for better quality.",
                        ModConfig.get().freeApiUrl);
            } else {
                LOGGER.warn("No AI API token set and the free AI fallback is disabled. Set a key "
                        + "in-game from /ai menu (AI & API tab), or via the BLOCKPAL_API_TOKEN "
                        + "environment variable.");
            }
        }
    }

    /**
     * Starts and stops the built-in MCP server with the world. It only actually listens
     * when the AI connection is set to {@code mcp} — see
     * {@link com.milkdromeda.blockpal.mcp.McpServer#sync}.
     */
    private void registerMcpServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            com.milkdromeda.blockpal.mcp.McpServer.sync(server);
            if (ModConfig.get().isMcpConnection()) {
                LOGGER.info("Blockpal AI connection: MCP — {}. Run /ai mcp in-game for the "
                        + "setup guide and access token.", com.milkdromeda.blockpal.mcp.McpServer.status());
            } else {
                LOGGER.info("Blockpal AI connection: {}", ModConfig.get().connection().display());
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                com.milkdromeda.blockpal.mcp.McpServer.stop());
    }

    /**
     * The local model: bring it up when the server starts if this server uses it and the
     * download has already been agreed to, and always shut it down cleanly — a stray
     * llama-server holding a GPU after Minecraft exits is somebody's next bug report.
     */
    private void registerLocalAi() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (ModConfig.get().connection() == com.milkdromeda.blockpal.ai.AiConnection.LOCAL) {
                com.milkdromeda.blockpal.localai.LocalAiManager.sync(server);
                LOGGER.info("Blockpal local AI: {}",
                        com.milkdromeda.blockpal.localai.LocalAiManager.state());
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                com.milkdromeda.blockpal.localai.LocalAiManager.stop());
    }

    /**
     * Pre-video training: tick the live recorders, start recording players who have
     * opted in as they join, and close every episode file cleanly on shutdown so a
     * stopped server never leaves a half-written recording behind.
     */
    private void registerPvt() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(
                com.milkdromeda.blockpal.pvt.PvtManager::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                com.milkdromeda.blockpal.pvt.PvtManager.onPlayerJoin(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                com.milkdromeda.blockpal.pvt.PvtManager.stopAll());
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
