package com.milkdromeda.blockpal.client.host;

import net.fabricmc.loader.api.FabricLoader;

import java.util.Locale;

/**
 * Best-effort detection of the <b>Lunar Client</b>. Lunar can load Fabric mods,
 * but it is a closed launcher with no mod-facing API — there is no call a mod can
 * make to trigger Lunar's own built-in world hosting. So the most useful honest
 * thing is to <i>detect</i> Lunar and point the player at that feature where it's
 * the better fit (Java-only friends), while Blockpal hosting stays the way to get
 * Bedrock cross-play.
 *
 * <p>Since there's no official signal either, this checks a handful of footprints:
 * Lunar-ish mod ids, its bootstrap classes, and {@code lunarclient} in the launch
 * paths (Lunar installs under {@code ~/.lunarclient}). Any error fails safe to
 * "not Lunar" — detection must never break the host flow.
 */
public final class LunarDetect {

    private static Boolean detected;

    private LunarDetect() {}

    /** True when the game appears to be running under the Lunar Client. */
    public static synchronized boolean isLunarClient() {
        if (detected == null) detected = detect();
        return detected;
    }

    private static boolean detect() {
        try {
            FabricLoader loader = FabricLoader.getInstance();
            for (String id : new String[]{"lunar", "lunarclient", "lunar-client"}) {
                if (loader.isModLoaded(id)) return true;
            }
            for (String cls : new String[]{
                    "com.moonsworth.lunar.genesis.Genesis",
                    "com.moonsworth.lunar.client.Lunar"}) {
                try {
                    Class.forName(cls, false, LunarDetect.class.getClassLoader());
                    return true;
                } catch (ClassNotFoundException ignored) {
                    // Not this footprint — try the next one.
                }
            }
            String haystack = (System.getProperty("java.class.path", "") + "|"
                    + System.getProperty("user.dir", "") + "|"
                    + loader.getGameDir().toAbsolutePath()).toLowerCase(Locale.ROOT);
            return haystack.contains("lunarclient");
        } catch (Throwable t) {
            return false;
        }
    }
}
