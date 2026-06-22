package com.milkdromeda.blockpal.entity.goal;

import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;

/**
 * Top-priority survival instinct that runs in <em>every</em> mode — including
 * while a plan is being generated or executed. This is what stops the assistant
 * from standing still and getting chewed on by mobs while it "thinks": planning
 * happens asynchronously, and this reflex reacts instantly with no API call.
 *
 * <p>It does not touch the entity's {@code mode}, so whatever it was doing
 * (executing a plan, following, building) resumes the moment the threat is gone.
 * When badly hurt it disengages and retreats toward its owner instead of trading
 * blows to the death.
 */
public class SurvivalReflexGoal extends Goal {

    private static final Random RAND = new Random();
    private final AiAssistantEntity entity;
    private LivingEntity target;
    private int attackCooldown = 0;
    private int scanCooldown = 0;
    private int repathCooldown = 0;
    private boolean wasFleeing = false;
    private boolean calledForHelp = false;

    public SurvivalReflexGoal(AiAssistantEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        // Throttle threat scans (every ~0.4s) so the reflex stays cheap. Being hit
        // is still caught promptly via getLastHurtByMob on the next scan.
        if (scanCooldown > 0) { scanCooldown--; return false; }
        scanCooldown = 8;
        target = findThreat();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        double r = ModConfig.get().guardRadius * 2.0;
        return target != null && target.isAlive() && entity.distanceToSqr(target) < r * r;
    }

    @Override
    public void tick() {
        if (target == null || !target.isAlive()) {
            target = findThreat();
            if (target == null) return;
        }

        entity.getLookControl().setLookAt(target, 30f, 30f);

        boolean lowHealth = entity.getHealth() < entity.getMaxHealth() * ModConfig.get().fleeHealthPercent;
        if (lowHealth) {
            retreat();
            return;
        }

        // Engage: close in (re-pathing only periodically) and strike.
        if (shouldRepath()) entity.getNavigation().moveTo(target, 1.3);
        if (attackCooldown <= 0 && entity.distanceToSqr(target) < 9.0) {
            entity.swing(InteractionHand.MAIN_HAND);
            if (entity.level() instanceof ServerLevel sl) {
                entity.doHurtTarget(sl, target);
            }
            attackCooldown = 18;
        } else if (attackCooldown > 0) {
            attackCooldown--;
        }
    }

    private static String pick(String... options) {
        return options[RAND.nextInt(options.length)];
    }

    private void retreat() {
        if (!wasFleeing) {
            if (!calledForHelp) {
                entity.broadcastMessage(pick(
                        "Help! I'm getting overwhelmed — someone help me!",
                        "A little help here?! I can't handle this alone!",
                        "I'm in trouble — anyone nearby, please help!",
                        "This is bad — I need backup!"));
                calledForHelp = true;
            } else {
                entity.broadcastMessage(pick(
                        "I need to fall back, this is too dangerous!",
                        "Retreating — this isn't worth dying over.",
                        "Way too risky — pulling back!",
                        "I'm getting out of here!"));
            }
            wasFleeing = true;
        }
        if (!shouldRepath()) return;
        Player owner = entity.getOwnerPlayer();
        Vec3 dest;
        if (owner != null && entity.distanceToSqr(owner) < 64 * 64) {
            dest = owner.position();
        } else {
            // Move directly away from the threat.
            Vec3 away = entity.position().subtract(target.position());
            if (away.lengthSqr() < 1.0e-4) away = new Vec3(1, 0, 0);
            dest = entity.position().add(away.normalize().scale(8.0));
        }
        entity.getNavigation().moveTo(dest.x, dest.y, dest.z, 1.45);
    }

    /** Limits path recalculation to ~twice a second so combat doesn't repath every tick. */
    private boolean shouldRepath() {
        if (repathCooldown > 0 && !entity.getNavigation().isDone()) { repathCooldown--; return false; }
        repathCooldown = 10;
        return true;
    }

    @Override
    public void stop() {
        target = null;
        attackCooldown = 0;
        repathCooldown = 0;
        wasFleeing = false;
        calledForHelp = false;
        entity.getNavigation().stop();
    }

    /** Finds the most pressing threat: whatever is hurting us or our owner, else the nearest hostile. */
    private LivingEntity findThreat() {
        // 1. Something is actively hitting us.
        if (entity.getLastHurtByMob() instanceof Monster m && m.isAlive()
                && entity.distanceToSqr(m) < 256) {
            return m;
        }
        // 2. A mob is attacking our owner.
        Player owner = entity.getOwnerPlayer();
        if (owner != null && owner.getLastHurtByMob() instanceof Monster m && m.isAlive()) {
            return m;
        }
        // 3. Nearest hostile within guard radius.
        double radius = ModConfig.get().guardRadius;
        AABB box = AABB.ofSize(entity.position(), radius * 2, 10, radius * 2);
        List<Monster> hostiles = entity.level().getEntitiesOfClass(Monster.class, box, Monster::isAlive);
        Monster nearest = null;
        double nearestSqr = Double.MAX_VALUE;
        for (Monster m : hostiles) {
            double d = entity.distanceToSqr(m);
            if (d < nearestSqr) { nearestSqr = d; nearest = m; }
        }
        return nearest;
    }
}
