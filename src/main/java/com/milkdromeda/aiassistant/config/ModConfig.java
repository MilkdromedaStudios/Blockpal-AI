package com.milkdromeda.aiassistant.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("ai-assistant.json");

    private static ModConfig instance;

    public String hfToken = "";
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

    public static ModConfig get() {
        if (instance == null) load();
        return instance;
    }

    public static void load() {
        if (CONFIG_PATH.toFile().exists()) {
            try (Reader r = new FileReader(CONFIG_PATH.toFile())) {
                instance = GSON.fromJson(r, ModConfig.class);
                return;
            } catch (IOException ignored) {}
        }
        instance = new ModConfig();
        save();
    }

    public static void save() {
        try (Writer w = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(instance, w);
        } catch (IOException e) {
            System.err.println("[AI-Assistant] Failed to save config: " + e.getMessage());
        }
    }

    public boolean hasApiToken() {
        return hfToken != null && !hfToken.isBlank();
    }
}
