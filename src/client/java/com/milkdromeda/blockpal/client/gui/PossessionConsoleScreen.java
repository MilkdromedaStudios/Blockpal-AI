package com.milkdromeda.blockpal.client.gui;

import com.milkdromeda.blockpal.network.PossessionInputPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * The possession-mode console — the "little textbox" for driving your own character
 * with your companion. Type an instruction and press Send; the AI turns it into
 * actions and drives you, streaming a live status feed into the log above.
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

    /** The console the player currently has open, if any (for in-place updates). */
    private static PossessionConsoleScreen instance;
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
        if (open) {
            if (instance != null) { instance.active = active; instance.refresh(); }
            else mc.setScreenAndShow(new PossessionConsoleScreen(active));
        } else if (instance != null) {
            instance.active = active;
            instance.refresh();
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
        instance = this;
        int x = this.width / 2 - W / 2;

        // -- title + status line --
        addRenderableWidget(TechTheme.centered(this.font, this.width, 8, 12, TechTheme.title("Possession")));
        addRenderableWidget(TechTheme.centered(this.font, this.width, 22, 10, Component.literal(active
                ? "§b⚡ POSSESSING — your companion has your controls"
                : "§7Possession inactive")));

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
    public void removed() {
        if (instance == this) instance = null;
        super.removed();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        TechTheme.backdrop(g, this.width, this.height);
        int x0 = this.width / 2 - W / 2 - 12;
        int x1 = this.width / 2 + W / 2 + 12;
        TechTheme.panel(g, x0, 2, x1, this.height - 2);
        TechTheme.rule(g, this.width / 2 - 130, this.width / 2 + 130, 19);
        // console log well, slightly darker than the plate
        g.fill(x0 + 8, 38, x1 - 8, this.height - 62, 0x66020609);
        g.outline(x0 + 8, 38, x1 - x0 - 16, this.height - 62 - 38, TechTheme.ACCENT_DIM);
    }

    @Override
    public boolean isPauseScreen() {
        return false;   // the world (and the possession) keeps running while it's open
    }
}
