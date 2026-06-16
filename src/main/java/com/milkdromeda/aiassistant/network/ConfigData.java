package com.milkdromeda.aiassistant.network;

import com.milkdromeda.aiassistant.config.ModConfig;
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
        double guardRadius
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
                c.guardRadius);
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
                buf.readDouble());
    }
}
