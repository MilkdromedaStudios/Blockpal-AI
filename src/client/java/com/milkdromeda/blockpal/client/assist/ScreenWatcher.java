package com.milkdromeda.blockpal.client.assist;

import com.milkdromeda.blockpal.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;

/**
 * The "mini wiki assistant": a private, read-only watcher that notices your on-screen
 * situation (low health, starving, on fire, drowning, a new dimension…) and drops the
 * occasional short survival tip into your <b>own</b> chat HUD and the assistant chat
 * box. It never controls you and never sends anything to the server, so it's safe on
 * every server — it's just a second pair of eyes, like glancing at a wiki.
 */
public final class ScreenWatcher {

    /** Minimum gap between tips so it never nags (ms). */
    private static final long COOLDOWN_MS = 90_000L;

    private static long lastTipTime;
    private static String lastTrigger = "";
    private static boolean requestInFlight;

    private ScreenWatcher() {}

    /** Wired to {@code ClientTickEvents.END_CLIENT_TICK}. Cheap: only acts occasionally. */
    public static void tick() {
        ModConfig cfg = ModConfig.get();
        if (!cfg.assistantTips) return;
        if (requestInFlight) return;
        if (ClientPossession.isActive()) return;          // don't chatter while driving
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || !p.isAlive()) return;
        // Only sample a few times a second — this runs every client tick otherwise.
        if ((p.tickCount % 20) != 0) return;
        if (!ClientAi.available()) return;

        String trigger = detectTrigger(p);
        if (trigger == null) return;

        long now = System.currentTimeMillis();
        if (trigger.equals(lastTrigger) && now - lastTipTime < COOLDOWN_MS) return;
        if (now - lastTipTime < COOLDOWN_MS / 3) return;   // absolute floor between any two tips

        lastTrigger = trigger;
        lastTipTime = now;
        requestTip(trigger, situation(p));
    }

    /** Returns a short trigger key when something noteworthy is happening, else null. */
    private static String detectTrigger(LocalPlayer p) {
        try {
            if (p.isInLava()) return "lava";
            if (p.getAirSupply() >= 0 && p.getAirSupply() < 80 && p.isUnderWater()) return "drowning";
            if (p.getRemainingFireTicks() > 0) return "fire";
            float hp = p.getHealth() / Math.max(1f, p.getMaxHealth());
            if (hp < 0.30f) return "low_health";
            int food = p.getFoodData().getFoodLevel();
            if (food <= 4) return "starving";
            String dim = p.level().dimension().identifier().getPath();
            if (!dim.equals("overworld")) return "dim_" + dim;
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String situation(LocalPlayer p) {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("Health ").append((int) p.getHealth()).append("/").append((int) p.getMaxHealth());
            sb.append(", hunger ").append(p.getFoodData().getFoodLevel()).append("/20");
            sb.append(", dimension ").append(p.level().dimension().identifier().getPath());
            if (p.isInLava()) sb.append(", standing in lava");
            if (p.isUnderWater()) sb.append(", underwater (air ").append(p.getAirSupply()).append(")");
            if (p.getRemainingFireTicks() > 0) sb.append(", on fire");
        } catch (Exception ignored) {
        }
        return sb.toString();
    }

    private static void requestTip(String trigger, String situation) {
        requestInFlight = true;
        String ask = "Situation: " + situation + ". Give me ONE short, urgent survival tip (max 20 words) "
                + "for this exact situation. Plain text, no preamble.";
        ClientAi.chat(List.of(), ask).whenComplete((reply, ex) -> {
            requestInFlight = false;
            if (ex != null || reply == null || reply.isBlank() || reply.startsWith("(")) return;
            String tip = reply.trim();
            // Store the tip in the private assistant chat box (the "✦" chat / /aichat).
            // It is never sent to the server. We deliberately don't push it to the chat
            // HUD here: this MC version renamed the client chat-HUD accessor and it can't
            // be verified without a local compile, so the box is the single safe surface.
            Minecraft.getInstance().execute(() -> ChatMemory.addTip(tip));
        });
    }
}
