package com.milkdromeda.blockpal.localai;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * <b>Unpacking downloaded archives, safely.</b>
 *
 * <p>llama.cpp ships {@code .zip} on Windows and {@code .tar.gz} everywhere else. The JDK
 * reads zip and gzip but has no tar reader, so there is a small one here — tar is a
 * fixed 512-byte-header format and this is the same trade the mod already makes with its
 * hand-rolled PNG encoder: sixty lines of well-understood parsing beats a dependency.
 *
 * <p><b>Every entry is checked before it is written.</b> An archive is untrusted input,
 * and an entry named {@code ../../.minecraft/mods/evil.jar} would otherwise escape the
 * directory it is supposed to land in — the "zip slip" bug. Entries that resolve outside
 * the destination are skipped, as are symlinks and anything absurdly large.
 */
public final class Archives {

    private Archives() {}

    /** Refuse to write a single file larger than this out of an archive. */
    private static final long MAX_ENTRY_BYTES = 2L * 1024 * 1024 * 1024;

    /** Unpacks a {@code .zip} or {@code .tar.gz} into {@code target}. */
    public static void extract(Path archive, Path target) throws IOException {
        String name = archive.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        Files.createDirectories(target);
        if (name.endsWith(".zip")) {
            unzip(archive, target);
        } else if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            untarGz(archive, target);
        } else {
            throw new IOException("I don't know how to unpack " + name);
        }
    }

    // ── zip ─────────────────────────────────────────────────────────────────────

    private static void unzip(Path archive, Path target) throws IOException {
        try (ZipInputStream in = new ZipInputStream(
                new BufferedInputStream(Files.newInputStream(archive), 1 << 16))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                Path out = safeResolve(target, entry.getName());
                if (out == null) continue;
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    copy(in, out);
                }
                in.closeEntry();
            }
        }
    }

    // ── tar.gz ──────────────────────────────────────────────────────────────────

    private static final int TAR_BLOCK = 512;

    private static void untarGz(Path archive, Path target) throws IOException {
        try (InputStream in = new BufferedInputStream(
                new GZIPInputStream(Files.newInputStream(archive)), 1 << 16)) {
            byte[] header = new byte[TAR_BLOCK];
            while (true) {
                if (!readFully(in, header)) break;
                // Two consecutive zero blocks mark the end of the archive.
                if (isZero(header)) break;

                String name = cString(header, 0, 100);
                long size = octal(header, 124, 12);
                char type = (char) (header[156] & 0xFF);

                // Long-name and extended-header entries carry metadata, not files.
                boolean isFile = type == '0' || type == 0;
                boolean isDir = type == '5';

                Path out = safeResolve(target, name);
                if (isDir) {
                    if (out != null) Files.createDirectories(out);
                } else if (isFile && out != null) {
                    Files.createDirectories(out.getParent());
                    copyExactly(in, out, size);
                } else {
                    skip(in, size);
                }
                // Entry bodies are padded up to the next 512-byte boundary.
                long padding = (TAR_BLOCK - (size % TAR_BLOCK)) % TAR_BLOCK;
                skip(in, padding);
            }
        }
    }

    private static boolean isZero(byte[] block) {
        for (byte b : block) {
            if (b != 0) return false;
        }
        return true;
    }

    private static String cString(byte[] buf, int offset, int max) {
        int end = offset;
        while (end < offset + max && buf[end] != 0) end++;
        return new String(buf, offset, end - offset, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static long octal(byte[] buf, int offset, int max) {
        long value = 0;
        for (int i = offset; i < offset + max; i++) {
            int c = buf[i] & 0xFF;
            if (c == 0 || c == ' ') {
                if (value != 0) break;
                continue;
            }
            if (c < '0' || c > '7') break;
            value = value * 8 + (c - '0');
        }
        return value;
    }

    private static boolean readFully(InputStream in, byte[] buf) throws IOException {
        int read = 0;
        while (read < buf.length) {
            int n = in.read(buf, read, buf.length - read);
            if (n < 0) return read != 0 && fill(buf, read);
            read += n;
        }
        return true;
    }

    /** A truncated final block is treated as end-of-archive rather than an error. */
    private static boolean fill(byte[] buf, int from) {
        java.util.Arrays.fill(buf, from, buf.length, (byte) 0);
        return false;
    }

    private static void skip(InputStream in, long bytes) throws IOException {
        long left = bytes;
        byte[] scratch = new byte[8192];
        while (left > 0) {
            int n = in.read(scratch, 0, (int) Math.min(scratch.length, left));
            if (n < 0) return;
            left -= n;
        }
    }

    private static void copyExactly(InputStream in, Path out, long size) throws IOException {
        if (size > MAX_ENTRY_BYTES) {
            skip(in, size);
            return;
        }
        try (OutputStream os = Files.newOutputStream(out)) {
            byte[] buf = new byte[1 << 16];
            long left = size;
            while (left > 0) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) break;
                os.write(buf, 0, n);
                left -= n;
            }
        }
    }

    private static void copy(InputStream in, Path out) throws IOException {
        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Resolves an archive entry name inside {@code target}, or null when it would escape.
     *
     * <p>This is the zip-slip guard. Anything absolute, anything that climbs out with
     * {@code ..}, and anything that normalises to a path outside the destination is
     * refused — a downloaded archive is untrusted input however official its source.
     *
     * <p>Public so it can be tested directly: it is the one function here whose failure
     * would be a security bug rather than an inconvenience, and a guard nobody can call
     * in a test is a guard nobody checks.
     */
    public static Path safeResolve(Path target, String entryName) {
        if (entryName == null || entryName.isBlank()) return null;
        String cleaned = entryName.replace('\\', '/');
        if (cleaned.startsWith("/") || cleaned.contains(":")) return null;
        Path base = target.toAbsolutePath().normalize();
        Path resolved = base.resolve(cleaned).normalize();
        return resolved.startsWith(base) ? resolved : null;
    }

    /**
     * Finds a named executable anywhere under a directory — the archives nest their
     * binaries differently per platform, so hunting beats hard-coding a layout.
     */
    public static Path findExecutable(Path root, String baseName) throws IOException {
        if (!Files.isDirectory(root)) return null;
        try (var stream = Files.walk(root, 4)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                        return n.equals(baseName) || n.equals(baseName + ".exe");
                    })
                    .findFirst()
                    .orElse(null);
        }
    }

    /** Marks a file runnable on Unix; a no-op where the filesystem has no such notion. */
    public static void makeExecutable(Path file) {
        try {
            file.toFile().setExecutable(true, false);
        } catch (Exception ignored) {
            // Windows and odd filesystems: nothing to do.
        }
    }
}
