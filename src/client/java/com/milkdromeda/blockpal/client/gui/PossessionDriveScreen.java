package com.milkdromeda.blockpal.client.gui;

import com.milkdromeda.blockpal.client.assist.ClientPossession;
import com.milkdromeda.blockpal.client.assist.PossessionLog;
import com.milkdromeda.blockpal.client.assist.ServerGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The client-side possession console — the "little textbox" for driving your own
 * character with the AI on a server that <b>doesn't</b> run Blockpal. Type an
 * instruction and the AI simulates your inputs to do basic survival tasks (mining,
 * gathering, walking). See {@link ClientPossession} for the strict safety limits
 * (no combat, no chat, no commands; hard-blocked on anti-cheat networks).
 *
 * <p>This is the fallback for vanilla servers; on a Blockpal server, {@code /ai possess}
 * opens the server-side console instead. Widget-based (no manual render) so it survives
 * the rebuilds a new status line triggers, exactly like the server console.
 */
public class PossessionDriveScreen extends Screen {

    private static final int W = 320;

    private EditBox input;
    private String draft = "";
    /** True once navigated away from — guards async status rebuilds. */
    private boolean dead;

    public PossessionDriveScreen() {
        super(Component.literal("Possession Console"));
    }

    @Override
    protected void init() {
        int x = this.width / 2 - W / 2;
        ServerGuard.Driving verdict = ServerGuard.driving();
        boolean canDrive = verdict == ServerGuard.Driving.ALLOWED
                || verdict == ServerGuard.Driving.ALLOWED_WITH_WARNING;

        addRenderableWidget(TechTheme.centered(this.font, this.width, 8, 12, TechTheme.title("Drive Console")));
        addRenderableWidget(TechTheme.centered(this.font, this.width, 22, 10, Component.literal(
                ClientPossession.isActive() ? "§b⚡ DRIVING — the AI is simulating your inputs"
                        : "§7Off-server possession (mining & gathering only)")));

        // Safety banner — always visible, word-driven from the guard verdict.
        int top = 40;
        for (String line : wrapForWidth(ServerGuard.explain(verdict), W)) {
            addRenderableWidget(new StringWidget(x, top, W, this.font.lineHeight + 1,
                    Component.literal(line), this.font));
            top += this.font.lineHeight + 1;
        }
        top += 2;

        // -- status log tail --
        int inputY = this.height - 58;
        int lineH = this.font.lineHeight + 1;
        List<String> log = PossessionLog.lines();
        int maxLines = Math.max(1, (inputY - 6 - top) / lineH);
        int from = Math.max(0, log.size() - maxLines);
        int y = top;
        for (int i = from; i < log.size(); i++) {
            Component line = Component.literal(log.get(i));
            int lw = Math.min(W, this.font.width(line));
            addRenderableWidget(new StringWidget(x, y, lw, lineH, line, this.font));
            y += lineH;
        }

        // -- input row --
        input = new EditBox(this.font, x, inputY, W - 70, 20, Component.literal("Instruction"));
        input.setMaxLength(256);
        input.setHint(Component.literal(canDrive ? "e.g. mine the ores in front of me…" : "driving is blocked here"));
        input.setValue(draft);
        input.setEditable(canDrive);
        addRenderableWidget(input);
        setInitialFocus(input);
        Button send = Button.builder(Component.literal("Send"), b -> send())
                .bounds(x + W - 66, inputY, 66, 20).build();
        send.active = canDrive;
        addRenderableWidget(send);

        // -- action row --
        int by = this.height - 32;
        int bw = (W - 8) / 2;
        addRenderableWidget(Button.builder(Component.literal("Stop driving"), b -> stop())
                .bounds(x, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(x + bw + 8, by, bw, 20).build());

        // Refresh in place whenever the driver posts a status line. The listener is
        // cleared in removed(), so it only ever fires while this console is open.
        PossessionLog.setListener(line -> {
            Minecraft mc = this.minecraft;
            if (mc != null && !dead) mc.execute(() -> { if (!dead) rebuild(); });
        });
    }

    private void send() {
        String text = input == null ? "" : input.getValue().trim();
        if (text.isEmpty()) return;
        String status = ClientPossession.start(text);
        PossessionLog.add("§b> §f" + text);
        if (status != null && !status.isBlank()) PossessionLog.add(status);
        draft = "";
        if (input != null) input.setValue("");
        rebuild();
    }

    private void stop() {
        ClientPossession.stop();
        PossessionLog.add("§7Driving stopped — you have control again.");
        onClose();
    }

    private void rebuild() {
        if (input != null) draft = input.getValue();
        rebuildWidgets();
    }

    private List<String> wrapForWidth(String s, int maxWidth) {
        List<String> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String cand = cur.length() == 0 ? word : cur + " " + word;
            if (this.font.width(cand) > maxWidth && cur.length() > 0) {
                out.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur.setLength(0);
                cur.append(cand);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    @Override
    public void removed() {
        dead = true;
        PossessionLog.setListener(null);
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
        g.fill(x0 + 8, 38, x1 - 8, this.height - 62, 0x66020609);
        g.outline(x0 + 8, 38, x1 - x0 - 16, this.height - 62 - 38, TechTheme.ACCENT_DIM);
    }

    @Override
    public boolean isPauseScreen() {
        return false;   // driving keeps running while the console is open
    }
}
