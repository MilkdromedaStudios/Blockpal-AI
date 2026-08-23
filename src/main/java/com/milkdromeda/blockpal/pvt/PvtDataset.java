package com.milkdromeda.blockpal.pvt;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * <b>The demonstrations PVT learns from</b> — an append-only pile of episode files, and
 * the loader that turns them into training batches.
 *
 * <p>Episodes are separate files rather than one big one so that recording is crash-safe
 * (a half-written episode costs you that episode, not the archive), so old data can be
 * dropped a whole session at a time when the cap is hit, and so a player can delete one
 * bad session by hand without a tool.
 */
public final class PvtDataset {

    /** File magic; bumped if the frame layout changes so old files are skipped, not misread. */
    private static final int MAGIC = 0x42504432;    // "BPD2"

    private final List<PvtFrame> frames = new ArrayList<>();
    /** (start, end) index range of each episode loaded, so consecutive pairs stay in-episode. */
    private final List<int[]> episodes = new ArrayList<>();
    private int rejected;
    private boolean shuffled;

    public List<PvtFrame> frames() { return frames; }
    public int size() { return frames.size(); }
    public boolean isEmpty() { return frames.isEmpty(); }
    /** Frames skipped on load because they were written by a different observation layout. */
    public int rejected() { return rejected; }

    public void add(PvtFrame frame) { frames.add(frame); }

    /** The (start, end) ranges of each loaded episode; empty once {@link #shuffle} runs. */
    public List<int[]> episodes() { return episodes; }

    /** How many frames carry a real observed action rather than an inferred one. */
    public int labelledCount() {
        int n = 0;
        for (PvtFrame f : frames) if (f.labelled()) n++;
        return n;
    }

    // ── writing ─────────────────────────────────────────────────────────────────

    /** Opens an episode file and writes its header. */
    public static DataOutputStream openEpisode(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        OutputStream raw = Files.newOutputStream(file,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(raw, 1 << 16));
        out.writeInt(MAGIC);
        out.writeInt(PvtObservation.SIZE);
        out.writeInt(PvtAction.HEADS);
        return out;
    }

    /** Appends one frame. */
    public static void writeFrame(DataOutputStream out, PvtFrame frame) throws IOException {
        out.writeByte(frame.labelled() ? 1 : 0);
        out.write(frame.obs());
        for (int label : frame.labels()) out.writeByte(label);
    }

    // ── reading ─────────────────────────────────────────────────────────────────

    /**
     * Loads every episode in a folder, newest first, stopping once {@code maxFrames} have
     * been read. Newest-first matters: when someone has recorded more than the cap, the
     * play they want the bot to imitate is the play they did most recently.
     */
    public static PvtDataset load(Path folder, int maxFrames) {
        PvtDataset set = new PvtDataset();
        if (folder == null || !Files.isDirectory(folder)) return set;
        List<Path> files = new ArrayList<>();
        try (var stream = Files.list(folder)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".bpd")).forEach(files::add);
        } catch (IOException e) {
            return set;
        }
        files.sort(Comparator.comparingLong((Path p) -> lastModified(p)).reversed());
        for (Path file : files) {
            if (set.size() >= maxFrames) break;
            set.readEpisode(file, maxFrames);
        }
        return set;
    }

    private static long lastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private void readEpisode(Path file, int maxFrames) {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(file), 1 << 16))) {
            if (in.readInt() != MAGIC) { rejected++; return; }
            int obsSize = in.readInt();
            int heads = in.readInt();
            // An episode recorded under a different observation layout is meaningless to
            // the current network — skip the file rather than feeding it garbage.
            if (obsSize != PvtObservation.SIZE || heads != PvtAction.HEADS) { rejected++; return; }

            int episodeStart = frames.size();
            byte[] obs = new byte[obsSize];
            while (size() < maxFrames) {
                int flags;
                try {
                    flags = in.readUnsignedByte();
                } catch (EOFException eof) {
                    closeEpisode(episodeStart);
                    return;                       // clean end of a complete episode
                }
                in.readFully(obs);
                int[] labels = new int[heads];
                for (int h = 0; h < heads; h++) labels[h] = in.readUnsignedByte();
                frames.add(new PvtFrame(obs.clone(), labels, (flags & 1) != 0));
            }
            closeEpisode(episodeStart);
        } catch (EOFException truncated) {
            // A session that ended in a crash leaves a partial final frame. Everything
            // before it is still perfectly good training data.
        } catch (IOException e) {
            rejected++;
        }
    }

    private void closeEpisode(int start) {
        if (frames.size() > start + 1) episodes.add(new int[]{start, frames.size()});
    }

    /**
     * Deletes the oldest episodes until the folder holds at most {@code maxFrames}
     * frames' worth of data.
     *
     * @return how many files were removed
     */
    public static int prune(Path folder, int maxFrames) {
        if (folder == null || !Files.isDirectory(folder)) return 0;
        List<Path> files = new ArrayList<>();
        try (var stream = Files.list(folder)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".bpd")).forEach(files::add);
        } catch (IOException e) {
            return 0;
        }
        files.sort(Comparator.comparingLong(PvtDataset::lastModified).reversed());
        long budget = (long) maxFrames * frameBytes();
        long used = 0;
        int removed = 0;
        for (Path f : files) {
            long size;
            try {
                size = Files.size(f);
            } catch (IOException e) {
                continue;
            }
            used += size;
            if (used > budget) {
                try {
                    Files.deleteIfExists(f);
                    removed++;
                    used -= size;
                } catch (IOException ignored) {
                    // Locked by something else; it'll be pruned next time.
                }
            }
        }
        return removed;
    }

    /** Bytes one stored frame occupies. */
    public static int frameBytes() {
        return 1 + PvtObservation.SIZE + PvtAction.HEADS;
    }

    // ── batching ────────────────────────────────────────────────────────────────

    /**
     * Shuffles the frames for training. This destroys the episode ordering, so anything
     * that needs consecutive frames — {@link #idmPairs}, {@link #unlabelledPairs} — must
     * be built first. Episode ranges are cleared to make that mistake impossible rather
     * than merely discouraged.
     */
    public void shuffle(Random rng) {
        shuffled = true;
        episodes.clear();
        for (int i = frames.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            PvtFrame tmp = frames.get(i);
            frames.set(i, frames.get(j));
            frames.set(j, tmp);
        }
    }

    /** Materialises a slice as network inputs. */
    public float[][] observations(int from, int to) {
        int n = Math.max(0, Math.min(to, frames.size()) - from);
        float[][] out = new float[n][];
        for (int i = 0; i < n; i++) out[i] = frames.get(from + i).observation();
        return out;
    }

    /** Materialises a slice as network targets. */
    public int[][] labels(int from, int to) {
        int n = Math.max(0, Math.min(to, frames.size()) - from);
        int[][] out = new int[n][];
        for (int i = 0; i < n; i++) out[i] = frames.get(from + i).labels();
        return out;
    }

    /** One training example for the inverse dynamics model. */
    public record Pair(float[] input, int[] labels, int frameIndex) {}

    /**
     * Builds (observation, next observation) → action examples from frames whose action
     * is actually known. This is what teaches the inverse dynamics model to read an
     * action off a pair of consecutive views.
     */
    public List<Pair> idmPairs() {
        List<Pair> pairs = new ArrayList<>();
        if (shuffled) return pairs;
        for (int[] range : episodes) {
            for (int i = range[0]; i + 1 < range[1]; i++) {
                PvtFrame a = frames.get(i);
                PvtFrame b = frames.get(i + 1);
                if (!a.labelled()) continue;
                pairs.add(new Pair(join(a, b), a.labels(), i));
            }
        }
        return pairs;
    }

    /**
     * The same pairing over frames whose action is <i>not</i> known — the ones the
     * inverse dynamics model exists to label.
     */
    public List<Pair> unlabelledPairs() {
        List<Pair> pairs = new ArrayList<>();
        if (shuffled) return pairs;
        for (int[] range : episodes) {
            for (int i = range[0]; i + 1 < range[1]; i++) {
                PvtFrame a = frames.get(i);
                if (a.labelled()) continue;
                pairs.add(new Pair(join(a, frames.get(i + 1)), a.labels(), i));
            }
        }
        return pairs;
    }

    /** Builds one inverse-dynamics input from two consecutive observations. */
    private static float[] join(PvtFrame a, PvtFrame b) {
        return PvtIdm.join(a.observation(), b.observation());
    }

    /** Replaces a frame's action with one the inverse dynamics model worked out. */
    public void relabel(int index, int[] labels) {
        PvtFrame f = frames.get(index);
        frames.set(index, new PvtFrame(f.obs(), labels, true));
    }

    /**
     * How lopsided the demonstrations are, per head — the share taken by the single
     * commonest class. A policy trained on frames that are 97% "standing still" will
     * happily learn to stand still and score well, so this is surfaced in
     * {@code /ai pvt status} rather than left for someone to discover in-world.
     */
    public double[] headBias() {
        double[] bias = new double[PvtAction.HEADS];
        if (frames.isEmpty()) return bias;
        for (int h = 0; h < PvtAction.HEADS; h++) {
            int[] counts = new int[PvtAction.HEAD_SIZES[h]];
            for (PvtFrame f : frames) {
                int c = f.labels()[h];
                if (c >= 0 && c < counts.length) counts[c]++;
            }
            int most = 0;
            for (int c : counts) most = Math.max(most, c);
            bias[h] = most / (double) frames.size();
        }
        return bias;
    }
}
