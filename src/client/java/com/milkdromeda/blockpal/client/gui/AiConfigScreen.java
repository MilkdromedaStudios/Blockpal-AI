package com.milkdromeda.blockpal.client.gui;

import com.milkdromeda.blockpal.ai.Personality;
import com.milkdromeda.blockpal.client.render.RuntimeSkins;
import com.milkdromeda.blockpal.network.ConfigData;
import com.milkdromeda.blockpal.network.ConfigUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;

/**
 * The AI assistant's settings menu, opened with {@code /ai menu} (or, unless
 * disabled, by sneak-right-clicking the assistant). Settings are split into
 * <b>tabs</b> — Identity, Behavior, AI, Combat and Developer — shown one
 * category at a time; the title, tab bar and Save / Apply / Cancel action bar
 * stay pinned while each tab's body scrolls.
 *
 * <p>Because only the current tab's widgets exist at any moment, every value is
 * held in a "pending" draft ({@code pXxx} fields). Switching tabs (or saving)
 * first {@link #capture() captures} the visible widgets into the draft, so
 * nothing is lost when you move between tabs. Saving sends the draft to the
 * server, so it works in singleplayer and multiplayer alike.
 */
public class AiConfigScreen extends Screen {

    private enum Tab {
        IDENTITY("Identity"), BEHAVIOR("Behavior"), AI("AI & API"),
        COMBAT("Combat"), DEVELOPER("Developer");
        final String label;
        Tab(String label) { this.label = label; }
    }

    private static final int W = 240;        // content column width
    private static final int FIELD_H = 18;
    private static final int LABEL_H = 9;
    private static final int SPACING = 3;
    private static final int NAV_Y = 20;     // cross-panel tab bar (Settings/Admin/Me)
    private static final int NAV_H = 14;
    // Sodium-style vertical tab column on the left, content beside it on the right.
    private static final int SIDEBAR_W = 84;   // width of the vertical tab column
    private static final int SIDEBAR_GAP = 8;  // gap between the tab column and the content
    private static final int TAB_Y = 40;     // top of the vertical sub-tab column
    private static final int TAB_H = 18;
    private static final int BODY_TOP = 40;  // first row of the scrollable body (beside the tabs)
    private static final int FOOTER = 28;
    private static final Component SAVED_MSG =
            Component.literal("Settings applied ✓").withStyle(s -> s.withColor(0x55FF55));

    /** Bullet-dot stand-in for a masked string, like a password field. */
    private static String maskOf(String s) {
        return "•".repeat(s == null ? 0 : s.length());
    }

    private final ConfigData initial;
    private Tab tab = Tab.IDENTITY;

    // ── pending draft (all settings, independent of which tab is visible) ──
    private String pName, pSkin, pModel, pApiUrl, pToken = "";
    private boolean pListen, pActive, pCommands, pDebug, pSneakMenu;
    private double pTemp, pFollow, pGuard, pFlee;
    private int pMaxTokens, pCmdLevel, pActionDelay, pMaxTask;
    private String pPreset;
    private String pDefaultPersonality;
    private boolean pAllowCustom;
    private boolean pAllowPossession;
    private boolean pAllowVoice;
    private boolean pFreeFallback;
    // The ONE AI connection + the vision/code brain (3.25.0).
    private String pConnection = "free";
    private String pLogicMode = "code";
    private boolean pVision = true, pSurvivalBrain = true, pCreativeWarning = true;
    private int pMcpPort = 8000;
    private boolean pMcpRemote, pMcpRequireToken = true, mcpRunning;
    private CycleButton<String> connectionButton, logicModeButton;
    private CycleButton<Boolean> visionButton, survivalBrainButton, creativeWarnButton,
            mcpRemoteButton, mcpTokenButton;
    private EditBox mcpPortBox;
    // Settings that used to be reachable only by hand-editing config.json (3.25.1).
    private int pVisionWidth = 80, pVisionHeight = 45, pVisionRange = 48, pScriptMaxTicks = 1200;
    private boolean pPreferSurvival = true, pHumanize = true;
    private String pFreeApiUrl = "", pFreeModel = "", pPlayer2LocalUrl = "";
    private int pVillageTarget = 24, pVillageStart = 5;
    // ── how fast it acts, how it fights, what it learned by watching (3.26.0) ──
    private String pReactionSpeed = "fast";
    private String pCombatSkill = "skilled";
    private boolean pAllowPvp = false;
    private boolean pPvtEnabled = true, pPvtAutoRecord = true, pvtHasPolicy = false;
    private int pPvtHiddenSize = 192, pPvtEpochs = 24, pPvtMaxFrames = 200000;
    private double pPvtLearningRate = 0.002, pPvtConfidence = 0.30;
    private CycleButton<String> reactionSpeedButton;
    private CycleButton<String> combatSkillButton;
    private CycleButton<Boolean> allowPvpButton;
    private CycleButton<Boolean> pvtEnabledButton;
    private CycleButton<Boolean> pvtAutoRecordButton;
    private OptionSlider pvtHiddenSlider, pvtEpochsSlider, pvtFramesSlider;
    private OptionSlider pvtLearningRateSlider, pvtConfidenceSlider;
    private OptionSlider visionWidthSlider, visionHeightSlider, visionRangeSlider, scriptTicksSlider;
    private CycleButton<Boolean> preferSurvivalButton, humanizeButton;
    private EditBox freeApiUrlBox, freeModelBox, player2LocalUrlBox;
    private OptionSlider villageTargetSlider, villageStartSlider;

    // Local Ollama + Player2 providers (surfaced on the AI & API tab).
    private boolean pOllamaEnabled, pPlayer2Enabled, pPlayer2KeySet;
    private String pOllamaUrl, pOllamaModel, pPlayer2Url, pPlayer2Model;
    private boolean tokenSet;
    // Whether the token box currently shows plaintext instead of masking dots. Purely a
    // display preference (not part of the saved config), so it isn't in ConfigData —
    // it just persists across tab switches like the other draft fields.
    private boolean tokenVisible;

    // ── widgets for the current tab (null when not on that tab) ──
    private EditBox nameBox, skinBox, modelBox, apiUrlBox, tokenBox;
    private StringWidget tokenStatus;
    private CycleButton<Boolean> listenButton, activeButton, commandsButton, debugButton, sneakButton, allowCustomButton, allowPossessionButton, allowVoiceButton, freeFallbackButton, tokenShowButton;
    private CycleButton<Boolean> ollamaEnabledButton, player2EnabledButton;
    private EditBox ollamaUrlBox, ollamaModelBox, player2UrlBox, player2ModelBox;
    private CycleButton<String> presetButton, defaultPersonalityButton, providerButton;
    private OptionSlider tempSlider, maxTokensSlider, followSlider, guardSlider, cmdLevelSlider;
    private OptionSlider actionDelaySlider, maxTaskSlider, fleeSlider;

    private ConfigData baseline;
    private boolean saveOnClose = true;
    private StringWidget appliedLabel;
    private long appliedFeedbackUntil;

    public AiConfigScreen(ConfigData d) {
        super(Component.literal("Blockpal Settings"));
        this.initial = d;
        this.tokenSet = d.tokenSet();
        // Seed the draft from the snapshot the server sent.
        pName = d.defaultName();
        pSkin = d.defaultSkin();
        pModel = d.model();
        pApiUrl = d.apiUrl();
        pListen = d.chatListening();
        pActive = d.activeMode();
        pCommands = d.allowCommands();
        pDebug = d.debugLogging();
        pSneakMenu = d.sneakToOpenMenu();
        pTemp = d.temperature();
        pFollow = d.followDistance();
        pGuard = d.guardRadius();
        pFlee = d.fleeHealthPercent();
        pMaxTokens = d.maxTokens();
        pCmdLevel = d.commandPermissionLevel();
        pActionDelay = d.actionTickDelay();
        pMaxTask = d.maxTaskSeconds();
        pPreset = d.performancePreset() != null ? d.performancePreset() : "normal";
        pDefaultPersonality = (d.defaultPersonality() != null && !d.defaultPersonality().isBlank())
                ? d.defaultPersonality() : Personality.DEFAULT.id();
        pAllowCustom = d.allowCustomPersonality();
        pAllowPossession = d.allowPossession();
        pAllowVoice = d.allowVoice();
        pFreeFallback = d.freeAiFallback();
        pOllamaEnabled = d.ollamaEnabled();
        pOllamaUrl = d.ollamaUrl();
        pOllamaModel = d.ollamaModel();
        pPlayer2Enabled = d.player2Enabled();
        pPlayer2Url = d.player2Url();
        pPlayer2Model = d.player2Model();
        pPlayer2KeySet = d.player2KeySet();
        pConnection = d.aiConnection();
        pLogicMode = d.aiLogicMode();
        pVision = d.visionEnabled();
        pSurvivalBrain = d.survivalBrain();
        pCreativeWarning = d.creativeModeWarning();
        pMcpPort = d.mcpPort();
        pMcpRemote = d.mcpAllowRemote();
        pMcpRequireToken = d.mcpRequireToken();
        mcpRunning = d.mcpRunning();
        pVisionWidth = d.visionWidth();
        pVisionHeight = d.visionHeight();
        pVisionRange = d.visionRange();
        pScriptMaxTicks = d.scriptMaxTicks();
        pPreferSurvival = d.preferSurvivalActions();
        pHumanize = d.humanizeActions();
        pFreeApiUrl = d.freeApiUrl();
        pFreeModel = d.freeModel();
        pPlayer2LocalUrl = d.player2LocalUrl();
        pVillageTarget = d.villageTargetPopulation();
        pVillageStart = d.villageStartPopulation();
        pReactionSpeed = d.reactionSpeed();
        pCombatSkill = d.combatSkill();
        pAllowPvp = d.allowPvp();
        pPvtEnabled = d.pvtEnabled();
        pPvtAutoRecord = d.pvtAutoRecord();
        pPvtHiddenSize = d.pvtHiddenSize();
        pPvtEpochs = d.pvtEpochs();
        pPvtLearningRate = d.pvtLearningRate();
        pPvtMaxFrames = d.pvtMaxFrames();
        pPvtConfidence = d.pvtConfidence();
        pvtHasPolicy = d.pvtHasPolicy();
        // Capture the as-loaded state once so dirty-tracking survives tab switches
        // (init() runs on every tab change, so we must NOT recompute it there).
        baseline = buildData();
    }

    @Override
    protected void init() {
        // init() reruns on EVERY widget rebuild — sub-tab clicks, window resize,
        // fullscreen toggle, GUI-scale change — so first fold anything typed since
        // the last capture into the draft, or the rebuild silently discards it
        // (most painfully a just-pasted API key). First open is a no-op (no widgets).
        capture();
        // Drop references from the previous tab so capture() never reads stale widgets.
        clearWidgetRefs();

        // -- pinned title --
        addRenderableWidget(TechTheme.centered(this.font, this.width, 6, 12, TechTheme.title("Settings")));

        // -- shared cross-panel tab bar (this screen is the admin "Settings" panel).
        // Switching panels replaces this screen with the next sync packet, so apply
        // pending edits first — otherwise a typed-but-unsaved API key would be lost.
        PanelNav.build(this.width, W + 12, NAV_Y, NAV_H, PanelNav.Tab.SETTINGS, true, this::addRenderableWidget,
                () -> { if (isDirty()) sendCurrent(); });

        // -- pinned Sodium-style vertical sub-tab column (left) --
        buildTabColumn();

        // -- scrollable body for the active tab (right of the tab column) --
        LinearLayout body = LinearLayout.vertical().spacing(SPACING);
        switch (tab) {
            case IDENTITY  -> buildIdentityTab(body);
            case BEHAVIOR  -> buildBehaviorTab(body);
            case AI        -> buildAiTab(body);
            case COMBAT    -> buildCombatTab(body);
            case DEVELOPER -> buildDeveloperTab(body);
        }

        int maxHeight = Math.max(FIELD_H, this.height - BODY_TOP - FOOTER);
        ScrollableLayout scroll = new ScrollableLayout(this.minecraft, body, maxHeight);
        scroll.setMinWidth(W + 12);
        scroll.arrangeElements();
        scroll.setX(contentLeft());
        scroll.setY(BODY_TOP);
        scroll.visitWidgets(this::addRenderableWidget);

        // -- pinned action bar --
        int bw = 100, gap = 8;
        int barW = bw * 3 + gap * 2;
        int bx = this.width / 2 - barW / 2;
        int by = this.height - FIELD_H - 6;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> saveAndClose())
                .bounds(bx, by, bw, FIELD_H).build());
        addRenderableWidget(Button.builder(Component.literal("Apply"), b -> apply())
                .bounds(bx + bw + gap, by, bw, FIELD_H).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> cancel())
                .bounds(bx + (bw + gap) * 2, by, bw, FIELD_H).build());

        appliedLabel = new StringWidget(0, by - 12, this.width, LABEL_H, Component.empty(), this.font);
        addRenderableWidget(appliedLabel);
    }

    private void clearWidgetRefs() {
        nameBox = skinBox = modelBox = apiUrlBox = tokenBox = null;
        tokenStatus = null;
        listenButton = activeButton = commandsButton = debugButton = sneakButton = allowCustomButton = allowPossessionButton = allowVoiceButton = freeFallbackButton = tokenShowButton = null;
        ollamaEnabledButton = player2EnabledButton = null;
        ollamaUrlBox = ollamaModelBox = player2UrlBox = player2ModelBox = null;
        connectionButton = logicModeButton = null;
        visionButton = survivalBrainButton = creativeWarnButton = null;
        mcpRemoteButton = mcpTokenButton = null;
        mcpPortBox = null;
        visionWidthSlider = visionHeightSlider = visionRangeSlider = scriptTicksSlider = null;
        preferSurvivalButton = humanizeButton = null;
        freeApiUrlBox = freeModelBox = player2LocalUrlBox = null;
        villageTargetSlider = villageStartSlider = null;
        presetButton = defaultPersonalityButton = providerButton = null;
        tempSlider = maxTokensSlider = followSlider = guardSlider = cmdLevelSlider = null;
        actionDelaySlider = maxTaskSlider = fleeSlider = null;
    }

    // ── tab column (Sodium-style vertical tabs on the left) ─────────────────────────

    /** Left edge of the whole sidebar+content block (both are centred as one unit). */
    private int blockLeft() {
        int totalW = SIDEBAR_W + SIDEBAR_GAP + (W + 12);
        return this.width / 2 - totalW / 2;
    }

    /** Left edge of the content column, to the right of the vertical tab column. */
    private int contentLeft() {
        return blockLeft() + SIDEBAR_W + SIDEBAR_GAP;
    }

    private void buildTabColumn() {
        Tab[] tabs = Tab.values();
        int x = blockLeft();
        int gap = 3;
        int y = TAB_Y;
        for (Tab t : tabs) {
            boolean current = (t == tab);
            Component label = current
                    ? Component.literal(t.label).withStyle(TechTheme::accent)
                    : Component.literal(t.label);
            Button b = Button.builder(label, btn -> { if (tab != t) { capture(); tab = t; rebuildWidgets(); } })
                    .bounds(x, y, SIDEBAR_W, TAB_H).build();
            b.active = !current;   // the current tab reads as "pressed"
            addRenderableWidget(b);
            y += TAB_H + gap;
        }
    }

    // ── tab bodies ────────────────────────────────────────────────────────────────

    private void buildIdentityTab(LinearLayout body) {
        header(body, "Who your assistant is");
        nameBox = bodyBox(body, "Assistant name", pName, 32, "The name shown above the assistant and used when it speaks.");
        skinBox = bodyBox(body, "Default skin", pSkin, 64, "Skin for newly summoned assistants: a built-in, a namespace:path, or a PNG in the skins folder.");
        Button open = Button.builder(Component.literal("📁 Open skins folder"), b -> openSkinsFolder())
                .bounds(0, 0, W, FIELD_H).build();
        open.setTooltip(Tooltip.create(Component.literal("Opens config/blockpal/skins/ — drop a 64×64 PNG in, then set the skin by its file name.")));
        body.addChild(open);

        body.addChild(new StringWidget(W, LABEL_H, Component.literal("Default personality"), this.font));
        defaultPersonalityButton = body.addChild(CycleButton.<String>builder(
                        s -> Component.literal(personalityLabel(s)), pDefaultPersonality)
                .withValues(personalityIds())
                .create(0, 0, W, FIELD_H, Component.literal("Default personality"),
                        (btn, val) -> pDefaultPersonality = val));
        defaultPersonalityButton.setTooltip(Tooltip.create(Component.literal(
                "Personality of newly summoned bots. Players change their own with /ai personality.")));

        body.addChild(new StringWidget(W, LABEL_H + 4, Component.literal(""), this.font));
        Button wikiBtn = Button.builder(Component.literal("§b📖 Open In-Game Wiki"),
                        b -> this.minecraft.setScreenAndShow(new AiManualScreen(this)))
                .bounds(0, 0, W, FIELD_H).build();
        wikiBtn.setTooltip(Tooltip.create(Component.literal(
                "Browse the full Blockpal reference: Quick Start, commands, personalities, settings, skins.")));
        body.addChild(wikiBtn);
    }

    private static java.util.List<String> personalityIds() {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (Personality p : Personality.values()) ids.add(p.id());
        return ids;
    }

    private static String personalityLabel(String id) {
        Personality p = Personality.byId(id);
        return p != null ? p.display() : id;
    }

    private void buildBehaviorTab(LinearLayout body) {
        header(body, "How it behaves");
        presetButton = body.addChild(CycleButton.<String>builder(s -> Component.literal(presetLabel(s)), pPreset)
                .withValues("normal", "opus", "potato")
                .create(0, 0, W, FIELD_H, Component.literal("Preset"), (btn, val) -> applyPreset(val)));
        presetButton.setTooltip(Tooltip.create(Component.literal("Performance preset — fills temperature, tokens and the developer settings in one go.")));
        listenButton = bodyToggle(body, "Chat listening", pListen, "React to things you say in chat without using a slash command.");
        activeButton = bodyToggle(body, "Active analysis", pActive, "Use the LLM to judge every nearby message — most helpful, most API calls.");
        commandsButton = bodyToggle(body, "Allow commands", pCommands, "Let the assistant run /setblock, /fill, /give, etc. as part of a plan.");
        cmdLevelSlider = bodySlider(body, "Command perm level", 0, 4, pCmdLevel, true, "Permission tier for those commands (2 = command-block tier).");
        sneakButton = bodyToggle(body, "Sneak-click opens menu", pSneakMenu, "When off, sneak-right-click just toggles follow/stay; the menu is still on /ai menu.");
        allowCustomButton = bodyToggle(body, "Allow custom personalities", pAllowCustom, "Let players give their bot a free-text personality (AI-checked to be family-friendly). Off = built-ins only.");
        allowPossessionButton = bodyToggle(body, "Allow possession mode", pAllowPossession, "Let players hand their own character to their nearby companion (/ai possess), which then drives them from typed instructions.");
        allowVoiceButton = bodyToggle(body, "Allow agent voice", pAllowVoice, "Let companions speak out loud (privately, to their owner and shared players) and accept push-to-talk voice input.");

        // ── how a companion thinks and stays alive ──
        header(body, "How companions think");
        logicModeButton = body.addChild(CycleButton.<String>builder(
                        Component::literal,
                        logicLabelOf(pLogicMode))
                .withValues(java.util.List.of(LOGIC_CODE, LOGIC_PLAN, LOGIC_PVT))
                .create(0, 0, W, FIELD_H, Component.literal("Thinking style"),
                        (btn, val) -> pLogicMode = logicIdOf(val)));
        logicModeButton.setTooltip(Tooltip.create(Component.literal(
                "\"Look and write code\" renders what the bot can see, sends the picture to the AI, and "
                        + "runs the little script it writes — everything it does, a player could do. "
                        + "\"Classic action plan\" is the older JSON planner: faster and more precise, but it "
                        + "can act on things the bot never actually saw.")));
        visionButton = bodyToggle(body, "Send pictures to the AI", pVision,
                "Render a small image from the bot's eyes and send it with each thought. Off = the written "
                        + "description only — cheaper, and needed for models that can't see images.");
        survivalBrainButton = bodyToggle(body, "Live on its own", pSurvivalBrain,
                "Keep-alive reflexes that need no AI at all: eat when hurt, swim up when drowning, get out "
                        + "of fires, unstick itself, and walk back to you when left behind.");
        creativeWarnButton = bodyToggle(body, "Warn me in creative mode", pCreativeWarning,
                "Companions never teleport — they walk. This warns you the first time you go creative with "
                        + "one out, since you can fly away from it in seconds.");

        preferSurvivalButton = bodyToggle(body, "Do things by hand", pPreferSurvival,
                "Build, mine and gather like a survival player instead of reaching for commands. Off lets "
                        + "the AI lean on /fill, /setblock and /give for speed.");
        humanizeButton = bodyToggle(body, "Human-like pauses", pHumanize,
                "Small randomised reaction delays before picking things up and between steps, so it "
                        + "doesn't act with inhuman, frame-perfect speed. Off = instant.");

        // ── what the bot's eyes are like ──
        header(body, "Its eyesight");
        body.addChild(new StringWidget(W, LABEL_H, Component.literal(
                "§7Each look costs one ray per pixel — bigger is sharper but slower."), this.font));
        visionWidthSlider = bodySlider(body, "Picture width", 32, 160, pVisionWidth, true,
                "Width in pixels of the image rendered from the bot's eyes (default 80).");
        visionHeightSlider = bodySlider(body, "Picture height", 18, 90, pVisionHeight, true,
                "Height in pixels of that image (default 45).");
        visionRangeSlider = bodySlider(body, "How far it can see", 8, 64, pVisionRange, true,
                "View distance in blocks (default 48). Nothing beyond this is in the picture at all.");
        scriptTicksSlider = bodySlider(body, "Script time limit (ticks)", 100, 6000, pScriptMaxTicks, true,
                "How long one AI-written script may run before it's stopped. 20 ticks = 1 second; "
                        + "1200 (a minute) by default.");

        // ── how quickly it reacts ──
        header(body, "How fast it acts");
        body.addChild(new StringWidget(W, LABEL_H, Component.literal(
                "§7One setting for every pause the mod adds — step delays, head-turn speed,"), this.font));
        body.addChild(new StringWidget(W, LABEL_H, Component.literal(
                "§7how often it re-thinks, and how much script it runs per tick."), this.font));
        reactionSpeedButton = body.addChild(CycleButton.<String>builder(
                        Component::literal, tempoLabelOf(pReactionSpeed))
                .withValues(java.util.List.of(TEMPO_INSTANT, TEMPO_FAST, TEMPO_HUMAN))
                .create(0, 0, W, FIELD_H, Component.literal("Reaction speed"),
                        (btn, val) -> pReactionSpeed = tempoIdOf(val)));
        reactionSpeedButton.setTooltip(Tooltip.create(Component.literal(
                "Fast is the default: snappy, but it still turns its head believably. Instant removes "
                        + "every invented pause and mines at double speed — quickest, least lifelike. "
                        + "Human is the older, more deliberate feel.")));

        // ── fighting ──
        header(body, "Fighting");
        combatSkillButton = body.addChild(CycleButton.<String>builder(
                        Component::literal, skillLabelOf(pCombatSkill))
                .withValues(java.util.List.of(SKILL_BASIC, SKILL_SKILLED, SKILL_EXPERT))
                .create(0, 0, W, FIELD_H, Component.literal("Combat skill"),
                        (btn, val) -> pCombatSkill = skillIdOf(val)));
        combatSkillButton.setTooltip(Tooltip.create(Component.literal(
                "Basic swings when in range. Skilled holds a distance, circles, raises a shield and "
                        + "backs off when hurt. Expert adds crit timing and bow work.")));
        allowPvpButton = bodyToggle(body, "May fight players", pAllowPvp,
                "OFF by default. Even when on, a companion only fights someone who attacked it or its "
                        + "owner in the last ten seconds, or who its owner named with /ai attack — and never "
                        + "its owner or anyone they trust. A server with PvP disabled overrides this anyway.");

        // ── learning by watching ──
        header(body, "Learning by watching (PVT)");
        body.addChild(new StringWidget(W, LABEL_H, Component.literal(
                pvtHasPolicy ? "§a✔ A trained policy is loaded."
                             : "§7No policy trained yet — /ai pvt watch on, play, /ai pvt train."), this.font));
        pvtEnabledButton = bodyToggle(body, "Enable PVT", pPvtEnabled,
                "The whole pre-video-training layer: recording play, training a policy from it, and letting "
                        + "companions be driven by what they learned. Recording is always opt-in per player.");
        pvtAutoRecordButton = bodyToggle(body, "Record opted-in players on join", pPvtAutoRecord,
                "Start recording automatically when somebody who has already said yes (/ai pvt watch on) "
                        + "joins. This never opts anybody in by itself.");
        pvtConfidenceSlider = bodySlider(body, "Confidence needed to drive (%)", 0, 100,
                (int) Math.round(pPvtConfidence * 100), true,
                "How sure the learned policy must be before it takes the controls. Below this it hands the "
                        + "tick back to the thinking brain. 30% by default.");
        pvtHiddenSlider = bodySlider(body, "Network width", 32, 512, pPvtHiddenSize, true,
                "Neurons per hidden layer. Wider learns more nuance and costs more per tick to run.");
        pvtEpochsSlider = bodySlider(body, "Training passes", 1, 200, pPvtEpochs, true,
                "How many times training goes over the recordings. It stops early anyway once the "
                        + "held-out score stops improving.");
        pvtLearningRateSlider = bodySlider(body, "Learning rate (÷10000)", 1, 1000,
                (int) Math.round(pPvtLearningRate * 10000), true,
                "Adam step size — 20 here means 0.002, the default. Larger learns faster and less stably.");
        pvtFramesSlider = bodySlider(body, "Frames kept (thousands)", 1, 2000,
                Math.max(1, pPvtMaxFrames / 1000), true,
                "How much recorded play to keep on disk. A frame is about 300 bytes, so 200 (thousand) is "
                        + "roughly 60 MB and three hours of play. The oldest sessions are dropped first.");

        // ── the Growth village game ──
        header(body, "Growth village");
        villageStartSlider = bodySlider(body, "Villagers at the start", 1, 20, pVillageStart, true,
                "How many AI villagers a fresh /village start begins with.");
        villageTargetSlider = bodySlider(body, "Population to \"as big as ever\"", 2, 100, pVillageTarget, true,
                "Once the village peaks at this size you may concede with /village surrender.");

        debugButton = bodyToggle(body, "Debug logging", pDebug, "Verbose logging to the game log for troubleshooting.");
    }

    /** Labels for the thinking-style cycler (mapped back to the stored id). */
    private static final String LOGIC_CODE = "Look at the world and write code";
    private static final String LOGIC_PLAN = "Classic action plan (JSON)";
    private static final String LOGIC_PVT = "Act on what it learned by watching";

    private static String logicLabelOf(String id) {
        if ("plan".equalsIgnoreCase(id)) return LOGIC_PLAN;
        if ("pvt".equalsIgnoreCase(id)) return LOGIC_PVT;
        return LOGIC_CODE;
    }

    private static String logicIdOf(String label) {
        if (LOGIC_PLAN.equals(label)) return "plan";
        if (LOGIC_PVT.equals(label)) return "pvt";
        return "code";
    }

    // ── labels for the speed and combat cyclers ──
    private static final String TEMPO_INSTANT = "Instant — no waiting at all";
    private static final String TEMPO_FAST = "Fast — snappy but believable";
    private static final String TEMPO_HUMAN = "Human — deliberate reactions";

    private static String tempoLabelOf(String id) {
        if ("instant".equalsIgnoreCase(id)) return TEMPO_INSTANT;
        if ("human".equalsIgnoreCase(id)) return TEMPO_HUMAN;
        return TEMPO_FAST;
    }

    private static String tempoIdOf(String label) {
        if (TEMPO_INSTANT.equals(label)) return "instant";
        if (TEMPO_HUMAN.equals(label)) return "human";
        return "fast";
    }

    private static final String SKILL_BASIC = "Basic — swing when in range";
    private static final String SKILL_SKILLED = "Skilled — circle, shield, disengage";
    private static final String SKILL_EXPERT = "Expert — crits, bow, potions";

    private static String skillLabelOf(String id) {
        if ("basic".equalsIgnoreCase(id)) return SKILL_BASIC;
        if ("expert".equalsIgnoreCase(id)) return SKILL_EXPERT;
        return SKILL_SKILLED;
    }

    private static String skillIdOf(String label) {
        if (SKILL_BASIC.equals(label)) return "basic";
        if (SKILL_EXPERT.equals(label)) return "expert";
        return "skilled";
    }

    private void buildAiTab(LinearLayout body) {
        // ── the ONE connection ──
        // Blockpal used to let a key, Player2, Ollama and the free service all be
        // "enabled" at once, resolved by a hidden priority order. Nobody could tell which
        // one was answering. Now it's a single picker, and choosing one turns the rest off.
        header(body, "How this server's bots think");
        body.addChild(new StringWidget(W, LABEL_H, Component.literal(
                "§7Exactly one connection runs at a time."), this.font));
        connectionButton = body.addChild(CycleButton.<String>builder(
                        s -> Component.literal("AI: " + s),
                        connectionLabelOf(pConnection))
                .withValues(com.milkdromeda.blockpal.ai.AiConnection.displayValues())
                .create(0, 0, W, FIELD_H, Component.literal("AI connection"),
                        (btn, val) -> { pConnection = connectionIdOf(val); rebuildWidgets(); }));
        connectionButton.setTooltip(Tooltip.create(Component.literal(
                "Which single AI drives your companions. \"MCP server\" is the easiest: point Claude, "
                        + "ChatGPT, Grok or Gemini at your world with /ai mcp — no key stored in the game. "
                        + "Picking one here switches the others off.")));
        body.addChild(new StringWidget(W, LABEL_H, connectionBlurb(), this.font));

        com.milkdromeda.blockpal.ai.AiConnection chosen =
                com.milkdromeda.blockpal.ai.AiConnection.byId(pConnection);
        if (chosen == null) chosen = com.milkdromeda.blockpal.ai.AiConnection.FREE;

        if (chosen == com.milkdromeda.blockpal.ai.AiConnection.MCP) buildMcpSection(body);

        header(body, "Language model & API");
        // One-click provider presets: pick HuggingFace / ChatGPT / Claude / Gemini /
        // Grok and the API URL + a matching default model are filled in for you (and
        // ChatGPT's public demo key is pre-filled). "Custom" = a URL that matches no
        // preset — chosen automatically when you hand-edit the API URL below.
        body.addChild(new StringWidget(W, LABEL_H, Component.literal("AI provider"), this.font));
        providerButton = body.addChild(CycleButton.<String>builder(
                        s -> Component.literal("Provider: " + s),
                        com.milkdromeda.blockpal.ai.ProviderPreset.labelForUrl(pApiUrl))
                .withValues(com.milkdromeda.blockpal.ai.ProviderPreset.displayValues())
                .create(0, 0, W, FIELD_H, Component.literal("AI provider"),
                        (btn, val) -> applyProvider(val)));
        providerButton.setTooltip(Tooltip.create(Component.literal(
                "Quick-switch which AI you use — fills the API URL and a matching default model below. "
                        + "ChatGPT / Claude / Gemini / Grok need that provider's own API key; HuggingFace uses yours. "
                        + "\"Custom\" is any other OpenAI-compatible endpoint — just edit the URL yourself.")));
        modelBox = bodyBox(body, "Model", pModel, 128, "Model id sent to the API (e.g. mistralai/Mistral-7B-Instruct-v0.2).");
        apiUrlBox = bodyBox(body, "API URL", pApiUrl, 256, "Any OpenAI-compatible chat-completions endpoint (HuggingFace, OpenAI, Ollama, LM Studio…).");
        // Seeded from the DRAFT (pToken), not the saved key: pToken only ever holds
        // text typed-but-not-yet-sent, so a key you pasted survives switching to
        // another tab and back instead of silently vanishing before Save. A key
        // that's already saved is never echoed back here (privacy) — the box then
        // starts empty and blank means "keep it".
        //
        // Masked by default (dots, read-only) like a password field; there's no
        // vanilla EditBox hook to substitute masking characters while still typing
        // normally, so "Show key" below switches the box to editable plaintext —
        // that's when you actually type/paste, then toggle back off to re-mask.
        tokenBox = bodyBox(body, "API token", tokenVisible ? pToken : maskOf(pToken), 256,
                "Your API key. Shown masked as dots by default — press \"Show key\" below to reveal and "
                        + "type/paste it. Never shown back once saved — leave blank to keep the current one.");
        tokenBox.setHint(Component.literal(tokenSet ? "set - blank keeps it" : "not set"));
        tokenBox.setEditable(tokenVisible);
        // The key is never echoed back, so the emptying box needs an explicit "it IS
        // saved" signal or players think Apply lost it.
        tokenStatus = body.addChild(new StringWidget(W, LABEL_H, tokenStatusText(), this.font));
        tokenShowButton = body.addChild(CycleButton.onOffBuilder(tokenVisible)
                .create(0, 0, W, FIELD_H, Component.literal("Show key"), (btn, val) -> {
                    // Leaving plaintext mode: capture whatever's typed before re-masking it.
                    if (!val && tokenBox != null) pToken = tokenBox.getValue();
                    tokenVisible = val;
                    if (tokenBox != null) {
                        tokenBox.setEditable(tokenVisible);
                        tokenBox.setValue(tokenVisible ? pToken : maskOf(pToken));
                    }
                }));
        tokenShowButton.setTooltip(Tooltip.create(Component.literal(
                "Reveal and edit the key above (masked and read-only by default, like a password field). "
                        + "Only affects what's currently typed here — an already-saved key is never sent to this menu.")));
        tempSlider = bodySlider(body, "Temperature", 0.0, 2.0, pTemp, false, "Creativity of the model — lower is more focused, higher is more varied.");
        maxTokensSlider = bodySlider(body, "Max tokens", 32, 2048, pMaxTokens, true, "Upper bound on the length of each plan the model returns.");

        // ── endpoints for the local/easy providers ──
        // These are plain settings now, not switches: whether they're USED is decided by
        // the one connection picker at the top of this tab.
        header(body, "Local & easy AI endpoints");
        body.addChild(new StringWidget(W, LABEL_H, activeProviderText(), this.font));

        player2ModelBox = bodyBox(body, "Player2 model", pPlayer2Model, 128,
                "Model sent to Player2 online (default gpt-oss-120b). Ignored for the local app.");
        player2UrlBox = bodyBox(body, "Player2 online URL", pPlayer2Url, 256,
                "Player2 cloud chat-completions endpoint (used when PLAYER2_KEY is set).");
        ollamaModelBox = bodyBox(body, "Ollama model", pOllamaModel, 128,
                "A model you've pulled, e.g. llama3.2, qwen2.5, phi3, gemma2:2b.");
        ollamaUrlBox = bodyBox(body, "Ollama URL", pOllamaUrl, 256,
                "Ollama's OpenAI-compatible endpoint (default http://localhost:11434/v1/chat/completions).");
        player2LocalUrlBox = bodyBox(body, "Player2 local app URL", pPlayer2LocalUrl, 256,
                "Where the Player2 desktop app listens (default http://localhost:4315/v1/chat/completions). "
                        + "Used when no PLAYER2_KEY is set.");
        freeModelBox = bodyBox(body, "Free service model", pFreeModel, 128,
                "Model name sent to the free keyless service.");
        freeApiUrlBox = bodyBox(body, "Free service URL", pFreeApiUrl, 256,
                "The keyless OpenAI-compatible endpoint used by the \"Free keyless service\" connection.");
    }

    /** The MCP block: what to connect, where, and how locked down it is. */
    private void buildMcpSection(LinearLayout body) {
        header(body, "MCP server");
        body.addChild(new StringWidget(W, LABEL_H, Component.literal(mcpRunning
                ? "§a▶ Listening — run §f/ai mcp§a in chat for the setup guide + token"
                : "§e▶ Not listening yet — save these settings, then run §f/ai mcp"), this.font));
        body.addChild(new StringWidget(W, LABEL_H, Component.literal(
                "§7Your AI app connects to the game; no key is stored here."), this.font));
        // A text box, not a slider: dragging across 64,000 values to land on 8000 is
        // nobody's idea of a good time.
        mcpPortBox = bodyBox(body, "MCP port", String.valueOf(pMcpPort), 5,
                "TCP port the MCP server listens on. 8000 by default (the address is then http://localhost:8000/blockpal) — change it only if "
                        + "something else already uses that port.");
        mcpTokenButton = bodyToggle(body, "Require access token", pMcpRequireToken,
                "Clients must send Authorization: Bearer <token>. Leave ON — the token is what "
                        + "stops anyone else driving your companions. See it with /ai mcp token.");
        mcpRemoteButton = bodyToggle(body, "Allow connections from other machines", pMcpRemote,
                "OFF (safest) listens on localhost only — all a desktop AI app on this PC needs. "
                        + "Turn ON only for cloud AI (ChatGPT, AI Studio) reaching in through a tunnel.");
        body.addChild(new StringWidget(W, LABEL_H, Component.literal(
                "§7Desktop apps (Claude Desktop, Gemini CLI) need nothing else."), this.font));
        body.addChild(new StringWidget(W, LABEL_H, Component.literal(
                "§7Cloud apps (ChatGPT, AI Studio) also need a tunnel."), this.font));
        // The guide holds the access token, so the server re-checks admin rights before
        // sending it — this button only asks.
        Button guide = Button.builder(
                        Component.literal("Open setup guide  (address + token)"),
                        b -> ClientPlayNetworking.send(
                                new com.milkdromeda.blockpal.network.McpInfoRequestPayload()))
                .bounds(0, 0, W, FIELD_H).build();
        guide.setTooltip(Tooltip.create(Component.literal(
                "Step-by-step setup for Claude, ChatGPT, Grok and Gemini, with this world's address "
                        + "and access token filled in and copy buttons for each. Same as /ai mcp.")));
        body.addChild(guide);
    }

    /** A typed port, or the previous value when it isn't a usable one. */
    private static int parsePort(String typed, int fallback) {
        try {
            int port = Integer.parseInt(typed.trim());
            return (port >= 1024 && port <= 65535) ? port : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Human label for a connection id, and back again — the cycler shows display names. */
    private static String connectionLabelOf(String id) {
        com.milkdromeda.blockpal.ai.AiConnection c = com.milkdromeda.blockpal.ai.AiConnection.byId(id);
        return (c == null ? com.milkdromeda.blockpal.ai.AiConnection.FREE : c).display();
    }

    private static String connectionIdOf(String display) {
        com.milkdromeda.blockpal.ai.AiConnection c =
                com.milkdromeda.blockpal.ai.AiConnection.byDisplay(display);
        return (c == null ? com.milkdromeda.blockpal.ai.AiConnection.FREE : c).id();
    }

    private Component connectionBlurb() {
        com.milkdromeda.blockpal.ai.AiConnection c =
                com.milkdromeda.blockpal.ai.AiConnection.byId(pConnection);
        if (c == null) c = com.milkdromeda.blockpal.ai.AiConnection.FREE;
        return Component.literal("§7" + c.blurb());
    }

    /** One line naming exactly what will answer, given the chosen connection. */
    private Component activeProviderText() {
        com.milkdromeda.blockpal.ai.AiConnection c =
                com.milkdromeda.blockpal.ai.AiConnection.byId(pConnection);
        if (c == null) c = com.milkdromeda.blockpal.ai.AiConnection.FREE;
        String who; String color = "§b";
        switch (c) {
            case API_KEY -> {
                boolean have = tokenSet || !pToken.isBlank();
                who = have ? "your API key (" + (pModel == null || pModel.isBlank() ? "default model" : pModel) + ")"
                        : "nothing yet — the connection is \"my own API key\" but no key is set";
                color = have ? "§a" : "§c";
            }
            case PLAYER2 -> who = pPlayer2KeySet ? "Player2 (online · " + pPlayer2Model + ")" : "Player2 (local app)";
            case OLLAMA -> who = "Ollama (" + (pOllamaModel == null || pOllamaModel.isBlank() ? "local" : pOllamaModel) + ")";
            case FREE -> who = "the free built-in AI";
            case MCP -> who = "whatever AI app you connect over MCP (/ai mcp)";
            default -> { who = "no AI — bots still survive on their own"; color = "§7"; }
        }
        return Component.literal(color + "▶ Bots will use: " + who);
    }

    private void buildCombatTab(LinearLayout body) {
        header(body, "Combat & movement");
        followSlider = bodySlider(body, "Follow distance", 1, 32, pFollow, false, "How close the assistant stays when following you.");
        guardSlider = bodySlider(body, "Guard radius", 4, 64, pGuard, false, "How far it ranges from its post while guarding.");
    }

    private void buildDeveloperTab(LinearLayout body) {
        header(body, "Advanced — handle with care");
        body.addChild(new StringWidget(W, LABEL_H,
                Component.literal("⚠  These can cause lag or crash the game. See developer.md.")
                        .withStyle(s -> s.withColor(0xFF5555)), this.font));
        actionDelaySlider = bodySlider(body, "Action tick delay (0=every tick!)", 0, 40, pActionDelay, true,
                "Ticks between plan steps. Very low values run actions every tick and can lag the server.");
        maxTaskSlider = bodySlider(body, "Task watchdog sec (0=disabled!)", 0, 600, pMaxTask, true,
                "Auto-stops a task after this many seconds. 0 lets a runaway task run forever.");
        fleeSlider = bodySlider(body, "Flee health % (0=never flees!)", 0.0, 1.0, pFlee, false,
                "Retreat when health drops below this fraction. 0 means it never flees.");
    }

    // ── preset helpers ────────────────────────────────────────────────────────────

    private static String presetLabel(String preset) {
        return switch (preset) {
            case "opus"   -> "Preset: Opus  ***";
            case "potato" -> "Preset: Potato  (low)";
            default       -> "Preset: Normal";
        };
    }

    private void applyPreset(String preset) {
        pPreset = preset;
        switch (preset) {
            case "opus" -> { pListen = true; pActive = true; pTemp = 0.8; pMaxTokens = 1024;
                             pActionDelay = 2; pMaxTask = 600; pFlee = 0.2; }
            case "potato" -> { pListen = true; pActive = false; pTemp = 0.5; pMaxTokens = 256;
                               pActionDelay = 20; pMaxTask = 120; pFlee = 0.25; }
            default -> { pListen = true; pActive = true; pTemp = 0.7; pMaxTokens = 512;
                         pActionDelay = 8; pMaxTask = 300; pFlee = 0.25; }
        }
        // Reflect into whatever widgets are currently on screen.
        if (listenButton != null) listenButton.setValue(pListen);
        if (activeButton != null) activeButton.setValue(pActive);
        if (tempSlider != null) tempSlider.setCurrent(pTemp);
        if (maxTokensSlider != null) maxTokensSlider.setCurrent(pMaxTokens);
        if (actionDelaySlider != null) actionDelaySlider.setCurrent(pActionDelay);
        if (maxTaskSlider != null) maxTaskSlider.setCurrent(pMaxTask);
        if (fleeSlider != null) fleeSlider.setCurrent(pFlee);
    }

    /**
     * Applies a provider preset: fills the API URL + a matching default model, and —
     * when the preset carries one (ChatGPT's public demo key) — pre-fills the API key
     * into the draft so Save sends it. "Custom" leaves every field as the player set it.
     */
    private void applyProvider(String display) {
        com.milkdromeda.blockpal.ai.ProviderPreset p =
                com.milkdromeda.blockpal.ai.ProviderPreset.byDisplay(display);
        if (p == null) return;   // "Custom" — don't touch what the player typed
        pApiUrl = p.url();
        pModel = p.model();
        if (apiUrlBox != null) apiUrlBox.setValue(pApiUrl);
        if (modelBox != null) modelBox.setValue(pModel);
        if (p.hasDefaultKey()) {
            // Reveal + fill the key so it's obviously applied (a Save then persists it).
            pToken = p.defaultKey();
            tokenVisible = true;
            if (tokenBox != null) {
                tokenBox.setEditable(true);
                tokenBox.setValue(pToken);
            }
            if (tokenShowButton != null) tokenShowButton.setValue(true);
            if (tokenStatus != null) tokenStatus.setMessage(tokenStatusText());
        }
    }

    private void openSkinsFolder() {
        try {
            Files.createDirectories(RuntimeSkins.SKIN_DIR);
        } catch (IOException ignored) {
            // openPath below still works if the folder already exists.
        }
        Util.getPlatform().openPath(RuntimeSkins.SKIN_DIR);
    }

    // ── body widget factories ───────────────────────────────────────────────────

    private void header(LinearLayout body, String text) {
        body.addChild(new StringWidget(W, LABEL_H + 3, TechTheme.header(text), this.font));
    }

    private EditBox bodyBox(LinearLayout body, String label, String value, int maxLen, String tooltip) {
        StringWidget lbl = new StringWidget(W, LABEL_H, Component.literal(label), this.font);
        body.addChild(lbl);
        EditBox box = new EditBox(this.font, 0, 0, W, FIELD_H, Component.literal(label));
        box.setMaxLength(maxLen);
        if (value != null) box.setValue(value);
        if (tooltip != null) box.setTooltip(Tooltip.create(Component.literal(tooltip)));
        return body.addChild(box);
    }

    private CycleButton<Boolean> bodyToggle(LinearLayout body, String label, boolean value, String tooltip) {
        CycleButton<Boolean> btn = body.addChild(CycleButton.onOffBuilder(value)
                .create(0, 0, W, FIELD_H, Component.literal(label)));
        if (tooltip != null) btn.setTooltip(Tooltip.create(Component.literal(tooltip)));
        return btn;
    }

    private OptionSlider bodySlider(LinearLayout body, String label, double min, double max,
                                    double value, boolean integer, String tooltip) {
        OptionSlider s = body.addChild(new OptionSlider(0, 0, W, FIELD_H, label, min, max, value, integer));
        if (tooltip != null) s.setTooltip(Tooltip.create(Component.literal(tooltip)));
        return s;
    }

    // ── data ────────────────────────────────────────────────────────────────────

    /** Read whatever widgets are on screen back into the pending draft. */
    private void capture() {
        if (nameBox != null) pName = nameBox.getValue();
        if (skinBox != null) pSkin = skinBox.getValue();
        if (modelBox != null) pModel = modelBox.getValue();
        if (apiUrlBox != null) pApiUrl = apiUrlBox.getValue();
        // Only read the token box while it's showing real plaintext (tokenVisible) —
        // when masked it holds dots, which must never overwrite the real draft.
        if (tokenBox != null && tokenVisible) pToken = tokenBox.getValue();
        if (listenButton != null) pListen = listenButton.getValue();
        if (activeButton != null) pActive = activeButton.getValue();
        if (commandsButton != null) pCommands = commandsButton.getValue();
        if (debugButton != null) pDebug = debugButton.getValue();
        if (sneakButton != null) pSneakMenu = sneakButton.getValue();
        if (allowCustomButton != null) pAllowCustom = allowCustomButton.getValue();
        if (allowPossessionButton != null) pAllowPossession = allowPossessionButton.getValue();
        if (allowVoiceButton != null) pAllowVoice = allowVoiceButton.getValue();
        if (freeFallbackButton != null) pFreeFallback = freeFallbackButton.getValue();
        if (ollamaEnabledButton != null) pOllamaEnabled = ollamaEnabledButton.getValue();
        if (ollamaUrlBox != null) pOllamaUrl = ollamaUrlBox.getValue();
        if (ollamaModelBox != null) pOllamaModel = ollamaModelBox.getValue();
        if (player2EnabledButton != null) pPlayer2Enabled = player2EnabledButton.getValue();
        if (player2UrlBox != null) pPlayer2Url = player2UrlBox.getValue();
        if (player2ModelBox != null) pPlayer2Model = player2ModelBox.getValue();
        if (connectionButton != null) pConnection = connectionIdOf(connectionButton.getValue());
        if (logicModeButton != null) pLogicMode = logicModeButton.getValue().startsWith("Look") ? "code" : "plan";
        if (visionButton != null) pVision = visionButton.getValue();
        if (survivalBrainButton != null) pSurvivalBrain = survivalBrainButton.getValue();
        if (creativeWarnButton != null) pCreativeWarning = creativeWarnButton.getValue();
        if (mcpRemoteButton != null) pMcpRemote = mcpRemoteButton.getValue();
        if (mcpTokenButton != null) pMcpRequireToken = mcpTokenButton.getValue();
        if (mcpPortBox != null) pMcpPort = parsePort(mcpPortBox.getValue(), pMcpPort);
        if (visionWidthSlider != null) pVisionWidth = (int) Math.round(visionWidthSlider.current());
        if (visionHeightSlider != null) pVisionHeight = (int) Math.round(visionHeightSlider.current());
        if (visionRangeSlider != null) pVisionRange = (int) Math.round(visionRangeSlider.current());
        if (scriptTicksSlider != null) pScriptMaxTicks = (int) Math.round(scriptTicksSlider.current());
        if (preferSurvivalButton != null) pPreferSurvival = preferSurvivalButton.getValue();
        if (humanizeButton != null) pHumanize = humanizeButton.getValue();
        if (freeApiUrlBox != null) pFreeApiUrl = freeApiUrlBox.getValue();
        if (freeModelBox != null) pFreeModel = freeModelBox.getValue();
        if (player2LocalUrlBox != null) pPlayer2LocalUrl = player2LocalUrlBox.getValue();
        if (villageTargetSlider != null) pVillageTarget = (int) Math.round(villageTargetSlider.current());
        if (villageStartSlider != null) pVillageStart = (int) Math.round(villageStartSlider.current());
        if (presetButton != null) pPreset = presetButton.getValue();
        if (defaultPersonalityButton != null) pDefaultPersonality = defaultPersonalityButton.getValue();
        if (tempSlider != null) pTemp = tempSlider.current();
        if (maxTokensSlider != null) pMaxTokens = (int) Math.round(maxTokensSlider.current());
        if (followSlider != null) pFollow = followSlider.current();
        if (guardSlider != null) pGuard = guardSlider.current();
        if (cmdLevelSlider != null) pCmdLevel = (int) Math.round(cmdLevelSlider.current());
        if (actionDelaySlider != null) pActionDelay = (int) Math.round(actionDelaySlider.current());
        if (maxTaskSlider != null) pMaxTask = (int) Math.round(maxTaskSlider.current());
        if (fleeSlider != null) pFlee = fleeSlider.current();
        if (reactionSpeedButton != null) pReactionSpeed = tempoIdOf(reactionSpeedButton.getValue());
        if (combatSkillButton != null) pCombatSkill = skillIdOf(combatSkillButton.getValue());
        if (allowPvpButton != null) pAllowPvp = allowPvpButton.getValue();
        if (pvtEnabledButton != null) pPvtEnabled = pvtEnabledButton.getValue();
        if (pvtAutoRecordButton != null) pPvtAutoRecord = pvtAutoRecordButton.getValue();
        if (pvtHiddenSlider != null) pPvtHiddenSize = (int) Math.round(pvtHiddenSlider.current());
        if (pvtEpochsSlider != null) pPvtEpochs = (int) Math.round(pvtEpochsSlider.current());
        if (pvtFramesSlider != null) pPvtMaxFrames = (int) Math.round(pvtFramesSlider.current()) * 1000;
        if (pvtLearningRateSlider != null) pPvtLearningRate = pvtLearningRateSlider.current() / 10000.0;
        if (pvtConfidenceSlider != null) pPvtConfidence = pvtConfidenceSlider.current() / 100.0;
    }

    private ConfigData buildData() {
        capture();
        return new ConfigData(
                pListen, pActive, pDebug, pName, pToken, tokenSet, pModel, pApiUrl,
                pTemp, pMaxTokens, pFollow, pGuard, pCommands, pCmdLevel, pSkin,
                pActionDelay, pMaxTask, pFlee, pPreset, pSneakMenu,
                pDefaultPersonality, pAllowCustom, pAllowPossession, pFreeFallback, pAllowVoice,
                pOllamaEnabled, pOllamaUrl, pOllamaModel,
                pPlayer2Enabled, pPlayer2Url, pPlayer2Model, pPlayer2KeySet,
                pConnection, pLogicMode, pVision, pSurvivalBrain, pCreativeWarning,
                pMcpPort, pMcpRemote, pMcpRequireToken, mcpRunning,
                pVisionWidth, pVisionHeight, pVisionRange, pScriptMaxTicks,
                pPreferSurvival, pHumanize, pFreeApiUrl, pFreeModel, pPlayer2LocalUrl,
                pVillageTarget, pVillageStart,
                pReactionSpeed, pCombatSkill, pAllowPvp,
                pPvtEnabled, pPvtAutoRecord, pPvtHiddenSize, pPvtEpochs,
                pPvtLearningRate, pPvtMaxFrames, pPvtConfidence, pvtHasPolicy);
    }

    private Component tokenStatusText() {
        if (!pToken.isBlank()) {
            return Component.literal("§e➤ Key typed but not saved yet — press Apply or Save");
        }
        if (tokenSet) {
            return Component.literal("§a✔ API key saved — hidden here for privacy; blank keeps it");
        }
        com.milkdromeda.blockpal.ai.AiConnection c =
                com.milkdromeda.blockpal.ai.AiConnection.byId(pConnection);
        if (c == com.milkdromeda.blockpal.ai.AiConnection.API_KEY) {
            return Component.literal("§cNo key set — this server's AI connection is \"my own API key\", "
                    + "so nothing can think until you add one");
        }
        return Component.literal("§7No key set — not needed: this server's AI connection is §f"
                + connectionLabelOf(pConnection));
    }

    private void sendCurrent() {
        ClientPlayNetworking.send(new ConfigUpdatePayload(buildData()));
        if (!pToken.isBlank()) {
            tokenSet = true;
            pToken = "";
            tokenVisible = false;
            if (tokenBox != null) {
                tokenBox.setValue("");
                tokenBox.setEditable(false);
                tokenBox.setHint(Component.literal("set - blank keeps it"));
            }
            if (tokenShowButton != null) tokenShowButton.setValue(false);
            if (tokenStatus != null) tokenStatus.setMessage(tokenStatusText());
        }
        baseline = buildData();
    }

    private boolean isDirty() {
        return !buildData().equals(baseline);
    }

    private void apply() {
        sendCurrent();
        appliedLabel.setMessage(SAVED_MSG);
        appliedFeedbackUntil = System.currentTimeMillis() + 1500;
    }

    private void saveAndClose() {
        sendCurrent();
        saveOnClose = false;
        onClose();
    }

    private void cancel() {
        saveOnClose = false;
        onClose();
    }

    @Override
    public void onClose() {
        if (saveOnClose && isDirty()) {
            sendCurrent();
        }
        super.onClose();
    }

    @Override
    public void tick() {
        super.tick();
        if (appliedFeedbackUntil != 0 && System.currentTimeMillis() >= appliedFeedbackUntil) {
            appliedFeedbackUntil = 0;
            appliedLabel.setMessage(Component.empty());
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        TechTheme.backdrop(g, this.width, this.height);
        // Wide enough for both the settings column and the 3-button action bar.
        TechTheme.panel(g, this.width / 2 - 172, 2, this.width / 2 + 172, this.height - 2);
        TechTheme.rule(g, this.width / 2 - 130, this.width / 2 + 130, 17);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
