package com.milkdromeda.aiassistant.entity.goal;

import com.milkdromeda.aiassistant.entity.AiAssistantEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;

import java.util.EnumSet;
import java.util.List;

public class CombatAssistGoal extends Goal {
    private final AiAssistantEntity entity;
    private LivingEntity target;
    private int attackCooldown = 0;

    public CombatAssistGoal(AiAssistantEntity entity) {
        this.entity = entity;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.TARGET));
    }

    @Override
    public boolean canStart() {
        if (entity.getMode() != AiAssistantEntity.Mode.GUARDING
                && entity.getMode() != AiAssistantEntity.Mode.FOLLOWING) return false;
        target = findNearestHostile();
        return target != null;
    }

    @Override
    public boolean shouldContinue() {
        return target != null && target.isAlive()
                && entity.squaredDistanceTo(target) < 256
                && (entity.getMode() == AiAssistantEntity.Mode.GUARDING
                    || entity.getMode() == AiAssistantEntity.Mode.FOLLOWING
                    || entity.getMode() == AiAssistantEntity.Mode.FIGHTING);
    }

    @Override
    public void start() {
        entity.setMode(AiAssistantEntity.Mode.FIGHTING);
        entity.broadcastMessage("Engaging hostiles!");
    }

    @Override
    public void tick() {
        if (target == null || !target.isAlive()) {
            target = findNearestHostile();
            if (target == null) return;
        }

        entity.getLookControl().lookAt(target, 30f, 30f);
        entity.getNavigation().startMovingTo(target, 1.2);

        if (attackCooldown <= 0 && entity.squaredDistanceTo(target) < 9) {
            entity.swingHand(Hand.MAIN_HAND);
            if (entity.getWorld() instanceof ServerWorld sw) {
                entity.tryAttack(sw, target);
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
        double guardRadius = com.milkdromeda.aiassistant.config.ModConfig.get().guardRadius;
        Box searchBox = Box.of(entity.getPos(), guardRadius * 2, 10, guardRadius * 2);

        List<HostileEntity> hostiles = entity.getWorld()
                .getEntitiesByClass(HostileEntity.class, searchBox, e -> e.isAlive());

        PlayerEntity owner = entity.getOwnerPlayer();
        if (owner != null) {
            LivingEntity ownerTarget = owner.getAttacking();
            if (ownerTarget instanceof HostileEntity h && h.isAlive()) return h;
        }

        return hostiles.stream()
                .min((a, b) -> Double.compare(entity.squaredDistanceTo(a), entity.squaredDistanceTo(b)))
                .orElse(null);
    }
}
