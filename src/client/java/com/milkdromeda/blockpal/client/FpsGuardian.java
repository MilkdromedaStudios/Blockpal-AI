package com.milkdromeda.blockpal.client;

import com.milkdromeda.blockpal.network.EmergencyDisablePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * Last-resort frame-rate watchdog (an "extreme" watchdog, beyond the per-task
 * one). It samples the client frame-rate every tick and, if FPS stays below a
 * small preset-dependent floor for a sustained window, trips the mod-wide
 * {@link com.milkdromeda.blockpal.EmergencyState emergency kill switch} on
 * the server. The assistant then stops doing anything until the player runs
 * {@code /ai resume}.
 *
 * <p>The threshold scales with the performance preset because a high-end ("opus")
 * setup expects far more headroom than a "potato" one:
 * <ul>
 *   <li>potato → 3 FPS</li>
 *   <li>normal → 4 FPS</li>
 *   <li>opus   → 5 FPS</li>
 * </ul>
 */
public final class FpsGuardian {

    private FpsGuardian() {}

    /** How many consecutive low-FPS client ticks before we trip (~3 s of misery). */
    private static final int LOW_TICKS_TO_TRIP = 60;
    /** The selected performance preset, kept in sync from server config packets. */
    private static volatile String preset = "normal";

    private static int lowTicks = 0;
    /** True once we've sent a disable request; latched until FPS recovers well. */
    private static boolean tripped = false;

    /** Updates the preset used to choose the FPS floor (from a config sync packet). */
    public static void setPreset(String value) {
        if (value != null && !value.isBlank()) preset = value.trim();
    }

    private static int fpsFloor() {
        return switch (preset) {
            case "potato" -> 3;
            case "opus"   -> 5;
            default       -> 4; // normal
        };
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(FpsGuardian::onTick);
    }

    private static void onTick(Minecraft mc) {
        // Only meaningful when actually in a world and connected to a server.
        if (mc.level == null || mc.player == null || mc.getConnection() == null) {
            lowTicks = 0;
            return;
        }

        int fps = mc.getFps();
        // getFps() can read 0 during the very first frames; ignore that to avoid
        // false trips on world load.
        if (fps <= 0) {
            lowTicks = 0;
            return;
        }

        int floor = fpsFloor();

        if (fps < floor) {
            if (!tripped && ++lowTicks >= LOW_TICKS_TO_TRIP) {
                tripped = true;
                lowTicks = 0;
                if (ClientPlayNetworking.canSend(EmergencyDisablePayload.TYPE)) {
                    ClientPlayNetworking.send(new EmergencyDisablePayload(true, fps));
                }
            }
        } else {
            lowTicks = 0;
            // Recovered comfortably (well above the floor): unlatch so the guardian
            // can trip again later if the player re-enables and FPS collapses anew.
            if (tripped && fps >= floor * 4) {
                tripped = false;
            }
        }
    }
}
