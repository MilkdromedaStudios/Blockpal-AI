package com.milkdromeda.blockpal.client.gui;

import com.milkdromeda.blockpal.network.AdminActionPayload;
import com.milkdromeda.blockpal.network.AdminStatsData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Blockpal <b>Admin</b> panel ({@code /ai admin menu} or the "Admin" tab),
 * ops only. Reads out server-wide state ({@link AdminStatsData}) and lets an admin
 * change the common server options <i>right here</i> — no commands or config-file
 * editing — plus one-shot actions (kill all bots, disable/enable the mod).
 *
 * <p>Setting toggles change in place (their widget shows the new value and the
 * server saves silently); the footer actions re-open the panel with fresh stats.
 */
public class AdminScreen extends Screen {

    private static final int W = 320;
    private static final int FIELD_H = 18;
    private static final int LABEL_H = 11;
    private static final int SPACING = 2;
    private static final int NAV_Y = 20;
    private static final int NAV_H = 16;
    private static final int BODY_TOP = 40;
    private static final int FOOTER = 28;

    private final AdminStatsData d;

    public AdminScreen(AdminStatsData data) {
        super(Component.literal("Blockpal — Admin"));
        this.d = data;
    }

    @Override
    protected void init() {
        addRenderableWidget(new StringWidget(0, 6, this.width, 12, this.title, this.font));

        // -- shared cross-panel tab bar --
        PanelNav.build(this.width, W + 12, NAV_Y, NAV_H, PanelNav.Tab.ADMIN, true, this::addRenderableWidget);

        // -- scrollable body --
        LinearLayout body = LinearLayout.vertical().spacing(SPACING);

        line(body, "§6Server status");
        line(body, "§eBots: §f" + d.totalBots() + " §7/ " + (d.maxBots() == 0 ? "∞" : d.maxBots()));
        line(body, "§eMod: " + (d.modDisabled() ? "§cDISABLED" : "§aactive")
                + "  §eToken: §f" + (d.tokenSet() ? ("set ✓" + (d.tokenFromEnv() ? " (env)" : "")) : "§cnot set"));

        line(body, " ");
        line(body, "§6Settings §7(click to change)");
        // Booleans
        addToggle(body, "Allow commands", d.allowCommands(), "allowcommands",
                "Let bots run /setblock, /fill, /give, etc. as part of a plan.");
        addToggle(body, "Require own API key", d.requireOwnKey(), "requirekey",
                "Players must use their own API key (except those on the key whitelist).");
        addToggle(body, "Players may pick model", d.allowModelChoice(), "modelchoice",
                "Let players choose their bot's model from the allowed list.");
        // Levels (cycle 0–4)
        addLevel(body, "Command perm level", d.commandLevel(), "commandlevel",
                "Permission tier for commands bots run (2 = command-block tier).");
        addLevel(body, "Admin level (who's an admin)", d.adminLevel(), "adminlevel",
                "Vanilla op tier needed to change settings / use this panel. 2 = ops.");
        addMaxBots(body);

        line(body, " ");
        line(body, "§7Models allowed: §f" + d.allowedModelCount()
                + " §7· Key whitelist: §f" + d.keyWhitelistCount());
        line(body, "§7Manage those lists with §f/ai admin models§7 and §f/ai admin keylist§7.");

        line(body, " ");
        line(body, "§6Players online (" + d.players().size() + ")");
        if (d.players().isEmpty()) line(body, "§7  none");
        for (AdminStatsData.PlayerRow p : d.players()) {
            line(body, "§f  " + p.name() + " §7— bots: §f" + p.bots()
                    + " §7— fps: §f" + (p.fps() < 0 ? "?" : p.fps()));
        }

        line(body, " ");
        line(body, "§6Bots (" + d.bots().size() + ")");
        if (d.bots().isEmpty()) line(body, "§7  none");
        for (AdminStatsData.BotRow b : d.bots()) {
            line(body, "§f  " + b.name() + " §7(" + b.owner() + ") — "
                    + b.mode().toLowerCase(Locale.ROOT) + " — " + b.dim() + " — hp " + b.health()
                    + " §8@ " + b.x() + "," + b.y() + "," + b.z());
        }

        int maxHeight = Math.max(FIELD_H, this.height - BODY_TOP - FOOTER);
        ScrollableLayout scroll = new ScrollableLayout(this.minecraft, body, maxHeight);
        scroll.setMinWidth(W + 12);
        scroll.arrangeElements();
        scroll.setX(this.width / 2 - (W + 12) / 2);
        scroll.setY(BODY_TOP);
        scroll.visitWidgets(this::addRenderableWidget);

        // -- pinned footer: one-shot actions --
        int bw = 100, gap = 8;
        int barW = bw * 3 + gap * 2;
        int bx = this.width / 2 - barW / 2;
        int by = this.height - FIELD_H - 6;
        addRenderableWidget(withTip(Button.builder(Component.literal("Kill all bots"),
                        b -> send("killall", 0)).bounds(bx, by, bw, FIELD_H).build(),
                "Remove every Blockpal entity on the server."));
        addRenderableWidget(Button.builder(
                        Component.literal(d.modDisabled() ? "Enable bots" : "Disable bots"),
                        b -> send(d.modDisabled() ? "enable" : "disable", 0))
                .bounds(bx + bw + gap, by, bw, FIELD_H).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh"),
                        b -> send("refresh", 0))
                .bounds(bx + (bw + gap) * 2, by, bw, FIELD_H).build());
    }

    // ── body widgets ────────────────────────────────────────────────────────────

    private void line(LinearLayout body, String text) {
        body.addChild(new StringWidget(W, LABEL_H, Component.literal(text), this.font));
    }

    private void addToggle(LinearLayout body, String label, boolean value, String action, String tip) {
        CycleButton<Boolean> btn = body.addChild(CycleButton.onOffBuilder(value)
                .create(0, 0, W, FIELD_H, Component.literal(label), (b, val) -> send(action, val ? 1 : 0)));
        btn.setTooltip(Tooltip.create(Component.literal(tip)));
    }

    private void addLevel(LinearLayout body, String label, int value, String action, String tip) {
        int cur = Math.max(0, Math.min(4, value));
        CycleButton<Integer> btn = body.addChild(
                CycleButton.<Integer>builder(i -> Component.literal(label + ": " + i), cur)
                        .withValues(0, 1, 2, 3, 4)
                        .create(0, 0, W, FIELD_H, Component.literal(label), (b, val) -> send(action, val)));
        btn.setTooltip(Tooltip.create(Component.literal(tip)));
    }

    private void addMaxBots(LinearLayout body) {
        // A handful of presets, plus whatever the current value is, so the current
        // value is always one of the selectable options.
        int cur = Math.max(0, Math.min(50, d.maxBots()));
        Set<Integer> set = new LinkedHashSet<>(List.of(0, 1, 2, 4, 8, 12, 16, 24, 32, 48));
        set.add(cur);
        List<Integer> values = new ArrayList<>(set);
        Collections.sort(values);
        CycleButton<Integer> btn = body.addChild(
                CycleButton.<Integer>builder(i -> Component.literal("Max bots: " + (i == 0 ? "∞" : i)), cur)
                        .withValues(values)
                        .create(0, 0, W, FIELD_H, Component.literal("Max bots"), (b, val) -> send("maxbots", val)));
        btn.setTooltip(Tooltip.create(Component.literal("Most bots allowed on the server at once (0 = unlimited).")));
    }

    private static Button withTip(Button button, String tip) {
        button.setTooltip(Tooltip.create(Component.literal(tip)));
        return button;
    }

    private void send(String action, int value) {
        if (ClientPlayNetworking.canSend(AdminActionPayload.TYPE)) {
            ClientPlayNetworking.send(new AdminActionPayload(action, value));
        }
        // One-shot actions trigger a server re-sync that reopens this screen;
        // setting toggles update their own widget in place.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
