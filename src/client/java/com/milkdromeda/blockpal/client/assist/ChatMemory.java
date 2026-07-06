package com.milkdromeda.blockpal.client.assist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The private chat history for the client-side AI assistant box. Client-only and
 * persisted to {@code config/blockpal/assistant-chats.json}, so your conversations
 * survive a restart. Nothing here ever touches the server.
 *
 * <p>Deliberately bounded, per the design ask: at most {@link #MAX_CONVERSATIONS}
 * saved conversations and {@link #MAX_MESSAGES} messages in each — the oldest roll
 * off the top so the file (and the API context we replay) stays small.
 */
public final class ChatMemory {

    public static final int MAX_MESSAGES = 100;
    public static final int MAX_CONVERSATIONS = 100;
    /** How many prior turns to replay to the model as context (keeps requests cheap). */
    private static final int CONTEXT_TURNS = 12;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("blockpal").resolve("assistant-chats.json");

    /** One stored message. {@code role} is "user", "assistant", or "tip". */
    public static final class Msg {
        public String role;
        public String text;
        public long time;

        public Msg() {}
        public Msg(String role, String text) {
            this.role = role;
            this.text = text;
            this.time = System.currentTimeMillis();
        }
    }

    /** One conversation thread. */
    public static final class Conversation {
        public String title = "Chat";
        public long updated = System.currentTimeMillis();
        public List<Msg> messages = new ArrayList<>();
    }

    private static final class Store {
        List<Conversation> conversations = new ArrayList<>();
        int current = 0;
    }

    private static Store store;

    private ChatMemory() {}

    private static synchronized Store store() {
        if (store == null) load();
        return store;
    }

    // ── access ──────────────────────────────────────────────────────────────────

    public static synchronized List<Conversation> conversations() {
        return store().conversations;
    }

    public static synchronized Conversation current() {
        Store s = store();
        if (s.conversations.isEmpty()) s.conversations.add(new Conversation());
        if (s.current < 0 || s.current >= s.conversations.size()) s.current = s.conversations.size() - 1;
        return s.conversations.get(s.current);
    }

    public static synchronized int currentIndex() {
        return store().current;
    }

    public static synchronized void select(int index) {
        Store s = store();
        if (index >= 0 && index < s.conversations.size()) {
            s.current = index;
            save();
        }
    }

    /** Starts a fresh conversation (rolling off the oldest once the cap is hit). */
    public static synchronized Conversation newConversation() {
        Store s = store();
        Conversation c = new Conversation();
        s.conversations.add(c);
        while (s.conversations.size() > MAX_CONVERSATIONS) s.conversations.remove(0);
        s.current = s.conversations.size() - 1;
        save();
        return c;
    }

    public static synchronized void addUser(String text) {
        add("user", text);
        // First user line becomes the conversation's title, trimmed for the tab.
        Conversation c = current();
        if ("Chat".equals(c.title) && text != null && !text.isBlank()) {
            c.title = text.length() > 24 ? text.substring(0, 23) + "…" : text;
        }
        save();
    }

    public static synchronized void addAssistant(String text) {
        add("assistant", text);
        save();
    }

    /** Records a proactive on-screen tip (kept in a dedicated "Tips" conversation). */
    public static synchronized void addTip(String text) {
        Store s = store();
        Conversation tips = null;
        for (Conversation c : s.conversations) {
            if ("Tips".equals(c.title)) { tips = c; break; }
        }
        if (tips == null) {
            tips = new Conversation();
            tips.title = "Tips";
            s.conversations.add(0, tips);
            if (s.current < s.conversations.size() - 1) s.current++; // keep the user on their thread
            while (s.conversations.size() > MAX_CONVERSATIONS) s.conversations.remove(s.conversations.size() - 1);
        }
        Msg m = new Msg("tip", text);
        tips.messages.add(m);
        while (tips.messages.size() > MAX_MESSAGES) tips.messages.remove(0);
        tips.updated = m.time;
        save();
    }

    private static void add(String role, String text) {
        Conversation c = current();
        Msg m = new Msg(role, text);
        c.messages.add(m);
        while (c.messages.size() > MAX_MESSAGES) c.messages.remove(0);
        c.updated = m.time;
    }

    /** The last few turns of the current conversation as {@code {role, text}} for the model. */
    public static synchronized List<String[]> contextForModel() {
        Conversation c = current();
        List<String[]> out = new ArrayList<>();
        int from = Math.max(0, c.messages.size() - CONTEXT_TURNS);
        for (int i = from; i < c.messages.size(); i++) {
            Msg m = c.messages.get(i);
            if ("tip".equals(m.role)) continue;              // tips aren't part of the dialogue
            out.add(new String[]{m.role, m.text});
        }
        return out;
    }

    // ── persistence ───────────────────────────────────────────────────────────────

    private static void load() {
        store = new Store();
        try {
            if (Files.exists(FILE)) {
                String json = Files.readString(FILE, StandardCharsets.UTF_8);
                Store loaded = GSON.fromJson(json, Store.class);
                if (loaded != null && loaded.conversations != null) store = loaded;
            }
        } catch (Exception ignored) {
            // Corrupt/unreadable history just starts fresh — it's only convenience data.
            store = new Store();
        }
        if (store.conversations == null) store.conversations = new ArrayList<>();
        if (store.conversations.isEmpty()) store.conversations.add(new Conversation());
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(store), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Best-effort; losing convenience history is not worth crashing over.
        }
    }
}
