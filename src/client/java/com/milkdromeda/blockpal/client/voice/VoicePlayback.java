package com.milkdromeda.blockpal.client.voice;

import com.milkdromeda.blockpal.AiAssistantMod;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayInputStream;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Plays synthesized agent speech (WAV bytes) through the system's default audio
 * output via {@code javax.sound.sampled} — deliberately independent of
 * Minecraft's OpenAL sound engine, so a long spoken line can never stall or be
 * culled by game sound logic.
 *
 * <p>Utterances play strictly <b>one at a time</b> from a queue on a single
 * daemon thread. The server already turn-takes linked agents
 * ({@code VoiceCoordinator}), so this local queue is the second safety net that
 * guarantees agents can never talk over each other on this machine, even if
 * two packets arrive close together.
 */
public final class VoicePlayback {

    private VoicePlayback() {}

    /** Bounded so a burst can't build an ever-growing backlog of stale speech. */
    private static final LinkedBlockingDeque<byte[]> QUEUE = new LinkedBlockingDeque<>(6);

    private static volatile Thread worker;
    private static volatile boolean speaking = false;
    private static volatile boolean shutTheAgentUp = false;

    /** Queues a WAV clip; the oldest queued (not-yet-started) clip is dropped when full. */
    public static void enqueue(byte[] wav) {
        if (wav == null || wav.length == 0) return;
        ensureWorker();
        while (!QUEUE.offerLast(wav)) {
            QUEUE.pollFirst();
        }
    }

    /** True while a clip is actually coming out of the speakers. */
    public static boolean speaking() {
        return speaking;
    }

    /** Drops everything queued and stops the current clip as soon as possible. */
    public static void stopAll() {
        QUEUE.clear();
        shutTheAgentUp = true;
    }

    private static synchronized void ensureWorker() {
        if (worker != null && worker.isAlive()) return;
        Thread t = new Thread(VoicePlayback::run, "Blockpal-Voice-Playback");
        t.setDaemon(true);
        t.start();
        worker = t;
    }

    private static void run() {
        while (true) {
            try {
                byte[] wav = QUEUE.takeFirst();
                shutTheAgentUp = false;
                speaking = true;
                try {
                    play(wav);
                } finally {
                    speaking = false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // A bad clip (unsupported encoding, device hiccup) skips to the next.
                AiAssistantMod.LOGGER.warn("[Voice] Playback failed: {}", e.toString());
            }
        }
    }

    private static void play(byte[] wav) throws Exception {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
            AudioFormat format = in.getFormat();
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                line.open(format);
                line.start();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (shutTheAgentUp) break;
                    line.write(buf, 0, n);
                }
                if (!shutTheAgentUp) line.drain();
                line.stop();
            }
        }
    }
}
