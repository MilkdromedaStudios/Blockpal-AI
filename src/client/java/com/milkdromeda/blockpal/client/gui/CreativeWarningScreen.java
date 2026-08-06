package com.milkdromeda.blockpal.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Shown once when a player switches to creative (or spectator) while a companion is out.
 *
 * <p>Companions never teleport — that's the deliberate rule that keeps them feeling like
 * something living in the world rather than a marker glued to your camera. The cost of
 * that rule lands entirely on flying players, so they're told about it plainly instead of
 * being left to wonder why their bot is a thousand blocks back.
 */
public class CreativeWarningScreen extends Screen {

    private static final int W = 300;
    private static final int FIELD_H = 20;

    private final String botName;

    public CreativeWarningScreen(String botName) {
        super(Component.literal("Blockpal — Creative mode"));
        this.botName = botName == null || botName.isBlank() ? "Your companion" : botName;
    }

    @Override
    protected void init() {
        addRenderableWidget(TechTheme.centered(this.font, this.width, 26, 12,
                TechTheme.title("You're flying — " + botName + " isn't")));

        String[] lines = {
                "",
                "§f" + botName + " walks, swims and climbs like you do.",
                "§fIt will §lnever§r§f teleport to you — not when you're",
                "§ffar away, not in another dimension, not ever.",
                "",
                "§7That's on purpose: a companion that blinks to your",
                "§7side isn't really living in the world with you.",
                "",
                "§eIn creative you can out-fly it in seconds.",
                "§fIt will keep walking toward you the whole time,",
                "§fand it can fall, drown or meet something nasty",
                "§fon the way.",
                "",
                "§bWhat to do:",
                "§7  • §f/ai stay §7— park it somewhere safe first",
                "§7  • §f/ai come §7— call it over once you've landed",
                "§7  • §f/ai locate §7— find out where it got to",
        };

        LinearLayout body = LinearLayout.vertical().spacing(1);
        for (String line : lines) {
            body.addChild(new StringWidget(W, 11, Component.literal(line), this.font));
        }
        body.arrangeElements();
        body.setX(this.width / 2 - W / 2);
        body.setY(44);
        body.visitWidgets(this::addRenderableWidget);

        addRenderableWidget(Button.builder(Component.literal("Got it"), b -> onClose())
                .bounds(this.width / 2 - 60, this.height - FIELD_H - 14, 120, FIELD_H).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        TechTheme.backdrop(g, this.width, this.height);
        TechTheme.panel(g, this.width / 2 - W / 2 - 14, 14,
                this.width / 2 + W / 2 + 14, this.height - 8);
        TechTheme.rule(g, this.width / 2 - 130, this.width / 2 + 130, 40);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
