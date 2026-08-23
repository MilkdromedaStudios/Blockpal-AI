package com.milkdromeda.blockpal.agent;

import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * <b>The jobs a companion knows how to do</b>, above the level of single button presses.
 *
 * <p>{@link BotApi} gives a script the controls — walk, aim, hold left-click. That is
 * enough to do anything in principle and exhausting in practice: "dig a tunnel" written
 * out of primitives is thirty lines of aiming and mining that a small model gets wrong.
 * These are the jobs that come up over and over, written once, correctly.
 *
 * <p>Every one of them is still <b>done by hand</b>. Tunnelling mines each block with
 * real break progress; bridging places real blocks out of the backpack and stops when it
 * runs out; harvesting walks to each crop. Nothing here reaches through a wall, fills an
 * area instantly, or produces an item the bot did not have.
 */
public final class BotSkills {

    private BotSkills() {}

    /** Where a waypoint lives in the bot's notebook. */
    private static final String WAYPOINT_PREFIX = "wp:";

    // ── waypoints (instant) ─────────────────────────────────────────────────────

    /** Notes the bot's current position under a name. */
    public static String setWaypoint(AiAssistantEntity bot, String name) {
        BlockPos p = bot.blockPosition();
        String value = p.getX() + "," + p.getY() + "," + p.getZ();
        bot.remember(WAYPOINT_PREFIX + clean(name), value);
        return value;
    }

    /** "x,y,z" for a named waypoint, or "" when there isn't one. */
    public static String getWaypoint(AiAssistantEntity bot, String name) {
        return bot.recall(WAYPOINT_PREFIX + clean(name));
    }

    public static boolean clearWaypoint(AiAssistantEntity bot, String name) {
        return bot.forget(WAYPOINT_PREFIX + clean(name));
    }

    /** Every waypoint the bot knows, as text. */
    public static String waypointList(AiAssistantEntity bot) {
        StringBuilder sb = new StringBuilder();
        bot.memory().forEach((k, v) -> {
            if (!k.startsWith(WAYPOINT_PREFIX)) return;
            if (sb.length() > 0) sb.append(", ");
            sb.append(k.substring(WAYPOINT_PREFIX.length())).append(" (").append(v).append(')');
        });
        return sb.length() == 0 ? "(no waypoints)" : sb.toString();
    }

    // ── senses (instant) ────────────────────────────────────────────────────────

    /** What the bot is carrying, as one readable line. */
    public static String inventoryList(AiAssistantEntity bot) {
        SimpleContainer pack = bot.getInventory();
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (int i = 0; i < pack.getContainerSize(); i++) {
            ItemStack s = pack.getItem(i);
            if (s.isEmpty()) continue;
            if (shown++ > 0) sb.append(", ");
            sb.append(s.getCount()).append("× ").append(BotApi.itemName(s));
        }
        ItemStack held = bot.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!held.isEmpty()) {
            sb.append(shown > 0 ? ", " : "").append("holding ").append(BotApi.itemName(held));
        }
        return sb.length() == 0 ? "(empty)" : sb.toString();
    }

    /** True when there is room in the backpack for more loot. */
    public static boolean hasSpace(AiAssistantEntity bot) {
        SimpleContainer pack = bot.getInventory();
        for (int i = 0; i < pack.getContainerSize(); i++) {
            if (pack.getItem(i).isEmpty()) return true;
        }
        return false;
    }

    /** Nearest player as "x,y,z", or "". */
    public static String nearestPlayer(AiAssistantEntity bot, ServerLevel level, double radius) {
        Player p = findNearestPlayer(bot, level, radius);
        return p == null ? "" : (int) p.getX() + "," + (int) p.getY() + "," + (int) p.getZ();
    }

    /** Nearest player's name, or "". */
    public static String nearestPlayerName(AiAssistantEntity bot, ServerLevel level, double radius) {
        Player p = findNearestPlayer(bot, level, radius);
        return p == null ? "" : p.getName().getString();
    }

    private static Player findNearestPlayer(AiAssistantEntity bot, ServerLevel level, double radius) {
        double r = Math.max(1, Math.min(64, radius));
        AABB box = AABB.ofSize(bot.position(), r * 2, r, r * 2);
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : level.getEntitiesOfClass(Player.class, box, LivingEntity::isAlive)) {
            double d = p.distanceToSqr(bot);
            if (d < bestDist) { bestDist = d; best = p; }
        }
        return best;
    }

    /** Daytime in the overworld sense — 0..12999 of the day cycle. */
    public static boolean isDay(ServerLevel level) {
        return level.getOverworldClockTime() % 24000L < 13000L;
    }

    public static String dimensionName(ServerLevel level) {
        try {
            return level.dimension().identifier().getPath();
        } catch (Exception e) {
            return "overworld";
        }
    }

    /** Is there a clear line of sight from the bot's eyes to a block? */
    public static boolean canSee(AiAssistantEntity bot, ServerLevel level, BlockPos target) {
        try {
            Vec3 eye = bot.getEyePosition();
            Vec3 to = Vec3.atCenterOf(target);
            BlockHitResult hit = level.clip(new ClipContext(eye, to,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, bot));
            return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
        } catch (Exception e) {
            return false;
        }
    }

    /** Puts the best armour on and the best weapon in hand, then reports what it wears. */
    public static String equipBestAndDescribe(AiAssistantEntity bot, ServerLevel level) {
        bot.optimizeEquipment(level);
        return "armour " + bot.getArmorValue() + ", holding "
                + BotApi.itemName(bot.getItemBySlot(EquipmentSlot.MAINHAND));
    }

    // ── digging a tunnel ────────────────────────────────────────────────────────

    /**
     * Digs a corridor two blocks high straight ahead — the single most common thing
     * anybody actually wants a mining companion to do.
     */
    public static final class TunnelTask implements BotApi.Task {
        private final int length;
        private final Direction facing;
        private BlockPos origin;
        private int dug;
        private int phase;
        private int ticks;
        private String outcome = "";

        public TunnelTask(AiAssistantEntity bot, int length) {
            this.length = Math.max(1, Math.min(64, length));
            this.facing = Direction.fromYRot(bot.getYRot());
        }

        @Override
        public boolean tick(AiAssistantEntity bot, ServerLevel level, BotInput input) {
            if (origin == null) origin = bot.blockPosition();
            if (dug >= length || ++ticks > length * 120L) {
                outcome = "tunnelled " + dug + " blocks";
                input.setAttack(false);
                input.setForward(0);
                return true;
            }

            BlockPos ahead = origin.relative(facing, dug + 1);
            BlockPos target = phase == 0 ? ahead : ahead.above();
            BlockState state = level.getBlockState(target);

            if (state.isAir() || state.canBeReplaced()) {
                // Nothing to dig at this height — move on.
                if (phase == 0) { phase = 1; return false; }
                phase = 0;
                dug++;
                stepForward(bot, level, input, origin.relative(facing, dug));
                return false;
            }
            if (state.getDestroySpeed(level, target) < 0) {
                outcome = "hit something I can't break after " + dug + " blocks";
                input.setAttack(false);
                return true;
            }

            aimAt(bot, input, target);
            input.setAttack(true);
            String event = input.consumeEvent();
            if (event.startsWith("broke")) {
                if (phase == 0) phase = 1;
                else {
                    phase = 0;
                    dug++;
                    stepForward(bot, level, input, origin.relative(facing, dug));
                }
            } else if (event.startsWith("can't")) {
                outcome = event + " after " + dug + " blocks";
                input.setAttack(false);
                return true;
            }
            return false;
        }

        private void stepForward(AiAssistantEntity bot, ServerLevel level, BotInput input, BlockPos to) {
            input.setAttack(false);
            bot.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 1.0);
        }

        @Override public Object result() { return outcome; }
        @Override public String summary() { return outcome; }
    }

    // ── bridging across a gap ───────────────────────────────────────────────────

    /**
     * Walks forward placing blocks under itself, the way a player crosses a ravine.
     * Stops when the blocks run out rather than pretending to have more.
     */
    public static final class BridgeTask implements BotApi.Task {
        private final int length;
        private final Direction facing;
        private BlockPos origin;
        private int placed;
        private int ticks;
        private String outcome = "";

        public BridgeTask(AiAssistantEntity bot, int length) {
            this.length = Math.max(1, Math.min(64, length));
            this.facing = Direction.fromYRot(bot.getYRot());
        }

        @Override
        public boolean tick(AiAssistantEntity bot, ServerLevel level, BotInput input) {
            if (origin == null) origin = bot.blockPosition().below();
            if (placed >= length || ++ticks > length * 100L) {
                outcome = "bridged " + placed + " blocks";
                input.setSneak(false);
                input.setForward(0);
                return true;
            }

            BlockPos under = origin.relative(facing, placed + 1);
            BlockState state = level.getBlockState(under);
            if (!state.isAir() && !state.canBeReplaced()) {
                placed++;                       // already solid ground here
                return false;
            }
            if (!holdBuildingBlock(bot)) {
                outcome = "ran out of blocks after " + placed;
                input.setSneak(false);
                return true;
            }

            // Sneaking at the edge is what stops a player walking off it.
            input.setSneak(true);
            aimAt(bot, input, under);
            input.setUse(true);
            String event = input.consumeEvent();
            if (event.startsWith("placed")) {
                input.setUse(false);
                placed++;
                bot.getNavigation().moveTo(
                        under.getX() + 0.5, under.getY() + 1, under.getZ() + 0.5, 0.8);
            }
            return false;
        }

        @Override public Object result() { return outcome; }
        @Override public String summary() { return outcome; }
    }

    // ── pillaring up ────────────────────────────────────────────────────────────

    /** Jump, place a block underneath, repeat — how a player gains height. */
    public static final class PillarTask implements BotApi.Task {
        private final int height;
        private int placed;
        private int ticks;
        private int startY = Integer.MIN_VALUE;
        private String outcome = "";

        public PillarTask(int height) { this.height = Math.max(1, Math.min(48, height)); }

        @Override
        public boolean tick(AiAssistantEntity bot, ServerLevel level, BotInput input) {
            if (startY == Integer.MIN_VALUE) startY = bot.blockPosition().getY();
            int gained = bot.blockPosition().getY() - startY;
            if (gained >= height || ++ticks > height * 60L) {
                outcome = "went up " + gained + " blocks";
                input.setJump(false);
                return true;
            }
            if (!holdBuildingBlock(bot)) {
                outcome = "ran out of blocks " + gained + " up";
                return true;
            }
            // Look straight down, hop, and place into the gap left behind.
            input.aim(bot.getYRot(), 90f);
            if (bot.onGround()) {
                input.setJump(true);
            } else {
                input.setJump(false);
                input.setUse(true);
                if (input.consumeEvent().startsWith("placed")) {
                    input.setUse(false);
                    placed++;
                }
            }
            return false;
        }

        @Override public Object result() { return outcome; }
        @Override public String summary() { return outcome; }
    }

    // ── staircase down ──────────────────────────────────────────────────────────

    /**
     * Digs down in a staircase rather than straight down. Straight down is how players
     * fall into lava; a staircase is the habit that stops it, and the bot checks what it
     * is about to break for liquid before breaking it.
     */
    public static final class StairsDownTask implements BotApi.Task {
        private final int depth;
        private final Direction facing;
        private BlockPos cursor;
        private int dug;
        private int phase;
        private int ticks;
        private String outcome = "";

        public StairsDownTask(AiAssistantEntity bot, int depth) {
            this.depth = Math.max(1, Math.min(64, depth));
            this.facing = Direction.fromYRot(bot.getYRot());
        }

        @Override
        public boolean tick(AiAssistantEntity bot, ServerLevel level, BotInput input) {
            if (cursor == null) cursor = bot.blockPosition();
            if (dug >= depth || ++ticks > depth * 160L) {
                outcome = "dug down " + dug;
                input.setAttack(false);
                return true;
            }

            BlockPos step = cursor.relative(facing).below();
            BlockPos head = step.above();
            BlockPos target = phase == 0 ? head : step;

            // Never open a hole into a liquid you're standing above.
            for (Direction d : Direction.values()) {
                BlockState neighbour = level.getBlockState(step.relative(d));
                if (neighbour.liquid()) {
                    outcome = "stopped — " + BotVisionName(neighbour) + " behind that block";
                    input.setAttack(false);
                    return true;
                }
            }

            BlockState state = level.getBlockState(target);
            if (state.isAir() || state.canBeReplaced()) {
                if (phase == 0) { phase = 1; return false; }
                phase = 0;
                cursor = step;
                dug++;
                bot.getNavigation().moveTo(step.getX() + 0.5, step.getY(), step.getZ() + 0.5, 0.8);
                return false;
            }
            if (state.getDestroySpeed(level, target) < 0) {
                outcome = "hit bedrock after " + dug;
                input.setAttack(false);
                return true;
            }

            aimAt(bot, input, target);
            input.setAttack(true);
            String event = input.consumeEvent();
            if (event.startsWith("broke")) {
                if (phase == 0) phase = 1;
                else {
                    phase = 0;
                    cursor = step;
                    dug++;
                }
            }
            return false;
        }

        private static String BotVisionName(BlockState state) {
            return com.milkdromeda.blockpal.vision.BotVision.blockName(state);
        }

        @Override public Object result() { return outcome; }
        @Override public String summary() { return outcome; }
    }

    // ── mining a whole vein ─────────────────────────────────────────────────────

    /**
     * Follows a seam of ore rather than taking one block out of it — flood fill over
     * connected blocks of the same kind, mined one at a time from within reach.
     */
    public static final class MineVeinTask implements BotApi.Task {
        private static final int MAX_BLOCKS = 64;
        private final BlockPos start;
        private final Deque<BlockPos> queue = new ArrayDeque<>();
        private final Set<BlockPos> seen = new HashSet<>();
        private String kind = "";
        private BotApi.Task current;
        private int mined;
        private int ticks;
        private String outcome = "";

        public MineVeinTask(BlockPos start) { this.start = start; }

        @Override
        public boolean tick(AiAssistantEntity bot, ServerLevel level, BotInput input) {
            if (kind.isEmpty()) {
                kind = com.milkdromeda.blockpal.vision.BotVision.blockName(level.getBlockState(start));
                if (kind.equals("air")) { outcome = "there's nothing there"; return true; }
                queue.add(start);
                seen.add(start);
            }
            if (++ticks > MAX_BLOCKS * 120L) {
                outcome = "mined " + mined + " of the vein before running out of time";
                return true;
            }

            if (current != null) {
                if (!current.tick(bot, level, input)) return false;
                current = null;
                mined++;
            }
            while (!queue.isEmpty()) {
                BlockPos next = queue.poll();
                BlockState state = level.getBlockState(next);
                if (!com.milkdromeda.blockpal.vision.BotVision.blockName(state).equals(kind)) continue;
                if (mined >= MAX_BLOCKS) break;
                // Push the neighbours before mining, so the seam is followed as it opens.
                for (Direction d : Direction.values()) {
                    BlockPos n = next.relative(d);
                    if (seen.add(n) && com.milkdromeda.blockpal.vision.BotVision
                            .blockName(level.getBlockState(n)).equals(kind)) {
                        queue.add(n);
                    }
                }
                current = BotApi.mineAtTask(next);
                return false;
            }
            outcome = "mined " + mined + " × " + kind;
            return true;
        }

        @Override public Object result() { return outcome; }
        @Override public String summary() { return outcome; }
    }

    // ── farming ─────────────────────────────────────────────────────────────────

    /** Walks to every fully-grown crop nearby and breaks it. */
    public static final class HarvestTask implements BotApi.Task {
        private final double radius;
        private final List<BlockPos> targets = new ArrayList<>();
        private boolean scanned;
        private BotApi.Task current;
        private int harvested;
        private int ticks;
        private String outcome = "";

        public HarvestTask(double radius) { this.radius = Math.max(1, Math.min(24, radius)); }

        @Override
        public boolean tick(AiAssistantEntity bot, ServerLevel level, BotInput input) {
            if (!scanned) {
                scanned = true;
                targets.addAll(findRipeCrops(bot, level, (int) radius));
                if (targets.isEmpty()) { outcome = "nothing ready to harvest"; return true; }
            }
            if (++ticks > 60L * 120) { outcome = "harvested " + harvested + " (out of time)"; return true; }

            if (current != null) {
                if (!current.tick(bot, level, input)) return false;
                current = null;
                harvested++;
            }
            while (!targets.isEmpty()) {
                BlockPos next = targets.remove(targets.size() - 1);
                if (!isRipe(level.getBlockState(next))) continue;
                current = BotApi.mineAtTask(next);
                return false;
            }
            outcome = "harvested " + harvested;
            return true;
        }

        @Override public Object result() { return outcome; }
        @Override public String summary() { return outcome; }
    }

    /** Plants carried seeds on any empty farmland nearby. */
    public static final class PlantTask implements BotApi.Task {
        private final double radius;
        private final List<BlockPos> targets = new ArrayList<>();
        private boolean scanned;
        private BotApi.Task current;
        private int planted;
        private int ticks;
        private String outcome = "";

        public PlantTask(double radius) { this.radius = Math.max(1, Math.min(24, radius)); }

        @Override
        public boolean tick(AiAssistantEntity bot, ServerLevel level, BotInput input) {
            if (!scanned) {
                scanned = true;
                targets.addAll(findEmptyFarmland(bot, level, (int) radius));
                if (targets.isEmpty()) { outcome = "no empty farmland nearby"; return true; }
            }
            if (++ticks > 60L * 120) { outcome = "planted " + planted + " (out of time)"; return true; }

            if (current != null) {
                if (!current.tick(bot, level, input)) return false;
                current = null;
                planted++;
            }
            while (!targets.isEmpty()) {
                BlockPos next = targets.remove(targets.size() - 1);
                if (!level.getBlockState(next.above()).isAir()) continue;
                if (!holdSeeds(bot)) { outcome = "planted " + planted + " — out of seeds"; return true; }
                current = BotApi.useAtTask(next.above(), true);
                return false;
            }
            outcome = "planted " + planted;
            return true;
        }

        @Override public Object result() { return outcome; }
        @Override public String summary() { return outcome; }
    }

    // ── fighting on command ─────────────────────────────────────────────────────

    /** Hands the controls to the combat brain for a while. */
    public static final class DefendTask implements BotApi.Task {
        private final com.milkdromeda.blockpal.combat.CombatBrain combat =
                new com.milkdromeda.blockpal.combat.CombatBrain();
        private final int maxTicks;
        private int ticks;

        public DefendTask(int maxTicks) { this.maxTicks = Math.max(20, Math.min(6000, maxTicks)); }

        @Override
        public boolean tick(AiAssistantEntity bot, ServerLevel level, BotInput input) {
            if (++ticks > maxTicks) {
                combat.disengage(bot, input);
                return true;
            }
            if (!combat.tick(bot, level, input)) {
                input.setForward(0);
                input.setAttack(false);
            }
            return false;
        }

        @Override public String summary() { return combat.describe(); }
    }

    // ── sleeping ────────────────────────────────────────────────────────────────

    /** Finds a bed nearby and gets in it. */
    public static final class SleepTask implements BotApi.Task {
        private BlockPos bed;
        private BotApi.Task walk;
        private int ticks;
        private String outcome = "";

        @Override
        public boolean tick(AiAssistantEntity bot, ServerLevel level, BotInput input) {
            if (bed == null) {
                bed = findBed(bot, level, 12);
                if (bed == null) { outcome = "I can't find a bed"; return true; }
                walk = BotApi.goToTask(bed.getX(), bed.getY(), bed.getZ(), 300);
            }
            if (isDay(level)) { outcome = "it's daytime — I'd rather not"; return true; }
            if (++ticks > 400) { outcome = "couldn't get to the bed"; return true; }

            if (walk != null && !walk.tick(bot, level, input)) return false;
            walk = null;
            try {
                bot.startSleeping(bed);
                outcome = "gone to sleep";
            } catch (Exception e) {
                outcome = "that bed wouldn't take me";
            }
            return true;
        }

        @Override public Object result() { return outcome; }
        @Override public String summary() { return outcome; }
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    /** Ripe when a crop block says it's at its maximum age. */
    public static boolean isRipe(BlockState state) {
        try {
            return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
        } catch (Exception e) {
            return false;
        }
    }

    private static List<BlockPos> findRipeCrops(AiAssistantEntity bot, ServerLevel level, int radius) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos centre = bot.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (isRipe(level.getBlockState(cursor))) out.add(cursor.immutable());
                }
            }
        }
        return out;
    }

    private static List<BlockPos> findEmptyFarmland(AiAssistantEntity bot, ServerLevel level, int radius) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos centre = bot.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (!level.getBlockState(cursor).is(Blocks.FARMLAND)) continue;
                    if (!level.getBlockState(cursor.above()).isAir()) continue;
                    out.add(cursor.immutable());
                }
            }
        }
        return out;
    }

    private static BlockPos findBed(AiAssistantEntity bot, ServerLevel level, int radius) {
        BlockPos centre = bot.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (!(level.getBlockState(cursor).getBlock() instanceof BedBlock)) continue;
                    double d = cursor.distSqr(centre);
                    if (d < bestDist) { bestDist = d; best = cursor.immutable(); }
                }
            }
        }
        return best;
    }

    /** Puts a placeable building block in hand; false when there isn't one. */
    public static boolean holdBuildingBlock(AiAssistantEntity bot) {
        ItemStack held = bot.getItemBySlot(EquipmentSlot.MAINHAND);
        if (held.getItem() instanceof net.minecraft.world.item.BlockItem && held.getCount() > 0) {
            return true;
        }
        SimpleContainer pack = bot.getInventory();
        for (String preferred : new String[]{"cobblestone", "dirt", "stone", "planks", "netherrack"}) {
            if (bot.holdItemMatching(preferred)) return true;
        }
        for (int i = 0; i < pack.getContainerSize(); i++) {
            ItemStack s = pack.getItem(i);
            if (s.isEmpty() || !(s.getItem() instanceof net.minecraft.world.item.BlockItem)) continue;
            if (bot.holdItemMatching(BotApi.itemName(s))) return true;
        }
        return false;
    }

    /** Puts something plantable in hand. */
    public static boolean holdSeeds(AiAssistantEntity bot) {
        for (String seed : new String[]{"wheat_seeds", "carrot", "potato", "beetroot_seeds"}) {
            if (bot.holdItemMatching(seed)) return true;
        }
        return false;
    }

    /** Places a torch (or any light source carried) where the bot stands. */
    public static String placeTorch(AiAssistantEntity bot, ServerLevel level, BotInput input) {
        for (String light : new String[]{"torch", "lantern", "glowstone", "shroomlight", "sea_lantern"}) {
            if (!bot.holdItemMatching(light)) continue;
            BlockPos at = bot.blockPosition();
            if (!level.getBlockState(at).isAir()) at = at.above();
            aimAt(bot, input, at.below());
            input.setUse(true);
            return "placing a " + light;
        }
        return "I have nothing to light the place with";
    }

    private static void aimAt(AiAssistantEntity bot, BotInput input, BlockPos target) {
        Vec3 eye = bot.getEyePosition();
        double dx = target.getX() + 0.5 - eye.x;
        double dy = target.getY() + 0.5 - eye.y;
        double dz = target.getZ() + 0.5 - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        input.aim((float) Math.toDegrees(Math.atan2(-dx, dz)),
                (float) -Math.toDegrees(Math.atan2(dy, flat)));
    }

    private static String clean(String s) {
        String v = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
        return v.isEmpty() ? "home" : v;
    }
}
