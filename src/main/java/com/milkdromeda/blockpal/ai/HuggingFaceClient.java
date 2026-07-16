package com.milkdromeda.blockpal.ai;

import com.google.gson.*;
import com.milkdromeda.blockpal.config.ModConfig;

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

    /**
     * The token + model + endpoint a particular request should use. Resolved per bot
     * from its owner's settings (personal key/model, the shared server key, or the
     * free keyless fallback), so one server can bill different players to their own
     * keys, let them pick models — and still work with no key at all.
     */
    public record ApiAuth(String token, String model, String url, boolean free) {
        public boolean hasToken() {
            return token != null && !token.isBlank();
        }

        /** True when a request can actually be sent: we have a key, or the endpoint is keyless. */
        public boolean usable() {
            return hasToken() || free;
        }

        /**
         * Resolves what a bot owned by {@code owner} should talk to. A personal or
         * shared key always wins (the configured — HuggingFace by default — endpoint
         * is used with it); with no key at all the free keyless service steps in,
         * unless ops disabled it — then the auth comes back unusable.
         */
        public static ApiAuth resolveFor(UUID owner, String ownerName) {
            ModConfig cfg = ModConfig.get();
            String token = cfg.resolveTokenFor(owner, ownerName);
            if (!token.isBlank()) {
                return new ApiAuth(token, cfg.resolveModelFor(owner), cfg.apiUrl, false);
            }
            if (cfg.freeAiFallback) {
                return new ApiAuth("", cfg.freeModel, cfg.freeApiUrl, true);
            }
            return new ApiAuth("", cfg.resolveModelFor(owner), cfg.apiUrl, false);
        }
    }

    private static final String SYSTEM_PROMPT = """
            You are a highly capable AI Minecraft assistant, physically present in
            the world as a character that can move, build, fight, dig, interact and
            run commands. Think ahead and plan several concrete steps at once.
            Respond ONLY with valid JSON matching this exact schema:
            {
              "thinking": "<one sentence of reasoning>",
              "description": "<short human-readable plan summary>",
              "loop": false,
              "steps": [
                {"action": "<ACTION>", "params": {<params>}}
              ]
            }

            Available actions and their params:
            MOVE_TO        {"x": int, "y": int, "z": int}            // walk/path to a spot
            PLACE_BLOCK    {"block": "minecraft:id", "x": int, "y": int, "z": int}
            BREAK_BLOCK    {"x": int, "y": int, "z": int}
            MINE_AREA      {"x1": int, "y1": int, "z1": int, "x2": int, "y2": int, "z2": int} // clear a box
            USE_BLOCK      {"x": int, "y": int, "z": int}            // flip lever / press button / open door
            ATTACK_NEAREST {"range": int}
            FOLLOW_PLAYER  {"name": "player_name", "distance": int}
            LOOK_AT        {"x": int, "y": int, "z": int}
            JUMP           {}                                        // hop (parkour / unstick)
            SET_SNEAK      {"value": true|false}
            RUN_COMMAND    {"command": "/setblock 10 64 10 minecraft:redstone_wire"} // see notes
            CHAT           {"message": "text to say in chat"}
            WAIT           {"ticks": int}
            COLLECT_ITEM   {"x": int, "y": int, "z": int}
            STOP           {}

            Power tips:
            - RUN_COMMAND is your superpower. Use it for anything fiddly or large:
              redstone with exact orientation (/setblock x y z minecraft:repeater[facing=east,delay=2]),
              big structures (/fill), copying (/clone), items (/give @p ...), summons,
              teleports, effects, time/weather. Prefer it for precise redstone builds.
            - For escape rooms/puzzles: read the context's "Interactables" list, then
              USE_BLOCK the lever/button, or MOVE_TO a pressure plate, to progress.
            - For building by hand, MOVE_TO near the spot then PLACE_BLOCK.
            - To chop a tree, BREAK_BLOCK the wood from the bottom up.
            - Set "loop": true for ongoing activities (patrol, guard, keep mining,
              explore) so you keep going and re-plan with fresh context each round.
            - Plan 5-15 steps per response; combat reflexes are automatic, so focus
              your steps on the actual task.
            - Respond with ONLY the JSON object, no extra text.
            """;

    private static final String CLASSIFIER_PROMPT = """
            You decide whether a single Minecraft chat message is aimed at an in-game
            AI helper named "%s", and if so what the player wants. Players usually will
            NOT say the helper's name, so judge from meaning and context.
            Respond with ONLY this JSON, nothing else:
            {"directed": true|false, "action": "come|follow|stay|stop|locate|task|none", "task": "<imperative or empty>"}
            Action meanings:
              come   - asking the helper to come over / come back / return to them
              follow - asking it to follow or come along
              stay   - asking it to wait / hold position / stand guard
              stop   - asking it to stop, cancel, or quit what it's doing
              locate - asking where the helper is
              task   - any other doable request (build, mine, dig, chop, fight, gather,
                       craft, explore, farm...). Put a short imperative in "task".
              none   - the message needs no action from the helper
            Set directed=false for player-to-player chatter or small talk, and whenever
            you are unsure. Keep "task" concise, e.g. "build a 5x5 stone floor".
            """;

    /** The verdict of a custom-personality safety check. */
    public record Moderation(boolean allowed, String reason) {}

    private static final String MODERATION_PROMPT = """
            You are a content moderator for a family-friendly Minecraft mod. A player
            wants to give their in-game AI companion a custom personality, described by
            the text below. Decide whether that text is acceptable for all ages.

            REJECT if it contains or asks the character to use: profanity or slurs,
            sexual or adult content, hate or harassment, graphic violence or gore,
            self-harm, illegal activity, real-world political/religious agitation,
            personal data, or instructions to be cruel, abusive or to ignore safety.
            Otherwise ACCEPT. A harmless character description (funny, grumpy, robotic,
            pirate, royal, etc.) should be ACCEPTED.

            Respond with ONLY this JSON, nothing else:
            {"allowed": true|false, "reason": "<short reason, <=12 words>"}
            """;

    /**
     * Safety-checks a player-written custom personality with the language model.
     * Never throws. With no API token it resolves to "not allowed" (we can't verify),
     * so callers should treat a missing key as "ask for a built-in instead".
     */
    public CompletableFuture<Moderation> moderatePersonality(String text, ApiAuth auth) {
        if (text == null || text.isBlank()) {
            return CompletableFuture.completedFuture(new Moderation(false, "the description was empty"));
        }
        if (auth == null || !auth.usable()) {
            return CompletableFuture.completedFuture(
                    new Moderation(false, "no API key to verify the text is safe"));
        }
        ModConfig cfg = ModConfig.get();
        HttpRequest request;
        try {
            request = buildModerationRequest(cfg, text, auth);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(new Moderation(false, "the API URL looks invalid"));
        }
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((resp, ex) -> {
                    // On any failure, fail safe: don't apply unverified custom text.
                    if (ex != null || resp.statusCode() != 200) {
                        return new Moderation(false, "couldn't reach the safety check — try again");
                    }
                    return parseModeration(resp.body());
                });
    }

    private HttpRequest buildModerationRequest(ModConfig cfg, String text, ApiAuth auth) {
        JsonObject body = new JsonObject();
        body.addProperty("model", auth.model());
        body.addProperty("temperature", 0.0);
        body.addProperty("max_tokens", 60);
        body.addProperty("stream", false);

        JsonArray messages = new JsonArray();
        messages.add(message("system", MODERATION_PROMPT));
        messages.add(message("user", "Custom personality text:\n" + text));
        body.add("messages", messages);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint(cfg, auth)))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));
        if (auth.hasToken()) {
            builder.header("Authorization", "Bearer " + auth.token());
        }
        return builder.build();
    }

    private Moderation parseModeration(String rawBody) {
        try {
            String content = extractContent(rawBody);
            if (content == null) return new Moderation(false, "unreadable safety-check response");
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start == -1 || end == -1 || end < start) {
                return new Moderation(false, "unreadable safety-check response");
            }
            JsonObject o = JsonParser.parseString(content.substring(start, end + 1)).getAsJsonObject();
            boolean allowed = o.has("allowed") && o.get("allowed").getAsBoolean();
            String reason = o.has("reason") ? o.get("reason").getAsString().trim() : "";
            return new Moderation(allowed, reason);
        } catch (Exception e) {
            return new Moderation(false, "couldn't read the safety check");
        }
    }

    private static final String ASSISTANT_PROMPT = """
            You are Blockpal's private in-game helper: a friendly, knowledgeable
            Minecraft companion answering a player through a small chat box on their
            own screen. Give concise, practical, accurate help — tips, how-tos,
            recipes, strategy, where to find things, what a block/mob/item does.

            Rules:
            - Keep replies short (1-4 sentences). Plain text, no markdown headings.
            - You only ADVISE. You cannot see or touch the world and you never claim to
              take actions, place blocks, or move the player. If they want you to control
              their character, tell them to open the drive/possession console.
            - Never help with cheating that could get them banned (X-ray, kill aura,
              auto-clickers, dupes, exploits). If asked, gently decline and suggest a
              legitimate approach instead.
            - Be encouraging and to the point.
            """;

    /**
     * A free-form conversational reply for the client-side assistant chat box. Unlike
     * {@link #requestPlan} this returns plain prose (not a JSON plan) and never throws —
     * any failure comes back as a short parenthetical message so the chat box can show
     * it. {@code history} is prior turns as {@code {role, content}} pairs (role
     * "user"/"assistant"); {@code auth} resolves the caller's key/model/endpoint.
     */
    public CompletableFuture<String> requestChat(List<String[]> history, String userMessage, ApiAuth auth) {
        if (auth == null || !auth.usable()) {
            return CompletableFuture.completedFuture(
                    "(No AI configured — add a key with /ai mykey <token>, or enable the free AI in /ai menu.)");
        }
        ModConfig cfg = ModConfig.get();
        HttpRequest request;
        try {
            request = buildChatRequest(cfg, history, userMessage, auth);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture("(The API URL looks invalid — an admin can fix it in /ai menu.)");
        }
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((resp, ex) -> {
                    if (ex != null) return "(" + friendlyNetworkError(ex) + ")";
                    if (resp.statusCode() != 200) return "(" + friendlyHttpError(resp, auth) + ")";
                    String content = extractContent(resp.body());
                    return content == null || content.isBlank() ? "(No reply — try again.)" : content.trim();
                });
    }

    private HttpRequest buildChatRequest(ModConfig cfg, List<String[]> history, String userMessage, ApiAuth auth) {
        JsonObject body = new JsonObject();
        body.addProperty("model", auth.model());
        body.addProperty("temperature", 0.6);
        body.addProperty("max_tokens", 300);
        body.addProperty("stream", false);

        JsonArray messages = new JsonArray();
        messages.add(message("system", ASSISTANT_PROMPT));
        if (history != null) {
            for (String[] turn : history) {
                if (turn == null || turn.length < 2 || turn[1] == null || turn[1].isBlank()) continue;
                String role = "assistant".equals(turn[0]) ? "assistant" : "user";
                messages.add(message(role, turn[1]));
            }
        }
        messages.add(message("user", userMessage));
        body.add("messages", messages);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint(cfg, auth)))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(45))
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));
        if (auth.hasToken()) {
            builder.header("Authorization", "Bearer " + auth.token());
        }
        return builder.build();
    }

    public CompletableFuture<ActionPlan> requestPlan(String task, String context, ApiAuth auth) {
        return requestPlan(task, context, auth, null);
    }

    /**
     * Requests a plan, blending the bot's {@code personaStyle} (a personality flavour
     * line) into the system prompt so its {@code CHAT} actions and general approach
     * stay in character. A blank/null style behaves exactly like the plain request.
     */
    public CompletableFuture<ActionPlan> requestPlan(String task, String context, ApiAuth auth,
                                                     String personaStyle) {
        ModConfig cfg = ModConfig.get();

        HttpRequest request;
        try {
            request = buildRequest(cfg, task, context, auth, personaStyle);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(
                    errorPlan(task, "the API URL looks invalid — an admin can fix it in /ai menu (AI & API tab)"));
        }

        return sendWithRetry(request, task, 2, auth);
    }

    /**
     * Asks the language model whether a chat message is aimed at the assistant
     * and what it should do. Never throws and never produces chat noise — any
     * problem resolves to {@link ChatIntent#none()}.
     */
    public CompletableFuture<ChatIntent> classifyMessage(String message, String context,
                                                         String assistantName, ApiAuth auth) {
        ModConfig cfg = ModConfig.get();
        if (auth == null || !auth.usable()) {
            return CompletableFuture.completedFuture(ChatIntent.none());
        }

        HttpRequest request;
        try {
            request = buildClassifierRequest(cfg, message, context, assistantName, auth);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(ChatIntent.none());
        }

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((resp, ex) -> {
                    if (ex != null || resp.statusCode() != 200) return ChatIntent.none();
                    return parseIntent(resp.body());
                });
    }

    private HttpRequest buildClassifierRequest(ModConfig cfg, String message, String context,
                                               String name, ApiAuth auth) {
        JsonObject body = new JsonObject();
        body.addProperty("model", auth.model());
        body.addProperty("temperature", 0.0);   // deterministic classification
        body.addProperty("max_tokens", 120);
        body.addProperty("stream", false);

        JsonArray messages = new JsonArray();
        messages.add(message("system", String.format(CLASSIFIER_PROMPT, name)));
        String user = (context == null || context.isBlank() ? "" : "Context: " + context + "\n")
                + "Message: " + message;
        messages.add(message("user", user));
        body.add("messages", messages);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint(cfg, auth)))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));

        if (auth.hasToken()) {
            builder.header("Authorization", "Bearer " + auth.token());
        }
        return builder.build();
    }

    private ChatIntent parseIntent(String rawBody) {
        try {
            String content = extractContent(rawBody);
            if (content == null) return ChatIntent.none();

            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start == -1 || end == -1 || end < start) return ChatIntent.none();

            JsonObject o = JsonParser.parseString(content.substring(start, end + 1)).getAsJsonObject();
            boolean directed = o.has("directed") && o.get("directed").getAsBoolean();
            String action = o.has("action")
                    ? o.get("action").getAsString().toLowerCase(Locale.ROOT).trim() : "none";
            String task = o.has("task") ? o.get("task").getAsString().trim() : "";
            return new ChatIntent(directed, action, task);
        } catch (Exception e) {
            return ChatIntent.none();
        }
    }

    private HttpRequest buildRequest(ModConfig cfg, String task, String context, ApiAuth auth,
                                     String personaStyle) {
        JsonObject body = new JsonObject();
        body.addProperty("model", auth.model());
        body.addProperty("temperature", cfg.temperature);
        body.addProperty("max_tokens", cfg.maxNewTokens);
        body.addProperty("stream", false);
        // Ask the provider to guarantee syntactically valid JSON. Small/free models
        // (the ones the keyless fallback and cheap keys use) otherwise emit broken
        // JSON often enough that plans fail to parse; the system prompt already says
        // "valid JSON", which OpenAI-compatible json_object mode requires.
        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_object");
        body.add("response_format", responseFormat);

        JsonArray messages = new JsonArray();
        messages.add(message("system", systemPrompt(personaStyle)));
        messages.add(message("user", "Context:\n" + context + "\n\nTask: " + task));
        body.add("messages", messages);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint(cfg, auth)))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));

        if (auth.hasToken()) {
            builder.header("Authorization", "Bearer " + auth.token());
        }
        return builder.build();
    }

    /** The planner system prompt, with the bot's personality flavour appended (if any). */
    private String systemPrompt(String personaStyle) {
        if (personaStyle == null || personaStyle.isBlank()) return SYSTEM_PROMPT;
        return SYSTEM_PROMPT
                + "\n\nPersonality: " + personaStyle.trim()
                + " Stay in character in the wording of any CHAT action, but never let it "
                + "change the JSON schema or the actions you choose.";
    }

    /** The chat-completions URL a request should go to — the auth's endpoint, else the configured one. */
    private static String endpoint(ModConfig cfg, ApiAuth auth) {
        return (auth != null && auth.url() != null && !auth.url().isBlank()) ? auth.url() : cfg.apiUrl;
    }

    private JsonObject message(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }

    private CompletableFuture<ActionPlan> sendWithRetry(HttpRequest request, String task, int attemptsLeft,
                                                        ApiAuth auth) {
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((resp, ex) -> {
                    if (ex != null) {
                        if (attemptsLeft > 1 && isRetryable(ex)) {
                            return sendWithRetry(request, task, attemptsLeft - 1, auth);
                        }
                        return CompletableFuture.completedFuture(errorPlan(task, friendlyNetworkError(ex)));
                    }
                    if (resp.statusCode() == 200) {
                        return CompletableFuture.completedFuture(parseResponse(resp.body(), task));
                    }
                    // 5xx/429 are usually transient — especially on the shared free
                    // service, which can briefly 500 under load — so retry once.
                    if ((resp.statusCode() >= 500 || resp.statusCode() == 429) && attemptsLeft > 1) {
                        return sendWithRetry(request, task, attemptsLeft - 1, auth);
                    }
                    return CompletableFuture.completedFuture(errorPlan(task, friendlyHttpError(resp, auth)));
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
                    + "and the API URL in /ai menu.";
        }
        if (cause instanceof ConnectException) {
            return "I can't connect to the AI service. Check your internet connection, and that the "
                    + "API URL is correct (/ai menu, AI & API tab). The default uses HuggingFace.";
        }
        if (cause instanceof HttpTimeoutException) {
            return "the AI service took too long to answer. Try again, or pick a faster model with "
                    + "/ai model <id>.";
        }
        String msg = cause.getMessage();
        return "couldn't reach the AI service" + (msg != null ? " (" + msg + ")" : "") + ".";
    }

    private String friendlyHttpError(HttpResponse<String> resp, ApiAuth auth) {
        String detail = apiErrorDetail(resp.body());
        String said = detail.isBlank() ? "" : " The service said: \"" + detail + "\".";
        return switch (resp.statusCode()) {
            case 400, 404, 422 -> "the AI service rejected the request (" + resp.statusCode() + ")."
                    + said + modelHint(auth);
            case 401, 403 -> "my API token is missing or invalid." + said
                    + " An admin can set one with /ai admin token <token>, or set your own with "
                    + "/ai mykey <token>.";
            case 429 -> "the AI service is rate-limiting me. Wait a moment and try again.";
            case 503 -> "the model is still loading or unavailable. Try again in a few seconds.";
            default -> {
                String preview = detail.isBlank()
                        ? (resp.body() == null ? ""
                            : resp.body().substring(0, Math.min(120, resp.body().length())))
                        : detail;
                yield "the AI service returned HTTP " + resp.statusCode()
                        + (preview.isBlank() ? "." : ": " + preview);
            }
        };
    }

    /** What to say about the model id when the service rejects a request outright. */
    private static String modelHint(ApiAuth auth) {
        if (auth == null || auth.model() == null || auth.model().isBlank()) return "";
        String advice = ModelIds.advice(auth.model());
        if (advice != null) return " " + advice;
        return " Model in use: \"" + auth.model() + "\" — check it's spelled exactly as the "
                + "provider lists it (for the HuggingFace router, copy the id from the model's "
                + "page and make sure it shows Inference Providers).";
    }

    /**
     * Pulls the human-readable message out of an error response body. Providers
     * vary — OpenAI-style {@code {"error":{"message":…}}}, flat {@code {"error":…}},
     * {@code {"message":…}} and {@code {"detail":…}} are all seen in the wild — so
     * the raw reason behind an opaque 400 can be shown to the player instead of a
     * guess. Returns {@code ""} when nothing readable is found.
     */
    private static String apiErrorDetail(String body) {
        if (body == null || body.isBlank()) return "";
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject()) return "";
            JsonObject o = root.getAsJsonObject();
            String msg = null;
            if (o.has("error")) {
                JsonElement err = o.get("error");
                if (err.isJsonObject() && err.getAsJsonObject().has("message")) {
                    msg = err.getAsJsonObject().get("message").getAsString();
                } else if (err.isJsonPrimitive()) {
                    msg = err.getAsString();
                }
            }
            if (msg == null && o.has("message") && o.get("message").isJsonPrimitive()) {
                msg = o.get("message").getAsString();
            }
            if (msg == null && o.has("detail") && o.get("detail").isJsonPrimitive()) {
                msg = o.get("detail").getAsString();
            }
            if (msg == null) return "";
            msg = msg.replaceAll("\\s+", " ").trim();
            return msg.length() > 200 ? msg.substring(0, 197) + "…" : msg;
        } catch (Exception e) {
            return "";
        }
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
            boolean loop = plan.has("loop") && plan.get("loop").getAsBoolean();

            List<ActionStep> steps = new ArrayList<>();
            if (plan.has("steps") && plan.get("steps").isJsonArray()) {
                for (JsonElement el : plan.getAsJsonArray("steps")) {
                    if (!el.isJsonObject()) continue;
                    ActionStep parsed = parseStep(el.getAsJsonObject());
                    if (parsed != null) steps.add(parsed);   // one bad step never sinks the plan
                }
            }

            if (steps.isEmpty()) return errorPlan(originalTask, "the AI didn't give me any steps to do.");
            return new ActionPlan(thinking, description, steps, loop);

        } catch (Exception e) {
            return errorPlan(originalTask, "I couldn't understand the AI's reply.");
        }
    }

    /**
     * Turns one step object into an {@link ActionStep}, tolerating the small
     * variations weaker/free models produce: a missing or lower-case
     * {@code action}, or the alternate {@code {"RUN_COMMAND": {..params..}}}
     * shape where the action name is the key. Returns {@code null} for a step it
     * can't make sense of, so a single malformed step never sinks the whole plan.
     */
    private ActionStep parseStep(JsonObject step) {
        try {
            String actionStr = null;
            JsonObject params = new JsonObject();
            if (step.has("action") && step.get("action").isJsonPrimitive()) {
                actionStr = step.get("action").getAsString();
                if (step.has("params") && step.get("params").isJsonObject()) {
                    params = step.getAsJsonObject("params");
                }
            } else {
                // Alternate shape: the action name is the key, its value is the params.
                for (Map.Entry<String, JsonElement> e : step.entrySet()) {
                    if (isActionType(e.getKey())) {
                        actionStr = e.getKey();
                        if (e.getValue().isJsonObject()) params = e.getValue().getAsJsonObject();
                        break;
                    }
                }
            }
            if (actionStr == null || actionStr.isBlank()) return null;
            return new ActionStep(
                    ActionStep.ActionType.valueOf(actionStr.trim().toUpperCase(Locale.ROOT)), params);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isActionType(String s) {
        if (s == null) return false;
        try {
            ActionStep.ActionType.valueOf(s.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
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
        // Autonomous/internal tasks can be whole paragraphs — don't flood the chat.
        String shownTask = task == null ? "" : task.strip();
        if (shownTask.length() > 60) shownTask = shownTask.substring(0, 57) + "…";
        params.addProperty("message", "Sorry, I couldn't do \"" + shownTask + "\" — " + reason);
        return new ActionPlan("error", task,
                List.of(new ActionStep(ActionStep.ActionType.CHAT, params)));
    }
}
