package com.milkdromeda.blockpal.network;

import com.milkdromeda.blockpal.config.ModConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * A flat, serializable snapshot of the user-facing settings, shipped between
 * client and server so the in-game config menu can read and write them.
 *
 * <p>For privacy the API token is never sent <em>to</em> the client: sync
 * snapshots carry an empty {@link #token} plus {@link #tokenSet} so the menu can
 * show "set / not set". When the menu saves, a blank token means "keep the
 * existing one".
 *
 * <p>Developer-mode fields ({@link #actionTickDelay}, {@link #maxTaskSeconds},
 * {@link #fleeHealthPercent}) are included in every packet but only shown in the
 * GUI when developer mode is enabled. Setting them incorrectly can cause lag or
 * crashes — see {@code developer.md} for details.
 */
public record ConfigData(
        boolean chatListening,
        boolean activeMode,
        boolean debugLogging,
        String defaultName,
        String token,
        boolean tokenSet,
        String model,
        String apiUrl,
        double temperature,
        int maxTokens,
        double followDistance,
        double guardRadius,
        boolean allowCommands,
        int commandPermissionLevel,
        String defaultSkin,
        // Developer-mode fields
        int actionTickDelay,
        int maxTaskSeconds,
        double fleeHealthPercent,
        String performancePreset,
        boolean sneakToOpenMenu,
        String defaultPersonality,
        boolean allowCustomPersonality,
        boolean allowPossession,
        boolean freeAiFallback,
        boolean allowVoice,
        // Local Ollama (custom local models)
        boolean ollamaEnabled,
        String ollamaUrl,
        String ollamaModel,
        // Player2 (player2.game) — easiest AI, local or online
        boolean player2Enabled,
        String player2Url,
        String player2Model,
        boolean player2KeySet,  // display-only: is a PLAYER2_KEY resolved (→ online)?
        // ── the ONE AI connection + the vision/code brain (3.25.0) ──
        String aiConnection,    // ai/AiConnection id: mcp | key | player2 | ollama | free | off
        String aiLogicMode,     // "code" (look → write a script) or "plan" (classic JSON planner)
        boolean visionEnabled,  // render a picture from the bot's eyes for the model
        boolean survivalBrain,  // keep-alive reflexes even with no AI at all
        boolean creativeModeWarning,
        // MCP server (bots driven by an outside AI app)
        int mcpPort,
        boolean mcpAllowRemote,
        boolean mcpRequireToken,
        boolean mcpRunning,     // display-only: is the MCP server actually listening?
        // ── everything that used to be config-file-only (3.25.1) ──
        int visionWidth,
        int visionHeight,
        int visionRange,
        int scriptMaxTicks,
        boolean preferSurvivalActions,
        boolean humanizeActions,
        String freeApiUrl,
        String freeModel,
        String player2LocalUrl,
        int villageTargetPopulation,
        int villageStartPopulation,
        // ── how fast it acts, how it fights, and what it learned (3.26.0) ──
        String reactionSpeed,   // agent/Tempo id: instant | fast | human
        String combatSkill,     // combat/CombatSkill id: basic | skilled | expert
        boolean allowPvp,       // may a companion fight a PLAYER at all (provoked only)
        boolean pvtEnabled,
        boolean pvtAutoRecord,
        int pvtHiddenSize,
        int pvtEpochs,
        double pvtLearningRate,
        int pvtMaxFrames,
        double pvtConfidence,
        boolean pvtHasPolicy,   // display-only: is there a trained policy on disk?
        // ── the local model that runs on this machine's GPU (3.27.0) ──
        String localModelId,
        int localPort,
        int localGpuLayers,
        int localContext,
        boolean localAutoStart,
        boolean localConsented, // display-only: consent is NEVER granted from a packet
        String localState       // display-only: what the local model is doing right now
) {
    public static final StreamCodec<FriendlyByteBuf, ConfigData> STREAM_CODEC =
            StreamCodec.of(ConfigData::write, ConfigData::read);

    /** Builds a snapshot from the live config, omitting the token value itself. */
    public static ConfigData fromConfig() {
        ModConfig c = ModConfig.get();
        return new ConfigData(
                c.chatListening,
                c.activeMode,
                c.debugLogging,
                c.defaultName,
                "",                 // never expose the token to the client
                c.hasApiToken(),
                c.hfModel,
                c.apiUrl,
                c.temperature,
                c.maxNewTokens,
                c.followDistance,
                c.guardRadius,
                c.allowCommands,
                c.commandPermissionLevel,
                c.defaultSkin,
                c.actionTickDelay,
                c.maxTaskSeconds,
                c.fleeHealthPercent,
                c.performancePreset,
                c.sneakToOpenMenu,
                c.defaultPersonality,
                c.allowCustomPersonality,
                c.allowPossession,
                c.freeAiFallback,
                c.allowVoice,
                c.ollamaEnabled,
                c.ollamaUrl,
                c.ollamaModel,
                c.player2Enabled,
                c.player2OnlineUrl,
                c.player2Model,
                !c.resolvePlayer2Key().isBlank(),
                c.aiConnection,
                c.aiLogicMode,
                c.visionEnabled,
                c.survivalBrain,
                c.creativeModeWarning,
                c.mcpPort,
                c.mcpAllowRemote,
                c.mcpRequireToken,
                com.milkdromeda.blockpal.mcp.McpServer.isRunning(),
                c.visionWidth,
                c.visionHeight,
                c.visionRange,
                c.scriptMaxTicks,
                c.preferSurvivalActions,
                c.humanizeActions,
                c.freeApiUrl,
                c.freeModel,
                c.player2Url,
                c.villageTargetPopulation,
                c.villageStartPopulation,
                c.reactionSpeed,
                c.combatSkill,
                c.allowPvp,
                c.pvtEnabled,
                c.pvtAutoRecord,
                c.pvtHiddenSize,
                c.pvtEpochs,
                c.pvtLearningRate,
                c.pvtMaxFrames,
                c.pvtConfidence,
                com.milkdromeda.blockpal.pvt.PvtManager.hasPolicy(),
                c.localModelId,
                c.localPort,
                c.localGpuLayers,
                c.localContext,
                c.localAutoStart,
                c.localConsented,
                com.milkdromeda.blockpal.localai.LocalAiManager.state().name()
                        .toLowerCase(java.util.Locale.ROOT));
    }

    /** Applies this snapshot onto the live config, clamping and keeping blanks. */
    public void applyTo(ModConfig c) {
        c.chatListening = chatListening;
        c.activeMode = activeMode;
        c.debugLogging = debugLogging;
        if (notBlank(defaultName)) c.defaultName = defaultName.trim();
        if (notBlank(token)) c.setToken(token.trim());   // blank = keep existing
        // Model ids are pasted from web pages, so scrub quotes/whitespace artifacts
        // that make an otherwise-valid id come back as an opaque HTTP 400.
        String cleanedModel = com.milkdromeda.blockpal.ai.ModelIds.clean(model);
        if (!cleanedModel.isBlank()) c.hfModel = cleanedModel;
        if (notBlank(apiUrl)) c.apiUrl = apiUrl.trim();
        c.temperature = clamp(temperature, 0.0, 2.0);
        c.maxNewTokens = (int) clamp(maxTokens, 32, 2048);
        c.followDistance = clamp(followDistance, 1.0, 32.0);
        c.guardRadius = clamp(guardRadius, 4.0, 64.0);
        c.allowCommands = allowCommands;
        c.commandPermissionLevel = (int) clamp(commandPermissionLevel, 0, 4);
        if (notBlank(defaultSkin)) c.defaultSkin = defaultSkin.trim();
        // Developer fields — applied as-is (intentionally no tight clamping)
        c.actionTickDelay = (int) clamp(actionTickDelay, 0, 40);
        c.maxTaskSeconds = (int) clamp(maxTaskSeconds, 0, 3600);
        c.fleeHealthPercent = clamp(fleeHealthPercent, 0.0, 1.0);
        if (notBlank(performancePreset)) c.performancePreset = performancePreset.trim();
        c.sneakToOpenMenu = sneakToOpenMenu;
        // Only accept a real personality id (guards against a forged/garbage value).
        if (com.milkdromeda.blockpal.ai.Personality.byId(defaultPersonality) != null) {
            c.defaultPersonality = defaultPersonality.trim().toLowerCase(java.util.Locale.ROOT);
        }
        c.allowCustomPersonality = allowCustomPersonality;
        c.allowPossession = allowPossession;
        c.allowVoice = allowVoice;
        // Endpoints/models for the providers, whichever one is chosen below.
        if (notBlank(ollamaUrl)) c.ollamaUrl = ollamaUrl.trim();
        if (notBlank(ollamaModel)) c.ollamaModel = com.milkdromeda.blockpal.ai.ModelIds.clean(ollamaModel);
        if (notBlank(player2Url)) c.player2OnlineUrl = player2Url.trim();
        if (notBlank(player2Model)) c.player2Model = com.milkdromeda.blockpal.ai.ModelIds.clean(player2Model);
        // player2KeySet is display-only — the key comes from the PLAYER2_KEY env, never the client.

        // The ONE AI connection. This drives freeAiFallback/ollamaEnabled/player2Enabled
        // rather than the panel setting each of them, so two providers can never be on.
        com.milkdromeda.blockpal.ai.AiConnection chosen =
                com.milkdromeda.blockpal.ai.AiConnection.byId(aiConnection);
        if (chosen != null) c.setConnection(chosen);
        if (c.player2Enabled) com.milkdromeda.blockpal.ai.HuggingFaceClient.warmPlayer2Local();

        // How a bot thinks, and what keeps it alive when nothing is thinking.
        c.aiLogicMode = "plan".equalsIgnoreCase(aiLogicMode) ? "plan" : "code";
        c.visionEnabled = visionEnabled;
        c.survivalBrain = survivalBrain;
        c.creativeModeWarning = creativeModeWarning;

        // MCP server. A port change is applied by the caller re-syncing the server.
        c.mcpPort = (int) clamp(mcpPort, 1024, 65535);
        c.mcpAllowRemote = mcpAllowRemote;
        c.mcpRequireToken = mcpRequireToken;
        // mcpRunning is display-only — the server's real state, not something to set.

        // The rest used to be reachable only by hand-editing config.json. Clamped to the
        // same ranges ModConfig.normalize() enforces, so the panel can't write a value the
        // next load would silently correct.
        c.visionWidth = (int) clamp(visionWidth, 32, 160);
        c.visionHeight = (int) clamp(visionHeight, 18, 90);
        c.visionRange = (int) clamp(visionRange, 8, 64);
        c.scriptMaxTicks = (int) clamp(scriptMaxTicks, 100, 12000);
        c.preferSurvivalActions = preferSurvivalActions;
        c.humanizeActions = humanizeActions;
        if (notBlank(freeApiUrl)) c.freeApiUrl = freeApiUrl.trim();
        if (notBlank(freeModel)) c.freeModel = com.milkdromeda.blockpal.ai.ModelIds.clean(freeModel);
        if (notBlank(player2LocalUrl)) c.player2Url = player2LocalUrl.trim();
        c.villageTargetPopulation = (int) clamp(villageTargetPopulation, 2, 200);
        c.villageStartPopulation = (int) clamp(villageStartPopulation, 1, 50);
        // Clamped to exactly what ModConfig.normalize() would enforce, so the panel can
        // never write a value the next load would silently correct behind the player.
        if (com.milkdromeda.blockpal.agent.Tempo.byId(reactionSpeed) != null) {
            c.reactionSpeed = reactionSpeed.trim().toLowerCase(java.util.Locale.ROOT);
        }
        if (com.milkdromeda.blockpal.combat.CombatSkill.byId(combatSkill) != null) {
            c.combatSkill = combatSkill.trim().toLowerCase(java.util.Locale.ROOT);
        }
        c.allowPvp = allowPvp;
        c.pvtEnabled = pvtEnabled;
        c.pvtAutoRecord = pvtAutoRecord;
        c.pvtHiddenSize = (int) clamp(pvtHiddenSize, 32, 512);
        c.pvtEpochs = (int) clamp(pvtEpochs, 1, 200);
        c.pvtLearningRate = clamp(pvtLearningRate, 0.00001, 0.1);
        c.pvtMaxFrames = (int) clamp(pvtMaxFrames, 1000, 5_000_000);
        c.pvtConfidence = clamp(pvtConfidence, 0, 1);

        if (com.milkdromeda.blockpal.ai.LocalModel.byId(localModelId) != null) {
            c.localModelId = localModelId.trim().toLowerCase(java.util.Locale.ROOT);
        }
        c.localPort = (int) clamp(localPort, 1024, 65535);
        c.localGpuLayers = (int) clamp(localGpuLayers, -1, 999);
        c.localContext = (int) clamp(localContext, 512, 32768);
        c.localAutoStart = localAutoStart;
        // localConsented is deliberately NOT applied. Agreeing to a two-gigabyte download
        // is a decision someone makes at a prompt that tells them the size, not something
        // a settings packet can flip — a modified client must not be able to start a
        // download on the server by sending a boolean.
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static void write(FriendlyByteBuf buf, ConfigData d) {
        buf.writeBoolean(d.chatListening);
        buf.writeBoolean(d.activeMode);
        buf.writeBoolean(d.debugLogging);
        buf.writeUtf(d.defaultName == null ? "" : d.defaultName);
        buf.writeUtf(d.token == null ? "" : d.token);
        buf.writeBoolean(d.tokenSet);
        buf.writeUtf(d.model == null ? "" : d.model);
        buf.writeUtf(d.apiUrl == null ? "" : d.apiUrl);
        buf.writeDouble(d.temperature);
        buf.writeInt(d.maxTokens);
        buf.writeDouble(d.followDistance);
        buf.writeDouble(d.guardRadius);
        buf.writeBoolean(d.allowCommands);
        buf.writeInt(d.commandPermissionLevel);
        buf.writeUtf(d.defaultSkin == null ? "default" : d.defaultSkin);
        buf.writeInt(d.actionTickDelay);
        buf.writeInt(d.maxTaskSeconds);
        buf.writeDouble(d.fleeHealthPercent);
        buf.writeUtf(d.performancePreset == null ? "normal" : d.performancePreset);
        buf.writeBoolean(d.sneakToOpenMenu);
        buf.writeUtf(d.defaultPersonality == null ? "friendly" : d.defaultPersonality);
        buf.writeBoolean(d.allowCustomPersonality);
        buf.writeBoolean(d.allowPossession);
        buf.writeBoolean(d.freeAiFallback);
        buf.writeBoolean(d.allowVoice);
        buf.writeBoolean(d.ollamaEnabled);
        buf.writeUtf(d.ollamaUrl == null ? "" : d.ollamaUrl);
        buf.writeUtf(d.ollamaModel == null ? "" : d.ollamaModel);
        buf.writeBoolean(d.player2Enabled);
        buf.writeUtf(d.player2Url == null ? "" : d.player2Url);
        buf.writeUtf(d.player2Model == null ? "" : d.player2Model);
        buf.writeBoolean(d.player2KeySet);
        buf.writeUtf(d.aiConnection == null ? "free" : d.aiConnection);
        buf.writeUtf(d.aiLogicMode == null ? "code" : d.aiLogicMode);
        buf.writeBoolean(d.visionEnabled);
        buf.writeBoolean(d.survivalBrain);
        buf.writeBoolean(d.creativeModeWarning);
        buf.writeInt(d.mcpPort);
        buf.writeBoolean(d.mcpAllowRemote);
        buf.writeBoolean(d.mcpRequireToken);
        buf.writeBoolean(d.mcpRunning);
        buf.writeInt(d.visionWidth);
        buf.writeInt(d.visionHeight);
        buf.writeInt(d.visionRange);
        buf.writeInt(d.scriptMaxTicks);
        buf.writeBoolean(d.preferSurvivalActions);
        buf.writeBoolean(d.humanizeActions);
        buf.writeUtf(d.freeApiUrl == null ? "" : d.freeApiUrl);
        buf.writeUtf(d.freeModel == null ? "" : d.freeModel);
        buf.writeUtf(d.player2LocalUrl == null ? "" : d.player2LocalUrl);
        buf.writeInt(d.villageTargetPopulation);
        buf.writeInt(d.villageStartPopulation);
        buf.writeUtf(d.reactionSpeed == null ? "fast" : d.reactionSpeed);
        buf.writeUtf(d.combatSkill == null ? "skilled" : d.combatSkill);
        buf.writeBoolean(d.allowPvp);
        buf.writeBoolean(d.pvtEnabled);
        buf.writeBoolean(d.pvtAutoRecord);
        buf.writeInt(d.pvtHiddenSize);
        buf.writeInt(d.pvtEpochs);
        buf.writeDouble(d.pvtLearningRate);
        buf.writeInt(d.pvtMaxFrames);
        buf.writeDouble(d.pvtConfidence);
        buf.writeBoolean(d.pvtHasPolicy);
        buf.writeUtf(d.localModelId == null ? "qwen3b" : d.localModelId);
        buf.writeInt(d.localPort);
        buf.writeInt(d.localGpuLayers);
        buf.writeInt(d.localContext);
        buf.writeBoolean(d.localAutoStart);
        buf.writeBoolean(d.localConsented);
        buf.writeUtf(d.localState == null ? "off" : d.localState);
    }

    private static ConfigData read(FriendlyByteBuf buf) {
        return new ConfigData(
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readDouble(),
                buf.readInt(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readBoolean(),
                buf.readInt(),
                buf.readUtf(),
                buf.readInt(),
                buf.readInt(),
                buf.readDouble(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),   // ollamaEnabled
                buf.readUtf(),       // ollamaUrl
                buf.readUtf(),       // ollamaModel
                buf.readBoolean(),   // player2Enabled
                buf.readUtf(),       // player2Url
                buf.readUtf(),       // player2Model
                buf.readBoolean(),   // player2KeySet
                buf.readUtf(),       // aiConnection
                buf.readUtf(),       // aiLogicMode
                buf.readBoolean(),   // visionEnabled
                buf.readBoolean(),   // survivalBrain
                buf.readBoolean(),   // creativeModeWarning
                buf.readInt(),       // mcpPort
                buf.readBoolean(),   // mcpAllowRemote
                buf.readBoolean(),   // mcpRequireToken
                buf.readBoolean(),   // mcpRunning
                buf.readInt(),       // visionWidth
                buf.readInt(),       // visionHeight
                buf.readInt(),       // visionRange
                buf.readInt(),       // scriptMaxTicks
                buf.readBoolean(),   // preferSurvivalActions
                buf.readBoolean(),   // humanizeActions
                buf.readUtf(),       // freeApiUrl
                buf.readUtf(),       // freeModel
                buf.readUtf(),       // player2LocalUrl
                buf.readInt(),       // villageTargetPopulation
                buf.readInt(),       // villageStartPopulation
                buf.readUtf(),       // reactionSpeed
                buf.readUtf(),       // combatSkill
                buf.readBoolean(),   // allowPvp
                buf.readBoolean(),   // pvtEnabled
                buf.readBoolean(),   // pvtAutoRecord
                buf.readInt(),       // pvtHiddenSize
                buf.readInt(),       // pvtEpochs
                buf.readDouble(),    // pvtLearningRate
                buf.readInt(),       // pvtMaxFrames
                buf.readDouble(),    // pvtConfidence
                buf.readBoolean(),   // pvtHasPolicy
                buf.readUtf(),       // localModelId
                buf.readInt(),       // localPort
                buf.readInt(),       // localGpuLayers
                buf.readInt(),       // localContext
                buf.readBoolean(),   // localAutoStart
                buf.readBoolean(),   // localConsented
                buf.readUtf());      // localState
    }
}
