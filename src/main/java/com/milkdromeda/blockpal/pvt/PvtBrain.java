package com.milkdromeda.blockpal.pvt;

import com.milkdromeda.blockpal.agent.BotInput;
import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import net.minecraft.server.level.ServerLevel;

/**
 * <b>Plays the bot from what it learned by watching.</b> No model call, no network, no
 * script: an observation goes in, nine button decisions come out, twenty times a second
 * — or rather, ten, since decisions are taken on
 * {@link PvtObservation#DECISION_INTERVAL} and held in between, exactly as they were
 * recorded.
 *
 * <p>This is the fastest the companion can possibly be, and that is the point of it as
 * much as the imitation is. A language model round trip is seconds; a forward pass
 * through a hundred thousand weights is tens of microseconds, so the bot reacts within a
 * tick of something happening in front of it.
 *
 * <p>It is also, on its own, <i>shallow</i>. Behaviour cloning reproduces what the
 * demonstrations contained and nothing else: a policy trained on somebody chopping trees
 * will chop trees, and will not go and build a house because you asked. So it yields in
 * two directions — the survival reflexes still outrank it, and when it is unsure
 * ({@code pvtConfidence}) it hands the tick back so the thinking brain can take over.
 * The learned policy is the reflexes; the model is the intent.
 */
public final class PvtBrain {

    private final PvtNet.Scratch scratch;
    private final PvtNet net;

    private PvtAction held = PvtAction.idle();
    private double lastConfidence;
    private int sinceDecision;
    private float aimYaw;
    private float aimPitch;
    private boolean aimPrimed;
    private long decisions;

    private PvtBrain(PvtNet net) {
        this.net = net;
        this.scratch = net.scratch();
    }

    /** A brain for the currently-trained policy, or null when there isn't one. */
    public static PvtBrain create() {
        PvtNet net = PvtManager.policy();
        return net == null ? null : new PvtBrain(net);
    }

    public double confidence() { return lastConfidence; }
    public PvtAction lastAction() { return held; }
    public long decisions() { return decisions; }

    /**
     * Drives the bot for one tick.
     *
     * @return true when the policy took the controls; false to let another brain have
     *         this tick (no policy, or it wasn't sure enough to act)
     */
    public boolean tick(AiAssistantEntity bot, ServerLevel level, BotInput input) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.pvtEnabled) return false;

        if (--sinceDecision <= 0) {
            sinceDecision = PvtObservation.DECISION_INTERVAL;
            float[] obs = PvtObservation.encode(bot, level);
            PvtNet.Prediction p = net.predict(obs, scratch);
            lastConfidence = p.confidence();
            decisions++;

            if (lastConfidence < cfg.pvtConfidence) {
                // Not sure enough to be trusted with the body this round.
                held = PvtAction.idle();
                aimPrimed = false;
                return false;
            }
            held = p.action();
            // Turn targets are relative to where the head is when the decision is taken,
            // which is how they were recorded — a delta, not a destination.
            aimYaw = bot.getYRot() + held.yawDelta();
            aimPitch = Math.max(-90f, Math.min(90f, bot.getXRot() + held.pitchDelta()));
            aimPrimed = true;
        } else if (lastConfidence < cfg.pvtConfidence) {
            return false;
        }

        apply(input);
        return true;
    }

    /** Presses what the policy chose. */
    private void apply(BotInput input) {
        input.setForward(held.forward());
        input.setStrafe(held.strafe());
        input.setJump(held.jump());
        input.setSneak(held.sneak());
        input.setSprint(held.sprint());
        input.setAttack(held.attack());
        input.setUse(held.use());
        if (aimPrimed) input.aim(aimYaw, aimPitch);
    }

    /** Drops everything the policy was holding. */
    public void release(BotInput input) {
        held = PvtAction.idle();
        aimPrimed = false;
        input.releaseAll();
    }

    /** A one-line description for {@code /ai pvt status} and the bot panel. */
    public String describe() {
        return String.format("%s (%.0f%% sure)", held, lastConfidence * 100);
    }
}
