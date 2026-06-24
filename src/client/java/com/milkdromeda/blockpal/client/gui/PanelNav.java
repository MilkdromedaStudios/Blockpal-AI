package com.milkdromeda.blockpal.client.gui;

import com.milkdromeda.blockpal.network.AdminActionPayload;
import com.milkdromeda.blockpal.network.ConfigRequestPayload;
import com.milkdromeda.blockpal.network.PlayerPrefsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The shared "panel switcher" tab bar that sits at the top of every Blockpal
 * screen, so the three panels — server <b>Settings</b>, the <b>Admin</b> panel,
 * and the per-player <b>My Settings</b> — all feel like one place.
 *
 * <p>Switching a tab just asks the server for that panel's data (the server is
 * authoritative for all of them); the matching sync packet then opens the right
 * screen. Non-admins only ever see the "My Settings" tab.
 */
public final class PanelNav {

    private PanelNav() {}

    public enum Tab { SETTINGS, ADMIN, ME }

    /** Adds the cross-panel tab buttons via {@code sink} (usually {@code this::addRenderableWidget}). */
    public static void build(int screenWidth, int width, int y, int h,
                             Tab active, boolean admin, Consumer<AbstractWidget> sink) {
        List<Tab> tabs = new ArrayList<>();
        if (admin) {
            tabs.add(Tab.SETTINGS);
            tabs.add(Tab.ADMIN);
        }
        tabs.add(Tab.ME);

        int gap = 2;
        int n = tabs.size();
        int bw = (width - gap * (n - 1)) / n;
        int x0 = screenWidth / 2 - width / 2;
        for (int i = 0; i < n; i++) {
            Tab t = tabs.get(i);
            boolean current = (t == active);
            Component label = current
                    ? Component.literal(labelOf(t)).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                    : Component.literal(labelOf(t));
            Button b = Button.builder(label, btn -> { if (!current) switchTo(t); })
                    .bounds(x0 + i * (bw + gap), y, bw, h).build();
            b.active = !current;   // the current tab reads as "pressed"
            sink.accept(b);
        }
    }

    private static String labelOf(Tab t) {
        return switch (t) {
            case SETTINGS -> "Settings";
            case ADMIN -> "Admin";
            case ME -> "My Settings";
        };
    }

    /** Asks the server to open another panel. Each request is answered with that panel's sync packet. */
    public static void switchTo(Tab t) {
        switch (t) {
            case SETTINGS -> {
                if (ClientPlayNetworking.canSend(ConfigRequestPayload.TYPE)) {
                    ClientPlayNetworking.send(new ConfigRequestPayload());
                }
            }
            case ADMIN -> {
                if (ClientPlayNetworking.canSend(AdminActionPayload.TYPE)) {
                    ClientPlayNetworking.send(new AdminActionPayload("refresh", 0));
                }
            }
            case ME -> {
                // A no-op prefs save doubles as "send me my prefs" (server re-syncs).
                if (ClientPlayNetworking.canSend(PlayerPrefsPayload.TYPE)) {
                    ClientPlayNetworking.send(new PlayerPrefsPayload("", "", false, "", ""));
                }
            }
        }
    }
}
