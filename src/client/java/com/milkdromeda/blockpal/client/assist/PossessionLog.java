package com.milkdromeda.blockpal.client.assist;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A tiny shared status buffer for the client-side possession driver, so
 * {@link ClientPossession} (running on the client tick) can stream progress lines to
 * whatever {@code PossessionDriveScreen} happens to be open — or just buffer them for
 * next time the console is opened.
 */
public final class PossessionLog {

    private static final int MAX = 200;
    private static final List<String> LINES = new ArrayList<>();
    private static Consumer<String> listener;

    private PossessionLog() {}

    public static synchronized void add(String line) {
        if (line == null || line.isBlank()) return;
        LINES.add(line);
        while (LINES.size() > MAX) LINES.remove(0);
        if (listener != null) {
            try { listener.accept(line); } catch (Exception ignored) {}
        }
    }

    public static synchronized List<String> lines() {
        return new ArrayList<>(LINES);
    }

    /** The open drive console registers here to be nudged when a new line arrives. */
    public static synchronized void setListener(Consumer<String> l) {
        listener = l;
    }
}
