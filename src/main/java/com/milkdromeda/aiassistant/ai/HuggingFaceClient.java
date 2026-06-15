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

    // OpenAI-compatible chat completions endpoint (works with all major HF models)
    private static final String CHAT_API = "https://api-inference.huggingface.co/v1/chat/completions";

    private static final String SYSTEM_PROMPT = """
            You are ARIA — an AI assistant physically present in a Minecraft world.
            Given a player task and world context, output ONLY valid JSON with this schema:
            {
              "thinking": "<one-line reasoning>",
              "description": "<short plan summary players will see>",
              "steps": [
                {"action": "<ACTION>", "params": {<params>}}
              ]
            }

            Available actions:
            MOVE_TO        {"x":int,"y":int,"z":int}
            PLACE_BLOCK    {"block":"minecraft:id","x":int,"y":int,"z":int}
            BREAK_BLOCK    {"x":int,"y":int,"z":int}
            ATTACK_NEAREST {"range":int}
            FOLLOW_PLAYER  {"name":"player_name","distance":int}
            LOOK_AT        {"x":int,"y":int,"z":int}
            CHAT           {"message":"text"}
            WAIT           {"ticks":int}
            COLLECT_ITEM   {"x":int,"y":int,"z":int}
            STOP           {}

            Rules:
            - Always start with a CHAT step to acknowledge the task
            - Stay within ±64 blocks of players unless instructed otherwise
            - For building tasks, chain MOVE_TO then PLACE_BLOCK steps
            - Respond with ONLY the JSON object — no markdown, no extra text
            """;

    public CompletableFuture<ActionPlan> requestPlan(String task, String context) {
        ModConfig cfg = ModConfig.get();

        JsonArray messages = new JsonArray();

        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", SYSTEM_PROMPT);
        messages.add(systemMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", "World context:\n" + context + "\n\nPlayer task: " + task);
        messages.add(userMsg);

        JsonObject body = new JsonObject();
        body.addProperty("model", cfg.hfModel);
        body.add("messages", messages);
        body.addProperty("max_tokens", cfg.maxNewTokens);
        body.addProperty("temperature", cfg.temperature);
        body.addProperty("stream", false);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CHAT_API))
                .header("Authorization", "Bearer " + cfg.hfToken)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(90))
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    int code = resp.statusCode();
                    if (code == 401) return errorPlan(task, "Invalid HF token — run /ai settings hf_token <token>");
                    if (code == 404) return errorPlan(task, "Model not found: " + cfg.hfModel);
                    if (code == 503) return errorPlan(task, "Model is loading, try again in ~20s");
                    if (code != 200) {
                        String preview = resp.body().substring(0, Math.min(160, resp.body().length()));
                        return errorPlan(task, "HTTP " + code + ": " + preview);
                    }
                    return parseResponse(resp.body(), task);
                })
                .exceptionally(ex -> errorPlan(task, "Network error: " + ex.getMessage()));
    }

    private ActionPlan parseResponse(String rawBody, String originalTask) {
        try {
            JsonElement root = JsonParser.parseString(rawBody);
            String content;

            if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                if (obj.has("choices")) {
                    // Chat completions format
                    content = obj.getAsJsonArray("choices")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("message")
                            .get("content").getAsString().trim();
                } else if (obj.has("generated_text")) {
                    content = obj.get("generated_text").getAsString().trim();
                } else {
                    content = rawBody;
                }
            } else if (root.isJsonArray()) {
                // Old HF text-generation format
                content = root.getAsJsonArray().get(0).getAsJsonObject()
                        .get("generated_text").getAsString().trim();
            } else {
                return errorPlan(originalTask, "Unexpected response format");
            }

            int start = content.indexOf('{');
            int end   = content.lastIndexOf('}');
            if (start == -1 || end == -1) return errorPlan(originalTask, "No JSON in AI response");

            JsonObject plan = JsonParser.parseString(content.substring(start, end + 1)).getAsJsonObject();
            String thinking    = plan.has("thinking")    ? plan.get("thinking").getAsString()    : "";
            String description = plan.has("description") ? plan.get("description").getAsString() : originalTask;

            List<ActionStep> steps = new ArrayList<>();
            if (plan.has("steps") && plan.get("steps").isJsonArray()) {
                for (JsonElement el : plan.getAsJsonArray("steps")) {
                    JsonObject step = el.getAsJsonObject();
                    String actionStr = step.get("action").getAsString();
                    JsonObject params = step.has("params") ? step.getAsJsonObject("params") : new JsonObject();
                    try {
                        steps.add(new ActionStep(ActionStep.ActionType.valueOf(actionStr), params));
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            if (steps.isEmpty()) return errorPlan(originalTask, "AI returned no steps");
            return new ActionPlan(thinking, description, steps);

        } catch (Exception e) {
            return errorPlan(originalTask, "Parse error: " + e.getMessage());
        }
    }

    private ActionPlan errorPlan(String task, String reason) {
        JsonObject params = new JsonObject();
        params.addProperty("message", "⚠ " + reason);
        return new ActionPlan("error", task,
                List.of(new ActionStep(ActionStep.ActionType.CHAT, params)));
    }
}
