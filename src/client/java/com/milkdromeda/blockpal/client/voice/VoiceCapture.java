package com.milkdromeda.blockpal.client.voice;

import com.milkdromeda.blockpal.AiAssistantMod;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Records the microphone while the push-to-talk key is held, via
 * {@code javax.sound.sampled} (the JDK's own audio capture — no native or
 * third-party dependency). Audio is captured as 16 kHz mono 16-bit PCM, the
 * format Whisper models are trained on, and wrapped into a standard WAV
 * container for the transcription request.
 *
 * <p>Capture runs on its own daemon thread; {@link #stop()} returns the
 * finished WAV bytes (or null if nothing usable was recorded). Recording is
 * hard-capped at 30 seconds so a stuck key can't record forever.
 */
public final class VoiceCapture {

    private VoiceCapture() {}

    /** Whisper's native input format: 16 kHz, 16-bit, mono, little-endian PCM. */
    private static final AudioFormat FORMAT = new AudioFormat(16000f, 16, 1, true, false);
    private static final int MAX_SECONDS = 30;
    private static final int MAX_BYTES = 16000 * 2 * MAX_SECONDS;

    private static TargetDataLine line;
    private static Thread reader;
    private static ByteArrayOutputStream pcm;
    private static volatile boolean recording = false;
    private static String lastError = "";

    public static synchronized boolean recording() {
        return recording;
    }

    /** The reason the last start() refused (e.g. no microphone), for the action bar. */
    public static synchronized String lastError() {
        return lastError;
    }

    /** Starts capturing. @return false (with {@link #lastError} set) if the mic can't open. */
    public static synchronized boolean start() {
        if (recording) return true;
        lastError = "";
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
            if (!AudioSystem.isLineSupported(info)) {
                lastError = "No microphone available";
                return false;
            }
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(FORMAT);
            line.start();
            pcm = new ByteArrayOutputStream(16000 * 2 * 4);
            recording = true;
            reader = new Thread(VoiceCapture::pump, "Blockpal-Voice-Capture");
            reader.setDaemon(true);
            reader.start();
            return true;
        } catch (Exception e) {
            lastError = "Mic error: " + e.getMessage();
            AiAssistantMod.LOGGER.warn("[Voice] Couldn't open microphone: {}", e.toString());
            cleanup();
            return false;
        }
    }

    /** Stops capturing. @return the recording as WAV bytes, or null if nothing usable. */
    public static synchronized byte[] stop() {
        if (!recording) return null;
        recording = false;
        try {
            if (reader != null) reader.join(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        byte[] data = pcm == null ? new byte[0] : pcm.toByteArray();
        cleanup();
        // Under ~0.35 s is a key-tap, not speech — don't waste a transcription call.
        if (data.length < 16000 * 2 * 35 / 100) return null;
        return wrapWav(data);
    }

    private static void pump() {
        byte[] buf = new byte[3200];   // 100 ms chunks
        try {
            while (recording && pcm.size() < MAX_BYTES) {
                int n = line.read(buf, 0, buf.length);
                if (n <= 0) break;
                pcm.write(buf, 0, n);
            }
        } catch (Exception e) {
            AiAssistantMod.LOGGER.warn("[Voice] Capture stopped: {}", e.toString());
        }
    }

    private static void cleanup() {
        try {
            if (line != null) {
                line.stop();
                line.close();
            }
        } catch (Exception ignored) {
        }
        line = null;
        reader = null;
    }

    /** Wraps raw PCM in a canonical 44-byte-header WAV container. */
    private static byte[] wrapWav(byte[] pcmData) {
        int byteRate = 16000 * 2;
        ByteBuffer b = ByteBuffer.allocate(44 + pcmData.length).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes());
        b.putInt(36 + pcmData.length);
        b.put("WAVE".getBytes());
        b.put("fmt ".getBytes());
        b.putInt(16);                 // PCM fmt chunk size
        b.putShort((short) 1);        // PCM
        b.putShort((short) 1);        // mono
        b.putInt(16000);              // sample rate
        b.putInt(byteRate);
        b.putShort((short) 2);        // block align
        b.putShort((short) 16);       // bits per sample
        b.put("data".getBytes());
        b.putInt(pcmData.length);
        b.put(pcmData);
        return b.array();
    }
}
