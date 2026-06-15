package com.milkdromeda.aiassistant.ai;

import com.google.gson.*;
import com.milkdromeda.aiassistant.config.ModConfig;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class HuggingFaceClient {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private static final Gson GSON = new Gson();
    private static final String API_BASE = "https://api-inference.huggingface.co/models/";

    private static final String SYSTEM_PROMPT = """
            You are an AI Minecraft assistant physically present in the game world.
            When given a task and context, respond ONLY with valid JSON matching this exact schema:
            {
              "thinking": "<one sentence of reasoning>",
              "description": "<short human-readable plan summary>",
              "steps": [
                {"action": "<ACTION>", "params": {<params>}}
              ]
            }

            Available actions and their params:
            MOVE_TO        {"x": int, "y": int, "z": int}
            PLACE_BLOCK    {"block": "minecraft:id", "x": int, "y": int, "z": int}
            BREAK_BLOCK    {"x": int, "y": int, "z": int}
            ATTACK_NEAREST {"range": int}
            FOLLOW_PLAYER  {"name": "player_name", "distance": int}
            LOOK_AT        {"x": int, "y": int, "z": int}
            CHAT           {"message": "text to say in chat"}
            WAIT           {"ticks": int}
            COLLECT_ITEM   {"x": int, "y": int, "z": int}
            STOP           {}

            Rules:
            - Use CHAT to acknowledge the player or report status
            - For building, generate MOVE_TO then PLACE_BLOCK steps
            - Always stay within ±64 blocks of the player unless told otherwise
            - Respond with ONLY the JSON object, no extra text
            """;

    public CompletableFuture<ActionPlan> requestPlan(String task, String context) {
        ModConfig cfg = ModConfig.get();
        String prompt = buildPrompt(task, context);

        JsonObject body = new JsonObject();
        body.addProperty("inputs", prompt);

        JsonObject parameters = new JsonObject();
        parameters.addProperty("max_new_tokens", cfg.maxNewTokens);
        parameters.addProperty("temperature", cfg.temperature);
        parameters.addProperty("return_full_text", false);
        parameters.addProperty("do_sample", true);
        body.add("parameters", parameters);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + cfg.hfModel))
                .header("Authorization", "Bearer " + cfg.hfToken)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> parseResponse(resp.body(), task));
    }

    private String buildPrompt(String task, String context) {
        return "[INST] <<SYS>>\n" + SYSTEM_PROMPT + "\n<</SYS>>\n\n"
                + "Context:\n" + context + "\n\n"
                + "Task: " + task + " [/INST]";
    }

    private ActionPlan parseResponse(String rawBody, String originalTask) {
        try {
            JsonElement root = JsonParser.parseString(rawBody);
            String generated;

            if (root.isJsonArray()) {
                JsonObject first = root.getAsJsonArray().get(0).getAsJsonObject();
                generated = first.get("generated_text").getAsString().trim();
            } else if (root.isJsonObject() && root.getAsJsonObject().has("generated_text")) {
                generated = root.getAsJsonObject().get("generated_text").getAsString().trim();
            } else {
                generated = rawBody;
            }

            // Extract first JSON object from the generated text
            int start = generated.indexOf('{');
            int end = generated.lastIndexOf('}');
            if (start == -1 || end == -1) return fallbackPlan(originalTask, "No JSON in response");

            JsonObject plan = JsonParser.parseString(generated.substring(start, end + 1)).getAsJsonObject();
            String thinking = plan.has("thinking") ? plan.get("thinking").getAsString() : "";
            String description = plan.has("description") ? plan.get("description").getAsString() : originalTask;

            List<ActionStep> steps = new ArrayList<>();
            if (plan.has("steps") && plan.get("steps").isJsonArray()) {
                for (JsonElement el : plan.getAsJsonArray("steps")) {
                    JsonObject step = el.getAsJsonObject();
                    String actionStr = step.get("action").getAsString();
                    JsonObject params = step.has("params") ? step.getAsJsonObject("params") : new JsonObject();
                    try {
                        ActionStep.ActionType type = ActionStep.ActionType.valueOf(actionStr);
                        steps.add(new ActionStep(type, params));
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            if (steps.isEmpty()) return fallbackPlan(originalTask, "Empty step list");
            return new ActionPlan(thinking, description, steps);

        } catch (Exception e) {
            return fallbackPlan(originalTask, "Parse error: " + e.getMessage());
        }
    }

    private ActionPlan fallbackPlan(String task, String reason) {
        JsonObject params = new JsonObject();
        params.addProperty("message", "I couldn't plan '" + task + "' (" + reason + ")");
        return new ActionPlan("fallback", task,
                List.of(new ActionStep(ActionStep.ActionType.CHAT, params)));
    }
}
