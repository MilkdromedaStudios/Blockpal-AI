package com.milkdromeda.blockpal.client.gui;

import com.milkdromeda.blockpal.client.assist.ChatMemory;
import com.milkdromeda.blockpal.client.assist.ClientAi;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The mini AI chat panel embedded straight into vanilla screens — press ESC and
 * it's sitting in the pause menu; open the chat screen and it's there too. A
 * quick way to ask the assistant something without going through a button into a
 * separate menu (the full {@link AssistantChatScreen} still exists for history,
 * threads and scrolling — the ⛶ button opens it).
 *
 * <p>Everything is injected through the Fabric screen API ({@code AFTER_INIT} +
 * {@code Screens.getWidgets}), the same proven path as the ✦ button, so no
 * vanilla screen is subclassed or patched. The panel is bottom-right anchored
 * and only installs when the screen has room for it (on the pause menu it stays
 * clear of the centered button column). Message lines are plain
 * {@link StringWidget}s recreated in place when a reply or tip arrives; the
 * input box itself is never rebuilt, so focus and typed text survive updates.
 *
 * <p>It talks to the same client-side {@link ClientAi} + {@link ChatMemory} as
 * the full chat box — private, advice-only, works on any server (or none).
 */
public final class MiniChatPanel {

    /** Widest the panel gets; it shrinks (or skips installing) on narrow GUIs. */
    private static final int MAX_W = 176;
    private static final int MIN_W = 96;
    /** Cap on visible message lines so the panel stays "mini" on tall screens. */
    private static final int MAX_LINES = 16;

    // ── the (single) live installation ────────────────────────────────────────────
    private static Screen screen;
    private static Font font;
    private static int left, panelW, msgTop, msgBottom;
    private static StringWidget header;
    private static EditBox input;
    private static Button sendBtn;
    private static final List<StringWidget> lineWidgets = new ArrayList<>();
    private static boolean waiting;
    /** Typed-but-unsent text, kept across screen switches so a draft never vanishes. */
    private static String draft = "";
    private static int ticks;

    private MiniChatPanel() {}

    /**
     * Installs the panel into {@code target} if it fits. {@code avoidCenterColumn}
     * keeps it clear of the pause menu's centered button stack; the chat screen
     * just anchors to the right edge (above the vanilla chat input line).
     *
     * @return true when the panel was installed.
     */
    public static boolean install(Minecraft client, Screen target, int width, int height,
                                  boolean avoidCenterColumn) {
        // A re-init (window resize) discards the old widgets; reset our refs too.
        captureDraft();
        clearRefs();

        Font f = Screens.getTextRenderer(target);
        if (f == null || height < 140) return false;
        int right = width - 6;
        int x0 = avoidCenterColumn
                ? Math.max(width / 2 + 108, width - MAX_W - 6)   // stay off the button column
                : width - Math.min(MAX_W, width / 3) - 6;
        if (right - x0 < MIN_W) return false;                    // tiny GUI scale — skip

        screen = target;
        font = f;
        left = x0;
        panelW = right - x0;

        int lineH = font.lineHeight + 1;
        int inputY = height - 60;
        msgBottom = inputY - 4;
        int maxLines = Math.min(MAX_LINES, Math.max(3, (msgBottom - 18) / lineH));
        msgTop = msgBottom - maxLines * lineH;

        var widgets = Screens.getWidgets(target);

        header = new StringWidget(left, msgTop - 12, panelW, 10, headerText(), font);
        widgets.add(header);

        input = new SendBox(font, left, inputY, panelW - 46, 18);
        input.setMaxLength(512);
        input.setHint(Component.literal("Ask the AI…"));
        input.setValue(draft);
        widgets.add(input);

        sendBtn = Button.builder(Component.literal("Send"), b -> send())
                .bounds(left + panelW - 44, inputY, 44, 18).build();
        widgets.add(sendBtn);

        Button full = Button.builder(Component.literal("Full chat & history ⛶"),
                        b -> client.setScreenAndShow(new AssistantChatScreen(target)))
                .bounds(left, height - 38, panelW, 16).build();
        widgets.add(full);

        ScreenEvents.remove(target).register(MiniChatPanel::onScreenRemoved);

        refresh();
        return true;
    }

    /** Called every client tick (from the mod initializer) to pick up async updates. */
    public static void tick() {
        if (screen == null) return;
        if (++ticks % 20 == 0) refresh();   // catches tips and other off-screen additions
    }

    // ── internals ─────────────────────────────────────────────────────────────────

    private static void onScreenRemoved(Screen s) {
        if (s != screen) return;
        captureDraft();
        clearRefs();
    }

    private static void captureDraft() {
        if (input != null) draft = input.getValue();
    }

    private static void clearRefs() {
        screen = null;
        font = null;
        header = null;
        input = null;
        sendBtn = null;
        lineWidgets.clear();
    }

    private static Component headerText() {
        String src = ClientAi.available() ? ClientAi.sourceLabel() : "no AI configured";
        String state = waiting ? " §b· thinking…" : "";
        return Component.literal("§b✦ Assistant §8· §7" + src + state);
    }

    /**
     * Re-lays the message lines (bottom-anchored, newest at the bottom). Only the
     * plain text widgets are recreated — the input box and buttons are untouched,
     * so focus and in-progress typing survive every refresh.
     */
    private static void refresh() {
        if (screen == null || font == null) return;
        var widgets = Screens.getWidgets(screen);
        widgets.removeAll(lineWidgets);
        lineWidgets.clear();

        int lineH = font.lineHeight + 1;
        int maxLines = (msgBottom - msgTop) / lineH;
        List<Component> lines = buildLines(panelW - 2, maxLines);
        int y = msgBottom - lines.size() * lineH;
        for (Component line : lines) {
            // Sized to the text so it renders flush-left (a full-width StringWidget centres).
            int lw = Math.min(panelW, Math.max(1, font.width(line)));
            StringWidget w = new StringWidget(left, y, lw, lineH, line, font);
            lineWidgets.add(w);
            widgets.add(w);
            y += lineH;
        }

        if (header != null) header.setMessage(headerText());
        if (sendBtn != null) sendBtn.active = !waiting;
    }

    static void send() {
        if (screen == null || waiting || input == null) return;
        String text = input.getValue().trim();
        if (text.isEmpty()) return;
        ChatMemory.addUser(text);
        input.setValue("");
        draft = "";
        waiting = true;
        List<String[]> history = ChatMemory.contextForModel();
        // The just-added user line is already in history; send the same text as the turn.
        ClientAi.chat(history.subList(0, Math.max(0, history.size() - 1)), text)
                .whenComplete((reply, ex) -> Minecraft.getInstance().execute(() -> {
                    waiting = false;
                    ChatMemory.addAssistant(ex != null ? "(the AI request failed — try again)" : reply);
                    refresh();
                }));
        refresh();
    }

    /** The wrapped tail of the current conversation, at most {@code maxLines} lines. */
    private static List<Component> buildLines(int maxWidth, int maxLines) {
        List<Component> out = new ArrayList<>();
        ChatMemory.Conversation conv = ChatMemory.current();
        if (conv.messages.isEmpty()) {
            out.add(Component.literal("Ask me anything — tips, recipes, strategy…")
                    .withStyle(s -> s.withColor(0x6E8291)));
            return out;
        }
        // Only the last few messages can possibly fit, so don't wrap the whole history.
        int from = Math.max(0, conv.messages.size() - 8);
        for (int i = from; i < conv.messages.size(); i++) {
            ChatMemory.Msg m = conv.messages.get(i);
            String prefix = switch (m.role == null ? "" : m.role) {
                case "assistant" -> "AI: ";
                case "tip" -> "Tip: ";
                default -> "You: ";
            };
            final int color = switch (m.role == null ? "" : m.role) {
                case "assistant" -> 0x9FE8C0;
                case "tip" -> 0xFFD479;
                default -> 0xD6E6F0;
            };
            for (String w : wrap(prefix + (m.text == null ? "" : m.text), maxWidth)) {
                out.add(Component.literal(w).withStyle(s -> s.withColor(color)));
            }
        }
        if (out.size() > maxLines) return out.subList(out.size() - maxLines, out.size());
        return out;
    }

    /** Greedy word-wrap to a pixel width, breaking over-long words as needed. */
    private static List<String> wrap(String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        for (String rawLine : text.split("\n", -1)) {
            StringBuilder cur = new StringBuilder();
            for (String word : rawLine.split(" ")) {
                while (font.width(word) > maxWidth && word.length() > 1) {
                    int cut = word.length();
                    while (cut > 1 && font.width(word.substring(0, cut)) > maxWidth) cut--;
                    String head = word.substring(0, cut);
                    if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
                    out.add(head);
                    word = word.substring(cut);
                }
                String candidate = cur.length() == 0 ? word : cur + " " + word;
                if (font.width(candidate) > maxWidth && cur.length() > 0) {
                    out.add(cur.toString());
                    cur = new StringBuilder(word);
                } else {
                    cur.setLength(0);
                    cur.append(candidate);
                }
            }
            out.add(cur.toString());
        }
        return out;
    }

    /**
     * An {@link EditBox} that submits on Enter. {@code keyPressed} deliberately has
     * no {@code @Override}: it's the standard input-event signature, but if this
     * Minecraft version ever changes it the method simply stops being called (and
     * the Send button remains the way to submit) instead of failing the build.
     */
    private static final class SendBox extends EditBox {
        SendBox(Font font, int x, int y, int w, int h) {
            super(font, x, y, w, h, Component.literal("Message"));
        }

        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == 257 || keyCode == 335) {   // Enter / numpad Enter
                MiniChatPanel.send();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }
}
