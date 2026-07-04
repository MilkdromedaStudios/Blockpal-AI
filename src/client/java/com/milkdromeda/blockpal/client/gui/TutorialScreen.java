package com.milkdromeda.blockpal.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A short, paged "how to use Blockpal" walkthrough. It auto-opens once on a
 * player's first join after a fresh install, and can be reopened any time with
 * {@code /ai tutorial}. The closing page points players at the full wiki panel.
 */
public class TutorialScreen extends Screen {

    private static final int W = 300;
    private static final int FIELD_H = 20;

    // Each page is a list of lines (first line is the heading).
    private static final String[][] PAGES = {
            {
                    "§l§bWelcome to Blockpal!", "",
                    "§fBlockpal adds a friendly AI companion",
                    "§f(default name §aEthan§f) to your world.",
                    "§fIt can build, mine, fight, run commands,",
                    "§fand react to what you say in chat.", "",
                    "§7Spawn one with §a/ai summon§7.", "",
                    "§eThis tutorial walks you through the basics.",
                    "§eFor the full wiki, click §bOpen Wiki§e below",
                    "§eor any time from the settings panel."
            },
            {
                    "§l§bQuick Start", "",
                    "§e1. §a/ai summon §f— spawn your companion", "",
                    "§e2. §fTalk in chat (no slash needed):",
                    "§7   \"follow me\"   \"come\"   \"stay\"   \"stop\"",
                    "§7   \"Ethan, build a 5x5 floor\"", "",
                    "§e3. §fAI tasks work out of the box on a",
                    "§f   free built-in AI. For better quality:",
                    "§7   §a/ai mymenu §7→ paste an API key → Save",
                    "§7   (free tokens at hf.co/settings/tokens)", "",
                    "§e4. §fTry: §a/ai mine 10 iron ore"
            },
            {
                    "§l§bTalking to it", "",
                    "§fJust type in chat — no slash needed:",
                    "§7  \"follow me\"   \"come\"   \"stay\"   \"stop\"",
                    "§7  \"clear these trees\"   \"build a door\"", "",
                    "§fOr give a task directly:",
                    "§a  /ai <task>  §7(e.g. /ai build a 5x5 floor)"
            },
            {
                    "§l§bOne panel for everything", "",
                    "§fAll settings live in one place:",
                    "§a  /ai panel", "",
                    "§fTabs across the top switch panels:",
                    "§7  • Settings §8(admins) — name, model, behaviour",
                    "§7  • Admin §8(ops) — bots, limits, keys, models",
                    "§7  • My Settings §8(everyone) — your model & key"
            },
            {
                    "§l§bThe AI key", "",
                    "§fWith no key, Blockpal thinks on a free",
                    "§fbuilt-in AI — it just works. For faster,",
                    "§fsmarter models add a key (e.g. HuggingFace):",
                    "§fan admin sets a shared one in the panel,",
                    "§for each player can bring their own:",
                    "§a  /ai mykey <token>  §7or in §a/ai mymenu", "",
                    "§fThat's it — have fun!", "",
                    "§eFor commands, personalities, skins and more,",
                    "§eclick §bOpen Wiki §ebelow — or find it any time",
                    "§ein §a/ai panel §e→ Settings."
            },
    };

    private int page = 0;

    public TutorialScreen() {
        super(Component.literal("Blockpal — Tutorial"));
    }

    @Override
    protected void init() {
        addRenderableWidget(TechTheme.centered(this.font, this.width, 8, 12, TechTheme.title("Tutorial")));

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

        // -- footer: Back · Open Wiki · Next/Done --
        int bw = 96, gap = 8;
        int barW = bw * 3 + gap * 2;
        int bx = this.width / 2 - barW / 2;
        int by = this.height - FIELD_H - 8;

        Button back = Button.builder(Component.literal("◀ Back"), b -> { if (page > 0) { page--; rebuildWidgets(); } })
                .bounds(bx, by, bw, FIELD_H).build();
        back.active = page > 0;
        addRenderableWidget(back);

        addRenderableWidget(Button.builder(Component.literal("§bOpen Wiki"),
                        b -> this.minecraft.setScreenAndShow(new AiManualScreen(this)))
                .bounds(bx + bw + gap, by, bw, FIELD_H).build());

        boolean last = page == PAGES.length - 1;
        addRenderableWidget(Button.builder(Component.literal(last ? "Done ✓" : "Next ▶"),
                        b -> { if (last) onClose(); else { page++; rebuildWidgets(); } })
                .bounds(bx + (bw + gap) * 2, by, bw, FIELD_H).build());

        // page indicator
        addRenderableWidget(TechTheme.centered(this.font, this.width, by - 14, 10,
                TechTheme.dim("page " + (page + 1) + " / " + PAGES.length)));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        TechTheme.backdrop(g, this.width, this.height);
        TechTheme.panel(g, this.width / 2 - (W + 12) / 2 - 10, 2,
                this.width / 2 + (W + 12) / 2 + 10, this.height - 2);
        TechTheme.rule(g, this.width / 2 - 130, this.width / 2 + 130, 21);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
