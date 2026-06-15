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
    public int actionTickDelay = 20;
    public double followDistance = 4.0;
    public double guardRadius = 16.0;
    // When true the assistant listens to normal chat and reacts to commands
    // like "Ethan, follow me" or "help me mine this tree" without needing /ai.
    public boolean chatListening = true;
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
