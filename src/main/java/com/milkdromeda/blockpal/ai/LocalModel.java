package com.milkdromeda.blockpal.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <b>The models Blockpal will download and run on your own machine.</b>
 *
 * <p>Every entry here is a <b>GGUF</b> quantisation — the format
 * {@code llama-server} reads — chosen so the whole thing fits comfortably in a
 * mid-range graphics card and on a normal internet connection. The hard rule is
 * {@link #MAX_BYTES}: <b>nothing over 3 GB</b>. A test asserts it, so a future entry
 * cannot quietly break the promise.
 *
 * <p>Sizes below are the real {@code Content-Length} of each file, checked against
 * HuggingFace rather than estimated, because the number is shown to the player before
 * they agree to the download and being wrong about it is a bad first impression.
 *
 * <p><b>Why 3B and not something bigger.</b> A 3-billion-parameter model at 4-bit is
 * roughly 2 GB on disk and about the same in VRAM, which fits a 4 GB card while leaving
 * room for Minecraft itself. It is not as clever as a hosted frontier model and it does
 * not pretend to be — but it is free, private, offline, and fast enough to answer in
 * about a second instead of five.
 */
public enum LocalModel {

    /**
     * The default. Good instruction-following for its size and reliable at emitting the
     * JSON and the little scripts Blockpal asks for.
     */
    QWEN3B("qwen3b", "Qwen2.5 3B Instruct",
            "bartowski/Qwen2.5-3B-Instruct-GGUF",
            "Qwen2.5-3B-Instruct-Q4_K_M.gguf",
            1_929_902_720L,
            "The best all-rounder that still fits a 4 GB card. Recommended."),

    /**
     * Same size, tuned for code — noticeably better at the script language the
     * look-and-write-code brain uses.
     */
    QWEN_CODER3B("coder3b", "Qwen2.5 Coder 3B",
            "bartowski/Qwen2.5-Coder-3B-Instruct-GGUF",
            "Qwen2.5-Coder-3B-Instruct-Q4_K_M.gguf",
            1_929_902_720L,
            "Tuned for writing code — better at the bot's script language, blunter in chat."),

    /** Meta's small instruct model, for people who prefer it. */
    LLAMA3B("llama3b", "Llama 3.2 3B Instruct",
            "bartowski/Llama-3.2-3B-Instruct-GGUF",
            "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            2_019_377_696L,
            "Meta's small instruct model. Chatty and friendly; a little weaker at JSON."),

    /** For laptops, integrated graphics and slow connections. */
    QWEN1_5B("qwen1.5b", "Qwen2.5 1.5B Instruct",
            "bartowski/Qwen2.5-1.5B-Instruct-GGUF",
            "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
            986_046_592L,
            "Half the size and twice the speed, for laptops and integrated graphics. Simpler answers.");

    /** The promise: nothing Blockpal offers to download is bigger than this. */
    public static final long MAX_BYTES = 3L * 1024 * 1024 * 1024;

    private final String id;
    private final String display;
    private final String repo;
    private final String file;
    private final long bytes;
    private final String blurb;

    LocalModel(String id, String display, String repo, String file, long bytes, String blurb) {
        this.id = id;
        this.display = display;
        this.repo = repo;
        this.file = file;
        this.bytes = bytes;
        this.blurb = blurb;
    }

    public String id() { return id; }
    public String display() { return display; }
    public String repo() { return repo; }
    public String fileName() { return file; }
    public long bytes() { return bytes; }
    public String blurb() { return blurb; }

    /** Where the weights come from. HuggingFace's {@code resolve/main} serves the file itself. */
    public String downloadUrl() {
        return "https://huggingface.co/" + repo + "/resolve/main/" + file + "?download=true";
    }

    /** "1.9 GB" — for the consent prompt. */
    public String sizeText() {
        return String.format(Locale.ROOT, "%.1f GB", bytes / 1_073_741_824.0);
    }

    /** Roughly how much video memory it wants, which is a little over the file size. */
    public String vramText() {
        return String.format(Locale.ROOT, "~%.1f GB", (bytes * 1.15) / 1_073_741_824.0);
    }

    public static LocalModel byId(String id) {
        if (id == null) return null;
        String needle = id.trim().toLowerCase(Locale.ROOT);
        for (LocalModel m : values()) {
            if (m.id.equals(needle)) return m;
        }
        return null;
    }

    /** The one used when nothing has been chosen. */
    public static LocalModel defaultModel() {
        return QWEN3B;
    }

    public static List<String> idList() {
        List<String> out = new ArrayList<>();
        for (LocalModel m : values()) out.add(m.id);
        return out;
    }

    /** A readable table for {@code /ai local models}. */
    public static String describeAll() {
        StringBuilder sb = new StringBuilder();
        for (LocalModel m : values()) {
            sb.append(String.format(Locale.ROOT, "%-10s %-24s %7s  %s%n",
                    m.id, m.display, m.sizeText(), m.blurb));
        }
        return sb.toString();
    }
}
