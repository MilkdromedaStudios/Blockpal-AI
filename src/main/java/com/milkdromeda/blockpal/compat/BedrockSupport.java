package com.milkdromeda.blockpal.compat;

import com.milkdromeda.blockpal.AiAssistantMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Soft, optional integration with <a href="https://geysermc.org">Geyser/Floodgate</a>
 * so Bedrock Edition players (iPad, console, phone, Win10) can play through a Geyser
 * proxy.
 *
 * <p><b>Why this exists:</b> Geyser is a server-side proxy that translates Bedrock ⇄
 * Java. Almost all of Blockpal is server-authoritative — chat, the {@code /ai}
 * commands, the entity AI, combat and inventory — so it already works for Bedrock
 * players once an admin installs Geyser + Floodgate. The gaps are the features a
 * Fabric <em>client</em> mod normally provides (the in-game GUI panels, the FPS
 * watchdog), which a Bedrock client cannot run. Knowing which players are on Bedrock
 * lets us hand them a sensible text/command fallback instead of pointing them at a
 * menu they can never open.
 *
 * <p><b>Zero hard dependency.</b> This class never references a Floodgate type at
 * compile time — everything goes through reflection and is gated on the {@code
 * floodgate} mod actually being present. On a normal Fabric server with no Geyser,
 * {@link #isFloodgateInstalled()} is {@code false} and every query returns {@code
 * false}, so Blockpal behaves exactly as before. That keeps Floodgate a true
 * <em>optional</em> dependency: the mod loads and runs fine without it.
 */
public final class BedrockSupport {

    private BedrockSupport() {}

    /** Whether the Floodgate mod is present on this server. Resolved once at init. */
    private static final boolean FLOODGATE_PRESENT =
            FabricLoader.getInstance().isModLoaded("floodgate");

    // Lazily-resolved reflection handles for org.geysermc.floodgate.api.FloodgateApi.
    // Resolved on first use (the Floodgate API singleton isn't ready at mod-init
    // time, so we keep retrying until getInstance() returns non-null).
    private static Method getInstanceMethod;       // FloodgateApi.getInstance()
    private static Method isFloodgatePlayerMethod; // FloodgateApi#isFloodgatePlayer(UUID)
    private static Object floodgateApi;            // cached singleton, once available
    private static boolean classLookupDone = false;

    /** True when Floodgate is installed (i.e. Bedrock players may be connected). */
    public static boolean isFloodgateInstalled() {
        return FLOODGATE_PRESENT;
    }

    /** Convenience: is this player connected from Bedrock Edition (via Floodgate)? */
    public static boolean isBedrockPlayer(ServerPlayer player) {
        return player != null && isBedrockPlayer(player.getUUID());
    }

    /**
     * Whether the player with this UUID is a Bedrock (Floodgate) player. Always
     * {@code false} when Floodgate isn't installed, or if the API can't be reached
     * for any reason — we fail safe to "treat as a Java player".
     */
    public static boolean isBedrockPlayer(UUID uuid) {
        if (!FLOODGATE_PRESENT || uuid == null) return false;
        try {
            Object api = api();
            if (api == null || isFloodgatePlayerMethod == null) return false;
            Object result = isFloodgatePlayerMethod.invoke(api, uuid);
            return result instanceof Boolean b && b;
        } catch (Throwable t) {
            // Never let a Floodgate hiccup break a Blockpal command — fail safe.
            AiAssistantMod.LOGGER.debug("Floodgate isBedrockPlayer check failed: {}", t.toString());
            return false;
        }
    }

    /**
     * Returns the Floodgate API singleton, resolving the reflection handles on the
     * first call. The class/method lookup happens once; {@code getInstance()} is
     * re-invoked until it returns non-null, because the singleton isn't ready at
     * mod-init time. Returns {@code null} if the API can't be reached.
     */
    private static synchronized Object api() throws Exception {
        if (floodgateApi != null) return floodgateApi;
        if (!classLookupDone) {
            classLookupDone = true;
            try {
                Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                getInstanceMethod = apiClass.getMethod("getInstance");
                isFloodgatePlayerMethod = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            } catch (Throwable t) {
                AiAssistantMod.LOGGER.debug("Floodgate API not reachable via reflection: {}", t.toString());
                getInstanceMethod = null;
            }
        }
        if (getInstanceMethod == null) return null;
        floodgateApi = getInstanceMethod.invoke(null);
        return floodgateApi;
    }
}
