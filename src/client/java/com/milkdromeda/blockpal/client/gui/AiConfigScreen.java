package com.milkdromeda.blockpal.client.gui;

import com.milkdromeda.blockpal.client.render.RuntimeSkins;
import com.milkdromeda.blockpal.network.ConfigData;
import com.milkdromeda.blockpal.network.ConfigUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
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
    private static final int TAB_Y = 22;     // tab bar
    private static final int TAB_H = 18;
    private static final int BODY_TOP = 46;  // first row of the scrollable body
    private static final int FOOTER = 28;
    private static final Component SAVED_MSG =
            Component.literal("Settings applied ✓").withStyle(s -> s.withColor(0x55FF55));

    private final ConfigData initial;
    private Tab tab = Tab.IDENTITY;

    // ── pending draft (all settings, independent of which tab is visible) ──
    private String pName, pSkin, pModel, pApiUrl, pToken = "";
    private boolean pListen, pActive, pCommands, pDebug, pSneakMenu;
    private double pTemp, pFollow, pGuard, pFlee;
    private int pMaxTokens, pCmdLevel, pActionDelay, pMaxTask;
    private String pPreset;
    private boolean tokenSet;

    // ── widgets for the current tab (null when not on that tab) ──
    private EditBox nameBox, skinBox, modelBox, apiUrlBox, tokenBox;
    private CycleButton<Boolean> listenButton, activeButton, commandsButton, debugButton, sneakButton;
    private CycleButton<String> presetButton;
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
        // Capture the as-loaded state once so dirty-tracking survives tab switches
        // (init() runs on every tab change, so we must NOT recompute it there).
        baseline = buildData();
    }

    @Override
    protected void init() {
        // Drop references from the previous tab so capture() never reads stale widgets.
        clearWidgetRefs();

        // -- pinned title --
        addRenderableWidget(new StringWidget(0, 6, this.width, 12, this.title, this.font));

        // -- pinned tab bar --
        buildTabBar();

        // -- scrollable body for the active tab --
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
        scroll.setX(this.width / 2 - (W + 12) / 2);
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
        listenButton = activeButton = commandsButton = debugButton = sneakButton = null;
        presetButton = null;
        tempSlider = maxTokensSlider = followSlider = guardSlider = cmdLevelSlider = null;
        actionDelaySlider = maxTaskSlider = fleeSlider = null;
    }

    // ── tab bar ─────────────────────────────────────────────────────────────────

    private void buildTabBar() {
        Tab[] tabs = Tab.values();
        int gap = 2;
        int barW = W + 12;
        int bw = (barW - gap * (tabs.length - 1)) / tabs.length;
        int x0 = this.width / 2 - barW / 2;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            boolean current = (t == tab);
            Component label = current
                    ? Component.literal(t.label).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                    : Component.literal(t.label);
            Button b = Button.builder(label, btn -> { if (tab != t) { capture(); tab = t; rebuildWidgets(); } })
                    .bounds(x0 + i * (bw + gap), TAB_Y, bw, TAB_H).build();
            b.active = !current;   // the current tab reads as "pressed"
            addRenderableWidget(b);
        }
    }

    // ── tab bodies ────────────────────────────────────────────────────────────────

    private void buildIdentityTab(LinearLayout body) {
        header(body, "§eWho your assistant is");
        nameBox = bodyBox(body, "Assistant name", pName, 32, "The name shown above the assistant and used when it speaks.");
        skinBox = bodyBox(body, "Default skin", pSkin, 64, "Skin for newly summoned assistants: a built-in, a namespace:path, or a PNG in the skins folder.");
        Button open = Button.builder(Component.literal("📁 Open skins folder"), b -> openSkinsFolder())
                .bounds(0, 0, W, FIELD_H).build();
        open.setTooltip(Tooltip.create(Component.literal("Opens config/blockpal/skins/ — drop a 64×64 PNG in, then set the skin by its file name.")));
        body.addChild(open);
    }

    private void buildBehaviorTab(LinearLayout body) {
        header(body, "§eHow it behaves");
        presetButton = body.addChild(CycleButton.<String>builder(s -> Component.literal(presetLabel(s)), pPreset)
                .withValues("normal", "opus", "potato")
                .create(0, 0, W, FIELD_H, Component.literal("Preset"), (btn, val) -> applyPreset(val)));
        presetButton.setTooltip(Tooltip.create(Component.literal("Performance preset — fills temperature, tokens and the developer settings in one go.")));
        listenButton = bodyToggle(body, "Chat listening", pListen, "React to things you say in chat without using a slash command.");
        activeButton = bodyToggle(body, "Active analysis", pActive, "Use the LLM to judge every nearby message — most helpful, most API calls.");
        commandsButton = bodyToggle(body, "Allow commands", pCommands, "Let the assistant run /setblock, /fill, /give, etc. as part of a plan.");
        cmdLevelSlider = bodySlider(body, "Command perm level", 0, 4, pCmdLevel, true, "Permission tier for those commands (2 = command-block tier).");
        sneakButton = bodyToggle(body, "Sneak-click opens menu", pSneakMenu, "When off, sneak-right-click just toggles follow/stay; the menu is still on /ai menu.");
        debugButton = bodyToggle(body, "Debug logging", pDebug, "Verbose logging to the game log for troubleshooting.");
    }

    private void buildAiTab(LinearLayout body) {
        header(body, "§eLanguage model & API");
        modelBox = bodyBox(body, "Model", pModel, 128, "Model id sent to the API (e.g. mistralai/Mistral-7B-Instruct-v0.2).");
        apiUrlBox = bodyBox(body, "API URL", pApiUrl, 256, "Any OpenAI-compatible chat-completions endpoint (HuggingFace, OpenAI, Ollama, LM Studio…).");
        tokenBox = bodyBox(body, "API token", "", 256, "Your API key. Never shown back for privacy — leave blank to keep the current one.");
        tokenBox.setHint(Component.literal(tokenSet ? "set - blank keeps it" : "not set"));
        tempSlider = bodySlider(body, "Temperature", 0.0, 2.0, pTemp, false, "Creativity of the model — lower is more focused, higher is more varied.");
        maxTokensSlider = bodySlider(body, "Max tokens", 32, 2048, pMaxTokens, true, "Upper bound on the length of each plan the model returns.");
    }

    private void buildCombatTab(LinearLayout body) {
        header(body, "§eCombat & movement");
        followSlider = bodySlider(body, "Follow distance", 1, 32, pFollow, false, "How close the assistant stays when following you.");
        guardSlider = bodySlider(body, "Guard radius", 4, 64, pGuard, false, "How far it ranges from its post while guarding.");
    }

    private void buildDeveloperTab(LinearLayout body) {
        header(body, "§eAdvanced — handle with care");
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
        body.addChild(new StringWidget(W, LABEL_H + 3, Component.literal(text), this.font));
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
        if (tokenBox != null) pToken = tokenBox.getValue();
        if (listenButton != null) pListen = listenButton.getValue();
        if (activeButton != null) pActive = activeButton.getValue();
        if (commandsButton != null) pCommands = commandsButton.getValue();
        if (debugButton != null) pDebug = debugButton.getValue();
        if (sneakButton != null) pSneakMenu = sneakButton.getValue();
        if (presetButton != null) pPreset = presetButton.getValue();
        if (tempSlider != null) pTemp = tempSlider.current();
        if (maxTokensSlider != null) pMaxTokens = (int) Math.round(maxTokensSlider.current());
        if (followSlider != null) pFollow = followSlider.current();
        if (guardSlider != null) pGuard = guardSlider.current();
        if (cmdLevelSlider != null) pCmdLevel = (int) Math.round(cmdLevelSlider.current());
        if (actionDelaySlider != null) pActionDelay = (int) Math.round(actionDelaySlider.current());
        if (maxTaskSlider != null) pMaxTask = (int) Math.round(maxTaskSlider.current());
        if (fleeSlider != null) pFlee = fleeSlider.current();
    }

    private ConfigData buildData() {
        capture();
        return new ConfigData(
                pListen, pActive, pDebug, pName, pToken, tokenSet, pModel, pApiUrl,
                pTemp, pMaxTokens, pFollow, pGuard, pCommands, pCmdLevel, pSkin,
                pActionDelay, pMaxTask, pFlee, pPreset, pSneakMenu);
    }

    private void sendCurrent() {
        ClientPlayNetworking.send(new ConfigUpdatePayload(buildData()));
        if (!pToken.isBlank()) {
            tokenSet = true;
            pToken = "";
            if (tokenBox != null) {
                tokenBox.setValue("");
                tokenBox.setHint(Component.literal("set - blank keeps it"));
            }
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
    public boolean isPauseScreen() {
        return false;
    }
}
