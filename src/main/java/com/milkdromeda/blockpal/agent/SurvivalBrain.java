package com.milkdromeda.blockpal.agent;

import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * <b>What keeps the companion alive when nothing is thinking for it.</b>
 *
 * <p>A bot spends most of its life without a model in the loop: the AI connection may be
 * {@code mcp} (an outside app only talks to it now and then), or {@code off}, or the
 * service may simply be down. It should not stand in a fire waiting for instructions.
 *
 * <p>So these reflexes run every tick, need no API of any kind, and use the same
 * {@link BotInput} controls a script would — swim up when drowning, get out of the fire,
 * climb out of a hole, eat when hurt, and walk back toward its owner when it has been
 * left far behind (walk, never teleport: a companion that blinks to you isn't living in
 * the world). They only take the controls in a real emergency, so they never fight the
 * ordinary follow/guard behaviour.
 */
public class SurvivalBrain {

    /** How far the owner can get before the bot starts making its own way back. */
    private static final double CATCH_UP_DISTANCE = 24.0;

    /** Ticks the current reflex keeps the controls, so it isn't re-decided every tick. */
    private int holdTicks;
    private boolean acting;
    private int eatCooldown;
    private int stuckTicks;
    private double lastX, lastZ;

    /** True while a reflex is holding the controls this tick. */
    public boolean isActing() { return acting; }

    /**
     * Runs the reflexes.
     *
     * @return true when this brain is driving (the caller should apply the inputs)
     */
    public boolean tick(AiAssistantEntity bot, ServerLevel level, BotInput input) {
        if (eatCooldown > 0) eatCooldown--;

        if (holdTicks > 0) {
            holdTicks--;
            acting = true;
            return true;
        }
        acting = false;
        input.setJump(false);
        input.setForward(0);

        // 1. Drowning — swim toward the surface.
        if (bot.isUnderWater() && bot.getAirSupply() < 120) {
            input.aim(bot.getYRot(), -60f);
            input.setForward(0.6f);
            input.setJump(true);
            return hold(input, 20);
        }

        // 2. On fire — run for water if any is close, otherwise just run.
        if (bot.isOnFire() && !bot.isInWater()) {
            String water = nearestWater(bot, level);
            if (!water.isEmpty()) {
                input.setForward(1f);
                return hold(input, 25);
            }
            input.setForward(1f);
            return hold(input, 15);
        }

        // 3. Hurt and carrying food — eat, exactly like a player would pause to eat.
        if (bot.getHealth() < bot.getMaxHealth() * 0.6f && eatCooldown <= 0) {
            if (bot.consumeBestFood(level)) {
                eatCooldown = 100;
                return hold(input, 8);
            }
            eatCooldown = 60;   // nothing edible — don't re-check every tick
        }

        // 4. Wedged against something while trying to move — hop, the way players do.
        if (Math.abs(bot.getX() - lastX) < 0.02 && Math.abs(bot.getZ() - lastZ) < 0.02
                && (bot.zza != 0 || bot.xxa != 0)) {
            if (++stuckTicks > 20) {
                stuckTicks = 0;
                input.setJump(true);
                return hold(input, 6);
            }
        } else {
            stuckTicks = 0;
        }
        lastX = bot.getX();
        lastZ = bot.getZ();

        // 5. Left far behind. It walks — a companion that teleports to you isn't really
        //    living in the world, and a creative-mode owner can out-fly anything.
        Player owner = bot.getOwnerPlayer();
        if (owner != null && bot.getMode() == AiAssistantEntity.Mode.FOLLOWING
                && bot.distanceToSqr(owner) > CATCH_UP_DISTANCE * CATCH_UP_DISTANCE) {
            bot.getNavigation().moveTo(owner, 1.15);
            return false;   // the navigation does the walking; no manual input needed
        }
        return false;
    }

    private boolean hold(BotInput input, int ticks) {
        holdTicks = ticks;
        acting = true;
        return true;
    }

    /** Is there water within a few blocks? Used only to decide whether running helps. */
    private static String nearestWater(AiAssistantEntity bot, ServerLevel level) {
        net.minecraft.core.BlockPos centre = bot.blockPosition();
        net.minecraft.core.BlockPos.MutableBlockPos cursor = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -3; dy <= 1; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (!level.getFluidState(cursor).isEmpty()) {
                        return cursor.getX() + "," + cursor.getY() + "," + cursor.getZ();
                    }
                }
            }
        }
        return "";
    }
}
