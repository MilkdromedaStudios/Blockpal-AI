package com.milkdromeda.blockpal.client.host;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs the downloaded Fabric server as a child process, reusing the JVM that's
 * already running the game (its {@code java.home}), and streams its console back
 * line-by-line. Stops it cleanly by sending {@code stop} on stdin, force-killing
 * only if it won't exit.
 */
final class ServerProcess {

    interface Listener {
        void onLine(String line);
        void onReady();
        void onExit(int code);
    }

    private Process process;
    private BufferedWriter stdin;
    private volatile boolean ready;

    void start(Path serverDir, Path launcherJar, int memoryMb, Listener listener) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(javaExecutable());
        cmd.add("-Xmx" + Math.max(1024, memoryMb) + "M");
        cmd.add("-jar");
        cmd.add(launcherJar.toAbsolutePath().toString());
        cmd.add("nogui");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(serverDir.toFile());
        pb.redirectErrorStream(true);
        process = pb.start();
        stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

        Thread reader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    listener.onLine(line);
                    // Vanilla prints: Done (12.345s)! For help, type "help"
                    if (!ready && line.contains("Done (") && line.contains("help")) {
                        ready = true;
                        listener.onReady();
                    }
                }
            } catch (IOException ignored) {
                // stream closed on shutdown
            }
            listener.onExit(process.exitValue());
        }, "blockpal-host-log");
        reader.setDaemon(true);
        reader.start();
    }

    boolean isRunning() { return process != null && process.isAlive(); }

    void stop() {
        try {
            if (stdin != null && isRunning()) {
                stdin.write("stop\n");
                stdin.flush();
            }
        } catch (IOException ignored) {
            // fall through to force-kill
        }
        try {
            if (process != null && !process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            if (process != null) process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    /** The {@code java} binary of the JVM currently running the game. */
    private static String javaExecutable() {
        String home = System.getProperty("java.home");
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return Path.of(home, "bin", windows ? "java.exe" : "java").toString();
    }
}
