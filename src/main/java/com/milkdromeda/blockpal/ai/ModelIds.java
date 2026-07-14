package com.milkdromeda.blockpal.ai;

/**
 * Cleanup + sanity advice for user-entered model ids.
 *
 * <p>Model ids get pasted from web pages, chat and readme files, so they arrive
 * with stray whitespace, wrapping quotes, line-wrap artifacts and the invisible
 * zero-width characters some pages copy along — none of which any
 * OpenAI-compatible API accepts, and all of which produce an opaque HTTP 400.
 * {@link #clean} strips all of that wherever a model id enters the mod.
 *
 * <p>{@link #advice} additionally recognises ids that name a <b>download-format
 * repository</b> (…-GGUF, …-GPTQ, …-AWQ and friends). Those repos are quantized
 * <i>file bundles</i> for local runtimes (llama.cpp, LM Studio, Ollama) — hosted
 * inference APIs like the HuggingFace router don't serve them, which is a classic
 * source of "my model id is valid but the API returns 400" confusion: the repo
 * exists on the hub, but no inference provider hosts it.
 */
public final class ModelIds {

    private ModelIds() {}

    /**
     * Normalizes a player/admin-entered model id: trims, strips one layer of
     * wrapping quotes/backticks, and removes all whitespace and zero-width/BOM
     * characters (model ids never legitimately contain any of these).
     */
    public static String clean(String raw) {
        if (raw == null) return "";
        String s = raw.strip();
        while (s.length() >= 2 && "\"'`".indexOf(s.charAt(0)) >= 0
                && s.charAt(s.length() - 1) == s.charAt(0)) {
            s = s.substring(1, s.length() - 1).strip();
        }
        return s.replaceAll("[\\s\\u00A0\\u200B\\u200C\\u200D\\uFEFF]+", "");
    }

    /**
     * A human-readable warning when {@code id} probably can't work on a hosted
     * API, or {@code null} when it looks fine. Plain text — callers add their own
     * chat colour codes.
     */
    public static String advice(String id) {
        if (id == null || id.isBlank()) return null;
        String base = stripFormatSuffix(id);
        if (!base.equals(id)) {
            return "\"" + id + "\" names a quantized download bundle — GGUF/GPTQ/AWQ repos are files "
                    + "for local apps (llama.cpp, LM Studio, Ollama), and hosted APIs don't serve them. "
                    + "For an API, use the base model id instead, e.g. \"" + base + "\" (and check its "
                    + "HuggingFace page lists Inference Providers).";
        }
        return null;
    }

    /** {@code "Qwen/Qwen2.5-Coder-3B-Instruct-GGUF"} → {@code "Qwen/Qwen2.5-Coder-3B-Instruct"}. */
    static String stripFormatSuffix(String id) {
        return id.replaceFirst("(?i)[-_.](gguf|ggml|awq|gptq|exl2|mlx)$", "");
    }
}
