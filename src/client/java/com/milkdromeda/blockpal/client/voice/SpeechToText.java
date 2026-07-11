package com.milkdromeda.blockpal.client.voice;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.milkdromeda.blockpal.AiAssistantMod;
import com.milkdromeda.blockpal.ai.HuggingFaceClient;
import com.milkdromeda.blockpal.client.assist.ClientAi;
import com.milkdromeda.blockpal.config.ModConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

/**
 * Turns a push-to-talk recording into text. The default engine is
 * <b>Whisper large-v3-turbo</b> ({@code sttModel}) on HuggingFace's serverless
 * inference API ({@code sttApiUrl}): the WAV is POSTed as a raw audio body with
 * the player's HF token and the reply is {@code {"text": "..."}}.
 *
 * <p>With no token at all, transcription falls back to the free voice-capable
 * OpenAI-compatible service (the same {@code freeApiUrl} the chat fallback
 * uses) by sending the audio as an {@code input_audio} chat part — so voice
 * input works out of the box, mirroring how the text AI falls back.
 *
 * <p>Failures resolve to {@code ""} with a log line (never an exception), so a
 * flaky network can't break the input loop.
 */
public final class SpeechToText {

    private SpeechToText() {}

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private static final Gson GSON = new Gson();

    /** Transcribes WAV audio to text asynchronously; resolves to "" on any failure. */
    public static CompletableFuture<String> transcribe(byte[] wav) {
        if (wav == null || wav.length == 0) return CompletableFuture.completedFuture("");
        HuggingFaceClient.ApiAuth auth = ClientAi.auth();
        if (auth.hasToken()) {
            return viaWhisper(wav, auth.token());
        }
        ModConfig cfg = ModConfig.get();
        if (cfg.freeAiFallback) {
            return viaFreeAudioChat(wav, cfg);
        }
        return CompletableFuture.completedFuture("");
    }

    /** True when some transcription path is available at all. */
    public static boolean available() {
        return ClientAi.auth().hasToken() || ModConfig.get().freeAiFallback;
    }

    /** Whisper on the HF serverless inference API: raw audio in, {"text": ...} out. */
    private static CompletableFuture<String> viaWhisper(byte[] wav, String token) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(ModConfig.get().sttApiUrl))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "audio/wav")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(wav))
                    .build();
        } catch (IllegalArgumentException e) {
            AiAssistantMod.LOGGER.warn("[Voice] Bad STT URL: {}", ModConfig.get().sttApiUrl);
            return CompletableFuture.completedFuture("");
        }
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((resp, ex) -> {
                    if (ex != null) {
                        AiAssistantMod.LOGGER.warn("[Voice] Whisper request failed: {}", ex.toString());
                        return "";
                    }
                    if (resp.statusCode() != 200) {
                        AiAssistantMod.LOGGER.warn("[Voice] Whisper HTTP {}: {}",
                                resp.statusCode(), truncate(resp.body()));
                        return "";
                    }
                    return parseWhisper(resp.body());
                });
    }

    private static String parseWhisper(String body) {
        try {
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            return o.has("text") ? o.get("text").getAsString().trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Keyless fallback: sends the audio as an OpenAI-style {@code input_audio}
     * chat part to the free voice-capable model and reads the text reply.
     */
    private static CompletableFuture<String> viaFreeAudioChat(byte[] wav, ModConfig cfg) {
        JsonObject audio = new JsonObject();
        audio.addProperty("data", Base64.getEncoder().encodeToString(wav));
        audio.addProperty("format", "wav");
        JsonObject audioPart = new JsonObject();
        audioPart.addProperty("type", "input_audio");
        audioPart.add("input_audio", audio);
        JsonObject textPart = new JsonObject();
        textPart.addProperty("type", "text");
        textPart.addProperty("text",
                "Transcribe this audio exactly, word for word. Reply with ONLY the transcription, nothing else.");

        JsonArray content = new JsonArray();
        content.add(textPart);
        content.add(audioPart);
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.add("content", content);
        JsonArray messages = new JsonArray();
        messages.add(user);

        JsonObject body = new JsonObject();
        body.addProperty("model", "openai-audio");
        body.addProperty("temperature", 0.0);
        body.add("messages", messages);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.freeApiUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                    .build();
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture("");
        }
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((resp, ex) -> {
                    if (ex != null || resp.statusCode() != 200) {
                        AiAssistantMod.LOGGER.warn("[Voice] Free transcription failed: {}",
                                ex != null ? ex.toString() : "HTTP " + resp.statusCode());
                        return "";
                    }
                    return parseChatContent(resp.body());
                });
    }

    /** choices[0].message.content of an OpenAI-style chat response, or "". */
    static String parseChatContent(String body) {
        try {
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            JsonArray choices = o.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) return "";
            JsonObject msg = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (msg == null || !msg.has("content") || msg.get("content").isJsonNull()) return "";
            return msg.get("content").getAsString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
