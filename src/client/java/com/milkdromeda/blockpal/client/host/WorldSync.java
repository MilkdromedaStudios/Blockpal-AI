package com.milkdromeda.blockpal.client.host;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * File plumbing for "host my <b>current</b> world": copy the singleplayer save into
 * the hosted server, and — when hosting ends — sync the played world <b>back</b> over
 * the save (keeping a timestamped backup of the pre-sync original) and delete the
 * server's copy, so there's always exactly one true version of the world.
 *
 * <p>A tiny marker file ({@code blockpal-host/pending-sync.json}) records the source
 * save while a copy exists, so a crash mid-host can't silently orphan the played
 * world — the Host screen offers the sync again on next launch.
 */
final class WorldSync {

    private WorldSync() {}

    /** Files never copied between save and server (each side makes its own). */
    private static boolean skip(Path file) {
        String name = file.getFileName().toString();
        return name.equals("session.lock");
    }

    /** Whole-percent progress ticks for a copy. May be called from worker threads. */
    interface CopyProgress {
        void at(int percent, long doneBytes, long totalBytes);
    }

    /** What a finished copy moved, for the status log. */
    record CopyStats(int files, long bytes) {}

    /** Recursively copies a world folder ({@code session.lock} excluded). */
    static CopyStats copyWorld(Path from, Path to) throws IOException {
        return copyWorld(from, to, null);
    }

    /**
     * Recursively copies a world folder ({@code session.lock} excluded), reporting
     * whole-percent progress to {@code progress} (may be null).
     *
     * <p>The copy is <b>parallel</b>: the file list is gathered first (a cheap
     * metadata walk), directories are created, then the files — a world is mostly
     * many independent multi-megabyte region files — are copied on a small worker
     * pool. A sequential per-file copy left the disk mostly idle and made "Copying
     * your world…" take far longer than the hardware needed.
     */
    static CopyStats copyWorld(Path from, Path to, CopyProgress progress) throws IOException {
        if (!Files.isDirectory(from)) throw new IOException("World folder not found: " + from);
        deleteRecursively(to);   // never merge into a stale copy

        // Plan first: every directory to create and file to copy, plus total bytes.
        record Entry(Path file, long size) {}
        List<Path> dirs = new ArrayList<>();
        List<Entry> files = new ArrayList<>();
        long[] totalBytes = {0};
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                dirs.add(dir);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!skip(file)) {
                    files.add(new Entry(file, attrs.size()));
                    totalBytes[0] += attrs.size();
                }
                return FileVisitResult.CONTINUE;
            }
        });
        for (Path dir : dirs) Files.createDirectories(to.resolve(from.relativize(dir)));

        final long total = totalBytes[0];
        AtomicLong done = new AtomicLong();
        AtomicInteger lastPct = new AtomicInteger(-1);
        int threads = Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors()));
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "blockpal-world-copy");
            t.setDaemon(true);
            return t;
        });
        try {
            List<Future<?>> pending = new ArrayList<>(files.size());
            for (Entry e : files) {
                pending.add(pool.submit(() -> {
                    Files.copy(e.file(), to.resolve(from.relativize(e.file())),
                            StandardCopyOption.REPLACE_EXISTING);
                    long d = done.addAndGet(e.size());
                    if (progress != null) {
                        int pct = total == 0 ? 100 : (int) (d * 100 / total);
                        // Only tick the callback when the whole percent changes.
                        int prev = lastPct.get();
                        if (pct > prev && lastPct.compareAndSet(prev, pct)) {
                            progress.at(pct, d, total);
                        }
                    }
                    return null;
                }));
            }
            for (Future<?> f : pending) f.get();   // surfaces the first copy failure
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("World copy interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            throw cause instanceof IOException io ? io : new IOException(cause);
        } finally {
            pool.shutdownNow();
        }
        return new CopyStats(files.size(), total);
    }

    /**
     * Brings the hosted world's changes home: the original save is moved to a
     * timestamped backup, the served copy is copied into its place, and the server's
     * copy is deleted. On failure the backup is restored so the save is never lost.
     *
     * @return the backup folder the pre-sync original was kept in.
     */
    static Path syncBack(Path servedWorld, Path savePath, Path backupsDir) throws IOException {
        return syncBack(servedWorld, savePath, backupsDir, null);
    }

    static Path syncBack(Path servedWorld, Path savePath, Path backupsDir,
                         CopyProgress progress) throws IOException {
        if (!Files.isDirectory(servedWorld)) throw new IOException("Hosted world copy not found: " + servedWorld);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        Path backup = backupsDir.resolve(savePath.getFileName() + "-" + stamp);
        Files.createDirectories(backupsDir);

        // 1) Move the original save aside (fast rename on the same volume, else copy+delete).
        if (Files.isDirectory(savePath)) {
            try {
                Files.move(savePath, backup);
            } catch (IOException e) {
                copyWorld(savePath, backup);
                deleteRecursively(savePath);
            }
        }
        // 2) Put the played world where the save was; restore the backup if that fails.
        try {
            copyWorld(servedWorld, savePath, progress);
        } catch (IOException e) {
            deleteRecursively(savePath);
            if (Files.isDirectory(backup)) copyWorld(backup, savePath);
            throw new IOException("Sync-back failed (" + e.getMessage() + ") — your original world was restored.");
        }
        // 3) The save is now the one true world — drop the server's copy.
        deleteRecursively(servedWorld);
        return backup;
    }

    static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // ── crash-safety marker ──────────────────────────────────────────────────────

    /** Records that {@code savePath}'s world is currently checked out to the server. */
    static void writeMarker(Path savePath) {
        try {
            Files.createDirectories(HostPaths.ROOT);
            Files.writeString(HostPaths.pendingSyncMarker(),
                    "{\"sourceWorld\": \"" + savePath.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"}",
                    StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Marker is best-effort; the live flow doesn't depend on it.
        }
    }

    /** The save recorded by {@link #writeMarker}, or null if none/unreadable. */
    static Path readMarker() {
        try {
            Path marker = HostPaths.pendingSyncMarker();
            if (!Files.exists(marker)) return null;
            String json = Files.readString(marker, StandardCharsets.UTF_8);
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            return Path.of(o.get("sourceWorld").getAsString());
        } catch (Exception e) {
            return null;
        }
    }

    static void clearMarker() {
        try {
            Files.deleteIfExists(HostPaths.pendingSyncMarker());
        } catch (IOException ignored) {
        }
    }
}
