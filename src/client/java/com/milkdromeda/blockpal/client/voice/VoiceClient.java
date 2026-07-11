package com.milkdromeda.blockpal.client.voice;

import com.milkdromeda.blockpal.AiAssistantMod;
import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.network.VoiceInputPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;

/**
 * The client half of agent voice: the <b>push-to-talk key</b> and the incoming
 * speech player.
 *
 * <p>Hold the talk key (default <b>V</b> — a raw GLFW key code in
 * {@code voicePushToTalkKey}, changed with {@code /aivoice key <code>}) while no
 * GUI is open to record; release to transcribe (Whisper large-v3-turbo by
 * default) and send the words to <b>your own companion only</b> — voice input
 * never touches public chat. The key state is read straight from GLFW
 * ({@link GLFW#glfwGetKey}) rather than through a registered KeyMapping, so it
 * can never collide with another mod's binding registration; "a GUI is open" is
 * tracked from Fabric's ScreenEvents (see AiAssistantClient), so pressing V
 * while typing in chat is safe.
 *
 * <p>Two spots deliberately use one-time reflection instead of a direct call —
 * the {@code Window} native-handle getter and the player's client-message
 * (action bar) method — because this Minecraft version renamed both accessors
 * and this codebase has no compiled use of either to borrow from (the 3.17.2
 * lesson: don't ship an unverifiable mapping guess). Both degrade gracefully:
 * no handle → the talk key is disabled with one log line; no message method →
 * status text is skipped (the server still echoes what it heard in chat).
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

    // One-time reflective lookups (see class javadoc). "Done" flags keep a failed
    // lookup from being retried every tick.
    private static Method windowHandleMethod;
    private static boolean windowHandleLookupDone;
    private static long windowHandle = 0L;
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

    /** Reads the raw key state from GLFW using the window's native handle. */
    private static boolean isTalkKeyDown(Minecraft mc) {
        try {
            long handle = handleOf(mc);
            if (handle == 0L) return false;
            return GLFW.glfwGetKey(handle, ModConfig.get().voicePushToTalkKey) == GLFW.GLFW_PRESS;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * The GLFW window handle, found once by reflection: the no-arg {@code long}
     * getter on {@code Window} whose name mentions window/handle/pointer (its
     * exact mapped name varies across Minecraft versions).
     */
    private static long handleOf(Minecraft mc) throws Exception {
        if (windowHandle != 0L) return windowHandle;
        Object window = mc.getWindow();
        if (window == null) return 0L;
        if (!windowHandleLookupDone) {
            windowHandleLookupDone = true;
            Method fallback = null;
            for (Method m : window.getClass().getMethods()) {
                if (m.getParameterCount() != 0 || m.getReturnType() != long.class) continue;
                String n = m.getName().toLowerCase(java.util.Locale.ROOT);
                if (n.contains("window") || n.contains("handle") || n.contains("pointer")) {
                    windowHandleMethod = m;
                    break;
                }
                if (fallback == null) fallback = m;
            }
            if (windowHandleMethod == null) windowHandleMethod = fallback;
            if (windowHandleMethod == null) {
                AiAssistantMod.LOGGER.warn(
                        "[Voice] Couldn't find the window-handle getter — push-to-talk disabled.");
            }
        }
        if (windowHandleMethod == null) return 0L;
        windowHandle = (long) windowHandleMethod.invoke(window);
        return windowHandle;
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
