package com.milkdromeda.aiassistant.client.gui;

import com.milkdromeda.aiassistant.network.ConfigData;
import com.milkdromeda.aiassistant.network.ConfigUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A real settings menu for the AI assistant: toggles, text fields and sliders
 * laid out in two columns. Opened with {@code /ai menu}. Saving sends the values
 * to the server, so it works in both singleplayer and multiplayer.
 *
 * <p>A collapsible <b>Developer Mode</b> section at the bottom exposes low-level
 * settings (action tick delay, task watchdog, flee threshold) that can cause lag
 * or crashes if misused. Each field shows a brief inline warning.
 */
public class AiConfigScreen extends Screen {

    private static final int COL_W = 154;
    private static final int COL_GAP = 14;
    private static final int FIELD_H = 18;
    private static final int ROW = 22;
    private static final int BOX_ROW = 30;
    private static final int TOP = 32;
    private static final Component SAVED_MSG =
            Component.literal("Settings applied ✓").withStyle(s -> s.withColor(0x55FF55));

    private final ConfigData initial;

    // Standard widgets
    private CycleButton<Boolean> listenButton;
    private CycleButton<Boolean> activeButton;
    private CycleButton<Boolean> debugButton;
    private CycleButton<Boolean> commandsButton;
    private EditBox nameBox;
    private EditBox tokenBox;
    private EditBox apiUrlBox;
    private EditBox skinBox;
    private EditBox modelBox;
    private OptionSlider temperatureSlider;
    private OptionSlider maxTokensSlider;
    private OptionSlider followSlider;
    private OptionSlider guardSlider;
    private OptionSlider commandLevelSlider;

    // Developer mode widgets (null when dev mode is off)
    private OptionSlider actionTickDelaySlider;
    private OptionSlider maxTaskSecondsSlider;
    private OptionSlider fleeHealthSlider;

    /** Whether the developer section is currently expanded. Survives rebuilds. */
    private boolean devMode = false;

    private ConfigData baseline;
    private boolean tokenSet;
    private boolean saveOnClose = true;
    private int actionBarY;
    private StringWidget appliedLabel;
    private long appliedFeedbackUntil;

    public AiConfigScreen(ConfigData initial) {
        super(Component.literal("AI Assistant Settings"));
        this.initial = initial;
        this.tokenSet = initial.tokenSet();
    }

    @Override
    protected void init() {
        int totalW = COL_W * 2 + COL_GAP;
        int left = this.width / 2 - totalW / 2;
        int right = left + COL_W + COL_GAP;

        addRenderableOnly(new StringWidget(0, 6, this.width, 12, this.title, this.font));

        // ── left column: identity & connection ──
        int ly = TOP;
        nameBox = labeledBox(left, ly, "Assistant name", initial.defaultName(), 32);
        ly += BOX_ROW;
        skinBox = labeledBox(left, ly, "Default skin", initial.defaultSkin(), 64);
        ly += BOX_ROW;
        modelBox = labeledBox(left, ly, "Model", initial.model(), 128);
        ly += BOX_ROW;
        apiUrlBox = labeledBox(left, ly, "API URL", initial.apiUrl(), 256);
        ly += BOX_ROW;
        tokenBox = labeledBox(left, ly, "API token", "", 256);
        tokenBox.setHint(Component.literal(initial.tokenSet() ? "set — blank keeps it" : "not set"));
        ly += BOX_ROW;
        debugButton = addToggle(left, ly, "Debug logging", initial.debugLogging());
        ly += ROW;

        // ── right column: behaviour toggles + sliders ──
        int ry = TOP;
        listenButton = addToggle(right, ry, "Chat listening", initial.chatListening());
        ry += ROW;
        activeButton = addToggle(right, ry, "Active analysis", initial.activeMode());
        ry += ROW;
        commandsButton = addToggle(right, ry, "Allow commands", initial.allowCommands());
        ry += ROW;
        temperatureSlider = addSlider(right, ry, "Temperature", 0.0, 2.0, initial.temperature(), false);
        ry += ROW;
        maxTokensSlider = addSlider(right, ry, "Max tokens", 32, 2048, initial.maxTokens(), true);
        ry += ROW;
        followSlider = addSlider(right, ry, "Follow distance", 1, 32, initial.followDistance(), false);
        ry += ROW;
        guardSlider = addSlider(right, ry, "Guard radius", 4, 64, initial.guardRadius(), false);
        ry += ROW;
        commandLevelSlider = addSlider(right, ry, "Command perm level", 0, 4, initial.commandPermissionLevel(), true);
        ry += ROW;

        int contentBottom = Math.max(ly, ry) + 4;

        // ── developer mode toggle ──
        int devToggleY = contentBottom + 6;
        addRenderableWidget(Button.builder(
                        Component.literal(devMode ? "▼ Developer Mode  [ON]" : "▶ Developer Mode  [OFF]"),
                        b -> { devMode = !devMode; rebuildWidgets(); })
                .bounds(left, devToggleY, totalW, FIELD_H)
                .build());

        int devSectionBottom = devToggleY + ROW;

        if (devMode) {
            int dw = (totalW - COL_GAP) / 2;
            int dl = left;
            int dr = left + dw + COL_GAP;
            int dy = devToggleY + ROW;

            // Warning label
            addRenderableOnly(new StringWidget(left, dy, totalW, 9,
                    Component.literal("⚠  These settings can cause lag or crash the game. See developer.md.")
                            .withStyle(s -> s.withColor(0xFF5555)),
                    this.font));
            dy += 12;

            actionTickDelaySlider = addSlider(dl, dy, "Action tick delay (0=every tick!)", 0, 40,
                    initial.actionTickDelay(), true, dw);
            maxTaskSecondsSlider = addSlider(dr, dy, "Task watchdog sec (0=disabled!)", 0, 600,
                    initial.maxTaskSeconds(), true, dw);
            dy += ROW;
            fleeHealthSlider = addSlider(dl, dy, "Flee health % (0=never flees!)", 0.0, 1.0,
                    initial.fleeHealthPercent(), false, dw);
            dy += ROW;

            devSectionBottom = dy;
        } else {
            actionTickDelaySlider = null;
            maxTaskSecondsSlider = null;
            fleeHealthSlider = null;
        }

        // ── action bar ──
        actionBarY = Math.min(devSectionBottom + 8, this.height - FIELD_H - 8);
        int bw = 100;
        int gap = 8;
        int barW = bw * 3 + gap * 2;
        int bx = this.width / 2 - barW / 2;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> saveAndClose())
                .bounds(bx, actionBarY, bw, FIELD_H).build());
        addRenderableWidget(Button.builder(Component.literal("Apply"), b -> apply())
                .bounds(bx + bw + gap, actionBarY, bw, FIELD_H).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> cancel())
                .bounds(bx + (bw + gap) * 2, actionBarY, bw, FIELD_H).build());

        appliedLabel = new StringWidget(0, actionBarY - 12, this.width, 9, Component.empty(), this.font);
        addRenderableOnly(appliedLabel);

        baseline = buildData();
    }

    private EditBox labeledBox(int x, int y, String label, String value, int maxLen) {
        addRenderableOnly(new StringWidget(x, y - 10, COL_W, 9, Component.literal(label), this.font));
        EditBox box = new EditBox(this.font, x, y, COL_W, FIELD_H, Component.literal(label));
        box.setMaxLength(maxLen);
        if (value != null) box.setValue(value);
        addRenderableWidget(box);
        return box;
    }

    private CycleButton<Boolean> addToggle(int x, int y, String label, boolean value) {
        CycleButton<Boolean> button = CycleButton.onOffBuilder(value)
                .create(x, y, COL_W, FIELD_H, Component.literal(label));
        addRenderableWidget(button);
        return button;
    }

    private OptionSlider addSlider(int x, int y, String label, double min, double max,
                                   double value, boolean integer) {
        return addSlider(x, y, label, min, max, value, integer, COL_W);
    }

    private OptionSlider addSlider(int x, int y, String label, double min, double max,
                                   double value, boolean integer, int width) {
        OptionSlider slider = new OptionSlider(x, y, width, FIELD_H, label, min, max, value, integer);
        addRenderableWidget(slider);
        return slider;
    }

    private ConfigData buildData() {
        return new ConfigData(
                listenButton.getValue(),
                activeButton.getValue(),
                debugButton.getValue(),
                nameBox.getValue(),
                tokenBox.getValue(),
                tokenSet,
                modelBox.getValue(),
                apiUrlBox.getValue(),
                temperatureSlider.current(),
                (int) Math.round(maxTokensSlider.current()),
                followSlider.current(),
                guardSlider.current(),
                commandsButton.getValue(),
                (int) Math.round(commandLevelSlider.current()),
                skinBox.getValue(),
                actionTickDelaySlider != null ? (int) Math.round(actionTickDelaySlider.current())
                        : initial.actionTickDelay(),
                maxTaskSecondsSlider != null ? (int) Math.round(maxTaskSecondsSlider.current())
                        : initial.maxTaskSeconds(),
                fleeHealthSlider != null ? fleeHealthSlider.current()
                        : initial.fleeHealthPercent());
    }

    private void sendCurrent() {
        ClientPlayNetworking.send(new ConfigUpdatePayload(buildData()));
        if (!tokenBox.getValue().isBlank()) {
            tokenSet = true;
            tokenBox.setValue("");
            tokenBox.setHint(Component.literal("set — blank keeps it"));
        }
        baseline = buildData();
    }

    private boolean isDirty() {
        if (!tokenBox.getValue().isBlank()) return true;
        ConfigData c = buildData();
        return c.chatListening() != baseline.chatListening()
                || c.activeMode() != baseline.activeMode()
                || c.debugLogging() != baseline.debugLogging()
                || c.allowCommands() != baseline.allowCommands()
                || c.maxTokens() != baseline.maxTokens()
                || c.commandPermissionLevel() != baseline.commandPermissionLevel()
                || Double.compare(c.temperature(), baseline.temperature()) != 0
                || Double.compare(c.followDistance(), baseline.followDistance()) != 0
                || Double.compare(c.guardRadius(), baseline.guardRadius()) != 0
                || !eq(c.defaultName(), baseline.defaultName())
                || !eq(c.model(), baseline.model())
                || !eq(c.apiUrl(), baseline.apiUrl())
                || !eq(c.defaultSkin(), baseline.defaultSkin())
                || c.actionTickDelay() != baseline.actionTickDelay()
                || c.maxTaskSeconds() != baseline.maxTaskSeconds()
                || Double.compare(c.fleeHealthPercent(), baseline.fleeHealthPercent()) != 0;
    }

    private static boolean eq(String a, String b) {
        return (a == null ? "" : a).equals(b == null ? "" : b);
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
