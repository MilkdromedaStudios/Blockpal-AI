package com.milkdromeda.aiassistant.client;

import com.milkdromeda.aiassistant.entity.AiAssistantEntity;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

public class AiAssistantHud implements HudElement {

    private static final Identifier ID = Identifier.fromNamespaceAndPath("ai-assistant", "status_hud");
    private static int tick = 0;

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, ID, new AiAssistantHud());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.getDebugOverlay().showDebugScreen()) return;

        // Find the nearest tracked AI assistant
        AiAssistantEntity nearest = null;
        double nearestDistSq = 64.0 * 64.0;

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof AiAssistantEntity ai) {
                double d = ai.distanceToSqr(mc.player);
                if (d < nearestDistSq) { nearestDistSq = d; nearest = ai; }
            }
        }
        if (nearest == null) return;

        tick++;
        String name = nearest.getSyncedName();
        String mode = nearest.getSyncedMode();
        String task = nearest.getSyncedTask();

        // ── Layout ─────────────────────────────────────────────────────────────
        int x = 6, y = 6;
        int panelW = 162;
        boolean hasTask = !task.isBlank();
        int panelH = hasTask ? 52 : 40;

        // ── Dark translucent background ─────────────────────────────────────────
        gfx.fill(x - 1, y - 1, x + panelW + 1, y + panelH + 1, modeAlpha(mode, 0x88));
        gfx.fill(x, y, x + panelW, y + panelH, 0xB8101010);

        // Mode accent bar (left edge)
        gfx.fill(x, y, x + 2, y + panelH, modeRGB(mode) | 0xFF000000);

        // ── Name (gold, bold) ──────────────────────────────────────────────────
        Component nameComp = Component.literal(name)
                .withStyle(Style.EMPTY.withColor(0xF0A000).withBold(true));
        gfx.text(mc.font, nameComp, x + 6, y + 4, 0xFFFFFF, true);

        // ── Distance (top-right) ───────────────────────────────────────────────
        int dist = (int) Math.sqrt(nearestDistSq);
        String distStr = dist + "m";
        gfx.text(mc.font, Component.literal(distStr)
                .withStyle(Style.EMPTY.withColor(0x777777)),
                x + panelW - mc.font.width(distStr) - 4, y + 4, 0xFFFFFF, false);

        // ── Mode line ──────────────────────────────────────────────────────────
        String modeLabel = "EXECUTING".equals(mode) && task.isBlank()
                ? "⟳ Thinking" + thinkDots()
                : modeIcon(mode) + " " + friendlyMode(mode);
        gfx.text(mc.font, Component.literal(modeLabel)
                .withStyle(Style.EMPTY.withColor(modeRGB(mode))),
                x + 6, y + 15, 0xFFFFFF, false);

        // ── Task line ──────────────────────────────────────────────────────────
        if (hasTask) {
            String trunc = task.length() > 22 ? task.substring(0, 19) + "…" : task;
            Component taskComp = Component.literal("» ").withStyle(Style.EMPTY.withColor(0x888888))
                    .append(Component.literal(trunc).withStyle(Style.EMPTY.withColor(0xDDDDDD)));
            gfx.text(mc.font, taskComp, x + 6, y + 27, 0xFFFFFF, false);
        }

        // ── Health bar ─────────────────────────────────────────────────────────
        float hp = nearest.getHealth();
        float maxHp = Math.max(nearest.getMaxHealth(), 1.0f);
        int barX = x + 5, barY = y + panelH - 7;
        int barW = panelW - 10;
        int filled = (int) (barW * Math.min(1.0f, hp / maxHp));

        gfx.fill(barX, barY, barX + barW, barY + 4, 0x66000000);
        if (filled > 0) gfx.fill(barX, barY, barX + filled, barY + 4, hpColor(hp, maxHp));
    }

    // ── Colour helpers ─────────────────────────────────────────────────────────

    private static int modeRGB(String mode) {
        return switch (mode) {
            case "FIGHTING"  -> 0xFF6B00;
            case "EXECUTING" -> 0xAA55FF;
            case "BUILDING"  -> 0x5588FF;
            case "GUARDING"  -> 0x55FFFF;
            case "FOLLOWING" -> 0x55FF55;
            default          -> 0xAAAAAA;
        };
    }

    private static int modeAlpha(String mode, int alpha) {
        int c = modeRGB(mode);
        return (alpha << 24) | (((c >> 16) & 0xFF) << 16) | (((c >> 8) & 0xFF) << 8) | (c & 0xFF);
    }

    private static int hpColor(float hp, float maxHp) {
        if (hp > maxHp * 0.5f) return 0xFF55FF55;
        if (hp > maxHp * 0.25f) return 0xFFFFAA00;
        return 0xFFFF5555;
    }

    private static String modeIcon(String mode) {
        return switch (mode) {
            case "FIGHTING"  -> "⚔";
            case "EXECUTING" -> "⟳";
            case "BUILDING"  -> "⚒";
            case "GUARDING"  -> "◉";
            case "FOLLOWING" -> "◈";
            default          -> "…";
        };
    }

    private static String friendlyMode(String mode) {
        return switch (mode) {
            case "FOLLOWING" -> "Following";
            case "EXECUTING" -> "Executing";
            case "BUILDING"  -> "Building";
            case "FIGHTING"  -> "Fighting";
            case "GUARDING"  -> "Guarding";
            default          -> "Idle";
        };
    }

    private static String thinkDots() {
        return switch ((tick / 8) % 4) {
            case 0  -> ".";
            case 1  -> "..";
            case 2  -> "...";
            default -> "";
        };
    }
}
