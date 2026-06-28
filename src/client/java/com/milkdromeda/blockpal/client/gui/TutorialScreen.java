package com.milkdromeda.blockpal.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A short, paged "how to use Blockpal" walkthrough. It auto-opens once on a
 * player's first join after a fresh install, and can be reopened any time with
 * {@code /ai tutorial}. The closing page points players at the unified panel.
 */
public class TutorialScreen extends Screen {

    private static final int W = 300;
    private static final int FIELD_H = 20;

    // Each page is a list of lines (first line is the heading).
    private static final String[][] PAGES = {
            {
                    "§l§6Welcome to Blockpal!", "",
                    "§fBlockpal adds a friendly AI companion",
                    "§f(default name §aEthan§f) to your world.",
                    "§fIt can build, mine, fight, run commands,",
                    "§fand react to what you say in chat.", "",
                    "§7Spawn one with §a/ai summon§7.", "",
                    "§eYou received an §6AI Manual §ein your inventory.",
                    "§7Right-click it any time to open the",
                    "§7full in-game wiki and reference."
            },
            {
                    "§l§6Quick Start", "",
                    "§e1. §a/ai summon §f— spawn your companion", "",
                    "§e2. §fTalk in chat (no slash needed):",
                    "§7   \"follow me\"   \"come\"   \"stay\"   \"stop\"",
                    "§7   \"Ethan, build a 5x5 floor\"", "",
                    "§e3. §fFor AI tasks, add an API key:",
                    "§7   §a/ai mymenu §7→ paste token → Save",
                    "§7   (free tokens at hf.co/settings/tokens)", "",
                    "§e4. §fTry: §a/ai mine 10 iron ore"
            },
            {
                    "§l§6Talking to it", "",
                    "§fJust type in chat — no slash needed:",
                    "§7  \"follow me\"   \"come\"   \"stay\"   \"stop\"",
                    "§7  \"clear these trees\"   \"build a door\"", "",
                    "§fOr give a task directly:",
                    "§a  /ai <task>  §7(e.g. /ai build a 5x5 floor)"
            },
            {
                    "§l§6One panel for everything", "",
                    "§fAll settings live in one place:",
                    "§a  /ai panel", "",
                    "§fTabs across the top switch panels:",
                    "§7  • Settings §8(admins) — name, model, behaviour",
                    "§7  • Admin §8(ops) — bots, limits, keys, models",
                    "§7  • My Settings §8(everyone) — your model & key"
            },
            {
                    "§l§6The AI key", "",
                    "§fBlockpal needs an AI service key to think.",
                    "§fAn admin can set a shared key in the panel,",
                    "§for each player can bring their own:",
                    "§a  /ai mykey <token>  §7or in §a/ai mymenu", "",
                    "§7That's it — have fun!", "",
                    "§eRight-click your §6AI Manual §eto open the",
                    "§efull in-game wiki with commands, settings,",
                    "§epersonalities, skins, and more."
            },
    };

    private int page = 0;

    public TutorialScreen() {
        super(Component.literal("Blockpal — Tutorial"));
    }

    @Override
    protected void init() {
        addRenderableWidget(new StringWidget(0, 8, this.width, 12, this.title, this.font));

        LinearLayout body = LinearLayout.vertical().spacing(2);
        for (String linext : PAGES[page]) {
            body.addChild(new StringWidget(W, 11, Component.literal(linext), this.font));
        }
        ScrollableLayout scroll = new ScrollableLayout(this.minecraft, body,
                Math.max(FIELD_H, this.height - 28 - 40));
        scroll.setMinWidth(W + 12);
        scroll.arrangeElements();
        scroll.setX(this.width / 2 - (W + 12) / 2);
        scroll.setY(28);
        scroll.visitWidgets(this::addRenderableWidget);

        // -- footer: Back · Open panel · Next/Done --
        int bw = 96, gap = 8;
        int barW = bw * 3 + gap * 2;
        int bx = this.width / 2 - barW / 2;
        int by = this.height - FIELD_H - 8;

        Button back = Button.builder(Component.literal("◀ Back"), b -> { if (page > 0) { page--; rebuildWidgets(); } })
                .bounds(bx, by, bw, FIELD_H).build();
        back.active = page > 0;
        addRenderableWidget(back);

        addRenderableWidget(Button.builder(Component.literal("Open panel"), b -> PanelNav.switchTo(PanelNav.Tab.ME))
                .bounds(bx + bw + gap, by, bw, FIELD_H).build());

        boolean last = page == PAGES.length - 1;
        addRenderableWidget(Button.builder(Component.literal(last ? "Done ✓" : "Next ▶"),
                        b -> { if (last) onClose(); else { page++; rebuildWidgets(); } })
                .bounds(bx + (bw + gap) * 2, by, bw, FIELD_H).build());

        // page indicator
        addRenderableWidget(new StringWidget(0, by - 14, this.width, 10,
                Component.literal("§7page " + (page + 1) + " / " + PAGES.length), this.font));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
