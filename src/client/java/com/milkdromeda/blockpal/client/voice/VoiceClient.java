package com.milkdromeda.blockpal.client.voice;

import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.network.VoiceInputPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * The client half of agent voice: the <b>push-to-talk key</b> and the incoming
 * speech player.
 *
 * <p>Hold the talk key (default <b>V</b> — a raw GLFW key code in
 * {@code voicePushToTalkKey}, changed with {@code /aivoice key <code>}) while no
 * GUI is open to record; release to transcribe (Whisper large-v3-turbo by
 * default) and send the words to <b>your own companion only</b> — voice input
 * never touches public chat. The key is polled directly (GLFW state via
 * {@link InputConstants#isKeyDown}) rather than registered as a KeyMapping, so
 * it can never collide with another mod's binding registration.
 *
 * <p>Incoming {@code VoiceSpeakPayload}s (your agent — or one shared with you —
 * said something) are synthesized and played privately; the local playback
 * queue plays one utterance at a time, backing up the server's turn-taking.
 */
public final class VoiceClient {

    private VoiceClient() {}

    private static boolean keyWasDown = false;
    private static volatile boolean transcribing = false;

    /** Polled from END_CLIENT_TICK: edge-detects the push-to-talk key. */
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) {
            if (VoiceCapture.recording()) VoiceCapture.stop();
            keyWasDown = false;
            return;
        }
        // Only listen when the mouse/keyboard belong to the game world (no screen
        // open) — otherwise "V" while typing in chat would start recording.
        boolean down = mc.screen == null && isTalkKeyDown(mc);
        if (down && !keyWasDown) {
            startTalking(mc);
        } else if (!down && keyWasDown) {
            stopTalking(mc);
        }
        keyWasDown = down;
    }

    private static boolean isTalkKeyDown(Minecraft mc) {
        try {
            return InputConstants.isKeyDown(mc.getWindow().getWindow(),
                    ModConfig.get().voicePushToTalkKey);
        } catch (Exception e) {
            return false;
        }
    }

    private static void startTalking(Minecraft mc) {
        if (transcribing) return;
        if (!ClientPlayNetworking.canSend(VoiceInputPayload.TYPE)) {
            actionbar(mc, "§eVoice needs Blockpal on the server — this one doesn't have it.");
            return;
        }
        if (!SpeechToText.available()) {
            actionbar(mc, "§eNo transcription available — add a key (/ai mykey) or enable the free AI.");
            return;
        }
        if (VoiceCapture.start()) {
            actionbar(mc, "§b🎤 Listening… §7(release to send to your companion)");
        } else {
            actionbar(mc, "§c" + VoiceCapture.lastError());
        }
    }

    private static void stopTalking(Minecraft mc) {
        byte[] wav = VoiceCapture.stop();
        if (wav == null) {
            actionbar(mc, "§7(too short — hold the key while you speak)");
            return;
        }
        transcribing = true;
        actionbar(mc, "§b✎ Transcribing…");
        SpeechToText.transcribe(wav).thenAccept(text -> mc.execute(() -> {
            transcribing = false;
            if (text == null || text.isBlank()) {
                actionbar(mc, "§eCouldn't make out any words — try again a bit closer to the mic.");
                return;
            }
            if (ClientPlayNetworking.canSend(VoiceInputPayload.TYPE)) {
                ClientPlayNetworking.send(new VoiceInputPayload(text));
            } else {
                actionbar(mc, "§eHeard \"" + text + "\" — but this server doesn't run Blockpal.");
            }
        }));
    }

    /** An incoming spoken line from the server (already turn-taken there). */
    public static void onSpeak(Minecraft mc, String speaker, String voice, String text) {
        if (!ModConfig.get().voiceResponses) return;
        TextToSpeech.speak(text, voice);
    }

    private static void actionbar(Minecraft mc, String msg) {
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(msg), true);
    }
}
