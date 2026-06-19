package com.milkdromeda.blockpal.network;

import com.milkdromeda.blockpal.config.ModConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * A flat, serializable snapshot of the user-facing settings, shipped between
 * client and server so the in-game config menu can read and write them.
 *
 * <p>For privacy the API token is never sent <em>to</em> the client: sync
 * snapshots carry an empty {@link #token} plus {@link #tokenSet} so the menu can
 * show "set / not set". When the menu saves, a blank token means "keep the
 * existing one".
 *
 * <p>Developer-mode fields ({@link #actionTickDelay}, {@link #maxTaskSeconds},
 * {@link #fleeHealthPercent}) are included in every packet but only shown in the
 * GUI when developer mode is enabled. Setting them incorrectly can cause lag or
 * crashes — see {@code developer.md} for details.
 */
public record ConfigData(
        boolean chatListening,
        boolean activeMode,
        boolean debugLogging,
        String defaultName,
        String token,
        boolean tokenSet,
        String model,
        String apiUrl,
        double temperature,
        int maxTokens,
        double followDistance,
        double guardRadius,
        boolean allowCommands,
        int commandPermissionLevel,
        String defaultSkin,
        // Developer-mode fields
        int actionTickDelay,
        int maxTaskSeconds,
        double fleeHealthPercent,
        String performancePreset,
        boolean sneakToOpenMenu
) {
    public static final StreamCodec<FriendlyByteBuf, ConfigData> STREAM_CODEC =
            StreamCodec.of(ConfigData::write, ConfigData::read);

    /** Builds a snapshot from the live config, omitting the token value itself. */
    public static ConfigData fromConfig() {
        ModConfig c = ModConfig.get();
        return new ConfigData(
                c.chatListening,
                c.activeMode,
                c.debugLogging,
                c.defaultName,
                "",                 // never expose the token to the client
                c.hasApiToken(),
                c.hfModel,
                c.apiUrl,
                c.temperature,
                c.maxNewTokens,
                c.followDistance,
                c.guardRadius,
                c.allowCommands,
                c.commandPermissionLevel,
                c.defaultSkin,
                c.actionTickDelay,
                c.maxTaskSeconds,
                c.fleeHealthPercent,
                c.performancePreset,
                c.sneakToOpenMenu);
    }

    /** Applies this snapshot onto the live config, clamping and keeping blanks. */
    public void applyTo(ModConfig c) {
        c.chatListening = chatListening;
        c.activeMode = activeMode;
        c.debugLogging = debugLogging;
        if (notBlank(defaultName)) c.defaultName = defaultName.trim();
        if (notBlank(token)) c.hfToken = token.trim();   // blank = keep existing
        if (notBlank(model)) c.hfModel = model.trim();
        if (notBlank(apiUrl)) c.apiUrl = apiUrl.trim();
        c.temperature = clamp(temperature, 0.0, 2.0);
        c.maxNewTokens = (int) clamp(maxTokens, 32, 2048);
        c.followDistance = clamp(followDistance, 1.0, 32.0);
        c.guardRadius = clamp(guardRadius, 4.0, 64.0);
        c.allowCommands = allowCommands;
        c.commandPermissionLevel = (int) clamp(commandPermissionLevel, 0, 4);
        if (notBlank(defaultSkin)) c.defaultSkin = defaultSkin.trim();
        // Developer fields — applied as-is (intentionally no tight clamping)
        c.actionTickDelay = (int) clamp(actionTickDelay, 0, 40);
        c.maxTaskSeconds = (int) clamp(maxTaskSeconds, 0, 3600);
        c.fleeHealthPercent = clamp(fleeHealthPercent, 0.0, 1.0);
        if (notBlank(performancePreset)) c.performancePreset = performancePreset.trim();
        c.sneakToOpenMenu = sneakToOpenMenu;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static void write(FriendlyByteBuf buf, ConfigData d) {
        buf.writeBoolean(d.chatListening);
        buf.writeBoolean(d.activeMode);
        buf.writeBoolean(d.debugLogging);
        buf.writeUtf(d.defaultName == null ? "" : d.defaultName);
        buf.writeUtf(d.token == null ? "" : d.token);
        buf.writeBoolean(d.tokenSet);
        buf.writeUtf(d.model == null ? "" : d.model);
        buf.writeUtf(d.apiUrl == null ? "" : d.apiUrl);
        buf.writeDouble(d.temperature);
        buf.writeInt(d.maxTokens);
        buf.writeDouble(d.followDistance);
        buf.writeDouble(d.guardRadius);
        buf.writeBoolean(d.allowCommands);
        buf.writeInt(d.commandPermissionLevel);
        buf.writeUtf(d.defaultSkin == null ? "default" : d.defaultSkin);
        buf.writeInt(d.actionTickDelay);
        buf.writeInt(d.maxTaskSeconds);
        buf.writeDouble(d.fleeHealthPercent);
        buf.writeUtf(d.performancePreset == null ? "normal" : d.performancePreset);
        buf.writeBoolean(d.sneakToOpenMenu);
    }

    private static ConfigData read(FriendlyByteBuf buf) {
        return new ConfigData(
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readDouble(),
                buf.readInt(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readBoolean(),
                buf.readInt(),
                buf.readUtf(),
                buf.readInt(),
                buf.readInt(),
                buf.readDouble(),
                buf.readUtf(),
                buf.readBoolean());
    }
}
