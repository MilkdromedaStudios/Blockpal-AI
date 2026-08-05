package com.milkdromeda.blockpal.entity.goal;

import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

public class FollowOwnerGoal extends Goal {
    private final AiAssistantEntity entity;
    private Player owner;
    private final double speed;
    private final double minDist;
    private final double maxDist;
    private int repathCooldown = 0;

    public FollowOwnerGoal(AiAssistantEntity entity, double speed, double minDist, double maxDist) {
        this.entity = entity;
        this.speed = speed;
        this.minDist = minDist;
        this.maxDist = maxDist;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (entity.getMode() != AiAssistantEntity.Mode.FOLLOWING) return false;
        owner = entity.getOwnerPlayer();
        return owner != null && entity.distanceToSqr(owner) > minDist * minDist;
    }

    @Override
    public boolean canContinueToUse() {
        return entity.getMode() == AiAssistantEntity.Mode.FOLLOWING
                && owner != null
                && entity.distanceToSqr(owner) > minDist * minDist;
    }

    /**
     * Walks after the owner — <b>and only walks</b>.
     *
     * <p>This goal used to blink the companion to the owner's feet once it fell more than
     * {@code maxDist} behind. That is the single most immersion-breaking thing a
     * companion can do: it means the bot isn't really in the world with you, it's a
     * marker that follows your camera. Since 3.25.0 there is no teleport anywhere in the
     * mod's follow path — if it falls behind, it runs, and if it truly can't get there it
     * says so. A creative-mode owner will out-fly it, which is exactly why players get a
     * warning when they switch to creative with a companion out.
     */
    @Override
    public void tick() {
        if (owner == null) return;
        entity.getLookControl().setLookAt(owner, 30f, 30f);

        boolean farBehind = entity.distanceToSqr(owner) > maxDist * maxDist;
        if (repathCooldown > 0 && !entity.getNavigation().isDone()) {
            repathCooldown--;   // keep walking the current path; don't repath every tick
            return;
        }
        // Sprint-equivalent when it has ground to make up, otherwise a normal walk.
        entity.getNavigation().moveTo(owner, farBehind ? Math.min(speed * 1.3, 1.5) : speed);
        repathCooldown = farBehind ? 5 : 10;
    }

    @Override
    public void stop() {
        repathCooldown = 0;
        entity.getNavigation().stop();
    }
}
