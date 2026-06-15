package com.milkdromeda.aiassistant.entity.goal;

import com.milkdromeda.aiassistant.config.ModConfig;
import com.milkdromeda.aiassistant.entity.AiAssistantEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;

public class CombatAssistGoal extends Goal {
    private final AiAssistantEntity entity;
    private LivingEntity target;
    private int attackCooldown = 0;

    public CombatAssistGoal(AiAssistantEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (entity.getMode() != AiAssistantEntity.Mode.GUARDING
                && entity.getMode() != AiAssistantEntity.Mode.FOLLOWING) return false;
        target = findNearestHostile();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && target.isAlive()
                && entity.distanceToSqr(target) < 256
                && (entity.getMode() == AiAssistantEntity.Mode.GUARDING
                    || entity.getMode() == AiAssistantEntity.Mode.FOLLOWING
                    || entity.getMode() == AiAssistantEntity.Mode.FIGHTING);
    }

    @Override
    public void start() {
        entity.setMode(AiAssistantEntity.Mode.FIGHTING);
        entity.say("⚔ Engaging hostiles!", AiAssistantEntity.COL_COMBAT);
    }

    @Override
    public void tick() {
        if (target == null || !target.isAlive()) {
            target = findNearestHostile();
            if (target == null) return;
        }

        entity.getLookControl().setLookAt(target, 30f, 30f);
        entity.getNavigation().moveTo(target, 1.2);

        if (attackCooldown <= 0 && entity.distanceToSqr(target) < 9) {
            entity.swing(InteractionHand.MAIN_HAND);
            if (entity.level() instanceof ServerLevel sl) {
                entity.doHurtTarget(sl, target);
            }
            attackCooldown = 20;
        } else {
            attackCooldown--;
        }
    }

    @Override
    public void stop() {
        target = null;
        attackCooldown = 0;
        if (entity.getMode() == AiAssistantEntity.Mode.FIGHTING) {
            entity.setMode(AiAssistantEntity.Mode.FOLLOWING);
        }
    }

    private LivingEntity findNearestHostile() {
        double radius = ModConfig.get().guardRadius;
        AABB box = AABB.ofSize(entity.position(), radius * 2, 10, radius * 2);
        List<Monster> hostiles = entity.level().getEntitiesOfClass(Monster.class, box,
                e -> e.isAlive() && (net.minecraft.world.entity.Entity)e != entity);

        Player owner = entity.getOwnerPlayer();
        if (owner != null && owner.getLastHurtByMob() instanceof Monster m && m.isAlive()) {
            return m;
        }

        return hostiles.stream()
                .min((a, b) -> Double.compare(entity.distanceToSqr(a), entity.distanceToSqr(b)))
                .orElse(null);
    }
}
