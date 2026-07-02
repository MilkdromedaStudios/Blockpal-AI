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

    /** Recursively copies a world folder ({@code session.lock} excluded). */
    static void copyWorld(Path from, Path to) throws IOException {
        if (!Files.isDirectory(from)) throw new IOException("World folder not found: " + from);
        deleteRecursively(to);   // never merge into a stale copy
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!skip(file)) {
                    Files.copy(file, to.resolve(from.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Brings the hosted world's changes home: the original save is moved to a
     * timestamped backup, the served copy is copied into its place, and the server's
     * copy is deleted. On failure the backup is restored so the save is never lost.
     *
     * @return the backup folder the pre-sync original was kept in.
     */
    static Path syncBack(Path servedWorld, Path savePath, Path backupsDir) throws IOException {
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
            copyWorld(servedWorld, savePath);
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
