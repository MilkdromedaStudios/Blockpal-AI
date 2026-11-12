package com.milkdromeda.blockpal.client.voice;

import com.milkdromeda.blockpal.AiAssistantMod;
import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.network.VoiceInputPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

import java.lang.reflect.Method;

/**
 * The client half of agent voice: the <b>push-to-talk key</b> and the incoming
 * speech player.
 *
 * <p>Hold the talk key (default <b>V</b>, SDL scancode 25, rebind with
 * {@code /aivoice key <code>}) while no GUI is open to record; release it to
 * transcribe and send the speech to your own companion. The key is polled using
 * Minecraft's SDL3 {@link InputConstants#isKeyDown(int)}, rather than GLFW,
 * and ScreenEvents prevent accidental recording when typing in chat.
 *
 * <p>The player's optional client-message (action bar) accessor is looked up
 * reflectively. If missing, cosmetic status text is skipped.
 *
 * <p>Incoming {@code VoiceSpeakPayload}s (your agent — or one shared with you —
 * said something) are synthesized and played privately; the local playback
 * queue plays one utterance at a time, backing up the server's turn-taking.
 */
public final class VoiceClient {

    private VoiceClient() {}

    private static boolean keyWasDown = false;
    private static volatile boolean transcribing = false;

    /** Whether some GUI screen is currently open (maintained via ScreenEvents). */
    private static volatile boolean screenOpen = false;

    // The optional action-bar client-message accessor is looked up once.
    private static Method clientMessageMethod;
    private static boolean clientMessageLookupDone;

    /** Called from the ScreenEvents hooks in AiAssistantClient. */
    public static void setScreenOpen(boolean open) {
        screenOpen = open;
    }

    /** Polled from END_CLIENT_TICK: edge-detects the push-to-talk key. */
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) {
            if (VoiceCapture.recording()) VoiceCapture.stop();
            keyWasDown = false;
            return;
        }
        // Only start listening when the keyboard belongs to the game world (no
        // screen open) — otherwise "V" while typing in chat would start recording.
        // A release is honoured regardless, so opening a menu mid-sentence sends.
        boolean down = !screenOpen && isTalkKeyDown(mc);
        if (down && !keyWasDown) {
            startTalking(mc);
        } else if (!down && keyWasDown) {
            stopTalking(mc);
        }
        keyWasDown = down;
    }

    /** Poll the SDL3 physical keyboard scancode used by Minecraft 26.3. */
    private static boolean isTalkKeyDown(Minecraft mc) {
        try {
            return InputConstants.isKeyDown(ModConfig.get().voicePushToTalkKey);
        } catch (RuntimeException e) {
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
                // Tell the truth about WHY: a service problem reads very
                // differently from "you mumbled".
                String why = SpeechToText.lastFailure();
                actionbar(mc, why.isBlank()
                        ? "§eCouldn't make out any words — try again a bit closer to the mic."
                        : "§e" + why);
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

    /**
     * Shows a short status on the player's action bar. The client-message method
     * was renamed in this Minecraft version, so it's found once by its distinctive
     * {@code (Component, boolean)} signature; if that fails, status text is simply
     * skipped — the server-side chat echo still confirms every voice command.
     */
    private static void actionbar(Minecraft mc, String msg) {
        if (mc.player == null) return;
        try {
            if (!clientMessageLookupDone) {
                clientMessageLookupDone = true;
                Method fallback = null;
                for (Method m : mc.player.getClass().getMethods()) {
                    Class<?>[] par = m.getParameterTypes();
                    if (par.length != 2 || m.getReturnType() != void.class) continue;
                    if (!Component.class.isAssignableFrom(par[0]) || par[1] != boolean.class) continue;
                    String n = m.getName().toLowerCase(java.util.Locale.ROOT);
                    if (n.contains("message")) {
                        clientMessageMethod = m;
                        break;
                    }
                    if (fallback == null) fallback = m;
                }
                if (clientMessageMethod == null) clientMessageMethod = fallback;
                if (clientMessageMethod == null) {
                    AiAssistantMod.LOGGER.info(
                            "[Voice] No client-message method found — voice status lines will be skipped.");
                }
            }
            if (clientMessageMethod != null) {
                clientMessageMethod.invoke(mc.player, Component.literal(msg), true);
            }
        } catch (Exception ignored) {
            // Status text is cosmetic; never let it break the input loop.
        }
    }
}
