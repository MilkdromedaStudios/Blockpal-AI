package com.milkdromeda.blockpal.entity.goal;

import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.LinkedList;
import java.util.Queue;

public class BuildGoal extends Goal {
    private final AiAssistantEntity entity;
    private final Queue<BuildTask> tasks = new LinkedList<>();
    private BuildTask current;
    private int waitTicks = 0;

    public record BuildTask(BlockPos pos, String blockId) {}

    public BuildGoal(AiAssistantEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public void queueBlock(int x, int y, int z, String blockId) {
        tasks.add(new BuildTask(new BlockPos(x, y, z), blockId));
    }

    public boolean hasTasks() { return !tasks.isEmpty() || current != null; }

    public void clearTasks() { tasks.clear(); current = null; }

    @Override
    public boolean canUse() {
        return entity.getMode() == AiAssistantEntity.Mode.BUILDING && hasTasks();
    }

    @Override
    public boolean canContinueToUse() {
        return entity.getMode() == AiAssistantEntity.Mode.BUILDING && hasTasks();
    }

    @Override
    public void tick() {
        if (waitTicks > 0) { waitTicks--; return; }

        if (current == null) {
            current = tasks.poll();
            if (current == null) return;
        }

        Vec3 center = Vec3.atCenterOf(current.pos());
        if (entity.distanceToSqr(center) > 16) {
            entity.getNavigation().moveTo(center.x, center.y, center.z, 1.0);
            return;
        }

        entity.getLookControl().setLookAt(center.x, center.y, center.z, 30f, 30f);
        placeBlock(current.pos(), current.blockId());
        entity.swing(InteractionHand.MAIN_HAND);
        current = null;
        waitTicks = com.milkdromeda.blockpal.config.ModConfig.get().actionTickDelay / 2;
    }

    private void placeBlock(BlockPos pos, String blockId) {
        Level level = entity.level();
        if (level.isClientSide()) return;
        Identifier id = Identifier.tryParse(blockId);
        if (id == null) return;
        Block block = BuiltInRegistries.BLOCK.get(id).orElseThrow().value();
        if (level.getBlockState(pos).canBeReplaced()) {
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
