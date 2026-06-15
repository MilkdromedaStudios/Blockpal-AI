package com.milkdromeda.aiassistant.entity.goal;

import com.milkdromeda.aiassistant.ai.ActionStep;
import com.milkdromeda.aiassistant.entity.AiAssistantEntity;
import net.minecraft.block.Block;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.List;

public class ExecuteTaskGoal extends Goal {
    private final AiAssistantEntity entity;
    private ActionStep currentStep;
    private int stepTimer = 0;
    private int waitRemaining = 0;

    public ExecuteTaskGoal(AiAssistantEntity entity) {
        this.entity = entity;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.TARGET));
    }

    @Override
    public boolean canStart() {
        return entity.getMode() == AiAssistantEntity.Mode.EXECUTING
                && entity.getTaskManager().hasPlan();
    }

    @Override
    public boolean shouldContinue() {
        return entity.getMode() == AiAssistantEntity.Mode.EXECUTING
                && (entity.getTaskManager().hasPlan() || currentStep != null);
    }

    @Override
    public void tick() {
        entity.getTaskManager().tick();

        if (waitRemaining > 0) {
            waitRemaining--;
            return;
        }

        if (currentStep == null) {
            currentStep = entity.getTaskManager().pollNextStep();
            stepTimer = 0;
            if (currentStep == null) {
                entity.finishTask();
                return;
            }
        }

        boolean done = executeStep(currentStep);
        stepTimer++;

        if (done || stepTimer > 200) {
            currentStep = null;
            waitRemaining = com.milkdromeda.aiassistant.config.ModConfig.get().actionTickDelay;
        }
    }

    @Override
    public void stop() {
        currentStep = null;
        stepTimer = 0;
    }

    private boolean executeStep(ActionStep step) {
        return switch (step.type()) {
            case MOVE_TO        -> executeMoveTo(step);
            case PLACE_BLOCK    -> executePlaceBlock(step);
            case BREAK_BLOCK    -> executeBreakBlock(step);
            case ATTACK_NEAREST -> executeAttackNearest(step);
            case FOLLOW_PLAYER  -> executeFollowPlayer(step);
            case LOOK_AT        -> executeLookAt(step);
            case CHAT           -> executeChat(step);
            case WAIT           -> executeWait(step);
            case COLLECT_ITEM   -> executeCollectItem(step);
            case STOP           -> { entity.setMode(AiAssistantEntity.Mode.IDLE); yield true; }
        };
    }

    private boolean executeMoveTo(ActionStep step) {
        double x = step.getDouble("x", entity.getX());
        double y = step.getDouble("y", entity.getY());
        double z = step.getDouble("z", entity.getZ());

        if (entity.squaredDistanceTo(x, y, z) < 4) return true;
        entity.getNavigation().startMovingTo(x, y, z, 1.0);
        entity.getLookControl().lookAt(x, y, z, 30f, 30f);
        return false;
    }

    private boolean executePlaceBlock(ActionStep step) {
        int x = step.getInt("x", (int) entity.getX());
        int y = step.getInt("y", (int) entity.getY());
        int z = step.getInt("z", (int) entity.getZ());
        String blockId = step.getString("block", "minecraft:stone");
        BlockPos pos = new BlockPos(x, y, z);

        if (entity.squaredDistanceTo(Vec3d.ofCenter(pos)) > 25) {
            entity.getNavigation().startMovingTo(x, y, z, 1.0);
            return false;
        }

        World world = entity.getWorld();
        if (!world.isClient && world.getBlockState(pos).isReplaceable()) {
            Identifier id = Identifier.tryParse(blockId);
            if (id != null) {
                world.setBlockState(pos, Registries.BLOCK.get(id).getDefaultState(), Block.NOTIFY_ALL);
                entity.swingHand(Hand.MAIN_HAND);
            }
        }
        return true;
    }

    private boolean executeBreakBlock(ActionStep step) {
        int x = step.getInt("x", (int) entity.getX());
        int y = step.getInt("y", (int) entity.getY());
        int z = step.getInt("z", (int) entity.getZ());
        BlockPos pos = new BlockPos(x, y, z);

        if (entity.squaredDistanceTo(Vec3d.ofCenter(pos)) > 25) {
            entity.getNavigation().startMovingTo(x, y, z, 1.0);
            return false;
        }

        World world = entity.getWorld();
        if (!world.isClient) {
            world.breakBlock(pos, true, entity);
            entity.swingHand(Hand.MAIN_HAND);
        }
        return true;
    }

    private boolean executeAttackNearest(ActionStep step) {
        double range = step.getDouble("range", 16.0);
        Box box = Box.of(entity.getPos(), range * 2, 10, range * 2);
        List<HostileEntity> hostiles = entity.getWorld()
                .getEntitiesByClass(HostileEntity.class, box, LivingEntity::isAlive);

        if (hostiles.isEmpty()) return true;

        LivingEntity target = hostiles.stream()
                .min((a, b) -> Double.compare(entity.squaredDistanceTo(a), entity.squaredDistanceTo(b)))
                .orElse(null);
        if (target == null) return true;

        entity.getLookControl().lookAt(target, 30f, 30f);
        entity.getNavigation().startMovingTo(target, 1.2);

        if (entity.squaredDistanceTo(target) < 9) {
            entity.swingHand(Hand.MAIN_HAND);
            if (entity.getWorld() instanceof ServerWorld sw) {
                entity.tryAttack(sw, target);
            }
            return !target.isAlive();
        }
        return false;
    }

    private boolean executeFollowPlayer(ActionStep step) {
        String name = step.getString("name", "");
        double dist = step.getDouble("distance", 3.0);

        PlayerEntity player = null;
        if (!name.isBlank() && entity.getWorld() instanceof ServerWorld sw) {
            player = sw.getServer().getPlayerManager().getPlayer(name);
        }
        if (player == null) player = entity.getOwnerPlayer();
        if (player == null) return true;

        if (entity.squaredDistanceTo(player) > dist * dist) {
            entity.getNavigation().startMovingTo(player, 1.0);
            entity.getLookControl().lookAt(player, 30f, 30f);
            return false;
        }
        return true;
    }

    private boolean executeLookAt(ActionStep step) {
        entity.getLookControl().lookAt(
                step.getDouble("x", entity.getX()),
                step.getDouble("y", entity.getY()),
                step.getDouble("z", entity.getZ()),
                30f, 30f);
        return true;
    }

    private boolean executeChat(ActionStep step) {
        String msg = step.getString("message", "...");
        if (!entity.getWorld().isClient) {
            entity.getWorld().getPlayers().forEach(p ->
                    p.sendMessage(Text.literal("[" + entity.getAssistantName() + "] " + msg), false));
        }
        return true;
    }

    private boolean executeWait(ActionStep step) {
        if (waitRemaining == 0) waitRemaining = step.getInt("ticks", 20);
        return true;
    }

    private boolean executeCollectItem(ActionStep step) {
        double x = step.getDouble("x", entity.getX());
        double y = step.getDouble("y", entity.getY());
        double z = step.getDouble("z", entity.getZ());

        if (entity.squaredDistanceTo(x, y, z) > 4) {
            entity.getNavigation().startMovingTo(x, y, z, 1.0);
            return false;
        }
        return true;
    }
}
