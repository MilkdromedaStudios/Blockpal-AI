package com.milkdromeda.blockpal.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * In-game wiki / manual opened by right-clicking the AI Manual item.
 * Contains a Quick Start section followed by reference pages covering
 * commands, personalities, settings, and custom skins.
 */
public class AiManualScreen extends Screen {

    private static final int W = 310;
    private static final int FIELD_H = 20;

    // Each entry: first line is the section heading (bold/coloured), rest are body lines.
    private static final String[][] PAGES = {
            // ── Page 1: Quick Start ────────────────────────────────────────────────
            {
                    "§l§6AI Manual  —  Quick Start", "",
                    "§e1. §fSpawn your companion:",
                    "§a   /ai summon",
                    "§e2. §fTalk to it in chat (no slash needed):",
                    "§7   \"follow me\"  \"come\"  \"stay\"  \"stop\"",
                    "§7   \"Ethan, build a 5×5 floor\"", "",
                    "§e3. §fFor AI tasks, add an API key:",
                    "§7   Run §a/ai mymenu §7→ paste your token → Save.",
                    "§7   Free tokens at §ahttps://hf.co/settings/tokens", "",
                    "§e4. §fTry it:",
                    "§a   /ai build a small house",
                    "§a   /ai mine 10 iron ore", "",
                    "§7Use §a/ai stop §7or say \"stop\" to cancel any task."
            },

            // ── Page 2: Commands ───────────────────────────────────────────────────
            {
                    "§l§6Commands  —  /ai ...", "",
                    "§eSummon / dismiss",
                    "§a  /ai summon [name]   §7spawn companion",
                    "§a  /ai dismiss         §7remove companion", "",
                    "§eMovement",
                    "§a  /ai follow   §7follow you",
                    "§a  /ai come     §7come to you now",
                    "§a  /ai stay     §7guard a position",
                    "§a  /ai stop     §7cancel current task",
                    "§a  /ai locate   §7show where it is", "",
                    "§ePersonalise",
                    "§a  /ai name <name>          §7rename",
                    "§a  /ai skin <name>          §7change skin",
                    "§a  /ai personality [<id>]   §7list / set personality",
                    "§a  /ai personality custom <text>", "",
                    "§eSettings & info",
                    "§a  /ai panel    §7open the unified settings panel",
                    "§a  /ai mymenu  §7your own key + model",
                    "§a  /ai inv      §7show what it's carrying",
                    "§a  /ai tutorial §7reopen the how-to tutorial"
            },

            // ── Page 3: Personalities ──────────────────────────────────────────────
            {
                    "§l§6Personalities", "",
                    "§fGive your companion a character. Built-ins:",
                    "§a  friendly  §7— warm, helpful (default)",
                    "§a  cheerful  §7— upbeat and enthusiastic",
                    "§a  grumpy   §7— reluctant but loyal",
                    "§a  stoic    §7— calm, no-nonsense",
                    "§a  heroic   §7— bold and brave",
                    "§a  shy      §7— quiet and gentle", "",
                    "§fChange it:",
                    "§a  /ai personality friendly",
                    "§a  /ai personality   §7(lists all + current)", "",
                    "§fCustom personality (free text):",
                    "§a  /ai personality custom a wise old wizard",
                    "§7Or in §a/ai mymenu §7→ My Settings → Personality.", "",
                    "§7Custom text is safety-checked by the AI before it",
                    "§7is applied. Inappropriate content is rejected."
            },

            // ── Page 4: Settings & API key ─────────────────────────────────────────
            {
                    "§l§6Settings & the API Key", "",
                    "§eAll settings live in one panel:",
                    "§a  /ai panel", "",
                    "§fPanel tabs:",
                    "§7  • §eSettings §8(admins) §7— name, model, AI & API,",
                    "§7    behaviour, combat, developer",
                    "§7  • §eAdmin §8(ops) §7— bot cap, shared key, kill-all",
                    "§7  • §eMy Settings §8(everyone) §7— your model & key", "",
                    "§eAPI key options:",
                    "§7  1. §fShared key §7— admin sets in Settings → AI tab.",
                    "§7  2. §fYour own key §7— §a/ai mymenu §7or §a/ai mykey <token>",
                    "§7     (stored obfuscated per-player, never shown).",
                    "§7  3. §fEnv var §7— §aBLOCKPAL_API_TOKEN §7on the server",
                    "§7     (never written to disk — most secure).", "",
                    "§eModel selection:",
                    "§7  Pick a model in §a/ai mymenu §7or §a/ai model <id>§7.",
                    "§7  Admins manage the allowed-models list."
            },

            // ── Page 5: Skins & advanced ───────────────────────────────────────────
            {
                    "§l§6Custom Skins & More", "",
                    "§eBuilt-in skins:",
                    "§7  default  steve  robot  void",
                    "§7  slate  ember  forest  amethyst", "",
                    "§eApply a skin:",
                    "§a  /ai skin robot",
                    "§a  /ai skin ember", "",
                    "§eCustom PNG skins:",
                    "§71. Drop a 64×64 PNG into:",
                    "§f   config/blockpal/skins/",
                    "§72. In-game: §a/aiskins reload",
                    "§73. Apply: §a/ai skin <filename-without-.png>", "",
                    "§7Use the §eOpen skins folder §7button in",
                    "§7Settings → Identity to open the folder.", "",
                    "§eMore help:",
                    "§7  /ai tutorial   §7— short how-to walkthrough",
                    "§7  /ai help       §7— command list in chat",
                    "§7  Full wiki at the mod's GitHub page."
            },
    };

    private int page = 0;

    public AiManualScreen() {
        super(Component.literal("AI Manual"));
    }

    @Override
    protected void init() {
        addRenderableWidget(new StringWidget(0, 8, this.width, 12, this.title, this.font));

        LinearLayout body = LinearLayout.vertical().spacing(2);
        for (String line : PAGES[page]) {
            body.addChild(new StringWidget(W, 11, Component.literal(line), this.font));
        }
        ScrollableLayout scroll = new ScrollableLayout(this.minecraft, body,
                Math.max(FIELD_H, this.height - 28 - 40));
        scroll.setMinWidth(W + 12);
        scroll.arrangeElements();
        scroll.setX(this.width / 2 - (W + 12) / 2);
        scroll.setY(28);
        scroll.visitWidgets(this::addRenderableWidget);

        // footer: Back · [chapter label] · Next/Done
        int bw = 96, gap = 8;
        int barW = bw * 3 + gap * 2;
        int bx = this.width / 2 - barW / 2;
        int by = this.height - FIELD_H - 8;

        Button back = Button.builder(Component.literal("◀ Back"),
                        b -> { if (page > 0) { page--; rebuildWidgets(); } })
                .bounds(bx, by, bw, FIELD_H).build();
        back.active = page > 0;
        addRenderableWidget(back);

        addRenderableWidget(Button.builder(Component.literal("Close"),
                        b -> onClose())
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
