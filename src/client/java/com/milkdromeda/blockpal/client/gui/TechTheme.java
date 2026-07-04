package com.milkdromeda.blockpal.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * Blockpal's shared "holo-terminal" look: a deep space-navy backdrop with a faint
 * hologram grid, console plates with a cyan edge light and bracketed corners, and
 * neon-cyan headings — dark mode, blue, futuristic. Every Blockpal screen draws
 * this from {@code extractBackground(...)}, which the 26.x render-state pipeline
 * puts in its own stratum below all widgets, so vanilla widgets stay fully
 * functional on top of the themed chrome.
 */
public final class TechTheme {

    private TechTheme() {}

    // ── palette (ARGB) ──────────────────────────────────────────────────────────
    /** Main neon cyan — edge lights, corner brackets, active accents. */
    public static final int ACCENT       = 0xFF3DE0FF;
    /** Dimmed cyan for quiet edges and separators. */
    public static final int ACCENT_DIM   = 0xFF14586F;
    /** Deep-navy screen gradient. */
    public static final int BG_TOP       = 0xF0040A12;
    public static final int BG_BOTTOM    = 0xF0081525;
    /** Console plate fill + its subtle inner top light. */
    public static final int PANEL_FILL   = 0xDE050D19;
    public static final int PANEL_LIGHT  = 0x5533C4E8;
    /** Hologram grid line (very faint). */
    public static final int GRID         = 0x08AEE7FF;

    // Text colors (RGB, for Component styles).
    public static final int TXT_ACCENT   = 0x3DE0FF;
    public static final int TXT_HEADER   = 0x7FEBFF;
    public static final int TXT_DIM      = 0x3E7A94;

    // ── chrome drawing ──────────────────────────────────────────────────────────

    /** Full-screen dark backdrop: navy gradient, faint grid, top/bottom frame lines. */
    public static void backdrop(GuiGraphicsExtractor g, int width, int height) {
        g.fillGradient(0, 0, width, height, BG_TOP, BG_BOTTOM);
        int step = 24;
        for (int x = step; x < width; x += step) g.fill(x, 0, x + 1, height, GRID);
        for (int y = step; y < height; y += step) g.fill(0, y, width, y + 1, GRID);
        g.fill(0, 0, width, 1, ACCENT_DIM);
        g.fill(0, height - 1, width, height, ACCENT_DIM);
    }

    /**
     * A console plate between the given outer corners: dark fill, dim cyan edge,
     * a light-catch line along the inner top, and bright bracketed corners.
     */
    public static void panel(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, PANEL_FILL);
        g.outline(x0, y0, x1 - x0, y1 - y0, ACCENT_DIM);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, PANEL_LIGHT);
        corners(g, x0, y0, x1, y1);
    }

    /** The bright L-shaped corner brackets (2px thick, 9px long). */
    private static void corners(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        int len = 9, t = 2;
        g.fill(x0, y0, x0 + len, y0 + t, ACCENT);          // top-left
        g.fill(x0, y0, x0 + t, y0 + len, ACCENT);
        g.fill(x1 - len, y0, x1, y0 + t, ACCENT);          // top-right
        g.fill(x1 - t, y0, x1, y0 + len, ACCENT);
        g.fill(x0, y1 - t, x0 + len, y1, ACCENT);          // bottom-left
        g.fill(x0, y1 - len, x0 + t, y1, ACCENT);
        g.fill(x1 - len, y1 - t, x1, y1, ACCENT);          // bottom-right
        g.fill(x1 - t, y1 - len, x1, y1, ACCENT);
    }

    /** A thin divider that is bright in the middle and dim at the ends. */
    public static void rule(GuiGraphicsExtractor g, int x0, int x1, int y) {
        int w = x1 - x0;
        g.fill(x0, y, x1, y + 1, ACCENT_DIM);
        g.fill(x0 + w / 4, y, x1 - w / 4, y + 1, ACCENT);
    }

    // ── text styling ────────────────────────────────────────────────────────────

    /** Screen title: dim "BLOCKPAL //" prefix + bright bold section name. */
    public static Component title(String section) {
        return Component.literal("BLOCKPAL // ")
                .withStyle(s -> s.withColor(TXT_DIM))
                .append(Component.literal(section.toUpperCase(java.util.Locale.ROOT))
                        .withStyle(s -> s.withColor(TXT_ACCENT).withBold(true)));
    }

    /** In-body section heading: "▶ HEADING" in header cyan. */
    public static Component header(String text) {
        return Component.literal("▶ ").withStyle(s -> s.withColor(TXT_ACCENT))
                .append(Component.literal(text).withStyle(s -> s.withColor(TXT_HEADER).withBold(true)));
    }

    /** Quiet caption text in the theme's dim blue. */
    public static Component dim(String text) {
        return Component.literal(text).withStyle(s -> s.withColor(TXT_DIM));
    }

    /** Applies the accent color to an existing style (for active tabs etc.). */
    public static Style accent(Style s) {
        return s.withColor(TXT_ACCENT).withBold(true);
    }

    /**
     * A label horizontally centered on the screen. 26.2's {@link StringWidget}
     * left-aligns (and marquee-scrolls) its text, so centering means sizing the
     * widget to the text and positioning it ourselves.
     */
    public static StringWidget centered(Font font, int screenWidth, int y, int h, Component text) {
        int tw = font.width(text);
        return new StringWidget(screenWidth / 2 - tw / 2, y, tw, h, text, font);
    }
}
