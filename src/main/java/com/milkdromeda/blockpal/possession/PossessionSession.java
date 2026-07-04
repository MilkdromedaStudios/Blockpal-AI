package com.milkdromeda.blockpal.possession;

import com.milkdromeda.blockpal.ai.ActionPlan;
import com.milkdromeda.blockpal.ai.ActionStep;
import com.milkdromeda.blockpal.ai.HuggingFaceClient;
import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
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
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * One active possession — the bot ({@code botId}) driving the player
 * ({@code playerId}) from typed instructions. Held and ticked by
 * {@link PossessionManager} on the server thread.
 *
 * <p>The flow mirrors the entity's own task system: an instruction is turned into a
 * JSON {@link ActionPlan} by the language model (off-thread), then the plan's steps
 * are executed one at a time — but against the <b>player's</b> body instead of the
 * bot's. Locomotion pushes the player toward a target with velocity (the same
 * server-authoritative technique the mini-game "chain" uses), so it works with no
 * client mod. Everything runs server-side, so it works on any server with Blockpal.
 */
public class PossessionSession {

    /** Reach distance (squared) for block interactions before we walk closer first. */
    private static final double REACH_SQR = 5.0 * 5.0;
    /** Push the player at roughly walking speed toward a MOVE target, per tick. */
    private static final double WALK_SPEED = 0.16;
    /** Beyond this many blocks from a target, teleport instead of walking. */
    private static final double TELEPORT_DIST = 48.0;
    /** How long (ticks) to spend on a single step before giving up on it. */
    private static final int STEP_TIMEOUT = 200;
    /** Minimum ticks between plan requests (rate-limit, ~3 s). */
    private static final int PLAN_COOLDOWN = 60;

    /** Commands possession will not run even though it acts as the player. */
    private static final Set<String> DENIED_COMMANDS = Set.of(
            "stop", "save-off", "save-all", "op", "deop", "ban", "ban-ip",
            "pardon", "pardon-ip", "kick", "whitelist", "reload", "datapack",
            "debug", "perf", "jfr", "setidletimeout", "publish");

    private final UUID playerId;
    private final int botId;
    private final HuggingFaceClient client;

    private String pendingInstruction;
    private boolean waitingForApi;
    private CompletableFuture<ActionPlan> future;
    private ActionPlan currentPlan;
    private ActionStep currentStep;
    private int stepTimer;
    private int waitRemaining;
    private final Queue<BlockPos> mineQueue = new ArrayDeque<>();
    private int tickCounter;
    private int lastRequestTick = -PLAN_COOLDOWN;

    /** Status lines waiting to be forwarded to the player's console. */
    private final Queue<String> status = new ArrayDeque<>();

    public PossessionSession(UUID playerId, int botId, HuggingFaceClient client) {
        this.playerId = playerId;
        this.botId = botId;
        this.client = client;
    }

    public UUID getPlayerId() { return playerId; }
    public int getBotId() { return botId; }

    /** Queues a new instruction — the latest one wins and preempts the current plan. */
    public void queue(String instruction) {
        this.pendingInstruction = instruction;
        this.currentPlan = null;
        resetStep();
    }

    /** Pops the next status line for the console, or null when there are none. */
    public String drainStatus() {
        return status.poll();
    }

    // ── per-tick ────────────────────────────────────────────────────────────────

    /** Advances planning and drives the player one tick. Runs on the server thread. */
    public void tick(MinecraftServer server, ServerPlayer player, AiAssistantEntity bot) {
        tickCounter++;

        // 1) A requested plan arrived — adopt it.
        if (waitingForApi && future != null && future.isDone()) {
            waitingForApi = false;
            try {
                currentPlan = future.get();
            } catch (Exception e) {
                currentPlan = null;
            }
            future = null;
            resetStep();
            if (currentPlan != null && currentPlan.description != null) {
                status.add("§7Plan: " + currentPlan.description);
            }
        }

        // 2) Start planning a pending instruction (rate-limited, one in flight).
        if (pendingInstruction != null && !waitingForApi
                && tickCounter - lastRequestTick >= PLAN_COOLDOWN) {
            requestPlan(player, bot, pendingInstruction);
            pendingInstruction = null;
        }

        // 3) Execute the current plan against the player.
        if (currentPlan != null) {
            executePlan(player);
        }
    }

    private void requestPlan(ServerPlayer player, AiAssistantEntity bot, String instruction) {
        HuggingFaceClient.ApiAuth auth = HuggingFaceClient.ApiAuth.resolveFor(
                player.getUUID(), player.getName().getString());
        if (!auth.usable()) {
            status.add("§cNo API key available — set one with §f/ai mykey <token>§c.");
            return;
        }
        lastRequestTick = tickCounter;
        waitingForApi = true;
        currentPlan = null;
        resetStep();
        status.add("§7Thinking about: §f" + instruction);
        String style = bot != null ? bot.getPlanStyle() : null;
        future = client.requestPlan(wrapInstruction(instruction), buildContext(player), auth, style);
    }

    /** Frames the instruction so the planner knows it is driving a player's body. */
    private String wrapInstruction(String instruction) {
        return "You are in POSSESSION MODE, remotely controlling a Minecraft player's own body. "
                + "Do exactly what they ask, using MOVE_TO to walk, plus BREAK_BLOCK / PLACE_BLOCK / "
                + "MINE_AREA / USE_BLOCK / ATTACK_NEAREST / COLLECT_ITEM / RUN_COMMAND / CHAT as needed. "
                + "All coordinates are absolute world coordinates from the context. Keep the plan short "
                + "(a few concrete steps). Do not loop. Instruction: " + instruction;
    }

    private void executePlan(ServerPlayer player) {
        if (waitRemaining > 0) { waitRemaining--; return; }

        if (currentStep == null) {
            currentStep = currentPlan.poll();
            stepTimer = 0;
            mineQueue.clear();
            if (currentStep == null) {          // plan exhausted
                currentPlan = null;
                status.add("§aDone — waiting for your next instruction.");
                stopPlayer(player);
                return;
            }
        }

        boolean done = executeStep(player, currentStep);
        stepTimer++;

        if (done || stepTimer > STEP_TIMEOUT) {
            currentStep = null;
            mineQueue.clear();
            waitRemaining = Math.max(1, ModConfig.get().actionTickDelay);
        }
    }

    // ── step execution (against the player) ───────────────────────────────────────

    private boolean executeStep(ServerPlayer p, ActionStep step) {
        return switch (step.type()) {
            case MOVE_TO        -> execMoveTo(p, step);
            case PLACE_BLOCK    -> execPlaceBlock(p, step);
            case BREAK_BLOCK    -> execBreakBlock(p, step);
            case MINE_AREA      -> execMineArea(p, step);
            case USE_BLOCK      -> execUseBlock(p, step);
            case RUN_COMMAND    -> execRunCommand(p, step);
            case JUMP           -> execJump(p);
            case SET_SNEAK      -> { p.setShiftKeyDown(step.getBool("value", true)); yield true; }
            case ATTACK_NEAREST -> execAttackNearest(p, step);
            case FOLLOW_PLAYER  -> execFollowPlayer(p, step);
            case LOOK_AT        -> { faceToward(p, step.getDouble("x", p.getX()),
                                                 step.getDouble("y", p.getEyeY()),
                                                 step.getDouble("z", p.getZ())); yield true; }
            case CHAT           -> execChat(p, step);
            case WAIT           -> { waitRemaining = Math.max(1, step.getInt("ticks", 20));
                                     currentStep = null; yield true; }
            case COLLECT_ITEM   -> execCollectItem(p, step);
            case STOP           -> { currentPlan = null; stopPlayer(p);
                                     status.add("§7Stopped."); yield true; }
        };
    }

    private boolean execMoveTo(ServerPlayer p, ActionStep step) {
        double x = step.getDouble("x", p.getX()), y = step.getDouble("y", p.getY()),
               z = step.getDouble("z", p.getZ());
        if (horizDistSqr(p, x, z) < 1.5 * 1.5) { stopPlayer(p); return true; }
        pushToward(p, x, y, z);
        return false;
    }

    private boolean execPlaceBlock(ServerPlayer p, ActionStep step) {
        int x = step.getInt("x", (int) p.getX()), y = step.getInt("y", (int) p.getY()),
            z = step.getInt("z", (int) p.getZ());
        BlockPos pos = new BlockPos(x, y, z);
        if (blockDistSqr(p, pos) > REACH_SQR) { pushToward(p, x, y, z); return false; }
        String blockId = step.getString("block", "minecraft:stone");
        if (p.level() instanceof ServerLevel level && level.getBlockState(pos).canBeReplaced()) {
            Identifier id = Identifier.tryParse(blockId);
            if (id != null) {
                BuiltInRegistries.BLOCK.get(id).ifPresent(holder -> {
                    level.setBlock(pos, holder.value().defaultBlockState(), Block.UPDATE_ALL);
                    p.swing(InteractionHand.MAIN_HAND);
                });
            }
        }
        return true;
    }

    private boolean execBreakBlock(ServerPlayer p, ActionStep step) {
        int x = step.getInt("x", (int) p.getX()), y = step.getInt("y", (int) p.getY()),
            z = step.getInt("z", (int) p.getZ());
        BlockPos pos = new BlockPos(x, y, z);
        if (blockDistSqr(p, pos) > REACH_SQR) { pushToward(p, x, y, z); return false; }
        if (p.level() instanceof ServerLevel level) {
            level.destroyBlock(pos, true, p);
            p.swing(InteractionHand.MAIN_HAND);
        }
        return true;
    }

    private boolean execMineArea(ServerPlayer p, ActionStep step) {
        if (mineQueue.isEmpty()) {
            int x1 = step.getInt("x1", (int) p.getX()), y1 = step.getInt("y1", (int) p.getY()),
                z1 = step.getInt("z1", (int) p.getZ());
            int x2 = step.getInt("x2", x1), y2 = step.getInt("y2", y1), z2 = step.getInt("z2", z1);
            int minX = Math.min(x1, x2), minY = Math.min(y1, y2), minZ = Math.min(z1, z2);
            // Cap the box so a bad plan can't queue thousands of blocks.
            int maxX = Math.min(Math.max(x1, x2), minX + 5);
            int maxY = Math.min(Math.max(y1, y2), minY + 5);
            int maxZ = Math.min(Math.max(z1, z2), minZ + 5);
            Level level = p.level();
            for (int x = minX; x <= maxX; x++)
                for (int y = minY; y <= maxY; y++)
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos bp = new BlockPos(x, y, z);
                        if (!level.getBlockState(bp).isAir()) mineQueue.add(bp);
                    }
            if (mineQueue.isEmpty()) return true;
        }

        BlockPos next = mineQueue.peek();
        if (next != null && blockDistSqr(p, next) > REACH_SQR * 2) {
            pushToward(p, next.getX() + 0.5, next.getY(), next.getZ() + 0.5);
            return false;
        }
        if (p.level() instanceof ServerLevel level) {
            BlockPos bp = mineQueue.poll();
            if (bp != null && !level.getBlockState(bp).isAir()) {
                level.destroyBlock(bp, true, p);
                p.swing(InteractionHand.MAIN_HAND);
            }
        }
        return mineQueue.isEmpty();
    }

    private boolean execUseBlock(ServerPlayer p, ActionStep step) {
        int x = step.getInt("x", (int) p.getX()), y = step.getInt("y", (int) p.getY()),
            z = step.getInt("z", (int) p.getZ());
        BlockPos pos = new BlockPos(x, y, z);
        if (blockDistSqr(p, pos) > REACH_SQR) { pushToward(p, x, y, z); return false; }
        faceToward(p, x, y, z);
        if (!(p.level() instanceof ServerLevel sl)) return true;
        try {
            BlockState state = sl.getBlockState(pos);
            Block block = state.getBlock();
            if (block instanceof LeverBlock && state.hasProperty(BlockStateProperties.POWERED)) {
                sl.setBlock(pos, state.cycle(BlockStateProperties.POWERED), Block.UPDATE_ALL);
            } else if (block instanceof ButtonBlock && state.hasProperty(BlockStateProperties.POWERED)) {
                sl.setBlock(pos, state.setValue(BlockStateProperties.POWERED, true), Block.UPDATE_ALL);
                sl.scheduleTick(pos, block, 20);
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
                sl.setBlock(pos, state.cycle(BlockStateProperties.OPEN), Block.UPDATE_ALL);
            }
            p.swing(InteractionHand.MAIN_HAND);
        } catch (Exception ignored) {
            // Never let a quirky block break the plan.
        }
        return true;
    }

    private boolean execRunCommand(ServerPlayer p, ActionStep step) {
        String command = step.getString("command", "").trim();
        if (command.startsWith("/")) command = command.substring(1);
        if (command.isEmpty()) return true;
        if (!ModConfig.get().allowCommands) {
            status.add("§7(commands are disabled here — skipped)");
            return true;
        }
        if (isDeniedCommand(command)) {
            status.add("§7(that command isn't allowed — skipped)");
            return true;
        }
        if (!(p.level() instanceof ServerLevel sl)) return true;
        MinecraftServer server = sl.getServer();
        if (server == null) return true;
        try {
            // Same capability-based gate the bot uses (commandPermissionLevel + the
            // denylist above), sourced at the possessed player, output suppressed.
            PermissionSet perms = ModConfig.get().commandPermissionLevel <= 0
                    ? PermissionSet.NO_PERMISSIONS
                    : PermissionSet.ALL_PERMISSIONS;
            CommandSourceStack source = p.createCommandSourceStackForNameResolution(sl)
                    .withPermission(perms)
                    .withSuppressedOutput();
            server.getCommands().performPrefixedCommand(source, command);
        } catch (Exception ignored) {
            status.add("§7(that command didn't work)");
        }
        return true;
    }

    private boolean execJump(ServerPlayer p) {
        if (p.onGround()) {
            p.setDeltaMovement(p.getDeltaMovement().x, 0.42, p.getDeltaMovement().z);
            p.hurtMarked = true;
        }
        return true;
    }

    private boolean execAttackNearest(ServerPlayer p, ActionStep step) {
        double range = step.getDouble("range", 16.0);
        AABB box = AABB.ofSize(p.position(), range * 2, 10, range * 2);
        List<Monster> hostiles = p.level().getEntitiesOfClass(Monster.class, box, LivingEntity::isAlive);
        if (hostiles.isEmpty()) return true;
        LivingEntity target = hostiles.stream()
                .min((a, b) -> Double.compare(p.distanceToSqr(a), p.distanceToSqr(b)))
                .orElse(null);
        if (target == null) return true;
        faceToward(p, target.getX(), target.getEyeY(), target.getZ());
        if (p.distanceToSqr(target) < 9) {
            p.swing(InteractionHand.MAIN_HAND);
            p.attack(target);
            return !target.isAlive();
        }
        pushToward(p, target.getX(), target.getY(), target.getZ());
        return false;
    }

    private boolean execFollowPlayer(ServerPlayer p, ActionStep step) {
        String name = step.getString("name", "");
        double dist = step.getDouble("distance", 3.0);
        MinecraftServer server = p.level().getServer();
        Player target = null;
        if (!name.isBlank() && server != null) target = server.getPlayerList().getPlayerByName(name);
        if (target == null) {
            // Nearest other player.
            double best = Double.MAX_VALUE;
            for (Player other : p.level().players()) {
                if (other == p) continue;
                double d = p.distanceToSqr(other);
                if (d < best) { best = d; target = other; }
            }
        }
        if (target == null) return true;
        if (p.distanceToSqr(target) > dist * dist) {
            pushToward(p, target.getX(), target.getY(), target.getZ());
            return false;
        }
        return true;
    }

    private boolean execChat(ServerPlayer p, ActionStep step) {
        String msg = step.getString("message", "...");
        Component line = Component.literal("<" + p.getName().getString() + "> " + msg);
        p.level().players().forEach(pl -> pl.sendSystemMessage(line));
        return true;
    }

    private boolean execCollectItem(ServerPlayer p, ActionStep step) {
        double x = step.getDouble("x", p.getX()), y = step.getDouble("y", p.getY()),
               z = step.getDouble("z", p.getZ());
        if (horizDistSqr(p, x, z) > 1.5 * 1.5) { pushToward(p, x, y, z); return false; }
        return true;   // walking over the item picks it up
    }

    // ── movement helpers ──────────────────────────────────────────────────────────

    /**
     * Drives the player toward a target by setting velocity each tick (and syncing it
     * to the client via {@code hurtMarked}), jumping to climb or when blocked, and
     * teleporting to catch up if it's very far. This is the same server-authoritative
     * push the mini-games use, so it needs no client mod.
     */
    private void pushToward(ServerPlayer p, double tx, double ty, double tz) {
        faceToward(p, tx, ty, tz);
        double dx = tx - p.getX(), dz = tz - p.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz > TELEPORT_DIST) {
            p.setDeltaMovement(Vec3.ZERO);
            p.teleportTo(tx, ty, tz);
            return;
        }
        if (horiz < 0.05) return;
        double nx = dx / horiz * WALK_SPEED, nz = dz / horiz * WALK_SPEED;
        double vy = p.getDeltaMovement().y;
        boolean needUp = ty > p.getY() + 0.6;
        if (p.onGround() && (needUp || p.horizontalCollision)) vy = 0.42;
        p.setDeltaMovement(nx, vy, nz);
        p.hurtMarked = true;
    }

    /** Stops the player's horizontal drift so it doesn't slide past a target. */
    private void stopPlayer(ServerPlayer p) {
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(0, v.y, 0);
        p.hurtMarked = true;
    }

    /** Aims the player's server-side facing at a point (best-effort — look is client-owned). */
    private void faceToward(ServerPlayer p, double tx, double ty, double tz) {
        double dx = tx - p.getX(), dy = ty - p.getEyeY(), dz = tz - p.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
        p.setXRot(pitch);
    }

    private double horizDistSqr(ServerPlayer p, double x, double z) {
        double dx = x - p.getX(), dz = z - p.getZ();
        return dx * dx + dz * dz;
    }

    private double blockDistSqr(ServerPlayer p, BlockPos pos) {
        return p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private boolean isDeniedCommand(String command) {
        String first = command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (first.startsWith("minecraft:")) first = first.substring("minecraft:".length());
        return DENIED_COMMANDS.contains(first);
    }

    private void resetStep() {
        currentStep = null;
        stepTimer = 0;
        waitRemaining = 0;
        mineQueue.clear();
    }

    // ── context ───────────────────────────────────────────────────────────────────

    /** A compact, player-centric context for the planner (position, threats, time). */
    private String buildContext(ServerPlayer p) {
        BlockPos pos = p.blockPosition();
        StringBuilder sb = new StringBuilder();
        sb.append("You are controlling player ").append(p.getName().getString()).append(".\n");
        sb.append("Player position: ").append(pos.getX()).append(",").append(pos.getY())
                .append(",").append(pos.getZ()).append("\n");

        if (p.level() instanceof ServerLevel sl) {
            sb.append("Dimension: ").append(sl.dimension().identifier().getPath()).append("\n");
            List<ServerPlayer> others = sl.players();
            if (others.size() > 1) {
                sb.append("Other players: ");
                others.stream().filter(o -> o != p).limit(5).forEach(o ->
                        sb.append(o.getName().getString())
                                .append("@").append(o.blockPosition().getX())
                                .append(",").append(o.blockPosition().getY())
                                .append(",").append(o.blockPosition().getZ()).append(" "));
                sb.append("\n");
            }
            AABB box = AABB.ofSize(p.position(), 24, 12, 24);
            List<Monster> nearby = sl.getEntitiesOfClass(Monster.class, box, LivingEntity::isAlive);
            if (!nearby.isEmpty()) {
                Monster m = nearby.get(0);
                sb.append("Hostile mobs nearby: ").append(nearby.size())
                        .append(" (nearest ").append(m.getType().toShortString())
                        .append(" @").append(m.blockPosition().getX()).append(",")
                        .append(m.blockPosition().getY()).append(",")
                        .append(m.blockPosition().getZ()).append(")\n");
            }
            long t = sl.getOverworldClockTime() % 24000L;
            sb.append("Time: ").append(t >= 13000 && t < 23000 ? "night" : "day").append("\n");
        }
        sb.append("Health: ").append((int) p.getHealth()).append("/").append((int) p.getMaxHealth());
        return sb.toString();
    }
}
