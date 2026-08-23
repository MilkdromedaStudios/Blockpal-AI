import com.milkdromeda.blockpal.localai.Archives;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * The tar reader is hand-rolled, and an archive is untrusted input. This checks it
 * against archives made by the system's own tar/zip, and that a malicious entry name
 * cannot escape the destination directory.
 */
public class ArchiveTest {
    static int pass = 0, fail = 0;
    static void check(String what, boolean ok, String detail) {
        if (ok) { pass++; System.out.println("  ok   " + what + (detail.isEmpty()?"":"  ("+detail+")")); }
        else { fail++; System.out.println("  FAIL " + what + "  " + detail); }
    }

    static byte[] blob(int n, int seed) {
        byte[] b = new byte[n];
        new Random(seed).nextBytes(b);
        return b;
    }

    public static void main(String[] a) throws Exception {
        Path work = Files.createTempDirectory("arch");
        Path src = work.resolve("src");
        Files.createDirectories(src.resolve("bin"));
        Files.createDirectories(src.resolve("lib/nested"));

        // Sizes chosen to straddle tar's 512-byte block padding in every direction.
        Map<String, byte[]> expect = new LinkedHashMap<>();
        expect.put("bin/llama-server", blob(511, 1));
        expect.put("bin/exact", blob(512, 2));
        expect.put("bin/over", blob(513, 3));
        expect.put("lib/big.so", blob(300_000, 4));
        expect.put("lib/nested/empty.txt", new byte[0]);
        for (var e : expect.entrySet()) Files.write(src.resolve(e.getKey()), e.getValue());

        // ── tar.gz, made by the system tar ────────────────────────────────────────
        System.out.println("tar.gz round-trip (archive built by system tar)");
        Path tgz = work.resolve("a.tar.gz");
        Process p = new ProcessBuilder("tar", "czf", tgz.toString(), "-C", src.toString(), ".")
                .redirectErrorStream(true).start();
        check("system tar produced an archive", p.waitFor() == 0 && Files.size(tgz) > 0,
                Files.exists(tgz) ? Files.size(tgz) + " bytes" : "missing");

        Path outTar = work.resolve("out-tar");
        Archives.extract(tgz, outTar);
        int matched = 0;
        for (var e : expect.entrySet()) {
            Path got = outTar.resolve(e.getKey());
            if (Files.exists(got) && Arrays.equals(Files.readAllBytes(got), e.getValue())) matched++;
            else System.out.println("      mismatch: " + e.getKey()
                    + (Files.exists(got) ? " (" + Files.size(got) + " vs " + e.getValue().length + ")" : " (absent)"));
        }
        check("every file extracted byte-identical", matched == expect.size(),
                matched + "/" + expect.size());
        check("nested directories recreated", Files.isDirectory(outTar.resolve("lib/nested")), "");
        check("a 300 KB file survives (multi-block)",
                Files.exists(outTar.resolve("lib/big.so"))
                && Files.size(outTar.resolve("lib/big.so")) == 300_000, "");
        check("an empty file is still created",
                Files.exists(outTar.resolve("lib/nested/empty.txt"))
                && Files.size(outTar.resolve("lib/nested/empty.txt")) == 0, "");

        // ── zip, made by Java ─────────────────────────────────────────────────────
        System.out.println("\nzip round-trip");
        Path zip = work.resolve("a.zip");
        try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
            for (var e : expect.entrySet()) {
                zos.putNextEntry(new java.util.zip.ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        Path outZip = work.resolve("out-zip");
        Archives.extract(zip, outZip);
        int zmatched = 0;
        for (var e : expect.entrySet()) {
            Path got = outZip.resolve(e.getKey());
            if (Files.exists(got) && Arrays.equals(Files.readAllBytes(got), e.getValue())) zmatched++;
        }
        check("every zip entry extracted byte-identical", zmatched == expect.size(),
                zmatched + "/" + expect.size());

        // ── finding the binary ────────────────────────────────────────────────────
        System.out.println("\nLocating the server binary");
        Path found = Archives.findExecutable(outTar, "llama-server");
        check("llama-server found inside the unpacked tree", found != null,
                found == null ? "not found" : outTar.relativize(found).toString());
        check("a binary that isn't there returns null",
                Archives.findExecutable(outTar, "definitely-not-here") == null, "");

        // ── zip slip ──────────────────────────────────────────────────────────────
        System.out.println("\nzip-slip: a malicious archive must not escape its directory");
        Path base = work.resolve("dest");
        Files.createDirectories(base);
        String[] hostile = {
            "../escaped.txt", "../../escaped.txt", "/etc/passwd", "a/../../escaped.txt",
            "..\\escaped.txt", "C:\\windows\\evil.dll", "", "   "
        };
        int refused = 0;
        for (String name : hostile) {
            Path r = Archives.safeResolve(base, name);
            if (r == null) refused++;
            else System.out.println("      ALLOWED: " + name + " -> " + r);
        }
        check("every hostile entry name refused", refused == hostile.length,
                refused + "/" + hostile.length);

        String[] fine = {"bin/llama-server", "./lib/x.so", "a/b/c/d.txt"};
        int allowed = 0;
        for (String name : fine) {
            Path r = Archives.safeResolve(base, name);
            if (r != null && r.startsWith(base.toAbsolutePath().normalize())) allowed++;
        }
        check("ordinary entry names still allowed", allowed == fine.length, allowed + "/" + fine.length);

        // a real tar containing a traversal entry must extract nothing outside
        Path evilSrc = work.resolve("evilsrc");
        Files.createDirectories(evilSrc);
        Files.writeString(evilSrc.resolve("payload"), "pwned");
        Path evil = work.resolve("evil.tar.gz");
        Process p2 = new ProcessBuilder("tar", "czf", evil.toString(),
                "-C", evilSrc.toString(), "--transform", "s,^payload,../../escaped-payload,",
                "payload").redirectErrorStream(true).start();
        p2.waitFor();
        Path dest = work.resolve("dest2/inner");
        Files.createDirectories(dest);
        try { Archives.extract(evil, dest); } catch (IOException ignored) {}
        boolean escaped = Files.exists(work.resolve("escaped-payload"))
                || Files.exists(work.resolve("dest2/escaped-payload"));
        check("a traversal entry in a REAL archive wrote nothing outside", !escaped,
                escaped ? "FILE ESCAPED" : "contained");

        System.out.println("\n" + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
