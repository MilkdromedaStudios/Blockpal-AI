package com.milkdromeda.blockpal;

/**
 * Global emergency kill switch for the whole mod.
 *
 * <p>When the client detects the game's frame-rate has collapsed (sustained FPS
 * below a small, preset-dependent threshold), it flips this flag on the server.
 * While disabled, the assistant entity stays in the world but does nothing: no
 * AI planning, no task execution, no chat analysis, no gear management. This is
 * a last-resort safety net to keep a runaway mod from crashing the game.
 *
 * <p>The flag is process-global (static) and intentionally simple — it is read
 * on the server thread and written from the network receiver / commands on the
 * same thread, so a plain {@code volatile} is sufficient.
 */
public final class EmergencyState {

    private EmergencyState() {}

    private static volatile boolean disabled = false;

    /** Whether the mod is currently emergency-disabled. */
    public static boolean isDisabled() {
        return disabled;
    }

    /** Trips or clears the kill switch. */
    public static void setDisabled(boolean value) {
        disabled = value;
    }
}
