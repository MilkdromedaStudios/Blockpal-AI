package com.milkdromeda.blockpal.agent;

import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * <b>The bot's keyboard and mouse.</b>
 *
 * <p>This is the whole point of the "non-cheating" brain: an AI-written script never
 * calls {@code setBlock} or teleports. It presses <i>inputs</i> — walk forward, turn
 * the view, hold left-click, tap right-click — and this class turns those inputs into
 * the same physical outcomes a player would get:
 *
 * <ul>
 *   <li><b>Movement</b> sets the mob's own {@code zza}/{@code xxa} drive, so the bot
 *       walks, is blocked by walls, falls off cliffs and swims like anything else.</li>
 *   <li><b>Looking</b> turns the head at a human-ish rate (never an instant snap).</li>
 *   <li><b>Hold left-click</b> mines whatever the crosshair is on, accumulating real
 *       break progress from the block's hardness and the held tool's speed — cracks and
 *       all — or swings at a creature in reach on a weapon cooldown.</li>
 *   <li><b>Right-click</b> places the held block against the face it's aimed at, flips
 *       levers/buttons/doors, or eats what it's holding.</li>
 * </ul>
 *
 * <p>Because everything goes through reach checks, tool speeds and block hardness, the
 * bot is <i>slower and clumsier</i> than the old "teleport-and-setblock" planner — that
 * is the trade the design makes on purpose.
 *
 * <p><b>It will not attack a player unless the server has allowed it and that player
 * started the fight.</b> A companion swinging at people is a griefing tool, so the one
 * decision is made in {@link com.milkdromeda.blockpal.combat.PvpRules} — off by default,
 * ops-only to enable, and provoked-only even then. Nothing here, and no script, can route
 * around it.
 */
public class BotInput {

    /** How far the bot can reach to break or place, in blocks (a survival player's arm). */
    public static final double REACH = 4.5;

    /**
      * Fastest a head can turn, in degrees per tick. Set by {@link Tempo}, because a
      * fixed 22°/tick was most of why the bot felt slow — turning to face something
      * behind it took the better part of a second before it could even start.
      */
    private static float turnRate() { return Tempo.current().turnRate(); }

    // ── the "buttons" a script can hold ─────────────────────────────────────────
    private float forward;      // -1..1  (S/W)
    private float strafe;       // -1..1  (D/A)
    private boolean jump;
    private boolean sneak;
    private boolean sprint;
    private boolean attackHeld;
    private boolean useHeld;
    private boolean navigating;

    // Where the view is being aimed (absolute, degrees) — approached at TURN_RATE.
    private float targetYaw;
    private float targetPitch;
    private boolean aiming;

    // ── mining state ────────────────────────────────────────────────────────────
    private BlockPos breaking;
    private float breakProgress;
    private int lastSentStage = -1;
    private int swingCooldown;
    private int attackCooldown;
    private int useCooldown;

    /** Last thing the driver actually did, surfaced to the script log. */
    private String lastEvent = "";

    // ── setters used by the script VM ───────────────────────────────────────────

    public void setForward(float v) { forward = clamp(v); }
    public void setStrafe(float v) { strafe = clamp(v); }
    public void setJump(boolean v) { jump = v; }
    public void setSneak(boolean v) { sneak = v; }
    public void setSprint(boolean v) { sprint = v; }
    public void setAttack(boolean v) { attackHeld = v; if (!v) cancelBreak(); }
    public void setUse(boolean v) { useHeld = v; }

    /**
     * While true, the pathfinder is steering (a {@code goTo}/{@code collect} action), so
     * the manual walk keys stand aside — otherwise the two would fight over the same
     * movement inputs and the bot would shuffle on the spot. Looking, mining and using
     * still work normally while navigating.
     */
    public void setNavigating(boolean v) { navigating = v; }

    public void aim(float yaw, float pitch) {
        targetYaw = yaw;
        targetPitch = Math.max(-90f, Math.min(90f, pitch));
        aiming = true;
    }

    /** Drops every held button — used when a script ends, is cancelled, or the bot is hurt. */
    public void releaseAll() {
        forward = 0;
        strafe = 0;
        jump = false;
        sneak = false;
        sprint = false;
        attackHeld = false;
        useHeld = false;
        aiming = false;
        cancelBreak();
    }

    public String consumeEvent() {
        String e = lastEvent;
        lastEvent = "";
        return e;
    }

    public boolean isBreaking() { return breaking != null; }

    /** 0..1 progress on the block currently being mined. */
    public float breakProgress() { return breakProgress; }

    // ── per-tick application ────────────────────────────────────────────────────

    /**
     * Applies the currently-held inputs for one tick. Called from the entity's own
     * tick while a script (or the survival brain) is driving.
     */
    public void tick(AiAssistantEntity bot, ServerLevel level) {
        if (swingCooldown > 0) swingCooldown--;
        if (attackCooldown > 0) attackCooldown--;
        if (useCooldown > 0) useCooldown--;

        applyLook(bot);
        applyMovement(bot);

        if (attackHeld) {
            applyAttack(bot, level);
        } else if (breaking != null) {
            cancelBreak(bot, level);
        }

        if (useHeld && useCooldown <= 0) {
            useCooldown = Tempo.current().useCooldown();   // like holding right-click
            applyUse(bot, level);
        }
    }

    private void applyLook(AiAssistantEntity bot) {
        if (!aiming) return;
        float yaw = approach(bot.getYRot(), targetYaw);
        float pitch = approach(bot.getXRot(), targetPitch);
        bot.setYRot(yaw);
        bot.setYHeadRot(yaw);
        bot.yBodyRot = yaw;
        bot.setXRot(pitch);
    }

    private void applyMovement(AiAssistantEntity bot) {
        bot.setShiftKeyDown(sneak);
        bot.setSprinting(sprint && forward > 0 && !sneak);
        if (!navigating) {
            // The mob's own travel() consumes these exactly like a player's WASD.
            bot.setZza(forward * (sneak ? 0.3f : 1f));
            bot.setXxa(strafe * (sneak ? 0.3f : 1f));
        }
        if (jump && bot.onGround()) bot.jumpFromGround();
    }

    // ── left click ──────────────────────────────────────────────────────────────

    private void applyAttack(AiAssistantEntity bot, ServerLevel level) {
        // A creature in reach always wins the click, exactly like aiming at a mob.
        LivingEntity target = entityInCrosshair(bot, level);
        if (target != null) {
            cancelBreak(bot, level);
            if (attackCooldown <= 0) {
                attackCooldown = 12;      // roughly a sword's swing cooldown
                bot.swing(InteractionHand.MAIN_HAND);
                bot.doHurtTarget(level, target);
                lastEvent = "hit " + target.getType().toShortString();
            }
            return;
        }
        mineCrosshair(bot, level);
    }

    /** Progressive block breaking: hardness vs. the tool actually being held. */
    private void mineCrosshair(AiAssistantEntity bot, ServerLevel level) {
        BlockHitResult hit = rayBlock(bot, level);
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            cancelBreak(bot, level);
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) { cancelBreak(bot, level); return; }

        if (!pos.equals(breaking)) {
            cancelBreak(bot, level);
            breaking = pos.immutable();
            breakProgress = 0f;
        }

        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) {                       // bedrock and friends
            lastEvent = "can't break " + com.milkdromeda.blockpal.vision.BotVision.blockName(state);
            cancelBreak(bot, level);
            return;
        }

        ItemStack held = bot.getItemBySlot(EquipmentSlot.MAINHAND);
        float toolSpeed = held.getDestroySpeed(state);
        boolean correctTool = !state.requiresCorrectToolForDrops() || held.isCorrectToolForDrops(state);
        // Vanilla's own formula: /30 with the right tool, /100 when bare-handing it.
        float perTick = hardness <= 0f ? 1f : toolSpeed / hardness / (correctTool ? 30f : 100f);
        // Vanilla-accurate at every tempo except "instant", which trades realism for pace.
        breakProgress += perTick * Tempo.current().miningMultiplier();

        if (swingCooldown <= 0) {
            swingCooldown = Tempo.current().swingCooldown();
            bot.swing(InteractionHand.MAIN_HAND);
        }

        int stage = (int) (breakProgress * 10f);
        if (stage != lastSentStage && stage < 10) {
            lastSentStage = stage;
            level.destroyBlockProgress(bot.getId(), breaking, stage);
        }

        if (breakProgress >= 1f) {
            level.destroyBlockProgress(bot.getId(), breaking, -1);
            String name = com.milkdromeda.blockpal.vision.BotVision.blockName(state);
            level.destroyBlock(breaking, true, bot);   // real drops, exactly like mining it
            if (!held.isEmpty() && hardness > 0) {
                held.hurtAndBreak(1, bot, EquipmentSlot.MAINHAND);
            }
            lastEvent = "broke " + name;
            breaking = null;
            breakProgress = 0f;
            lastSentStage = -1;
        }
    }

    private void cancelBreak() {
        breaking = null;
        breakProgress = 0f;
        lastSentStage = -1;
    }

    private void cancelBreak(AiAssistantEntity bot, ServerLevel level) {
        if (breaking != null) level.destroyBlockProgress(bot.getId(), breaking, -1);
        cancelBreak();
    }

    // ── right click ─────────────────────────────────────────────────────────────

    private void applyUse(AiAssistantEntity bot, ServerLevel level) {
        BlockHitResult hit = rayBlock(bot, level);
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            // Nothing aimed at: right-clicking air eats/drinks what's held, like a player.
            if (eatHeld(bot, level)) lastEvent = "ate what I was holding";
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);

        if (!bot.isShiftKeyDown() && toggle(level, pos, state)) {
            bot.swing(InteractionHand.MAIN_HAND);
            lastEvent = "used " + com.milkdromeda.blockpal.vision.BotVision.blockName(state);
            return;
        }
        if (placeHeld(bot, level, hit)) return;
        if (eatHeld(bot, level)) lastEvent = "ate what I was holding";
    }

    /**
     * Flips the things a right-click flips: levers, buttons, doors, trapdoors, gates.
     * Containers are deliberately not opened here — a script opens those explicitly so
     * item moves are auditable (see {@link BotApi}).
     *
     * @return true when this block had something to toggle
     */
    public static boolean toggle(ServerLevel level, BlockPos pos, BlockState state) {
        try {
            Block block = state.getBlock();
            if (block instanceof LeverBlock && state.hasProperty(BlockStateProperties.POWERED)) {
                level.setBlock(pos, state.cycle(BlockStateProperties.POWERED), Block.UPDATE_ALL);
                return true;
            }
            if (block instanceof ButtonBlock && state.hasProperty(BlockStateProperties.POWERED)) {
                level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, true), Block.UPDATE_ALL);
                level.scheduleTick(pos, block, 20);
                return true;
            }
            if (block instanceof DoorBlock && state.hasProperty(BlockStateProperties.OPEN)) {
                boolean open = !state.getValue(BlockStateProperties.OPEN);
                level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
                BlockPos other = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                        ? pos.above() : pos.below();
                BlockState os = level.getBlockState(other);
                if (os.getBlock() instanceof DoorBlock && os.hasProperty(BlockStateProperties.OPEN)) {
                    level.setBlock(other, os.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
                }
                return true;
            }
            if (state.hasProperty(BlockStateProperties.OPEN)) {       // trapdoors, fence gates
                level.setBlock(pos, state.cycle(BlockStateProperties.OPEN), Block.UPDATE_ALL);
                return true;
            }
        } catch (Exception ignored) {
            // A quirky/modded block must never break the driving loop.
        }
        return false;
    }

    /** Places the held block against the aimed face, consuming it from the hand. */
    private boolean placeHeld(AiAssistantEntity bot, ServerLevel level, BlockHitResult hit) {
        ItemStack held = bot.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!(held.getItem() instanceof BlockItem blockItem)) return false;

        BlockPos target = level.getBlockState(hit.getBlockPos()).canBeReplaced()
                ? hit.getBlockPos()
                : hit.getBlockPos().relative(hit.getDirection());
        if (!level.getBlockState(target).canBeReplaced()) return false;
        if (bot.distanceToSqr(Vec3.atCenterOf(target)) > REACH * REACH * 1.4) return false;

        BlockState placed = orient(blockItem.getBlock().defaultBlockState(), bot, hit.getDirection());
        try {
            if (!placed.canSurvive(level, target)) return false;
            // Never place inside something living (including ourselves).
            if (!placed.getCollisionShape(level, target).isEmpty()
                    && !level.getEntities(null, new AABB(target)).isEmpty()) {
                return false;
            }
            level.setBlock(target, placed, Block.UPDATE_ALL);
        } catch (Exception e) {
            return false;
        }
        held.shrink(1);
        bot.swing(InteractionHand.MAIN_HAND);
        lastEvent = "placed " + com.milkdromeda.blockpal.vision.BotVision.blockName(placed);
        return true;
    }

    /**
     * Orients a freshly placed block the way a player's placement would: facing blocks
     * turn to face the placer, logs/pillars take the axis of the face clicked.
     */
    private static BlockState orient(BlockState state, AiAssistantEntity bot, Direction face) {
        try {
            if (state.hasProperty(BlockStateProperties.AXIS)) {
                return state.setValue(BlockStateProperties.AXIS, face.getAxis());
            }
            Direction facing = Direction.fromYRot(bot.getYRot()).getOpposite();
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                return state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
            }
            if (state.hasProperty(BlockStateProperties.FACING)) {
                return state.setValue(BlockStateProperties.FACING, facing);
            }
        } catch (Exception ignored) {
            // Fall through to the default state.
        }
        return state;
    }

    /** Eats/drinks the held item if it's edible, applying its real effects. */
    private boolean eatHeld(AiAssistantEntity bot, ServerLevel level) {
        ItemStack held = bot.getItemBySlot(EquipmentSlot.MAINHAND);
        if (held.isEmpty() || held.get(DataComponents.FOOD) == null) return false;
        return bot.consumeHeld(level);
    }

    // ── ray casting ─────────────────────────────────────────────────────────────

    /** What the crosshair is on, within arm's reach. */
    public static BlockHitResult rayBlock(AiAssistantEntity bot, ServerLevel level) {
        Vec3 eye = bot.getEyePosition();
        Vec3 end = eye.add(bot.getViewVector(1.0f).scale(REACH));
        return level.clip(new ClipContext(eye, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, bot));
    }

    /**
     * The nearest creature the crosshair line passes through, within reach.
     *
     * <p>A player is only ever returned when
     * {@link com.milkdromeda.blockpal.combat.PvpRules#mayAttack} says so, which is the
     * single place that decision is made. Every path that can produce a swing — a script
     * calling {@code attack()}, the combat brain, a learned policy pressing the button —
     * comes through here, so there is exactly one gate rather than one per caller.
     */
    public static LivingEntity entityInCrosshair(AiAssistantEntity bot, ServerLevel level) {
        Vec3 eye = bot.getEyePosition();
        Vec3 end = eye.add(bot.getViewVector(1.0f).scale(REACH));
        AABB search = new AABB(eye, end).inflate(1.0);
        List<Entity> candidates = level.getEntities(bot, search,
                e -> e.isAlive() && e instanceof LivingEntity && !(e instanceof ItemEntity)
                        && (!(e instanceof Player p)
                            || com.milkdromeda.blockpal.combat.PvpRules.mayAttack(bot, p)));
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : candidates) {
            if (!e.getBoundingBox().inflate(0.25).intersects(eye, end)) continue;
            double d = e.distanceToSqr(bot);
            if (d < bestDist) { bestDist = d; best = (LivingEntity) e; }
        }
        return best;
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private static float approach(float current, float target) {
        float rate = turnRate();
        float delta = net.minecraft.util.Mth.wrapDegrees(target - current);
        if (delta > rate) delta = rate;
        if (delta < -rate) delta = -rate;
        return current + delta;
    }

    private static float clamp(float v) {
        return Math.max(-1f, Math.min(1f, v));
    }
}
