package com.milkdromeda.blockpal.entity.goal;

import com.milkdromeda.blockpal.ai.ActionStep;
import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;

public class ExecuteTaskGoal extends Goal {
    private final AiAssistantEntity entity;
    private ActionStep currentStep;
    private int stepTimer = 0;
    private int waitRemaining = 0;
    /** Block queue for MINE_AREA — breaks one block per tick instead of all at once. */
    private final Queue<BlockPos> mineQueue = new ArrayDeque<>();
    private int pathCooldown = 0;

    public ExecuteTaskGoal(AiAssistantEntity entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        return entity.getMode() == AiAssistantEntity.Mode.EXECUTING
                && entity.getTaskManager().hasPlan();
    }

    @Override
    public boolean canContinueToUse() {
        return entity.getMode() == AiAssistantEntity.Mode.EXECUTING
                && (entity.getTaskManager().hasPlan() || currentStep != null);
    }

    @Override
    public void tick() {
        entity.getTaskManager().tick();

        if (waitRemaining > 0) { waitRemaining--; return; }

        if (currentStep == null) {
            currentStep = entity.getTaskManager().pollNextStep();
            stepTimer = 0;
            pathCooldown = 0;   // let the new step issue its path immediately
            if (currentStep == null) { entity.finishTask(); return; }
        }

        boolean done = executeStep(currentStep);
        stepTimer++;

        if (done || stepTimer > 200) {
            currentStep = null;
            mineQueue.clear();
            waitRemaining = com.milkdromeda.blockpal.config.ModConfig.get().actionTickDelay;
        }
    }

    @Override
    public void stop() { currentStep = null; stepTimer = 0; pathCooldown = 0; mineQueue.clear(); }

    /**
     * Issues a navigation path only periodically (or once the current one ends)
     * rather than every tick. Pathfinding is one of the most expensive things an
     * entity does, so recomputing it every tick during long moves/digs is a big
     * source of lag; the entity keeps walking the existing path in between.
     */
    private void navTo(double x, double y, double z, double speed) {
        if (pathCooldown > 0 && !entity.getNavigation().isDone()) { pathCooldown--; return; }
        pathCooldown = 10;
        entity.getNavigation().moveTo(x, y, z, speed);
    }

    private void navTo(net.minecraft.world.entity.Entity target, double speed) {
        if (pathCooldown > 0 && !entity.getNavigation().isDone()) { pathCooldown--; return; }
        pathCooldown = 10;
        entity.getNavigation().moveTo(target, speed);
    }

    private boolean executeStep(ActionStep step) {
        return switch (step.type()) {
            case MOVE_TO        -> execMoveTo(step);
            case PLACE_BLOCK    -> execPlaceBlock(step);
            case BREAK_BLOCK    -> execBreakBlock(step);
            case MINE_AREA      -> execMineArea(step);
            case USE_BLOCK      -> execUseBlock(step);
            case RUN_COMMAND    -> execRunCommand(step);
            case JUMP           -> execJump(step);
            case SET_SNEAK      -> execSetSneak(step);
            case ATTACK_NEAREST -> execAttackNearest(step);
            case FOLLOW_PLAYER  -> execFollowPlayer(step);
            case LOOK_AT        -> execLookAt(step);
            case CHAT           -> execChat(step);
            case WAIT           -> execWait(step);
            case COLLECT_ITEM   -> execCollectItem(step);
            case STOP           -> { entity.setMode(AiAssistantEntity.Mode.IDLE); yield true; }
        };
    }

    private boolean execMoveTo(ActionStep step) {
        double x = step.getDouble("x", entity.getX()), y = step.getDouble("y", entity.getY()),
               z = step.getDouble("z", entity.getZ());
        if (entity.distanceToSqr(x, y, z) < 4) return true;
        entity.getNavigation().moveTo(x, y, z, 1.0);
        entity.getLookControl().setLookAt(x, y, z, 30f, 30f);
        return false;
    }

    private boolean execPlaceBlock(ActionStep step) {
        int x = step.getInt("x", (int) entity.getX()),
            y = step.getInt("y", (int) entity.getY()),
            z = step.getInt("z", (int) entity.getZ());
        String blockId = step.getString("block", "minecraft:stone");
        BlockPos pos = new BlockPos(x, y, z);

        if (entity.distanceToSqr(Vec3.atCenterOf(pos)) > 25) {
            navTo(x, y, z, 1.0);
            return false;
        }
        Level level = entity.level();
        if (!level.isClientSide() && level.getBlockState(pos).canBeReplaced()) {
            Identifier id = Identifier.tryParse(blockId);
            if (id != null) {
                BuiltInRegistries.BLOCK.get(id).ifPresent(holder -> {
                    level.setBlock(pos, holder.value().defaultBlockState(), Block.UPDATE_ALL);
                    entity.swing(InteractionHand.MAIN_HAND);
                });
            }
        }
        return true;
    }

    private boolean execBreakBlock(ActionStep step) {
        int x = step.getInt("x", (int) entity.getX()),
            y = step.getInt("y", (int) entity.getY()),
            z = step.getInt("z", (int) entity.getZ());
        BlockPos pos = new BlockPos(x, y, z);

        if (entity.distanceToSqr(Vec3.atCenterOf(pos)) > 25) {
            navTo(x, y, z, 1.0);
            return false;
        }
        Level level = entity.level();
        if (!level.isClientSide()) {
            level.destroyBlock(pos, true, entity);
            entity.swing(InteractionHand.MAIN_HAND);
        }
        return true;
    }

    /** Clears every block in a small box (capped so it can't lag the server). */
    private boolean execMineArea(ActionStep step) {
        // On the first call for this step, build the block queue.
        if (mineQueue.isEmpty()) {
            int x1 = step.getInt("x1", (int) entity.getX()),
                y1 = step.getInt("y1", (int) entity.getY()),
                z1 = step.getInt("z1", (int) entity.getZ());
            int x2 = step.getInt("x2", x1), y2 = step.getInt("y2", y1), z2 = step.getInt("z2", z1);
            int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
            int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
            int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
            // Hard cap so a bad LLM plan can't queue thousands of blocks.
            maxX = Math.min(maxX, minX + 5);
            maxY = Math.min(maxY, minY + 5);
            maxZ = Math.min(maxZ, minZ + 5);
            Level level = entity.level();
            for (int x = minX; x <= maxX; x++)
                for (int y = minY; y <= maxY; y++)
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos p = new BlockPos(x, y, z);
                        if (!level.getBlockState(p).isAir()) mineQueue.add(p);
                    }
            if (mineQueue.isEmpty()) return true;
        }

        // Walk to the area before starting if far away.
        BlockPos next = mineQueue.peek();
        Vec3 center = Vec3.atCenterOf(next);
        if (entity.distanceToSqr(center) > 64) {
            navTo(center.x, center.y, center.z, 1.0);
            return false;
        }

        // Break one block per tick — player-like and server-safe.
        Level level = entity.level();
        if (!level.isClientSide()) {
            BlockPos p = mineQueue.poll();
            if (p != null && !level.getBlockState(p).isAir()) {
                level.destroyBlock(p, true, entity);
                entity.swing(InteractionHand.MAIN_HAND);
            }
        }
        return mineQueue.isEmpty();
    }

    /** Activates a lever / button / door / trapdoor / fence gate — the heart of escape-room puzzles. */
    private boolean execUseBlock(ActionStep step) {
        int x = step.getInt("x", (int) entity.getX()),
            y = step.getInt("y", (int) entity.getY()),
            z = step.getInt("z", (int) entity.getZ());
        BlockPos pos = new BlockPos(x, y, z);

        if (entity.distanceToSqr(Vec3.atCenterOf(pos)) > 25) {
            navTo(x, y, z, 1.0);
            return false;
        }
        entity.getLookControl().setLookAt(x, y, z, 30f, 30f);

        if (!(entity.level() instanceof ServerLevel sl)) return true;
        try {
            BlockState state = sl.getBlockState(pos);
            Block block = state.getBlock();
            if (block instanceof LeverBlock && state.hasProperty(BlockStateProperties.POWERED)) {
                sl.setBlock(pos, state.cycle(BlockStateProperties.POWERED), Block.UPDATE_ALL);
            } else if (block instanceof ButtonBlock && state.hasProperty(BlockStateProperties.POWERED)) {
                sl.setBlock(pos, state.setValue(BlockStateProperties.POWERED, true), Block.UPDATE_ALL);
                sl.scheduleTick(pos, block, 20); // auto-release like a real button press
            } else if (block instanceof DoorBlock && state.hasProperty(BlockStateProperties.OPEN)) {
                boolean open = !state.getValue(BlockStateProperties.OPEN);
                sl.setBlock(pos, state.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
                BlockPos other = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                        ? pos.above() : pos.below();
                BlockState os = sl.getBlockState(other);
                if (os.getBlock() instanceof DoorBlock && os.hasProperty(BlockStateProperties.OPEN)) {
                    sl.setBlock(other, os.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
                }
            } else if (state.hasProperty(BlockStateProperties.OPEN)) {
                // trapdoors, fence gates, etc.
                sl.setBlock(pos, state.cycle(BlockStateProperties.OPEN), Block.UPDATE_ALL);
            }
            entity.swing(InteractionHand.MAIN_HAND);
        } catch (Exception ignored) {
            // Never let a quirky block break the whole plan.
        }
        return true;
    }

    /** Commands not allowed even at the configured permission level. */
    private static final Set<String> DENIED_COMMANDS = Set.of(
            "stop", "save-off", "save-all", "op", "deop", "ban", "ban-ip",
            "pardon", "pardon-ip", "kick", "whitelist", "reload", "datapack",
            "debug", "perf", "jfr", "setidletimeout", "publish");

    /** Runs a Minecraft command as the assistant — its key to "doing almost anything". */
    private boolean execRunCommand(ActionStep step) {
        String command = step.getString("command", "").trim();
        if (command.startsWith("/")) command = command.substring(1);
        if (command.isEmpty()) return true;

        ModConfig cfg = ModConfig.get();
        if (!cfg.allowCommands) {
            entity.broadcastMessage("I can't run commands right now — you can enable that in /ai menu.");
            return true;
        }
        if (isDeniedCommand(command)) {
            entity.broadcastMessage("Hmm, I'm not going to run that one.");
            return true;
        }
        if (!(entity.level() instanceof ServerLevel sl)) return true;
        MinecraftServer server = sl.getServer();
        if (server == null) return true;
        try {
            // Level 0 = no privileged commands; 1+ = full access. The denylist above
            // is the real guard, since this version uses capability-based permissions.
            PermissionSet perms = cfg.commandPermissionLevel <= 0
                    ? PermissionSet.NO_PERMISSIONS
                    : PermissionSet.ALL_PERMISSIONS;
            CommandSourceStack source = entity.createCommandSourceStackForNameResolution(sl)
                    .withPermission(perms)
                    .withSuppressedOutput();
            server.getCommands().performPrefixedCommand(source, command);
        } catch (Exception e) {
            entity.broadcastMessage("Hmm, that command didn't work.");
        }
        return true;
    }

    private boolean isDeniedCommand(String command) {
        String first = command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (first.startsWith("minecraft:")) first = first.substring("minecraft:".length());
        return DENIED_COMMANDS.contains(first);
    }

    private boolean execJump(ActionStep step) {
        if (entity.onGround()) entity.doJump();
        return true;
    }

    private boolean execSetSneak(ActionStep step) {
        entity.setShiftKeyDown(step.getBool("value", true));
        return true;
    }

    private boolean execAttackNearest(ActionStep step) {
        double range = step.getDouble("range", 16.0);
        AABB box = AABB.ofSize(entity.position(), range * 2, 10, range * 2);
        List<Monster> hostiles = entity.level().getEntitiesOfClass(Monster.class, box, LivingEntity::isAlive);
        if (hostiles.isEmpty()) return true;

        LivingEntity target = hostiles.stream()
                .min((a, b) -> Double.compare(entity.distanceToSqr(a), entity.distanceToSqr(b)))
                .orElse(null);
        if (target == null) return true;

        entity.getLookControl().setLookAt(target, 30f, 30f);
        navTo(target, 1.2);

        if (entity.distanceToSqr(target) < 9 && entity.level() instanceof ServerLevel sl) {
            entity.swing(InteractionHand.MAIN_HAND);
            entity.doHurtTarget(sl, target);
            return !target.isAlive();
        }
        return false;
    }

    private boolean execFollowPlayer(ActionStep step) {
        String name = step.getString("name", "");
        double dist = step.getDouble("distance", 3.0);

        Player player = null;
        if (!name.isBlank() && entity.level() instanceof ServerLevel sl) {
            player = sl.getServer().getPlayerList().getPlayerByName(name);
        }
        if (player == null) player = entity.getOwnerPlayer();
        if (player == null) return true;

        if (entity.distanceToSqr(player) > dist * dist) {
            navTo(player, 1.0);
            entity.getLookControl().setLookAt(player, 30f, 30f);
            return false;
        }
        return true;
    }

    private boolean execLookAt(ActionStep step) {
        entity.getLookControl().setLookAt(
                step.getDouble("x", entity.getX()),
                step.getDouble("y", entity.getY()),
                step.getDouble("z", entity.getZ()), 30f, 30f);
        return true;
    }

    private boolean execChat(ActionStep step) {
        String msg = step.getString("message", "...");
        if (!entity.level().isClientSide()) {
            entity.level().players().forEach(p ->
                    p.sendSystemMessage(Component.literal(entity.getAssistantName() + ": \"" + msg + "\"")));
        }
        return true;
    }

    private boolean execWait(ActionStep step) {
        // Set wait and skip the normal inter-step delay (return false keeps currentStep set)
        waitRemaining = step.getInt("ticks", 20);
        currentStep = null; // consume step now so we don't re-enter
        return true;
    }

    private boolean execCollectItem(ActionStep step) {
        double x = step.getDouble("x", entity.getX()),
               y = step.getDouble("y", entity.getY()),
               z = step.getDouble("z", entity.getZ());
        if (entity.distanceToSqr(x, y, z) > 4) {
            navTo(x, y, z, 1.0);
            return false;
        }
        return true;
    }
}
