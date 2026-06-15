package com.milkdromeda.aiassistant.ai;

import com.google.gson.*;
import com.milkdromeda.aiassistant.config.ModConfig;

import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Talks to an OpenAI-compatible chat-completions endpoint to turn a natural
 * language task into a JSON action plan.
 *
 * <p>Defaults to HuggingFace's modern router ({@code router.huggingface.co}).
 * The old {@code api-inference.huggingface.co} endpoint is deprecated and was
 * the cause of the "java.net.ConnectException" errors — this client targets the
 * supported endpoint and turns low-level network failures into friendly,
 * actionable messages.
 */
public class HuggingFaceClient {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private static final Gson GSON = new Gson();

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
            - To chop a tree, BREAK_BLOCK the wood blocks from the bottom up
            - Always stay within ±64 blocks of the player unless told otherwise
            - Respond with ONLY the JSON object, no extra text
            """;

    public CompletableFuture<ActionPlan> requestPlan(String task, String context) {
        ModConfig cfg = ModConfig.get();

        HttpRequest request;
        try {
            request = buildRequest(cfg, task, context);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(
                    errorPlan(task, "the API URL looks invalid — fix it with /ai settings"));
        }

        return sendWithRetry(request, task, 2);
    }

    private HttpRequest buildRequest(ModConfig cfg, String task, String context) {
        JsonObject body = new JsonObject();
        body.addProperty("model", cfg.hfModel);
        body.addProperty("temperature", cfg.temperature);
        body.addProperty("max_tokens", cfg.maxNewTokens);
        body.addProperty("stream", false);

        JsonArray messages = new JsonArray();
        messages.add(message("system", SYSTEM_PROMPT));
        messages.add(message("user", "Context:\n" + context + "\n\nTask: " + task));
        body.add("messages", messages);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(cfg.apiUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));

        if (cfg.hasApiToken()) {
            builder.header("Authorization", "Bearer " + cfg.hfToken);
        }
        return builder.build();
    }

    private JsonObject message(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }

    private CompletableFuture<ActionPlan> sendWithRetry(HttpRequest request, String task, int attemptsLeft) {
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((resp, ex) -> {
                    if (ex != null) {
                        if (attemptsLeft > 1 && isRetryable(ex)) {
                            return sendWithRetry(request, task, attemptsLeft - 1);
                        }
                        return CompletableFuture.completedFuture(errorPlan(task, friendlyNetworkError(ex)));
                    }
                    if (resp.statusCode() == 200) {
                        return CompletableFuture.completedFuture(parseResponse(resp.body(), task));
                    }
                    if ((resp.statusCode() == 503 || resp.statusCode() == 429) && attemptsLeft > 1) {
                        return sendWithRetry(request, task, attemptsLeft - 1);
                    }
                    return CompletableFuture.completedFuture(errorPlan(task, friendlyHttpError(resp)));
                })
                .thenCompose(future -> future);
    }

    private boolean isRetryable(Throwable ex) {
        Throwable cause = unwrap(ex);
        return cause instanceof ConnectException
                || cause instanceof HttpTimeoutException;
    }

    private String friendlyNetworkError(Throwable ex) {
        Throwable cause = unwrap(ex);
        if (cause instanceof UnknownHostException) {
            return "I can't reach the AI service (unknown host). Check your internet connection "
                    + "and the API URL with /ai settings.";
        }
        if (cause instanceof ConnectException) {
            return "I can't connect to the AI service. Check your internet connection, and that the "
                    + "API URL is correct (see /ai settings). The default uses HuggingFace.";
        }
        if (cause instanceof HttpTimeoutException) {
            return "the AI service took too long to answer. Try again, or pick a faster model with "
                    + "/ai settings model <id>.";
        }
        String msg = cause.getMessage();
        return "couldn't reach the AI service" + (msg != null ? " (" + msg + ")" : "") + ".";
    }

    private String friendlyHttpError(HttpResponse<String> resp) {
        return switch (resp.statusCode()) {
            case 400 -> "the AI service rejected the request (400). Your model id may be wrong — set it "
                    + "with /ai settings model <id>.";
            case 401, 403 -> "my API token is missing or invalid. Set it with /ai token <token>.";
            case 404 -> "that model wasn't found (404). Set a valid one with /ai settings model <id>.";
            case 429 -> "the AI service is rate-limiting me. Wait a moment and try again.";
            case 503 -> "the model is still loading or unavailable. Try again in a few seconds.";
            default -> {
                String preview = resp.body() == null ? "" :
                        resp.body().substring(0, Math.min(120, resp.body().length()));
                yield "the AI service returned HTTP " + resp.statusCode()
                        + (preview.isBlank() ? "." : ": " + preview);
            }
        };
    }

    private Throwable unwrap(Throwable ex) {
        Throwable cause = ex;
        while ((cause instanceof CompletionException || cause instanceof java.util.concurrent.ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private ActionPlan parseResponse(String rawBody, String originalTask) {
        try {
            String content = extractContent(rawBody);
            if (content == null) {
                return errorPlan(originalTask, "the AI sent back an unexpected response format.");
            }

            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start == -1 || end == -1 || end < start) {
                return errorPlan(originalTask, "the AI didn't return a plan I could follow.");
            }

            JsonObject plan = JsonParser.parseString(content.substring(start, end + 1)).getAsJsonObject();
            String thinking = plan.has("thinking") ? plan.get("thinking").getAsString() : "";
            String description = plan.has("description") ? plan.get("description").getAsString() : originalTask;

            List<ActionStep> steps = new ArrayList<>();
            if (plan.has("steps") && plan.get("steps").isJsonArray()) {
                for (JsonElement el : plan.getAsJsonArray("steps")) {
                    JsonObject step = el.getAsJsonObject();
                    String actionStr = step.get("action").getAsString();
                    JsonObject params = step.has("params") ? step.getAsJsonObject("params") : new JsonObject();
                    try {
                        steps.add(new ActionStep(ActionStep.ActionType.valueOf(actionStr), params));
                    } catch (IllegalArgumentException ignored) {
                        // Skip unknown actions rather than failing the whole plan.
                    }
                }
            }

            if (steps.isEmpty()) return errorPlan(originalTask, "the AI didn't give me any steps to do.");
            return new ActionPlan(thinking, description, steps);

        } catch (Exception e) {
            return errorPlan(originalTask, "I couldn't understand the AI's reply.");
        }
    }

    /** Pulls the assistant text out of an OpenAI-style or legacy HuggingFace response. */
    private String extractContent(String rawBody) {
        JsonElement root = JsonParser.parseString(rawBody);

        // OpenAI-compatible: { "choices": [ { "message": { "content": "..." } } ] }
        if (root.isJsonObject() && root.getAsJsonObject().has("choices")) {
            JsonArray choices = root.getAsJsonObject().getAsJsonArray("choices");
            if (!choices.isEmpty()) {
                JsonObject first = choices.get(0).getAsJsonObject();
                if (first.has("message")) {
                    return first.getAsJsonObject("message").get("content").getAsString().trim();
                }
                if (first.has("text")) {
                    return first.get("text").getAsString().trim();
                }
            }
        }

        // Legacy text-generation: [ { "generated_text": "..." } ] or { "generated_text": "..." }
        if (root.isJsonArray() && !root.getAsJsonArray().isEmpty()) {
            JsonObject first = root.getAsJsonArray().get(0).getAsJsonObject();
            if (first.has("generated_text")) return first.get("generated_text").getAsString().trim();
        }
        if (root.isJsonObject() && root.getAsJsonObject().has("generated_text")) {
            return root.getAsJsonObject().get("generated_text").getAsString().trim();
        }

        return null;
    }

    private ActionPlan errorPlan(String task, String reason) {
        JsonObject params = new JsonObject();
        params.addProperty("message", "Sorry, I couldn't do \"" + task + "\" — " + reason);
        return new ActionPlan("error", task,
                List.of(new ActionStep(ActionStep.ActionType.CHAT, params)));
    }
}
