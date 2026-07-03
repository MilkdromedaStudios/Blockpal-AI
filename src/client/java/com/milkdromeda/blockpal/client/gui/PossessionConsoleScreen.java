package com.milkdromeda.blockpal.client.gui;

import com.milkdromeda.blockpal.network.PossessionInputPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The possession-mode console — the "little textbox" for driving your own character
 * with your companion. Type an instruction and press Send (or Enter); the AI turns it
 * into actions and drives you, streaming a live status feed into the log above.
 *
 * <p>The log is a static tail buffer so it survives the screen being rebuilt when a
 * new status line arrives (which keeps the whole thing widget-based — no manual
 * {@code render()} — and preserves what you were typing). All the real work is
 * server-side, so the console is only a convenience: on a client that can't show it,
 * {@code /ai possess <instruction>} does the same thing in chat.
 */
public class PossessionConsoleScreen extends Screen {

    private static final int W = 320;
    private static final int FIELD_H = 20;
    private static final int MAX_LOG = 200;

    /** Shared log tail, kept across screen rebuilds/reopens. */
    private static final List<String> LOG = new ArrayList<>();

    private boolean active;
    private EditBox input;
    /** What was being typed, preserved across a rebuild triggered by a new log line. */
    private String draftInput = "";

    public PossessionConsoleScreen(boolean active) {
        super(Component.literal("Possession Console"));
        this.active = active;
    }

    /**
     * Handles a possession update from the server: opens the console (or focuses it)
     * when {@code open}, and appends any status {@code line} to the shared log —
     * refreshing an already-open console in place so typing isn't lost.
     */
    public static void handleSync(Minecraft mc, boolean open, boolean active, String line) {
        if (line != null && !line.isBlank()) addLog(line);
        PossessionConsoleScreen shown = (mc.screen instanceof PossessionConsoleScreen c) ? c : null;
        if (open) {
            if (shown != null) { shown.active = active; shown.refresh(); }
            else mc.setScreenAndShow(new PossessionConsoleScreen(active));
        } else if (shown != null) {
            shown.active = active;
            shown.refresh();
        }
        // If not open and no console is showing, the line is simply buffered for next time.
    }

    private static void addLog(String s) {
        LOG.add(s);
        while (LOG.size() > MAX_LOG) LOG.remove(0);
    }

    /** Re-lays the widgets so the log tail shows the newest lines, keeping the input. */
    private void refresh() {
        if (this.minecraft == null) return;
        if (input != null) draftInput = input.getValue();
        rebuildWidgets();
    }

    @Override
    protected void init() {
        int x = this.width / 2 - W / 2;

        // -- title + status line --
        addRenderableWidget(new StringWidget(0, 8, this.width, 12, this.title, this.font));
        addRenderableWidget(new StringWidget(0, 22, this.width, 10, Component.literal(active
                ? "§dPossessing — your companion has your controls"
                : "§7Possession inactive"), this.font));

        // -- log tail (newest lines that fit, oldest scroll off the top) --
        int top = 40;
        int inputY = this.height - 58;
        int lineH = this.font.lineHeight + 1;
        int maxLines = Math.max(1, (inputY - 6 - top) / lineH);
        int from = Math.max(0, LOG.size() - maxLines);
        int y = top;
        for (int i = from; i < LOG.size(); i++) {
            Component line = Component.literal(LOG.get(i));
            // Size the label to its own text width so it renders flush-left at x
            // (a full-width StringWidget would centre it).
            int lw = Math.min(W, this.font.width(line));
            addRenderableWidget(new StringWidget(x, y, lw, lineH, line, this.font));
            y += lineH;
        }

        // -- input row --
        input = new EditBox(this.font, x, inputY, W - 70, FIELD_H, Component.literal("Instruction"));
        input.setMaxLength(256);
        input.setHint(Component.literal("Tell your companion what to do…"));
        input.setValue(draftInput);
        addRenderableWidget(input);
        setInitialFocus(input);
        addRenderableWidget(Button.builder(Component.literal("Send"), b -> send())
                .bounds(x + W - 66, inputY, 66, FIELD_H).build());

        // -- action row --
        int by = this.height - 32;
        int bw = (W - 8) / 2;
        addRenderableWidget(Button.builder(Component.literal("Stop possession"), b -> stopPossession())
                .bounds(x, by, bw, FIELD_H).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(x + bw + 8, by, bw, FIELD_H).build());
    }

    private void send() {
        String text = input == null ? "" : input.getValue().trim();
        if (text.isEmpty()) return;
        if (ClientPlayNetworking.canSend(PossessionInputPayload.TYPE)) {
            ClientPlayNetworking.send(new PossessionInputPayload("instruction", text));
        }
        draftInput = "";
        if (input != null) input.setValue("");
    }

    private void stopPossession() {
        if (ClientPlayNetworking.canSend(PossessionInputPayload.TYPE)) {
            ClientPlayNetworking.send(new PossessionInputPayload("stop", ""));
        }
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            send();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;   // the world (and the possession) keeps running while it's open
    }
}
