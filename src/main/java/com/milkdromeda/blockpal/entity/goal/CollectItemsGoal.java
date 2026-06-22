package com.milkdromeda.blockpal.entity.goal;

import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;

/**
 * Walks the assistant over to nearby dropped items it wants — loot from a fight,
 * a sword on the ground, mined ores, anything it has room for. The actual
 * transfer into the inventory/equipment happens in {@link AiAssistantEntity}'s
 * pickup hook once it's close enough.
 *
 * <p>Runs only while idle, following or guarding, so it never abandons combat or
 * an active build/task (those goals sit at a higher priority and pre-empt it).
 */
public class CollectItemsGoal extends Goal {

    private static final double SEARCH_RADIUS = 10.0;
    private static final int SCAN_INTERVAL = 30;    // ticks between searches for loot
    private static final int REPATH_INTERVAL = 10;  // ticks between path recalculations
    private static final int MAX_PURSUIT = 100;     // give up if it can't reach in ~5s

    private final AiAssistantEntity entity;
    private ItemEntity target;
    private int scanCooldown;
    private int repathCooldown;
    private int pursuitTicks;

    public CollectItemsGoal(AiAssistantEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean modeAllows() {
        return switch (entity.getMode()) {
            case IDLE, FOLLOWING, GUARDING -> true;
            default -> false;
        };
    }

    @Override
    public boolean canUse() {
        if (!modeAllows()) return false;
        if (scanCooldown > 0) { scanCooldown--; return false; }
        scanCooldown = SCAN_INTERVAL;
        target = findItem();
        if (target == null) return false;
        repathCooldown = 0;          // path on the very first tick
        pursuitTicks = MAX_PURSUIT;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return modeAllows() && pursuitTicks > 0
                && target != null && target.isAlive() && !target.getItem().isEmpty();
    }

    @Override
    public void tick() {
        if (target == null) return;
        pursuitTicks--;
        entity.getLookControl().setLookAt(target, 30f, 30f);
        // Re-path only periodically (or when the current path finishes) rather than
        // every tick — recomputing a path every tick is a major performance cost.
        if (repathCooldown > 0 && !entity.getNavigation().isDone()) {
            repathCooldown--;
        } else {
            entity.getNavigation().moveTo(target, 1.1);
            repathCooldown = REPATH_INTERVAL;
        }
    }

    @Override
    public void stop() {
        target = null;
        pursuitTicks = 0;
        entity.getNavigation().stop();
    }

    private ItemEntity findItem() {
        AABB box = AABB.ofSize(entity.position(), SEARCH_RADIUS * 2, 6, SEARCH_RADIUS * 2);
        List<ItemEntity> items = entity.level().getEntitiesOfClass(ItemEntity.class, box,
                it -> it.isAlive() && !it.getItem().isEmpty() && entity.canTake(it.getItem()));
        ItemEntity nearest = null;
        double nearestSqr = Double.MAX_VALUE;
        for (ItemEntity it : items) {
            double d = entity.distanceToSqr(it);
            if (d < nearestSqr) { nearestSqr = d; nearest = it; }
        }
        return nearest;
    }
}
