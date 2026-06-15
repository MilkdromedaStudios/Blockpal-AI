package com.milkdromeda.aiassistant.entity.goal;

import com.milkdromeda.aiassistant.entity.AiAssistantEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.LinkedList;
import java.util.Queue;

public class BuildGoal extends Goal {
    private final AiAssistantEntity entity;
    private final Queue<BuildTask> tasks = new LinkedList<>();
    private BuildTask current;
    private int waitTicks = 0;

    public BuildGoal(AiAssistantEntity entity) {
        this.entity = entity;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    public record BuildTask(BlockPos pos, String blockId) {}

    public void queueBlock(int x, int y, int z, String blockId) {
        tasks.add(new BuildTask(new BlockPos(x, y, z), blockId));
    }

    public boolean hasTasks() {
        return !tasks.isEmpty() || current != null;
    }

    public void clearTasks() {
        tasks.clear();
        current = null;
    }

    @Override
    public boolean canStart() {
        return entity.getMode() == AiAssistantEntity.Mode.BUILDING && hasTasks();
    }

    @Override
    public boolean shouldContinue() {
        return entity.getMode() == AiAssistantEntity.Mode.BUILDING && hasTasks();
    }

    @Override
    public void tick() {
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        if (current == null) {
            current = tasks.poll();
            if (current == null) return;
        }

        Vec3d targetCenter = Vec3d.ofCenter(current.pos());
        double dist = entity.squaredDistanceTo(targetCenter.x, targetCenter.y, targetCenter.z);

        if (dist > 16) {
            entity.getNavigation().startMovingTo(
                    current.pos().getX(), current.pos().getY(), current.pos().getZ(), 1.0);
            return;
        }

        entity.getLookControl().lookAt(targetCenter.x, targetCenter.y, targetCenter.z, 30f, 30f);
        placeBlock(current.pos(), current.blockId());
        entity.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        current = null;
        waitTicks = com.milkdromeda.aiassistant.config.ModConfig.get().actionTickDelay / 2;
    }

    private void placeBlock(BlockPos pos, String blockId) {
        World world = entity.getWorld();
        if (world.isClient) return;

        Identifier id = Identifier.tryParse(blockId);
        if (id == null) return;

        Block block = Registries.BLOCK.get(id);
        BlockState state = block.getDefaultState();

        if (world.getBlockState(pos).isReplaceable()) {
            world.setBlockState(pos, state, Block.NOTIFY_ALL);
        }
    }
}
