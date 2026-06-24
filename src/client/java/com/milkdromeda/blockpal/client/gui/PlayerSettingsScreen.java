package com.milkdromeda.blockpal.client.gui;

import com.milkdromeda.blockpal.ai.Personality;
import com.milkdromeda.blockpal.network.PlayerPrefsPayload;
import com.milkdromeda.blockpal.network.PlayerPrefsSyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The per-player "{@code /ai mymenu}" screen — open to everyone (unlike the admin
 * config menu). It lets a player pick their bot's model from the server's allowed
 * list, set their own API key privately (better than typing it in chat), and choose
 * their nearby bot's personality — a built-in one, or a free-text "custom" one that
 * the server safety-checks before applying.
 *
 * <p>Saving sends a {@link PlayerPrefsPayload}; the server applies it to the
 * sending player only (and their nearest owned bot) and replies with a fresh
 * {@link PlayerPrefsSyncPayload}, which reopens this screen showing the saved state.
 */
public class PlayerSettingsScreen extends Screen {

    private static final String CUSTOM = "custom";   // sentinel value in the personality cycler

    private static final int W = 260;
    private static final int FIELD_H = 20;

    private final PlayerPrefsSyncPayload data;
    private CycleButton<String> modelButton;
    private EditBox keyBox;
    private CycleButton<Boolean> clearKeyButton;
    private String chosenModel;

    private CycleButton<String> personalityButton;
    private EditBox customBox;
    private String chosenPersonality;
    // The personality state as the screen opened, so we only send a change (and so a
    // model-only save doesn't needlessly re-run moderation on the same custom text).
    private final boolean initialIsCustom;
    private final String initialPersonality;
    private final String initialCustom;

    public PlayerSettingsScreen(PlayerPrefsSyncPayload data) {
        super(Component.literal("Blockpal — My Settings"));
        this.data = data;
        this.chosenModel = data.currentModel();
        this.initialCustom = data.currentCustom() == null ? "" : data.currentCustom();
        this.initialIsCustom = data.allowCustom() && !initialCustom.isBlank();
        this.initialPersonality = (data.currentPersonality() == null || data.currentPersonality().isBlank())
                ? Personality.DEFAULT.id() : data.currentPersonality();
        this.chosenPersonality = initialIsCustom ? CUSTOM : initialPersonality;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - W / 2;
        int y = 44;

        addRenderableWidget(new StringWidget(0, 6, this.width, 12, this.title, this.font));

        // -- shared cross-panel tab bar (admins also see Settings & Admin) --
        PanelNav.build(this.width, W, 22, 16, PanelNav.Tab.ME, data.isAdmin(), this::addRenderableWidget);

        // -- model picker --
        addRenderableWidget(new StringWidget(x, y, W, 10, Component.literal("§eBot model"), this.font));
        y += 12;
        if (data.canChooseModel() && !data.allowedModels().isEmpty()) {
            String init = data.allowedModels().contains(chosenModel)
                    ? chosenModel : data.allowedModels().get(0);
            chosenModel = init;
            modelButton = CycleButton.<String>builder(s -> Component.literal(shorten(s)), init)
                    .withValues(data.allowedModels())
                    .create(x, y, W, FIELD_H, Component.literal("Model"), (btn, val) -> chosenModel = val);
            modelButton.setTooltip(Tooltip.create(Component.literal("Which AI model your companion uses.")));
            addRenderableWidget(modelButton);
        } else {
            addRenderableWidget(new StringWidget(x, y + 4, W, 10,
                    Component.literal("§7" + shorten(chosenModel)
                            + (data.canChooseModel() ? "" : "  (locked by server)")), this.font));
        }
        y += FIELD_H + 6;

        // -- personality picker (applies to your nearby bot) --
        addRenderableWidget(new StringWidget(x, y, W, 10, Component.literal("§ePersonality"), this.font));
        y += 12;
        List<String> values = new ArrayList<>();
        for (Personality p : Personality.values()) values.add(p.id());
        if (data.allowCustom()) values.add(CUSTOM);
        if (!values.contains(chosenPersonality)) chosenPersonality = values.get(0);
        personalityButton = CycleButton.<String>builder(s -> Component.literal(personalityLabel(s)), chosenPersonality)
                .withValues(values)
                .create(x, y, W, FIELD_H, Component.literal("Personality"), (btn, val) -> chosenPersonality = val);
        personalityButton.setTooltip(Tooltip.create(Component.literal(
                "How your nearby bot talks and acts. Stand near it when you save.")));
        addRenderableWidget(personalityButton);
        y += FIELD_H + 4;

        customBox = new EditBox(this.font, x, y, W, FIELD_H, Component.literal("Custom personality"));
        customBox.setMaxLength(300);
        customBox.setValue(initialCustom);
        customBox.setHint(Component.literal(data.allowCustom()
                ? "custom: e.g. a wise old wizard" : "custom personalities are off"));
        customBox.setEditable(data.allowCustom());
        customBox.setTooltip(Tooltip.create(Component.literal(
                "Used when Personality = custom. Checked by the AI to be family-friendly before it's applied.")));
        addRenderableWidget(customBox);
        y += FIELD_H + 8;

        // -- personal API key --
        addRenderableWidget(new StringWidget(x, y, W, 10, Component.literal("§ePersonal API key"), this.font));
        y += 12;
        keyBox = new EditBox(this.font, x, y, W, FIELD_H, Component.literal("API key"));
        keyBox.setMaxLength(256);
        keyBox.setHint(Component.literal(data.hasPersonalKey() ? "set — blank keeps it" : "paste your token"));
        keyBox.setTooltip(Tooltip.create(Component.literal(
                "Your own API key (kept private & obfuscated). Leave blank to keep the current one.")));
        addRenderableWidget(keyBox);
        y += FIELD_H + 4;

        clearKeyButton = CycleButton.onOffBuilder(false)
                .create(x, y, W, FIELD_H, Component.literal("Clear my key"));
        addRenderableWidget(clearKeyButton);
        y += FIELD_H + 8;

        // -- status line --
        String status = data.hasPersonalKey() ? "§aPersonal key set ✓" : "§7No personal key set";
        if (data.requireOwnKey()) {
            status += data.whitelisted()
                    ? "  §7(server requires own key — you're exempt)"
                    : "  §c(this server requires your own key)";
        }
        addRenderableWidget(new StringWidget(x, y, W, 10, Component.literal(status), this.font));
        y += 16;

        // -- buttons --
        int bw = (W - 8) / 2;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .bounds(x, y, bw, FIELD_H).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(x + bw + 8, y, bw, FIELD_H).build());
    }

    private void save() {
        boolean clear = clearKeyButton != null && clearKeyButton.getValue();
        String token = keyBox == null ? "" : keyBox.getValue();
        String model = chosenModel == null ? "" : chosenModel;

        // Only send a personality change (so a model-only save doesn't re-moderate the
        // same custom text). Blank both fields = "leave the bot's personality alone".
        String sendBuiltin = "";
        String sendCustom = "";
        String pers = chosenPersonality == null ? "" : chosenPersonality;
        if (pers.equals(CUSTOM)) {
            String txt = customBox == null ? "" : customBox.getValue().trim();
            boolean changed = !(initialIsCustom && txt.equals(initialCustom.trim()));
            if (changed && !txt.isEmpty()) sendCustom = txt;
        } else if (!pers.isEmpty()) {
            boolean changed = initialIsCustom || !pers.equals(initialPersonality);
            if (changed) sendBuiltin = pers;
        }

        if (ClientPlayNetworking.canSend(PlayerPrefsPayload.TYPE)) {
            ClientPlayNetworking.send(new PlayerPrefsPayload(model, token, clear, sendBuiltin, sendCustom));
        }
        // The server applies it and re-syncs, which reopens this screen refreshed.
    }

    /** A friendly label for the personality cycler: "Personality: Friendly" / "…: Custom". */
    private static String personalityLabel(String id) {
        if (CUSTOM.equals(id)) return "Personality: Custom";
        Personality p = Personality.byId(id);
        return "Personality: " + (p != null ? p.display() : id);
    }

    /** Shortens a long "org/Model-Name" id to just the model name for the button label. */
    private static String shorten(String model) {
        if (model == null || model.isBlank()) return "(default)";
        int slash = model.indexOf('/');
        return (slash > 0 && model.length() > 28) ? model.substring(slash + 1) : model;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
