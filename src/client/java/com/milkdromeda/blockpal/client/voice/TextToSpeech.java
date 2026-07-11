package com.milkdromeda.blockpal.client.voice;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.milkdromeda.blockpal.AiAssistantMod;
import com.milkdromeda.blockpal.config.ModConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

/**
 * Gives the agent an audible voice: turns a line of its dialogue into WAV audio
 * and queues it on {@link VoicePlayback}. Synthesis goes through the free
 * voice-capable OpenAI-compatible service (the same {@code freeApiUrl} host the
 * text fallback uses) with an OpenAI-style audio-output chat request —
 * {@code modalities:["text","audio"]} plus {@code audio:{voice, format:"wav"}}
 * — so it needs <b>no API key</b> and works out of the box; WAV is requested
 * because the JDK can play it natively (no MP3 decoder exists in vanilla Java).
 *
 * <p>Each bot can carry its own voice id (set with {@code /ai voice set});
 * blank falls back to the client's {@code ttsVoice} default ("alloy"). The text
 * sent is the agent's exact line — synthesis only, no rewriting — with a
 * "read verbatim" instruction so the audio model doesn't answer instead.
 */
public final class TextToSpeech {

    private TextToSpeech() {}

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private static final Gson GSON = new Gson();

    /** Longer lines are truncated for synthesis — nobody wants a 2-minute monologue. */
    private static final int MAX_TTS_CHARS = 300;

    /** Synthesizes and queues the line. Resolves true if audio was queued. */
    public static CompletableFuture<Boolean> speak(String text, String voice) {
        if (text == null || text.isBlank()) return CompletableFuture.completedFuture(false);
        ModConfig cfg = ModConfig.get();
        if (!cfg.voiceResponses) return CompletableFuture.completedFuture(false);
        String line = text.length() > MAX_TTS_CHARS ? text.substring(0, MAX_TTS_CHARS) : text;
        String v = voice == null || voice.isBlank() ? cfg.ttsVoice : voice;

        JsonObject audio = new JsonObject();
        audio.addProperty("voice", v);
        audio.addProperty("format", "wav");
        JsonArray modalities = new JsonArray();
        modalities.add("text");
        modalities.add("audio");

        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content",
                "You are a text-to-speech engine. Speak the user's message aloud verbatim, exactly as "
                        + "written, with natural human delivery. Do not answer it, add to it, or comment on it.");
        messages.add(system);
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", line);
        messages.add(user);

        JsonObject body = new JsonObject();
        body.addProperty("model", "openai-audio");
        body.add("modalities", modalities);
        body.add("audio", audio);
        body.add("messages", messages);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.freeApiUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(45))
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                    .build();
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(false);
        }
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((resp, ex) -> {
                    if (ex != null) {
                        AiAssistantMod.LOGGER.warn("[Voice] TTS request failed: {}", ex.toString());
                        return false;
                    }
                    if (resp.statusCode() != 200) {
                        AiAssistantMod.LOGGER.warn("[Voice] TTS HTTP {}", resp.statusCode());
                        return false;
                    }
                    byte[] wav = extractAudio(resp.body());
                    if (wav == null || wav.length == 0) return false;
                    VoicePlayback.enqueue(wav);
                    return true;
                });
    }

    /** choices[0].message.audio.data (base64 WAV) of an OpenAI-style audio chat response. */
    private static byte[] extractAudio(String body) {
        try {
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            JsonArray choices = o.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) return null;
            JsonObject msg = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (msg == null) return null;
            JsonObject audio = msg.getAsJsonObject("audio");
            if (audio == null || !audio.has("data")) return null;
            return Base64.getDecoder().decode(audio.get("data").getAsString());
        } catch (Exception e) {
            return null;
        }
    }
}
