package com.milkdromeda.blockpal.admin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side store of lightweight per-player client stats — currently just the
 * frame-rate, which clients report roughly once a second so the admin menu can
 * show how everyone's game is performing. FPS is inherently a client-side number,
 * so the only way the server can display it is for clients to send it up.
 *
 * <p>Updated from the network (netty) thread and read from the server thread, so
 * it lives in a concurrent map. A value of {@code -1} means "unknown" — e.g. a
 * player without the mod, or one who hasn't reported yet.
 */
public final class PlayerStatsTracker {

    private PlayerStatsTracker() {}

    private static final Map<UUID, Integer> FPS = new ConcurrentHashMap<>();

    public static void report(UUID player, int fps) {
        if (player == null) return;
        FPS.put(player, Math.max(0, fps));
    }

    public static int fps(UUID player) {
        Integer v = player == null ? null : FPS.get(player);
        return v == null ? -1 : v;
    }

    public static void clear(UUID player) {
        if (player != null) FPS.remove(player);
    }
}
