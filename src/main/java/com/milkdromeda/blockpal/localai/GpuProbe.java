package com.milkdromeda.blockpal.localai;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * <b>Is there a graphics card worth using here?</b>
 *
 * <p>Java has no view of the GPU, so this asks the vendor's own tool. It is deliberately
 * a <i>hint</i>, not a gate: everything it reports only changes which llama.cpp build is
 * preferred, and every path still falls back to a build that runs on the CPU. A wrong
 * answer therefore costs speed, never correctness.
 *
 * <p>Probes are run once and cached, with a short timeout — a missing tool must not hold
 * up a server tick, and on most machines {@code nvidia-smi} simply is not on the PATH.
 */
public final class GpuProbe {

    private GpuProbe() {}

    private static Boolean nvidia;
    private static String description;

    /** True when an NVIDIA driver is present, so the CUDA build is worth downloading. */
    public static synchronized boolean hasNvidia() {
        if (nvidia == null) probe();
        return nvidia;
    }

    /** What was found, in words, for the setup screen. */
    public static synchronized String describe() {
        if (nvidia == null) probe();
        return description;
    }

    private static void probe() {
        nvidia = false;
        description = "no NVIDIA tools found — will use a general GPU build";
        try {
            Process process = new ProcessBuilder(
                    "nvidia-smi", "--query-gpu=name,memory.total", "--format=csv,noheader")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(4, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return;
            }
            if (process.exitValue() != 0) return;
            String output = new String(process.getInputStream().readAllBytes()).trim();
            if (output.isEmpty()) return;
            nvidia = true;
            String first = output.lines().findFirst().orElse("").trim();
            description = first.isEmpty() ? "NVIDIA GPU detected" : "NVIDIA " + first;
        } catch (Exception e) {
            // No nvidia-smi, no permission, no driver — all the same answer: assume not.
            nvidia = false;
        }
    }

    /** Re-runs the probe (used after someone installs a driver and retries). */
    public static synchronized void reset() {
        nvidia = null;
        description = null;
    }

    /** True on a Mac with Apple Silicon, where Metal comes free with the normal build. */
    public static boolean isAppleSilicon() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return (os.contains("mac") || os.contains("darwin")) && arch.contains("aarch64");
    }
}
