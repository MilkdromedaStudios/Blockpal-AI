package com.milkdromeda.blockpal.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Schema version of this config file. Bumped whenever a new setting is added
     * so an older file (which lacks the field) can be migrated to a sensible
     * default instead of silently inheriting Java's zero/false. A file with no
     * version at all reads back as {@code 0} and is migrated from there.
     */
    public static final int CURRENT_CONFIG_VERSION = 11;

    // Settings (including the API key) live in their own folder under the game's
    // config directory. That directory is untouched when you replace the mod jar,
    // so your key and preferences carry over when you update the mod.
    private static final Path CONFIG_DIR = FabricLoader.getInstance()
            .getConfigDir().resolve("blockpal");
    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("config.json");
    // Older builds stored a single file here; it's migrated automatically.
    private static final Path LEGACY_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("blockpal.json");

    // Reversible obfuscation key for the token at rest. This is deliberately a
    // light, in-jar XOR — it stops the key being read at a glance, accidentally
    // pasted from config.json, or caught in a screenshot. It is NOT encryption
    // (the jar is decompilable): for real protection set BLOCKPAL_API_TOKEN as an
    // environment variable so the secret never touches disk. See wiki/Security.md.
    private static final byte[] OBF_KEY =
            "blockpal:token-obfuscation/v1".getBytes(StandardCharsets.UTF_8);

    private static ModConfig instance;

    // The live, in-memory token. It is never written to disk as plaintext: save()
    // persists only the obfuscated form (hfTokenObf), and an env-provided token is
    // never persisted at all. Kept non-transient so legacy plaintext files still load.
    public String hfToken = "";
    // Obfuscated token as stored on disk (see OBF_KEY). Players don't edit this.
    public String hfTokenObf = "";
    public String hfModel = "mistralai/Mistral-7B-Instruct-v0.2";
    // Modern HuggingFace router endpoint (OpenAI-compatible chat completions).
    // The old api-inference.huggingface.co endpoint is deprecated and causes
    // connection errors. You can point this at any OpenAI-compatible API
    // (HuggingFace, OpenAI, a local Ollama/LM Studio server, etc.).
    public String apiUrl = "https://router.huggingface.co/v1/chat/completions";
    public int maxNewTokens = 512;
    public double temperature = 0.7;
    public boolean debugLogging = false;
    // Lower = faster, snappier action execution (ticks between plan steps).
    public int actionTickDelay = 8;
    public double followDistance = 4.0;
    public double guardRadius = 16.0;
    // Self-preservation: flee/heal-up when health drops below this fraction.
    public double fleeHealthPercent = 0.25;
    // When true the assistant may execute Minecraft commands as part of a plan
    // (e.g. /setblock for redstone, /fill, /give). This is what lets it "do
    // almost anything". Gated to a permission level and a denylist for safety.
    public boolean allowCommands = true;
    // Permission level for commands the assistant runs (vanilla: 2 = command
    // block tier — allows /setblock, /fill, /summon, /give, /tp, /effect, but
    // NOT server-admin commands like /op or /stop, which need level 3-4).
    public int commandPermissionLevel = 2;
    // When true the assistant listens to normal chat and reacts to commands
    // like "Ethan, follow me" or "help me mine this tree" without needing /ai.
    public boolean chatListening = true;
    // When true the assistant analyses *every* chat message with the language
    // model to decide if you need it — so you don't have to use its name or any
    // exact command words. Requires an API token; ignored if chatListening off.
    public boolean activeMode = true;
    // Default name given to a freshly summoned assistant.
    public String defaultName = "Ethan";
    // Default skin for a freshly summoned assistant: "default"/"steve", a
    // "namespace:path.png" texture, or a name under
    // assets/blockpal/textures/entity/skins/<name>.png.
    public String defaultSkin = "default";

    // Default personality for a freshly summoned assistant (lowercase id from the
    // Personality enum, e.g. "friendly", "cheerful", "grumpy", "stoic", "heroic",
    // "shy"). Drives both how the bot talks and the tone of its AI plans. Each bot
    // remembers its own personality; change one with /ai personality <id>.
    public String defaultPersonality = "friendly";

    // When true, players may give their bot a free-text "custom" personality
    // (/ai personality custom <text> or the My Settings panel). The text is checked
    // by the language model for family-friendly safety before it's applied. Ops can
    // turn this off to restrict players to the built-in personalities only.
    public boolean allowCustomPersonality = true;

    // When true, a player may hand control of their OWN character to their nearby
    // companion ("possession mode", /ai possess): the AI then drives their movement
    // and actions from typed instructions. Fully server-authoritative, so it works on
    // any server running Blockpal and in singleplayer, with no client mod required to
    // be controlled. Admins can turn it off (/ai admin possession off).
    public boolean allowPossession = true;

    // ── Client-side assistant (works on ANY server, even without Blockpal) ──────────
    // These are read on the player's own client. They govern the private in-game AI
    // chat box and the client-side "drive my character" (possession) fallback used on
    // servers that don't run Blockpal. They never sync to, or depend on, the server.

    // Client-side possession: let the AI drive YOUR OWN character on a server that
    // doesn't have Blockpal, by simulating your inputs. Deliberately limited to basic
    // survival tasks (walk, mine, gather, place, use, sneak/jump) — it will NEVER
    // attack players or mobs and never types in chat or runs commands, so it can't be
    // used for a PvP/combat advantage. It is also hard-blocked on servers whose rules
    // forbid automation (Hypixel and other anti-cheat networks) regardless of this
    // flag. Off leaves only the always-safe advice chat below.
    public boolean allowClientPossession = true;

    // The private AI helper watches your on-screen situation and drops the occasional
    // short survival tip into the assistant chat box (never the server chat). Purely
    // informational — it never controls you — so it's safe on every server.
    public boolean assistantTips = true;

    // ── Voice (3.19.0) ────────────────────────────────────────────────────────────

    // SERVER gate for the whole voice layer: when off, the server never sends agent
    // speech to anyone and ignores push-to-talk input. Toggle on the Behavior tab
    // or with /ai admin voice on|off.
    public boolean allowVoice = true;

    // CLIENT-local: speak the agent's replies out loud (text-to-speech) on this
    // machine. Off = voice input still works, but the agent stays text-only.
    public boolean voiceResponses = true;

    // CLIENT-local: the push-to-talk key as a GLFW key code (default 86 = V). Held
    // to record, released to send. Deliberately a raw key code polled outside GUI
    // screens (change with /aivoice key <code>), so it can't clash with another
    // mod's keybinding registration.
    public int voicePushToTalkKey = 86;

    // CLIENT-local: speech-to-text endpoint + model for push-to-talk. The default
    // is Whisper large-v3-turbo on HuggingFace's serverless inference API (raw
    // audio POST, works with the same HF token as the chat models). With no token
    // at all, transcription falls back to the free voice-capable service instead.
    public String sttApiUrl = "https://router.huggingface.co/hf-inference/models/openai/whisper-large-v3-turbo";
    public String sttModel = "openai/whisper-large-v3-turbo";

    // CLIENT-local: the default text-to-speech voice used when a bot has no voice
    // of its own (per-bot voices are set with /ai voice set <id>). OpenAI-style
    // voice ids: alloy, echo, fable, onyx, nova, shimmer, coral, ...
    public String ttsVoice = "alloy";

    // Safety cap: automatically stop a running task after this many seconds, so a
    // task stuck in an endless loop can't keep running (and lagging) forever.
    // Ongoing activities like patrol/guard count against this too. 0 = no limit.
    public int maxTaskSeconds = 300;

    // Performance preset last applied by the user: "opus", "normal", or "potato".
    // Selecting a preset auto-fills several settings at once in the config GUI.
    public String performancePreset = "normal";

    // When true, sneak-right-clicking the assistant opens the settings menu.
    // Some players find this trips accidentally, so it can be turned off; the
    // menu is always reachable with /ai menu regardless of this setting.
    public boolean sneakToOpenMenu = true;

    // Minimum permission level a player needs to use the admin menu / global
    // controls and to CHANGE any server-wide setting (token, API URL, model,
    // command perms, etc.). Vanilla tiers: 0 = everyone, 2 = ops (command-block
    // tier, the default), 4 = full operator / single-player world owner.
    public int adminPermissionLevel = 2;

    // Hard cap on how many Blockpal entities may exist on the server at once.
    // /ai summon refuses past this. 0 = unlimited. Owner-controlled anti-grief /
    // anti-lag knob; change with /ai admin maxbots <n> or the admin menu.
    public int maxBotsPerServer = 8;

    // ---- Per-player API keys (bring-your-own-key, to avoid one big shared bill) ----

    // When true, a player's bot uses *that player's* own API key. Players without a
    // personal key get no AI planning/analysis — UNLESS they're on ownKeyWhitelist
    // (those keep using the server's shared key for free). Off by default, so a fresh
    // install behaves exactly as before. See wiki/Per-Player-Keys-and-Models.md.
    public boolean requireOwnApiKey = false;

    // Players exempt from requireOwnApiKey: they may use the server's shared token.
    // Stored as lowercased usernames (UUID strings also accepted/matched). Manage
    // with /ai admin keylist add|remove|list <player>.
    public List<String> ownKeyWhitelist = new ArrayList<>();

    // Per-player personal API keys, keyed by player UUID string, stored OBFUSCATED
    // at rest exactly like the shared token (never plaintext, never sent to clients).
    public Map<String, String> playerApiKeysObf = new HashMap<>();

    // ---- Player-selectable models ----

    // When true, players may pick their bot's model from allowedModels (via
    // /ai model <id>, the personal /ai mymenu screen, or the picker). When false,
    // everyone uses the server hfModel.
    public boolean allowPlayerModelChoice = true;

    // The curated set of models players may choose from (a model "whitelist").
    // Admin-managed with /ai admin models add|remove|list. hfModel is always kept in
    // the list so the server default is always selectable.
    public List<String> allowedModels = new ArrayList<>();

    // Per-player chosen model, keyed by player UUID string. Falls back to hfModel
    // when unset, disallowed, or player choice is turned off.
    public Map<String, String> playerModels = new HashMap<>();

    // ---- Free built-in AI (works with no key at all) ----

    // When no API key resolves for a request (no shared server key, no personal
    // key), fall back to this free, keyless OpenAI-compatible service so the
    // companion works straight out of the box. HuggingFace (apiUrl + a token)
    // stays the configured default and always wins the moment a token is set —
    // the free service is only ever the no-key fallback. Ops can turn it off to
    // make a real key strictly required again.
    public boolean freeAiFallback = true;

    // The free fallback endpoint + model. Pollinations is an open, keyless
    // OpenAI-compatible chat-completions service; any other keyless endpoint
    // (e.g. a local Ollama) can be pointed at here instead.
    public String freeApiUrl = "https://text.pollinations.ai/openai";
    public String freeModel = "openai";

    // ---- Local Ollama / custom local models ----

    // When true, and no personal/shared API key resolves for a request, the bot talks
    // to a LOCAL Ollama server (or any keyless OpenAI-compatible local server such as
    // LM Studio) instead of the free internet service. This is what lets you run your
    // own CUSTOM local models with no key and no internet. A real API key still wins;
    // Ollama sits above the free fallback. Toggle with /ai admin ollama on|off.
    public boolean ollamaEnabled = false;

    // Ollama's OpenAI-compatible chat-completions endpoint. The default is a local
    // Ollama's own compatibility route; point it at any local server you like.
    public String ollamaUrl = "http://localhost:11434/v1/chat/completions";

    // The default local model to use (any model you've `ollama pull`ed, e.g.
    // "llama3.2", "qwen2.5:3b", "phi3", "gemma2:2b"). Set with /ai admin ollama model <id>.
    public String ollamaModel = "llama3.2";

    // A pool of small local models the Growth village game hands out to its AI
    // villagers — one each, round-robin — so different villagers "think differently".
    // Any models you've pulled into Ollama. Managed with /ai admin ollama models ...
    public List<String> ollamaModels = new ArrayList<>();

    // ---- Player2 (player2.game) — the easiest keyless AI, local OR online ----

    // When true, and no personal/shared API key resolves, the bot uses Player2
    // (player2.game) — an OpenAI-compatible AI that works TWO ways:
    //   • LOCAL  — install the free Player2 app, no key needed (localhost:4315); or
    //   • ONLINE — a hosted cloud API (api.player2.game) used when a PLAYER2_KEY is set.
    // It sits ABOVE Ollama and the free service, but a real HuggingFace key still wins.
    // Toggle with /ai admin player2 on|off. This is the lowest-effort way to get real AI.
    public boolean player2Enabled = false;

    // Player2's LOCAL OpenAI-compatible endpoint (its desktop app serves this, keyless).
    public String player2Url = "http://localhost:4315/v1/chat/completions";

    // Player2's ONLINE (cloud) OpenAI-compatible endpoint, used when a PLAYER2_KEY is set.
    public String player2OnlineUrl = "https://api.player2.game/v1/chat/completions";

    // The model id sent to Player2. Defaults to the strong open model gpt-oss-120b
    // (Player2 serves it online); change with /ai admin player2 model <id>.
    public String player2Model = "gpt-oss-120b";

    // Optional Player2 cloud API key. PREFER the PLAYER2_KEY environment variable /
    // -Dplayer2.key system property, which is used but NEVER written to disk (the
    // same secure pattern as the main token). A value stored here is obfuscated at
    // rest like the main token. When a key resolves, Player2 runs ONLINE with it;
    // with no key it runs against the LOCAL app.
    public String player2KeyObf = "";
    // Live, in-memory Player2 key (env/property or deobfuscated store); never plaintext on disk.
    public transient String player2Key = "";
    private transient boolean player2KeyFromEnv = false;

    // ---- Behaviour tuning (survival feel) ----

    // When true, the planner is told to do things BY HAND like a survival player and to
    // treat RUN_COMMAND as a last resort (only when a task truly can't be done by hand).
    // Turn off to let the bot lean on commands (/fill, /setblock, /give) for efficiency.
    public boolean preferSurvivalActions = true;

    // When true, the bot adds small, slightly-randomised delays before picking things
    // up and between action steps, so it doesn't teleport loot into its hands or act
    // inhumanly fast. Off = snappy/instant (the old behaviour).
    public boolean humanizeActions = true;

    // ---- Growth village mini-game ----

    // Population the AI village must reach for the player to be allowed to surrender
    // ("as big as ever"). The player WINS instead if the village dies out (hits 0).
    public int villageTargetPopulation = 24;

    // How many AI villagers a fresh Growth game starts with.
    public int villageStartPopulation = 5;

    // Whether the first-run tutorial has already been shown. Fresh installs start
    // false (so the tutorial auto-opens once); upgrading installs are set true by
    // migrate() so existing servers aren't nagged.
    public boolean tutorialShown = false;

    // Schema version this file was written with — see CURRENT_CONFIG_VERSION.
    // Used only for migration; players don't need to touch it.
    public int configVersion = CURRENT_CONFIG_VERSION;

    // Runtime-only: true when the live token came from the BLOCKPAL_API_TOKEN env
    // var / -Dblockpal.apiToken property. Such a token is used but never persisted.
    private transient boolean tokenFromEnv = false;

    public static ModConfig get() {
        if (instance == null) load();
        return instance;
    }

    /** Loads settings, migrating the legacy file and surviving missing/corrupt data. */
    public static void load() {
        Path source = Files.exists(CONFIG_PATH) ? CONFIG_PATH
                : (Files.exists(LEGACY_PATH) ? LEGACY_PATH : null);
        if (source != null) {
            try (Reader r = Files.newBufferedReader(source)) {
                ModConfig loaded = GSON.fromJson(r, ModConfig.class);
                if (loaded != null) {
                    instance = loaded;
                    instance.deobfuscateToken();   // hfTokenObf -> live hfToken (must run before save)
                    instance.deobfuscatePlayer2Key();
                    instance.migrate();
                    instance.normalize();
                    instance.applyEnvToken();      // env/property override (never persisted)
                    instance.applyEnvPlayer2Key(); // PLAYER2_KEY env/property (never persisted)
                    save();   // (re)write into the folder, persisting the token obfuscated
                    return;
                }
            } catch (Exception e) {
                // Don't lose a recoverable key: keep the bad file as .bak, then fall
                // back to defaults rather than failing to start.
                backup(source);
                System.err.println("[Blockpal] Couldn't read config (" + e.getMessage()
                        + "); starting from defaults. Previous file kept as .bak");
            }
        }
        instance = new ModConfig();
        instance.applyEnvToken();
        instance.applyEnvPlayer2Key();
        save();
    }

    /**
     * Writes the config safely: the JSON is fully serialized in memory first, then
     * written to a temp file in the same folder and atomically moved over
     * {@code config.json} — so a crash, full disk, or antivirus interruption can
     * never leave a half-written (corrupt) settings file behind. The previous good
     * file is kept as {@code config.json.prev}, and a transient failure (e.g. a
     * virus scanner briefly locking the file on Windows) is retried once.
     *
     * @return true when the file was written; false is also logged so it's never silent.
     */
    public static synchronized boolean save() {
        // Persist the token only in obfuscated form, and never an env-provided
        // one. Swap the live plaintext out for serialization, then restore it.
        String plain = instance.hfToken == null ? "" : instance.hfToken;
        instance.hfTokenObf = obfuscate(instance.tokenFromEnv ? "" : plain);
        instance.hfToken = "";
        // Persist the Player2 key obfuscated too, and never an env-provided one.
        String p2plain = instance.player2Key == null ? "" : instance.player2Key;
        instance.player2KeyObf = obfuscate(instance.player2KeyFromEnv ? "" : p2plain);
        String json;
        try {
            json = GSON.toJson(instance);
        } finally {
            instance.hfToken = plain;
        }

        IOException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                writeAtomically(json);
                return true;
            } catch (IOException e) {
                last = e;
                // Give a transient lock (antivirus / sync tool) a moment, then retry.
                try { Thread.sleep(150); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        System.err.println("[Blockpal] Failed to save config to "
                + CONFIG_PATH.toAbsolutePath() + ": " + (last == null ? "interrupted" : last.getMessage()));
        return false;
    }

    /** Temp-file-then-move write so config.json is always either the old or the new file. */
    private static void writeAtomically(String json) throws IOException {
        Files.createDirectories(CONFIG_DIR);
        // Keep the last good file around for hand recovery (.bak is used for corrupt files).
        if (Files.exists(CONFIG_PATH)) {
            try {
                Files.copy(CONFIG_PATH, CONFIG_DIR.resolve("config.json.prev"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // Best-effort backup; never blocks the real save.
            }
        }
        Path tmp = CONFIG_DIR.resolve("config.json.tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Where the settings actually live on disk — {@code config/blockpal/config.json}
     * under the game directory of whatever launcher is running (a third-party
     * launcher like Lunar may use a different game folder than vanilla's
     * {@code .minecraft}, which is why the path is surfaced to players on save).
     */
    public static Path configPath() {
        return CONFIG_PATH.toAbsolutePath();
    }

    /**
     * Upgrades a config written by an older mod version. Fields that didn't exist
     * then deserialize to Java's default (false/0), so we restore their intended
     * default here based on the file's recorded {@link #configVersion}, then stamp
     * it as current. New installs (already at the current version) are untouched.
     */
    private void migrate() {
        if (configVersion < 1) {
            // sneakToOpenMenu was added in v1; older files default it to true.
            sneakToOpenMenu = true;
        }
        if (configVersion < 2) {
            // adminPermissionLevel / maxBotsPerServer were added in v2. Default an
            // upgrading install to ops-only admin (2) and an 8-bot cap rather than
            // the dangerous 0 (= everyone is admin / unlimited bots).
            adminPermissionLevel = 2;
            maxBotsPerServer = 8;
            // Any legacy plaintext token in hfToken is preserved here and gets
            // obfuscated on the save() that follows load().
        }
        if (configVersion < 3) {
            // Per-player keys / model choice were added in v3. Keep upgrading
            // installs behaving as before: shared key for everyone, model choice on.
            requireOwnApiKey = false;
            allowPlayerModelChoice = true;
            // Collections are seeded/guarded in normalize().
        }
        if (configVersion < 4) {
            // The tutorial was added in v4. An existing install already knows the
            // mod, so don't pop the tutorial for them — only fresh installs see it.
            tutorialShown = true;
        }
        if (configVersion < 5) {
            // Selectable personalities were added in v5. An upgrading install keeps
            // the historical tone, so default it to the friendly personality rather
            // than the empty string an older file deserializes to.
            defaultPersonality = "friendly";
        }
        if (configVersion < 6) {
            // Custom personalities were added in v6; allow them by default (an old
            // file deserializes the new boolean to false, which would silently
            // disable a feature we mean to ship on).
            allowCustomPersonality = true;
        }
        if (configVersion < 7) {
            // Possession mode was added in v7; ship it on by default (an old file
            // deserializes the new boolean to false, which would silently disable it).
            allowPossession = true;
        }
        if (configVersion < 8) {
            // The free keyless AI fallback was added in v8; ship it on by default so
            // servers without a key get a working companion (an old file deserializes
            // the new boolean to false, which would silently disable it).
            freeAiFallback = true;
        }
        if (configVersion < 9) {
            // The client-side assistant (private chat box + off-server possession) was
            // added in v9; ship both on by default (an old file deserializes the new
            // booleans to false, which would silently disable features we mean to ship).
            allowClientPossession = true;
            assistantTips = true;
        }
        if (configVersion < 10) {
            // Agent voice was added in v10; ship it on by default (an old file
            // deserializes the new booleans to false / the key code to 0). The
            // string fields (STT endpoint/model, default TTS voice) are seeded in
            // normalize() since they also cover a partially-edited file.
            allowVoice = true;
            voiceResponses = true;
            voicePushToTalkKey = 86;
        }
        if (configVersion < 11) {
            // Local Ollama support, survival-first behaviour and the Growth village
            // game were added in v11. Ship the survival-feel toggles ON by default
            // (an old file deserializes the new booleans to false, which would silently
            // disable them); leave Ollama OFF (opt-in — a real key / free AI still works
            // out of the box). The int/list fields are seeded in normalize().
            preferSurvivalActions = true;
            humanizeActions = true;
        }
        configVersion = CURRENT_CONFIG_VERSION;
    }

    /** Fills in sensible defaults for any field that came back null/blank/invalid. */
    private void normalize() {
        if (hfToken == null) hfToken = "";
        if (hfTokenObf == null) hfTokenObf = "";
        // A hand-edited/pasted model id may carry quotes or whitespace artifacts —
        // scrub them here so a broken saved id self-heals on the next load.
        hfModel = com.milkdromeda.blockpal.ai.ModelIds.clean(hfModel);
        if (hfModel.isBlank()) hfModel = "mistralai/Mistral-7B-Instruct-v0.2";
        if (apiUrl == null || apiUrl.isBlank()) apiUrl = "https://router.huggingface.co/v1/chat/completions";
        if (freeApiUrl == null || freeApiUrl.isBlank()) freeApiUrl = "https://text.pollinations.ai/openai";
        if (freeModel == null || freeModel.isBlank()) freeModel = "openai";
        if (ollamaUrl == null || ollamaUrl.isBlank()) ollamaUrl = "http://localhost:11434/v1/chat/completions";
        if (ollamaModel == null || ollamaModel.isBlank()) ollamaModel = "llama3.2";
        if (ollamaModels == null) ollamaModels = new ArrayList<>();
        if (player2Url == null || player2Url.isBlank()) player2Url = "http://localhost:4315/v1/chat/completions";
        if (player2OnlineUrl == null || player2OnlineUrl.isBlank()) player2OnlineUrl = "https://api.player2.game/v1/chat/completions";
        if (player2Model == null || player2Model.isBlank()) player2Model = "gpt-oss-120b";
        if (player2Key == null) player2Key = "";
        if (player2KeyObf == null) player2KeyObf = "";
        if (villageTargetPopulation < 2) villageTargetPopulation = 24;
        if (villageStartPopulation < 1) villageStartPopulation = 5;
        if (defaultName == null || defaultName.isBlank()) defaultName = "Ethan";
        if (defaultSkin == null || defaultSkin.isBlank()) defaultSkin = "default";
        if (com.milkdromeda.blockpal.ai.Personality.byId(defaultPersonality) == null) {
            defaultPersonality = "friendly";
        }
        if (sttApiUrl == null || sttApiUrl.isBlank()) {
            sttApiUrl = "https://router.huggingface.co/hf-inference/models/openai/whisper-large-v3-turbo";
        }
        if (sttModel == null || sttModel.isBlank()) sttModel = "openai/whisper-large-v3-turbo";
        if (ttsVoice == null || ttsVoice.isBlank()) ttsVoice = "alloy";
        if (voicePushToTalkKey <= 0) voicePushToTalkKey = 86;   // GLFW_KEY_V
        if (maxTaskSeconds < 0) maxTaskSeconds = 0;
        if (performancePreset == null || performancePreset.isBlank()) performancePreset = "normal";
        if (adminPermissionLevel < 0) adminPermissionLevel = 0;
        if (adminPermissionLevel > 4) adminPermissionLevel = 4;
        if (maxBotsPerServer < 0) maxBotsPerServer = 0;

        // Per-player collections must never be null (an old/partial file may omit them).
        if (ownKeyWhitelist == null) ownKeyWhitelist = new ArrayList<>();
        if (playerApiKeysObf == null) playerApiKeysObf = new HashMap<>();
        if (allowedModels == null) allowedModels = new ArrayList<>();
        if (playerModels == null) playerModels = new HashMap<>();
        // Scrub paste artifacts out of the allowed-model list too (dropping dupes
        // and entries that clean away to nothing).
        List<String> cleanedModels = new ArrayList<>();
        for (String m : allowedModels) {
            String c = com.milkdromeda.blockpal.ai.ModelIds.clean(m);
            if (!c.isBlank() && !cleanedModels.contains(c)) cleanedModels.add(c);
        }
        allowedModels = cleanedModels;
        // Seed a useful starter set of models the first time, and always keep the
        // server's own default model selectable.
        if (allowedModels.isEmpty()) {
            allowedModels.add(hfModel);
            allowedModels.add("meta-llama/Llama-3.1-8B-Instruct");
            allowedModels.add("Qwen/Qwen2.5-7B-Instruct");
            allowedModels.add("HuggingFaceH4/zephyr-7b-beta");
        }
        if (!allowedModels.contains(hfModel)) allowedModels.add(0, hfModel);
    }

    /** Recovers the live token from its obfuscated on-disk form (if needed). */
    private void deobfuscateToken() {
        if (hfToken == null) hfToken = "";
        if (hfToken.isBlank() && hfTokenObf != null && !hfTokenObf.isBlank()) {
            hfToken = deobfuscate(hfTokenObf);
        }
    }

    /** Lets server owners keep the key out of the config file entirely. */
    private void applyEnvToken() {
        String env = System.getProperty("blockpal.apiToken");
        if (env == null || env.isBlank()) env = System.getenv("BLOCKPAL_API_TOKEN");
        if (env != null && !env.isBlank()) {
            hfToken = env.trim();
            tokenFromEnv = true;
        }
    }

    /** Recovers the live Player2 key from its obfuscated on-disk form (if needed). */
    private void deobfuscatePlayer2Key() {
        if (player2Key == null) player2Key = "";
        if (player2Key.isBlank() && player2KeyObf != null && !player2KeyObf.isBlank()) {
            player2Key = deobfuscate(player2KeyObf);
        }
    }

    /**
     * Applies a PLAYER2_KEY from the environment / {@code -Dplayer2.key} property. Used
     * but NEVER written to disk — the secure way to supply the Player2 cloud key (a CI
     * build/test can inject it, a self-hosted server can set it), so it isn't baked into
     * the distributed jar. An env/property key overrides any value stored in config.
     */
    private void applyEnvPlayer2Key() {
        String env = System.getProperty("player2.key");
        if (env == null || env.isBlank()) env = System.getenv("PLAYER2_KEY");
        if (env != null && !env.isBlank()) {
            player2Key = env.trim();
            player2KeyFromEnv = true;
        }
    }

    /** The Player2 cloud key to use (may be ""). When blank, Player2 runs against the local app. */
    public String resolvePlayer2Key() {
        return player2Key == null ? "" : player2Key;
    }

    public boolean isPlayer2KeyFromEnv() {
        return player2KeyFromEnv;
    }

    /** Sets the Player2 key and marks it as a persisted (non-env) value. */
    public void setPlayer2Key(String key) {
        player2Key = key == null ? "" : key.trim();
        player2KeyFromEnv = false;
    }

    private static void backup(Path source) {
        try {
            Files.copy(source, source.resolveSibling(source.getFileName() + ".bak"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {}
    }

    public boolean hasApiToken() {
        return hfToken != null && !hfToken.isBlank();
    }

    /**
     * True when SOME language model is reachable server-wide: a shared key is set,
     * or the free keyless fallback is enabled. Gates the "can the AI run at all"
     * checks — {@link #hasApiToken()} stays strictly "is a key set" for display.
     */
    public boolean aiAvailable() {
        return hasApiToken() || player2Enabled || ollamaEnabled || freeAiFallback;
    }

    /**
     * True when a bot owned by {@code owner} can use SOME language model: a
     * personal/shared key resolves for them, the local Player2 app or a local Ollama
     * server is enabled, or the free fallback is enabled.
     */
    public boolean aiAvailableFor(UUID owner, String ownerName) {
        return !resolveTokenFor(owner, ownerName).isBlank()
                || player2Enabled || ollamaEnabled || freeAiFallback;
    }

    // ---- Local Ollama model pool (used by the Growth village game) ----

    /** @return true if added (false if blank or already present). */
    public boolean addOllamaModel(String model) {
        if (model == null || model.isBlank()) return false;
        String m = com.milkdromeda.blockpal.ai.ModelIds.clean(model);
        if (m.isBlank() || ollamaModels.contains(m)) return false;
        ollamaModels.add(m);
        return true;
    }

    /** @return true if an entry was removed. */
    public boolean removeOllamaModel(String model) {
        return model != null && ollamaModels.remove(com.milkdromeda.blockpal.ai.ModelIds.clean(model));
    }

    /**
     * The pool of models the village game hands out to villagers so they "think
     * differently". Prefers the curated {@link #ollamaModels} list; falls back to the
     * single {@link #ollamaModel}. Never empty.
     */
    public List<String> villageModelPool() {
        List<String> pool = new ArrayList<>();
        if (ollamaEnabled) {
            for (String m : ollamaModels) if (m != null && !m.isBlank()) pool.add(m);
            if (pool.isEmpty()) pool.add(ollamaModel);
        }
        if (pool.isEmpty()) {
            // No Ollama: fall back to the curated allowed-models list, then the default.
            for (String m : allowedModels) if (m != null && !m.isBlank()) pool.add(m);
        }
        if (pool.isEmpty()) pool.add(hfModel);
        return pool;
    }

    /** True when the active token came from the environment (never persisted). */
    public boolean isTokenFromEnv() {
        return tokenFromEnv;
    }

    /** Sets the API token and marks it as a persisted (non-env) value. */
    public void setToken(String token) {
        hfToken = token == null ? "" : token.trim();
        tokenFromEnv = false;
    }

    // ---- Per-player key & model resolution ----

    /**
     * The API token a bot owned by {@code owner} should use (may be {@code ""}).
     * A player's personal key always wins; otherwise, if {@link #requireOwnApiKey}
     * is on and the player isn't whitelisted, there is no key (AI is unavailable for
     * them); otherwise the shared server token is used.
     */
    public String resolveTokenFor(UUID owner, String ownerName) {
        if (owner != null) {
            String personal = getPlayerToken(owner);
            if (!personal.isBlank()) return personal;
        }
        if (requireOwnApiKey && !isKeyWhitelisted(ownerName, owner)) {
            return "";
        }
        return hfToken == null ? "" : hfToken;
    }

    /** The model a bot owned by {@code owner} should use. */
    public String resolveModelFor(UUID owner) {
        if (allowPlayerModelChoice && owner != null) {
            String m = playerModels.get(owner.toString());
            if (m != null && !m.isBlank() && isModelAllowed(m)) return m;
        }
        return hfModel;
    }

    public String getPlayerToken(UUID owner) {
        if (owner == null) return "";
        return deobfuscate(playerApiKeysObf.get(owner.toString()));
    }

    public boolean hasPlayerToken(UUID owner) {
        return !getPlayerToken(owner).isBlank();
    }

    /** Stores (blank clears) a player's personal API key — obfuscated at rest. */
    public void setPlayerToken(UUID owner, String raw) {
        if (owner == null) return;
        if (raw == null || raw.isBlank()) {
            playerApiKeysObf.remove(owner.toString());
        } else {
            playerApiKeysObf.put(owner.toString(), obfuscate(raw.trim()));
        }
    }

    public boolean isKeyWhitelisted(String name, UUID owner) {
        if (name != null && ownKeyWhitelist.contains(name.toLowerCase(Locale.ROOT))) return true;
        return owner != null && ownKeyWhitelist.contains(owner.toString());
    }

    /** @return true if added (false if it was already present). */
    public boolean addKeyWhitelist(String entry) {
        if (entry == null || entry.isBlank()) return false;
        String e = entry.trim().toLowerCase(Locale.ROOT);
        if (ownKeyWhitelist.contains(e)) return false;
        ownKeyWhitelist.add(e);
        return true;
    }

    /** @return true if an entry was removed. */
    public boolean removeKeyWhitelist(String entry) {
        return entry != null && ownKeyWhitelist.remove(entry.trim().toLowerCase(Locale.ROOT));
    }

    public boolean isModelAllowed(String model) {
        return model != null && allowedModels.contains(model);
    }

    public boolean addAllowedModel(String model) {
        if (model == null || model.isBlank()) return false;
        String m = model.trim();
        if (allowedModels.contains(m)) return false;
        allowedModels.add(m);
        return true;
    }

    public boolean removeAllowedModel(String model) {
        if (model == null) return false;
        String m = model.trim();
        if (m.equals(hfModel)) return false;   // never remove the server default
        return allowedModels.remove(m);
    }

    public void setPlayerModel(UUID owner, String model) {
        if (owner == null) return;
        if (model == null || model.isBlank()) {
            playerModels.remove(owner.toString());
        } else {
            playerModels.put(owner.toString(), model.trim());
        }
    }

    public String getPlayerModel(UUID owner) {
        if (owner == null) return "";
        String m = playerModels.get(owner.toString());
        return m == null ? "" : m;
    }

    // ---- token at-rest obfuscation (NOT encryption — see OBF_KEY note) ----

    static String obfuscate(String s) {
        if (s == null || s.isEmpty()) return "";
        return Base64.getEncoder().encodeToString(xor(s.getBytes(StandardCharsets.UTF_8)));
    }

    static String deobfuscate(String s) {
        if (s == null || s.isEmpty()) return "";
        try {
            return new String(xor(Base64.getDecoder().decode(s)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";   // corrupt/garbage → treat as "no token" rather than crash
        }
    }

    private static byte[] xor(byte[] data) {
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) (data[i] ^ OBF_KEY[i % OBF_KEY.length]);
        }
        return out;
    }
}
