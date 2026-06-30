package com.milkdromeda.blockpal.client.gui;

import com.milkdromeda.blockpal.ai.Personality;
import com.milkdromeda.blockpal.network.BotActionPayload;
import com.milkdromeda.blockpal.network.BotListData;
import com.milkdromeda.blockpal.network.BotListData.BotInfo;
import com.milkdromeda.blockpal.network.BotListRequestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The visual <b>Bots</b> manager: a server on a busy world can have many companions,
 * so this lists every bot (with its owner), lets you pick one, and gives you buttons
 * to manage just that bot — instead of acting on "the nearest" one. What you're
 * allowed to do per bot comes from the server ({@link BotInfo#canCommand()} /
 * {@link BotInfo#canManage()}); the server re-checks again when an action runs.
 */
public class BotManagerScreen extends Screen {

    private static final int NAV_Y = 20, NAV_H = 14;
    private static final int BODY_TOP = 42;
    private static final int FOOTER = 30;
    private static final int CONTENT_W = 330;
    private static final int LIST_W = 150;
    private static final int GAP = 10;

    /** Remembers the last-picked bot so a server re-sync (or reopening) keeps the selection. */
    private static int lastSelected = -1;

    private final BotListData data;
    private int selectedId;

    // Right-panel widgets we read when an action button is pressed.
    private EditBox renameBox, skinBox;
    private CycleButton<String> personalityCycle;
    private String pPersonality = Personality.DEFAULT.id();

    public BotManagerScreen(BotListData data, int preselectId) {
        super(Component.literal("Blockpal — Bots"));
        this.data = data != null ? data : new BotListData(new ArrayList<>());
        this.selectedId = preselectId;
        // If nothing is preselected (or it's gone), default to the first bot.
        if (find(selectedId) == null && !this.data.bots().isEmpty()) {
            this.selectedId = this.data.bots().get(0).entityId();
        }
        lastSelected = this.selectedId;
    }

    public int selectedId() { return selectedId; }

    /** The bot selected most recently, so a fresh open/refresh can restore it. */
    public static int lastSelected() { return lastSelected; }

    private BotInfo find(int id) {
        for (BotInfo b : data.bots()) if (b.entityId() == id) return b;
        return null;
    }

    @Override
    protected void init() {
        renameBox = skinBox = null;
        personalityCycle = null;

        addRenderableWidget(new StringWidget(0, 6, this.width, 12, this.title, this.font));
        PanelNav.build(this.width, CONTENT_W, NAV_Y, NAV_H, PanelNav.Tab.BOTS, isAdminClient(), this::addRenderableWidget);

        int leftX = this.width / 2 - CONTENT_W / 2;
        int listBottom = this.height - FOOTER;
        int maxHeight = Math.max(20, listBottom - BODY_TOP);

        // ── left: scrollable bot picker ──
        if (data.bots().isEmpty()) {
            addRenderableWidget(new StringWidget(leftX, BODY_TOP, CONTENT_W, 12,
                    Component.literal("No bots on the server yet. Summon one with /ai summon.")
                            .withStyle(ChatFormatting.GRAY), this.font));
        } else {
            LinearLayout list = LinearLayout.vertical().spacing(2);
            for (BotInfo b : data.bots()) {
                boolean current = b.entityId() == selectedId;
                Component label = Component.literal(trim(b.name(), 16) + " §7(" + trim(b.owner(), 10) + ")")
                        .withStyle(current ? ChatFormatting.YELLOW : ChatFormatting.WHITE);
                Button entry = Button.builder(label, btn -> { selectedId = b.entityId(); lastSelected = selectedId; rebuildWidgets(); })
                        .bounds(0, 0, LIST_W, 18).build();
                entry.setTooltip(Tooltip.create(Component.literal(
                        b.name() + " — owner: " + b.owner() + "\n"
                                + b.mode().toLowerCase() + " · " + b.dim() + " @ " + b.x() + "," + b.y() + "," + b.z()
                                + "\nhp " + b.health() + "/" + b.maxHealth() + " · " + b.personality()
                                + " · trusted: " + b.trustedCount())));
                entry.active = !current;
                list.addChild(entry);
            }
            ScrollableLayout scroll = new ScrollableLayout(this.minecraft, list, maxHeight);
            scroll.setMinWidth(LIST_W);
            scroll.arrangeElements();
            scroll.setX(leftX);
            scroll.setY(BODY_TOP);
            scroll.visitWidgets(this::addRenderableWidget);
        }

        // ── right: details + actions for the selected bot ──
        int rightX = leftX + LIST_W + GAP;
        int rightW = CONTENT_W - LIST_W - GAP;
        BotInfo sel = find(selectedId);
        if (sel != null) buildDetails(sel, rightX, rightW);

        // ── footer: refresh + close ──
        int by = this.height - 24;
        addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> {
                    if (ClientPlayNetworking.canSend(BotListRequestPayload.TYPE)) {
                        ClientPlayNetworking.send(new BotListRequestPayload());
                    }
                }).bounds(this.width / 2 - CONTENT_W / 2, by, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(this.width / 2 + CONTENT_W / 2 - 100, by, 100, 20).build());
    }

    private void buildDetails(BotInfo sel, int x, int w) {
        int y = BODY_TOP;
        addRenderableWidget(new StringWidget(x, y, w, 12,
                Component.literal(sel.name()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), this.font)); y += 13;
        addRenderableWidget(line(x, y, w, "Owner: " + sel.owner() + (sel.ownedByViewer() ? " (you)" : ""))); y += 11;
        addRenderableWidget(line(x, y, w, sel.mode().toLowerCase() + " · " + sel.dim())); y += 11;
        addRenderableWidget(line(x, y, w, "@ " + sel.x() + ", " + sel.y() + ", " + sel.z())); y += 11;
        addRenderableWidget(line(x, y, w, "Health " + sel.health() + "/" + sel.maxHealth())); y += 11;
        addRenderableWidget(line(x, y, w, "Personality: " + sel.personality() + " · trusted: " + sel.trustedCount())); y += 15;

        if (!sel.canCommand() && !sel.canManage()) {
            addRenderableWidget(new StringWidget(x, y, w, 11,
                    Component.literal("You can view this bot, but only " + sel.owner()
                            + " (or an admin) can control it.").withStyle(ChatFormatting.GRAY), this.font));
            return;
        }

        // Orders (come/follow/stay/stop) — need canCommand.
        int bw = (w - 6) / 4;
        addCmd(x, y, bw, "Come", "come", sel.canCommand());
        addCmd(x + bw + 2, y, bw, "Follow", "follow", sel.canCommand());
        addCmd(x + (bw + 2) * 2, y, bw, "Stay", "stay", sel.canCommand());
        addCmd(x + (bw + 2) * 3, y, bw, "Stop", "stop", sel.canCommand());
        y += 24;

        // Management (rename/skin/personality/dismiss) — need canManage.
        renameBox = new EditBox(this.font, x, y, w - 56, 18, Component.literal("Name"));
        renameBox.setMaxLength(32);
        renameBox.setValue(sel.name());
        renameBox.setEditable(sel.canManage());
        addRenderableWidget(renameBox);
        addApply(x + w - 52, y, "rename", () -> renameBox.getValue(), sel.canManage());
        y += 22;

        skinBox = new EditBox(this.font, x, y, w - 56, 18, Component.literal("Skin"));
        skinBox.setMaxLength(64);
        skinBox.setEditable(sel.canManage());
        skinBox.setHint(Component.literal("skin name"));
        addRenderableWidget(skinBox);
        addApply(x + w - 52, y, "skin", () -> skinBox.getValue(), sel.canManage());
        y += 22;

        pPersonality = builtinId(sel.personality());
        personalityCycle = CycleButton.<String>builder(id -> Component.literal(personalityLabel(id)), pPersonality)
                .withValues(personalityIds())
                .create(x, y, w - 56, 18, Component.literal("Personality"), (btn, val) -> pPersonality = val);
        personalityCycle.active = sel.canManage();
        addRenderableWidget(personalityCycle);
        addApply(x + w - 52, y, "personality", () -> pPersonality, sel.canManage());
        y += 24;

        Button dismiss = Button.builder(Component.literal("Dismiss bot").withStyle(ChatFormatting.RED),
                        b -> send("dismiss", ""))
                .bounds(x, y, w, 20).build();
        dismiss.active = sel.canManage();
        addRenderableWidget(dismiss);
    }

    private StringWidget line(int x, int y, int w, String text) {
        StringWidget sw = new StringWidget(x, y, w, 10, Component.literal(text).withStyle(ChatFormatting.GRAY), this.font);
        return sw;
    }

    private void addCmd(int x, int y, int w, String label, String action, boolean enabled) {
        Button b = Button.builder(Component.literal(label), btn -> send(action, "")).bounds(x, y, w, 20).build();
        b.active = enabled;
        addRenderableWidget(b);
    }

    private void addApply(int x, int y, String action, java.util.function.Supplier<String> arg, boolean enabled) {
        Button b = Button.builder(Component.literal("Apply"), btn -> send(action, arg.get())).bounds(x, y, 52, 18).build();
        b.active = enabled;
        addRenderableWidget(b);
    }

    private void send(String action, String arg) {
        if (ClientPlayNetworking.canSend(BotActionPayload.TYPE)) {
            ClientPlayNetworking.send(new BotActionPayload(selectedId, action, arg));
        }
    }

    private boolean isAdminClient() {
        // Heuristic for the tab bar only (server is authoritative): show the admin tab
        // if any bot reports the viewer can manage a bot they don't own.
        for (BotInfo b : data.bots()) if (b.canManage() && !b.ownedByViewer()) return true;
        return false;
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static List<String> personalityIds() {
        List<String> ids = new ArrayList<>();
        for (Personality p : Personality.values()) ids.add(p.id());
        return ids;
    }

    private static String personalityLabel(String id) {
        Personality p = Personality.byId(id);
        return p != null ? p.display() : id;
    }

    /** Maps a display label (e.g. "Custom" or "Grumpy") back to a built-in id, defaulting sensibly. */
    private static String builtinId(String label) {
        for (Personality p : Personality.values()) {
            if (p.display().equalsIgnoreCase(label) || p.id().equalsIgnoreCase(label)) return p.id();
        }
        return Personality.DEFAULT.id();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
