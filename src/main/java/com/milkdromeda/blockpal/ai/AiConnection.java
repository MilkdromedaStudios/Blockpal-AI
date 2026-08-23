package com.milkdromeda.blockpal.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <b>How this server's bots get their intelligence — exactly one way at a time.</b>
 *
 * <p>Blockpal used to stack several "if this isn't set, try that" providers on top of
 * each other (a shared key, then Player2, then Ollama, then the free service). That was
 * confusing: two or three of them could be switched on at once and nobody could tell
 * which one a bot was actually talking to, or which one was being billed. This enum
 * makes the choice <b>exclusive</b> — a server picks one connection and that is the one
 * that runs. {@code ModConfig.setConnection(...)} turns the others off for you.
 *
 * <p>The modes:
 * <ul>
 *   <li>{@link #MCP} — <b>the easy one.</b> Blockpal runs an <i>MCP server</i> and you
 *       point an AI app you already pay for (Claude, ChatGPT, Grok, Gemini) at it. No
 *       API key is stored in the game at all: the AI connects to the world and drives
 *       the bot with tools. See {@code /ai mcp} for the in-game setup guide.</li>
 *   <li>{@link #API_KEY} — your own OpenAI-compatible key (HuggingFace, OpenAI,
 *       Anthropic, Google, xAI …) typed into the settings panel.</li>
 *   <li>{@link #PLAYER2} — the free Player2 app / its cloud.</li>
 *   <li>{@link #LOCAL} — <b>the keyless one.</b> Blockpal downloads a small model (never
 *       more than 3 GB) and runs it on this machine's graphics card. Free, private,
 *       offline, and it asks before downloading anything. This replaced the free internet
 *       service as the option that works without a key.</li>
 *   <li>{@link #OLLAMA} — a local Ollama (or LM Studio) server you manage yourself.</li>
 *   <li>{@link #FREE} — the old free keyless internet service. Superseded by
 *       {@link #LOCAL} and kept only so servers already on it keep working.</li>
 *   <li>{@link #OFF} — no AI. The bot still lives on its own with its built-in
 *       survival brain; it just won't plan or hold a conversation.</li>
 * </ul>
 */
public enum AiConnection {
    MCP("mcp", "MCP server (Claude / ChatGPT / Grok / Gemini)",
            "An AI app you already use connects to your world and drives the bot. No key stored in-game."),
    API_KEY("key", "My own API key",
            "Type an OpenAI-compatible key + model into the AI & API tab."),
    PLAYER2("player2", "Player2 app",
            "Install the free Player2 app — keyless local AI, or its cloud with a PLAYER2_KEY."),
    LOCAL("local", "Local AI on this machine's GPU",
            "Blockpal downloads a small model (under 3 GB) and runs it on your graphics card. "
                    + "Free, private and offline — it asks before downloading anything."),
    OLLAMA("ollama", "Local Ollama / LM Studio",
            "Run your own model on this machine, managed by you. No key, no internet."),
    FREE("free", "Free keyless internet service (legacy)",
            "A small shared internet model. Superseded by \"Local AI\", which is private and "
                    + "not rate-limited; kept so servers already using it keep working."),
    OFF("off", "No AI",
            "Bots still live, fight, eat and survive on their own — they just don't think with a model.");

    private final String id;
    private final String display;
    private final String blurb;

    AiConnection(String id, String display, String blurb) {
        this.id = id;
        this.display = display;
        this.blurb = blurb;
    }

    public String id() { return id; }
    public String display() { return display; }

    /** One-line explanation, shown in the settings panel tooltip and {@code /ai connection}. */
    public String blurb() { return blurb; }

    /** True when this connection talks to a language model from inside the game. */
    public boolean usesModel() {
        return this != MCP && this != OFF;
    }

    /** Looks up a connection by its short id ({@code "mcp"}, {@code "key"}, …); null if unknown. */
    public static AiConnection byId(String id) {
        if (id == null) return null;
        String needle = id.trim().toLowerCase(Locale.ROOT);
        for (AiConnection c : values()) {
            if (c.id.equals(needle)) return c;
        }
        // Friendly aliases so a typed word people expect still lands somewhere sane.
        return switch (needle) {
            case "apikey", "api_key", "api-key", "token" -> API_KEY;
            case "none", "disabled" -> OFF;
            case "lmstudio", "lm-studio" -> OLLAMA;
            case "gpu", "localgpt", "local-gpt", "localai" -> LOCAL;
            default -> null;
        };
    }

    /** Looks up a connection by its display label (used by the settings-panel cycler). */
    public static AiConnection byDisplay(String display) {
        if (display == null) return null;
        String needle = display.trim();
        for (AiConnection c : values()) {
            if (c.display.equalsIgnoreCase(needle)) return c;
        }
        return null;
    }

    /** All display labels, in order, for a cycle button. */
    public static List<String> displayValues() {
        List<String> out = new ArrayList<>();
        for (AiConnection c : values()) out.add(c.display);
        return out;
    }

    /** Comma-joined short ids, for command help and suggestions. */
    public static String idList() {
        StringBuilder sb = new StringBuilder();
        for (AiConnection c : values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(c.id);
        }
        return sb.toString();
    }
}
