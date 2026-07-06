package com.milkdromeda.blockpal.client.gui;

import com.milkdromeda.blockpal.client.assist.ChatMemory;
import com.milkdromeda.blockpal.client.assist.ClientAi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The private AI chat box: a small, scrollable, futuristic panel for talking to the
 * Blockpal helper without ever touching the server chat. Reachable from any screen
 * where the mouse is free (a tiny "✦" button is injected into the pause menu,
 * inventory and containers) and via {@code /aichat}.
 *
 * <p>Everything is drawn from widgets (no manual {@code render()}) so it survives the
 * rebuilds a chat reply triggers, exactly like {@code PossessionConsoleScreen}. The
 * message list is word-wrapped to the panel width and scrolls (wheel or the ▲/▼
 * buttons); history is persisted by {@link ChatMemory} (100 messages / 100 chats).
 */
public class AssistantChatScreen extends Screen {

    private final Screen parent;

    private int panelW;
    private int panelX;
    private int listTop;
    private int listBottom;
    private int lineH;

    private EditBox input;
    private String draft = "";
    private boolean waiting;
    /** True once this screen has been navigated away from (guards async rebuilds). */
    private boolean dead;

    /** Scroll position, in lines from the top; -1 means "stick to the newest". */
    private int scroll = -1;

    private final List<Component> lines = new ArrayList<>();

    public AssistantChatScreen(Screen parent) {
        super(Component.literal("Blockpal Assistant"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.lineH = this.font.lineHeight + 1;
        this.panelW = Math.min(300, this.width - 40);
        this.panelX = (this.width - panelW) / 2;
        this.listTop = 46;
        int inputY = this.height - 56;
        this.listBottom = inputY - 6;

        // -- header --
        addRenderableWidget(TechTheme.centered(this.font, this.width, 8, 12, TechTheme.title("Assistant")));

        ChatMemory.Conversation conv = ChatMemory.current();
        String src = ClientAi.available() ? ClientAi.sourceLabel() : "§cno AI configured";
        addRenderableWidget(TechTheme.centered(this.font, this.width, 22, 10,
                Component.literal("§7" + trim(conv.title, 28) + "  §8· §7" + src)));

        // -- conversation nav (mini buttons, top-left of the panel) --
        int navY = 34;
        addRenderableWidget(Button.builder(Component.literal("＋"), b -> { ChatMemory.newConversation(); scroll = -1; rebuild(); })
                .bounds(panelX, navY, 20, 14).build());
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> cycleConversation(-1))
                .bounds(panelX + 22, navY, 18, 14).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> cycleConversation(1))
                .bounds(panelX + 42, navY, 18, 14).build());
        // -- scroll buttons (top-right of the panel) --
        addRenderableWidget(Button.builder(Component.literal("▲"), b -> scrollBy(-3))
                .bounds(panelX + panelW - 40, navY, 18, 14).build());
        addRenderableWidget(Button.builder(Component.literal("▼"), b -> scrollBy(3))
                .bounds(panelX + panelW - 20, navY, 18, 14).build());

        // -- wrapped message lines, scrolled --
        buildLines(panelW - 12);
        int maxVisible = Math.max(1, (listBottom - listTop) / lineH);
        int total = lines.size();
        int top = (scroll < 0) ? Math.max(0, total - maxVisible) : Math.min(scroll, Math.max(0, total - maxVisible));
        this.scroll = (scroll < 0) ? -1 : top;   // keep sticky when at bottom
        int y = listTop;
        for (int i = top; i < total && i < top + maxVisible; i++) {
            Component line = lines.get(i);
            addRenderableWidget(new StringWidget(panelX + 6, y, panelW - 12, lineH, line, this.font));
            y += lineH;
        }
        if (total == 0) {
            addRenderableWidget(new StringWidget(panelX + 6, listTop, panelW - 12, lineH,
                    Component.literal("§8Ask me anything — recipes, tips, where to find things…"), this.font));
        }

        // -- input row --
        input = new EditBox(this.font, panelX, inputY, panelW - 56, 18, Component.literal("Message"));
        input.setMaxLength(512);
        input.setHint(Component.literal(waiting ? "Thinking…" : "Ask the assistant…"));
        input.setValue(draft);
        input.setEditable(!waiting);
        addRenderableWidget(input);
        setInitialFocus(input);
        addRenderableWidget(Button.builder(Component.literal("Send"), b -> send())
                .bounds(panelX + panelW - 52, inputY, 52, 18).build());

        // -- bottom row --
        int by = this.height - 30;
        int bw = (panelW - 6) / 2;
        boolean tipsOn = ClientAi.cfg().assistantTips;
        addRenderableWidget(Button.builder(Component.literal(tipsOn ? "Tips: ON" : "Tips: off"), b -> {
            ClientAi.cfg().assistantTips = !ClientAi.cfg().assistantTips;
            ClientAi.cfg().save();
            rebuild();
        }).bounds(panelX, by, bw, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(panelX + bw + 6, by, bw, 18).build());
    }

    // ── actions ─────────────────────────────────────────────────────────────────────

    private void send() {
        if (waiting) return;
        String text = input == null ? "" : input.getValue().trim();
        if (text.isEmpty()) return;
        ChatMemory.addUser(text);
        if (input != null) input.setValue("");
        draft = "";
        scroll = -1;
        waiting = true;
        List<String[]> history = ChatMemory.contextForModel();
        // The just-added user line is already in history; send the same text as the turn.
        ClientAi.chat(history.subList(0, Math.max(0, history.size() - 1)), text)
                .whenComplete((reply, ex) -> Minecraft.getInstance().execute(() -> {
                    waiting = false;
                    ChatMemory.addAssistant(ex != null ? "(the AI request failed — try again)" : reply);
                    scroll = -1;
                    if (this.minecraft != null && !dead) rebuild();
                }));
        rebuild();
    }

    private void cycleConversation(int dir) {
        List<ChatMemory.Conversation> all = ChatMemory.conversations();
        if (all.isEmpty()) return;
        int idx = Math.floorMod(ChatMemory.currentIndex() + dir, all.size());
        ChatMemory.select(idx);
        scroll = -1;
        rebuild();
    }

    private void scrollBy(int deltaLines) {
        int maxVisible = Math.max(1, (listBottom - listTop) / lineH);
        int total = lines.size();
        int max = Math.max(0, total - maxVisible);
        int cur = (scroll < 0) ? max : scroll;
        int next = Math.max(0, Math.min(max, cur + deltaLines));
        scroll = (next >= max) ? -1 : next;
        rebuild();
    }

    /** Mouse-wheel scroll (4-arg form used by this MC version; ▲/▼ buttons also work). */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) { scrollBy(scrollY > 0 ? -2 : 2); return true; }
        return false;
    }

    private void rebuild() {
        if (input != null) draft = input.getValue();
        rebuildWidgets();
    }

    // ── line building ─────────────────────────────────────────────────────────────

    private void buildLines(int maxWidth) {
        lines.clear();
        ChatMemory.Conversation conv = ChatMemory.current();
        for (ChatMemory.Msg m : conv.messages) {
            String prefix = switch (m.role == null ? "" : m.role) {
                case "assistant" -> "AI: ";
                case "tip" -> "Tip: ";
                default -> "You: ";
            };
            int color = switch (m.role == null ? "" : m.role) {
                case "assistant" -> 0x9FE8C0;
                case "tip" -> 0xFFD479;
                default -> 0xD6E6F0;
            };
            List<String> wrapped = wrap(prefix + (m.text == null ? "" : m.text), maxWidth);
            for (String w : wrapped) {
                final int c = color;
                lines.add(Component.literal(w).withStyle(s -> s.withColor(c)));
            }
        }
    }

    /** Greedy word-wrap to a pixel width, breaking over-long words as needed. */
    private List<String> wrap(String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        for (String rawLine : text.split("\n", -1)) {
            StringBuilder cur = new StringBuilder();
            for (String word : rawLine.split(" ")) {
                // Break a single word that's wider than the box.
                while (this.font.width(word) > maxWidth && word.length() > 1) {
                    int cut = word.length();
                    while (cut > 1 && this.font.width(word.substring(0, cut)) > maxWidth) cut--;
                    String head = word.substring(0, cut);
                    if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
                    out.add(head);
                    word = word.substring(cut);
                }
                String candidate = cur.length() == 0 ? word : cur + " " + word;
                if (this.font.width(candidate) > maxWidth && cur.length() > 0) {
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

    private static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    @Override
    public void removed() {
        dead = true;
        super.removed();
    }

    @Override
    public void onClose() {
        Screen ret = returnScreen();
        if (ret == null || this.minecraft == null) {
            super.onClose();          // engine closes to the game (no setScreen needed)
        } else {
            this.minecraft.setScreenAndShow(ret);
        }
    }

    /** Return to the pause screen we came from, but not to a (now server-closed) container. */
    private Screen returnScreen() {
        if (parent == null) return null;
        // Leaving a container sends a close packet, so it can't be reopened — go to the game.
        if (parent instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) return null;
        return parent;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        TechTheme.backdrop(g, this.width, this.height);
        int x0 = panelX - 6, x1 = panelX + panelW + 6;
        TechTheme.panel(g, x0, 2, x1, this.height - 2);
        TechTheme.rule(g, panelX, panelX + panelW, 31);
        // message well
        g.fill(panelX, listTop - 4, panelX + panelW, listBottom + 2, 0x66020609);
        g.outline(panelX, listTop - 4, panelW, (listBottom + 2) - (listTop - 4), TechTheme.ACCENT_DIM);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
