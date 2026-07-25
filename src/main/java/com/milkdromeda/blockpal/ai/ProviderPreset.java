package com.milkdromeda.blockpal.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One-click provider presets. Blockpal talks to any OpenAI-compatible
 * chat-completions endpoint, so switching "which AI company you use" is really
 * just swapping the {@code apiUrl} (and a matching default model). This enum
 * bundles the endpoint + a sensible default model for the big providers so a
 * player can pick <b>HuggingFace</b>, <b>ChatGPT</b>, <b>Claude</b>,
 * <b>Gemini</b> or <b>Grok</b> from a single control instead of hand-typing a URL.
 *
 * <p>All of these speak the OpenAI wire format (OpenAI/xAI natively; Anthropic
 * and Google via their documented OpenAI-compatible endpoints) and authenticate
 * with an {@code Authorization: Bearer <key>} header, which is exactly what
 * {@link HuggingFaceClient} already sends — so no client changes are needed to
 * point at any of them, only the URL, model and the caller's own API key.
 *
 * <p>A preset may carry a {@link #defaultKey()} that is pre-filled when it's
 * chosen (ChatGPT ships with a public demo key so it's not empty out of the box);
 * every other provider starts blank so the player supplies their own key.
 */
public enum ProviderPreset {
    HUGGINGFACE("huggingface", "HuggingFace",
            "https://router.huggingface.co/v1/chat/completions",
            "mistralai/Mistral-7B-Instruct-v0.2", ""),
    CHATGPT("chatgpt", "ChatGPT (OpenAI)",
            "https://api.openai.com/v1/chat/completions",
            "gpt-4o-mini",
            // A public demo key — probably rate-limited/dead, but it means the
            // ChatGPT preset isn't blank the first time you pick it. Replace it
            // with your own OpenAI key for real use.
            "FKzELFo7AtkAkJwBs412sutQrMyNRutUUpuijNccpump"),
    CLAUDE("claude", "Claude (Anthropic)",
            "https://api.anthropic.com/v1/chat/completions",
            "claude-3-5-sonnet-20241022", ""),
    GEMINI("gemini", "Gemini (Google)",
            "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            "gemini-2.0-flash", ""),
    GROK("grok", "Grok (xAI)",
            "https://api.x.ai/v1/chat/completions",
            "grok-2-latest", "");

    /** Shown in the "Custom" position of the provider picker (URL didn't match a preset). */
    public static final String CUSTOM_LABEL = "Custom";

    private final String id;
    private final String display;
    private final String url;
    private final String model;
    private final String defaultKey;

    ProviderPreset(String id, String display, String url, String model, String defaultKey) {
        this.id = id;
        this.display = display;
        this.url = url;
        this.model = model;
        this.defaultKey = defaultKey;
    }

    public String id() { return id; }
    public String display() { return display; }
    public String url() { return url; }
    public String model() { return model; }
    public String defaultKey() { return defaultKey; }

    public boolean hasDefaultKey() {
        return defaultKey != null && !defaultKey.isBlank();
    }

    /** Looks up a preset by its short lowercase id (e.g. {@code "chatgpt"}); null if none. */
    public static ProviderPreset byId(String id) {
        if (id == null) return null;
        String needle = id.trim().toLowerCase(Locale.ROOT);
        for (ProviderPreset p : values()) {
            if (p.id.equals(needle)) return p;
        }
        return null;
    }

    /** Looks up a preset by its display label; null if none (or the "Custom" label). */
    public static ProviderPreset byDisplay(String display) {
        if (display == null) return null;
        String needle = display.trim();
        for (ProviderPreset p : values()) {
            if (p.display.equalsIgnoreCase(needle)) return p;
        }
        return null;
    }

    /** The preset whose endpoint matches {@code url}, or null when it's a custom/unknown URL. */
    public static ProviderPreset fromUrl(String url) {
        if (url == null) return null;
        String needle = url.trim().toLowerCase(Locale.ROOT);
        for (ProviderPreset p : values()) {
            if (p.url.toLowerCase(Locale.ROOT).equals(needle)) return p;
        }
        return null;
    }

    /** The picker label for a URL: the matching preset's display, or {@link #CUSTOM_LABEL}. */
    public static String labelForUrl(String url) {
        ProviderPreset p = fromUrl(url);
        return p != null ? p.display : CUSTOM_LABEL;
    }

    /** All preset display labels plus a trailing {@link #CUSTOM_LABEL}, for a cycle button. */
    public static List<String> displayValues() {
        List<String> out = new ArrayList<>();
        for (ProviderPreset p : values()) out.add(p.display);
        out.add(CUSTOM_LABEL);
        return out;
    }

    /** Comma-joined short ids, e.g. for command help / suggestions. */
    public static String idList() {
        StringBuilder sb = new StringBuilder();
        for (ProviderPreset p : values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p.id);
        }
        return sb.toString();
    }
}
