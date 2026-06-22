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

    @Override
    public void tick() {
        if (owner == null) return;
        entity.getLookControl().setLookAt(owner, 30f, 30f);

        if (entity.distanceToSqr(owner) > maxDist * maxDist) {
            entity.setPos(owner.getX(), owner.getY(), owner.getZ());
        } else if (repathCooldown > 0 && !entity.getNavigation().isDone()) {
            repathCooldown--;   // keep walking the current path; don't repath every tick
        } else {
            entity.getNavigation().moveTo(owner, speed);
            repathCooldown = 10;
        }
    }

    @Override
    public void stop() {
        repathCooldown = 0;
        entity.getNavigation().stop();
    }
}
