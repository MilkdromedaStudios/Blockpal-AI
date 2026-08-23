package com.milkdromeda.blockpal.pvt;

import com.milkdromeda.blockpal.vision.BotVision;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * <b>What the agent sees, as numbers.</b> This is the input side of PVT, and the single
 * most important design constraint in the whole feature is written here:
 *
 * <p><b>A player and a bot must encode to the same thing.</b> The point of pre-video
 * training is that a demonstration recorded while a <i>person</i> plays can be learned
 * from and then replayed by a <i>companion</i>. That only works if "a tree two blocks
 * ahead and slightly left" produces the same numbers whichever body is looking at it.
 * So everything here is egocentric — distances and angles relative to the viewer, never
 * world coordinates — and every feature is derived from something both a player and a
 * mob genuinely have.
 *
 * <p>The observation is in two parts:
 * <ul>
 *   <li>a coarse <b>retina</b> ({@value RETINA_W}×{@value RETINA_H} rays over the
 *       viewer's field of view) giving, per cell, how close the thing there is, what
 *       kind of material it is, and how hard it would be to break;</li>
 *   <li><b>proprioception</b> — health, footing, motion, what's in hand, and where the
 *       nearest threat, item and friendly are relative to where the viewer is facing.</li>
 * </ul>
 *
 * <p>The retina is deliberately tiny. {@link BotVision} renders 80×45 for a language
 * model to look at; this is 12×7 because it runs several times a second for every
 * recording player and every PVT-driven bot, and because a reflex policy needs shape and
 * distance, not detail. That is 84 ray casts per observation against BotVision's 3,600.
 */
public final class PvtObservation {

    private PvtObservation() {}

    /** Retina width in cells. */
    public static final int RETINA_W = 12;
    /** Retina height in cells. */
    public static final int RETINA_H = 7;
    /** Numbers stored per retina cell: closeness, material class, hardness. */
    public static final int PER_CELL = 3;
    /** Body/context features that follow the retina. */
    public static final int PROPRIO = 36;

    /** Total length of an observation vector. */
    public static final int SIZE = RETINA_W * RETINA_H * PER_CELL + PROPRIO;

    /** Horizontal field of view the retina covers, in degrees. */
    private static final double FOV_DEGREES = 100.0;

    /** How far the retina looks. Short on purpose — this drives reflexes, not planning. */
    private static final double RETINA_RANGE = 24.0;

    /** How far out the "nearest thing" features search. */
    private static final double AWARENESS = 24.0;

    /**
     * Ticks between two decisions. Recording and driving both run at this rate, so a
     * learned turn of "10 degrees" means the same amount of turning in training as it
     * does in play. Frame-skip is also what makes the whole thing affordable.
     */
    public static final int DECISION_INTERVAL = 2;

    /**
     * Encodes what {@code agent} can perceive right now. Must be called on the server
     * thread — it reads block states. Never throws: a broken feature reads as zero
     * rather than taking down a tick.
     */
    public static float[] encode(LivingEntity agent, ServerLevel level) {
        float[] out = new float[SIZE];
        try {
            fillRetina(agent, level, out);
            fillProprioception(agent, level, out, RETINA_W * RETINA_H * PER_CELL);
        } catch (Exception ignored) {
            // A half-filled observation is survivable; a thrown tick is not.
        }
        return out;
    }

    // ── the retina ──────────────────────────────────────────────────────────────

    private static void fillRetina(LivingEntity agent, ServerLevel level, float[] out) {
        Vec3 eye = agent.getEyePosition();
        double yawRad = Math.toRadians(agent.getYRot());
        double pitchRad = Math.toRadians(agent.getXRot());

        // Same camera basis BotVision uses (and which its 47 camera tests pin down):
        // yaw 0 faces +Z and increases clockwise.
        Vec3 forward = new Vec3(
                -Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(pitchRad),
                Math.cos(yawRad) * Math.cos(pitchRad)).normalize();
        Vec3 right = new Vec3(-Math.cos(yawRad), 0, -Math.sin(yawRad)).normalize();
        Vec3 up = right.cross(forward).normalize();

        double tanH = Math.tan(Math.toRadians(FOV_DEGREES) / 2.0);
        double tanV = tanH * RETINA_H / (double) RETINA_W;

        int at = 0;
        for (int cy = 0; cy < RETINA_H; cy++) {
            double ndcY = 1.0 - 2.0 * (cy + 0.5) / RETINA_H;
            for (int cx = 0; cx < RETINA_W; cx++, at += PER_CELL) {
                double ndcX = 2.0 * (cx + 0.5) / RETINA_W - 1.0;
                Vec3 dir = forward
                        .add(right.scale(ndcX * tanH))
                        .add(up.scale(ndcY * tanV))
                        .normalize();

                BlockHitResult hit = level.clip(new ClipContext(
                        eye, eye.add(dir.scale(RETINA_RANGE)),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, agent));

                if (hit.getType() == HitResult.Type.MISS) {
                    out[at] = 0f;        // nothing out there: open sky or open air
                    out[at + 1] = 0f;
                    out[at + 2] = 0f;
                    continue;
                }
                BlockPos pos = hit.getBlockPos();
                BlockState state = level.getBlockState(pos);
                double dist = eye.distanceTo(hit.getLocation());

                out[at] = (float) (1.0 / (1.0 + dist));      // 1 = touching, → 0 = far
                out[at + 1] = materialClass(state);
                out[at + 2] = hardness(state, level, pos);
            }
        }
    }

    /**
     * A single number standing for "what kind of stuff is that", ordered so that
     * numerically close classes behave similarly: soft diggables low, valuable middling,
     * things that will hurt you high.
     */
    public static float materialClass(BlockState state) {
        if (state.isAir()) return 0f;
        String name = BotVision.blockName(state).toLowerCase(Locale.ROOT);
        if (contains(name, "lava", "fire", "magma", "cactus", "sweet_berry", "campfire")) return 1.0f;
        if (contains(name, "water", "bubble")) return 0.9f;
        if (name.contains("ore") || contains(name, "diamond", "emerald", "ancient_debris")) return 0.8f;
        if (contains(name, "stone", "deepslate", "cobble", "granite", "andesite", "diorite",
                "basalt", "obsidian", "brick", "concrete", "tuff", "blackstone")) return 0.65f;
        if (contains(name, "dirt", "grass_block", "sand", "gravel", "clay", "mud", "podzol",
                "mycelium", "soul_", "snow")) return 0.5f;
        if (contains(name, "log", "wood", "planks", "stem", "hyphae")) return 0.35f;
        if (contains(name, "leaves", "grass", "fern", "flower", "vine", "sapling", "bush",
                "moss", "wheat", "crop", "kelp", "seagrass", "mushroom")) return 0.2f;
        return 0.6f;    // an unfamiliar (or modded) solid block
    }

    private static boolean contains(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) return true;
        }
        return false;
    }

    /** 0 = instant, 1 = unbreakable. Tells the policy what is worth swinging at. */
    private static float hardness(BlockState state, ServerLevel level, BlockPos pos) {
        try {
            float h = state.getDestroySpeed(level, pos);
            if (h < 0) return 1f;                       // bedrock and friends
            return (float) Math.min(1.0, h / 25.0);
        } catch (Exception e) {
            return 0.5f;
        }
    }

    // ── proprioception ──────────────────────────────────────────────────────────

    private static void fillProprioception(LivingEntity agent, ServerLevel level, float[] o, int at) {
        float max = Math.max(1f, agent.getMaxHealth());
        o[at++] = clamp01(agent.getHealth() / max);
        o[at++] = agent instanceof Player p ? clamp01(p.getFoodData().getFoodLevel() / 20f) : 1f;
        o[at++] = agent.onGround() ? 1f : 0f;
        o[at++] = agent.isInWater() ? 1f : 0f;
        o[at++] = agent.isSprinting() ? 1f : 0f;
        o[at++] = agent.isShiftKeyDown() ? 1f : 0f;

        // Motion, rotated into the agent's own frame so "moving forward" is one number
        // regardless of which way it happens to be facing.
        Vec3 v = agent.getDeltaMovement();
        double yawRad = Math.toRadians(agent.getYRot());
        double fx = -Math.sin(yawRad), fz = Math.cos(yawRad);
        double rx = -Math.cos(yawRad), rz = -Math.sin(yawRad);
        o[at++] = (float) clamp(v.x * fx + v.z * fz, -1, 1);      // forward speed
        o[at++] = (float) clamp(v.x * rx + v.z * rz, -1, 1);      // sideways speed
        o[at++] = (float) clamp(v.y, -1, 1);                       // falling / rising

        o[at++] = clamp(agent.getXRot() / 90f, -1, 1);
        o[at++] = (float) Math.sin(yawRad);
        o[at++] = (float) Math.cos(yawRad);

        try {
            o[at++] = clamp01(level.getMaxLocalRawBrightness(agent.blockPosition()) / 15f);
        } catch (Exception e) {
            o[at++] = 0.5f;
        }
        double dayAngle = (level.getOverworldClockTime() % 24000L) / 24000.0 * Math.PI * 2;
        o[at++] = (float) Math.sin(dayAngle);
        o[at++] = (float) Math.cos(dayAngle);

        // What's in hand, as five yes/no features rather than an item id the network
        // would have to memorise. A stone pickaxe and a diamond one should behave alike.
        ItemStack held = agent.getItemBySlot(EquipmentSlot.MAINHAND);
        String item = held.isEmpty() ? "" : itemName(held);
        o[at++] = held.isEmpty() ? 1f : 0f;
        o[at++] = held.getItem() instanceof net.minecraft.world.item.BlockItem ? 1f : 0f;
        o[at++] = contains(item, "pickaxe", "axe", "shovel", "hoe", "shears") ? 1f : 0f;
        o[at++] = contains(item, "sword", "axe", "bow", "crossbow", "trident") ? 1f : 0f;
        o[at++] = held.get(net.minecraft.core.component.DataComponents.FOOD) != null ? 1f : 0f;

        // Nearest hostile, item and friendly — each as present / closeness / bearing.
        at = writeBearing(agent, nearestMonster(agent, level), o, at);
        at = writeBearing(agent, nearestItem(agent, level), o, at);
        at = writeBearing(agent, nearestFriendly(agent, level), o, at);

        // What the crosshair is actually on, within arm's reach — the difference between
        // "a tree is over there" and "I am touching a tree and could mine it".
        float[] cross = crosshair(agent, level);
        o[at++] = cross[0];
        o[at++] = cross[1];

        o[at++] = clamp01(agent.hurtTime / 10f);        // was I just hit?
        o[at++] = 1f;                                    // bias
    }

    /** Writes present / closeness / sin(bearing) / cos(bearing) for a target. */
    private static int writeBearing(LivingEntity agent, Entity target, float[] o, int at) {
        if (target == null) {
            o[at++] = 0f; o[at++] = 0f; o[at++] = 0f; o[at++] = 0f;
            return at;
        }
        double dx = target.getX() - agent.getX();
        double dz = target.getZ() - agent.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        // Bearing relative to where the agent is facing: 0 = dead ahead.
        double bearing = Math.toRadians(net.minecraft.util.Mth.wrapDegrees(
                Math.toDegrees(Math.atan2(-dx, dz)) - agent.getYRot()));
        o[at++] = 1f;
        o[at++] = (float) (1.0 / (1.0 + dist));
        o[at++] = (float) Math.sin(bearing);
        o[at++] = (float) Math.cos(bearing);
        return at;
    }

    private static Entity nearestMonster(LivingEntity agent, ServerLevel level) {
        return nearest(level, agent, e -> e instanceof Monster && e.isAlive());
    }

    private static Entity nearestItem(LivingEntity agent, ServerLevel level) {
        return nearest(level, agent, e -> e instanceof ItemEntity && e.isAlive());
    }

    /**
     * The nearest friendly humanoid: a companion's owner-ish anchor, or for a player the
     * nearest other person. Not identical in meaning between the two bodies, but "there
     * is someone friendly over there" is the signal the policy actually uses.
     */
    private static Entity nearestFriendly(LivingEntity agent, ServerLevel level) {
        if (agent instanceof com.milkdromeda.blockpal.entity.AiAssistantEntity bot) {
            Player owner = bot.getOwnerPlayer();
            if (owner != null && owner.distanceToSqr(bot) < AWARENESS * AWARENESS * 4) return owner;
        }
        return nearest(level, agent, e -> e instanceof Player && e.isAlive() && e != agent);
    }

    private static Entity nearest(ServerLevel level, LivingEntity agent,
                                  java.util.function.Predicate<Entity> filter) {
        try {
            AABB box = AABB.ofSize(agent.position(), AWARENESS * 2, AWARENESS, AWARENESS * 2);
            Entity best = null;
            double bestDist = Double.MAX_VALUE;
            for (Entity e : level.getEntities(agent, box, filter)) {
                double d = e.distanceToSqr(agent);
                if (d < bestDist) { bestDist = d; best = e; }
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    /** {closeness, is-solid} for whatever the crosshair is on within reach. */
    private static float[] crosshair(LivingEntity agent, ServerLevel level) {
        try {
            Vec3 eye = agent.getEyePosition();
            Vec3 end = eye.add(agent.getViewVector(1f).scale(5.0));
            BlockHitResult hit = level.clip(new ClipContext(eye, end,
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, agent));
            if (hit.getType() == HitResult.Type.MISS) return new float[]{0f, 0f};
            double dist = eye.distanceTo(hit.getLocation());
            return new float[]{(float) (1.0 / (1.0 + dist)), 1f};
        } catch (Exception e) {
            return new float[]{0f, 0f};
        }
    }

    private static String itemName(ItemStack stack) {
        try {
            return com.milkdromeda.blockpal.agent.BotApi.itemName(stack).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }

    private static float clamp01(double v) {
        return (float) Math.max(0, Math.min(1, v));
    }

    private static float clamp(double v, double lo, double hi) {
        return (float) Math.max(lo, Math.min(hi, v));
    }
}
