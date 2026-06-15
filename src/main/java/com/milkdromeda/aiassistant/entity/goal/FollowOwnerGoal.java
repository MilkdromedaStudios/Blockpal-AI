package com.milkdromeda.aiassistant.entity.goal;

import com.milkdromeda.aiassistant.entity.AiAssistantEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;

import java.util.EnumSet;

public class FollowOwnerGoal extends Goal {
    private final AiAssistantEntity entity;
    private PlayerEntity owner;
    private final double speed;
    private final double minDist;
    private final double maxDist;

    public FollowOwnerGoal(AiAssistantEntity entity, double speed, double minDist, double maxDist) {
        this.entity = entity;
        this.speed = speed;
        this.minDist = minDist;
        this.maxDist = maxDist;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (entity.getMode() != AiAssistantEntity.Mode.FOLLOWING) return false;
        owner = entity.getOwnerPlayer();
        return owner != null && entity.squaredDistanceTo(owner) > minDist * minDist;
    }

    @Override
    public boolean shouldContinue() {
        return entity.getMode() == AiAssistantEntity.Mode.FOLLOWING
                && owner != null
                && entity.squaredDistanceTo(owner) > minDist * minDist;
    }

    @Override
    public void tick() {
        if (owner == null) return;
        double distSq = entity.squaredDistanceTo(owner);

        if (distSq > maxDist * maxDist) {
            // Teleport if too far — use requestTeleport for safe server-side movement
            entity.teleport(owner.getX(), owner.getY(), owner.getZ(), true);
        } else {
            entity.getLookControl().lookAt(owner, 30f, 30f);
            entity.getNavigation().startMovingTo(owner, speed);
        }
    }

    @Override
    public void stop() {
        entity.getNavigation().stop();
    }
}
