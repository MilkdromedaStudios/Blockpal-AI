package com.milkdromeda.blockpal.combat;

import com.milkdromeda.blockpal.agent.BotInput;
import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * <b>How the companion actually fights.</b>
 *
 * <p>The mod already had a combat <i>reflex</i> — notice a monster, walk at it, swing —
 * which is the behaviour of something that has never been in a fight. This drives the
 * same keys and mouse a player uses, with the habits a player has: hold a range rather
 * than standing inside the enemy's swing, circle instead of trading blows head-on, put a
 * shield up between your own swings, jump before striking so the hit lands as a crit, and
 * leave when you are losing.
 *
 * <p>Everything goes through {@link BotInput}, so none of it cheats: the swing still has
 * to reach, the shield still has to be in the off hand, the bow still has to be drawn.
 *
 * <p>It fights <b>monsters</b> by default and always has. Fighting a <b>person</b> is
 * gated entirely by {@link PvpRules} — off unless a server operator turns it on, and even
 * then only against someone who started it.
 */
public final class CombatBrain {

    /** Re-pick a target at most this often — target scanning is the expensive part. */
    private static final int RETARGET_INTERVAL = 10;
    /** Ticks between two swings; roughly a sword's cooldown. */
    private static final int SWING_INTERVAL = 12;
    /** Beyond this the target is forgotten. */
    private static final double DISENGAGE_RANGE = 24.0;
    /** How close a melee swing has to be to land. */
    private static final double MELEE_RANGE = 3.4;

    private LivingEntity target;
    private int retargetIn;
    private int swingIn;
    private int strafeDirection = 1;
    private int strafeFlipIn;
    private int drawTicks;
    private int equipIn;
    private String lastNote = "";

    public LivingEntity target() { return target; }
    public String lastNote() { return lastNote; }
    public boolean isFighting() { return target != null && target.isAlive(); }

    /**
     * Fights for one tick.
     *
     * @return true when combat took the controls this tick
     */
    public boolean tick(AiAssistantEntity bot, ServerLevel level, BotInput input) {
        if (swingIn > 0) swingIn--;
        if (strafeFlipIn > 0) strafeFlipIn--;
        if (equipIn > 0) equipIn--;

        if (--retargetIn <= 0) {
            retargetIn = RETARGET_INTERVAL;
            target = pickTarget(bot, level);
        }
        if (target == null || !target.isAlive()) {
            target = null;
            return false;
        }

        double dist = Math.sqrt(bot.distanceToSqr(target));
        if (dist > DISENGAGE_RANGE) {
            target = null;
            return false;
        }

        CombatSkill skill = CombatSkill.current();

        // Keep a weapon in hand — but not every tick; scanning the backpack is not free.
        if (equipIn <= 0) {
            equipIn = 40;
            bot.optimizeEquipment(level);
        }

        aimAt(bot, input, target);

        if (bot.getHealth() / Math.max(1f, bot.getMaxHealth()) < skill.retreatBelow()) {
            return retreat(bot, level, input, dist);
        }

        if (skill.crits() && dist > 6.0 && drawBow(bot, level, input, dist)) return true;
        stopBow(bot);

        return melee(bot, level, input, skill, dist);
    }

    // ── target selection ────────────────────────────────────────────────────────

    /**
     * Who to fight. Monsters that are already the entity's target come first (the
     * survival reflex sets that), then anyone who has provoked us.
     */
    private LivingEntity pickTarget(AiAssistantEntity bot, ServerLevel level) {
        LivingEntity current = bot.getTarget();
        if (current != null && current.isAlive() && allowed(bot, current)) return current;

        // A person who just hit us or our owner outranks a wandering zombie.
        Player aggressor = findAggressor(bot, level);
        if (aggressor != null) return aggressor;

        AABB box = AABB.ofSize(bot.position(), 24, 12, 24);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Monster m : level.getEntitiesOfClass(Monster.class, box, LivingEntity::isAlive)) {
            double d = m.distanceToSqr(bot);
            if (d < bestDist) { bestDist = d; best = m; }
        }
        return best;
    }

    /** The nearest player this bot is actually permitted to fight back against. */
    private Player findAggressor(AiAssistantEntity bot, ServerLevel level) {
        if (!com.milkdromeda.blockpal.config.ModConfig.get().allowPvp) return null;
        AABB box = AABB.ofSize(bot.position(), 32, 16, 32);
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : level.getEntitiesOfClass(Player.class, box, LivingEntity::isAlive)) {
            if (!PvpRules.mayAttack(bot, p)) continue;
            double d = p.distanceToSqr(bot);
            if (d < bestDist) { bestDist = d; best = p; }
        }
        return best;
    }

    private boolean allowed(AiAssistantEntity bot, LivingEntity candidate) {
        return !(candidate instanceof Player p) || PvpRules.mayAttack(bot, p);
    }

    // ── melee ───────────────────────────────────────────────────────────────────

    private boolean melee(AiAssistantEntity bot, ServerLevel level, BotInput input,
                          CombatSkill skill, double dist) {
        double preferred = skill.preferredRange();

        if (dist > preferred + 0.6) {
            // Close the gap.
            input.setForward(1f);
            input.setSprint(dist > 6);
            input.setStrafe(0);
        } else if (dist < preferred - 1.0) {
            // Too close to swing properly — give ground.
            input.setForward(-0.6f);
            input.setSprint(false);
        } else {
            input.setForward(0);
            input.setSprint(false);
        }

        if (skill.strafes() && dist < preferred + 2.5) {
            // Circle. Changing direction on an irregular beat is what makes a fighter
            // hard to hit; a constant orbit is just a predictable target.
            if (strafeFlipIn <= 0) {
                strafeFlipIn = 20 + bot.getRandom().nextInt(30);
                strafeDirection = -strafeDirection;
            }
            input.setStrafe(strafeDirection * 0.85f);
        }

        boolean canReach = dist <= MELEE_RANGE;
        if (canReach && swingIn <= 0) {
            swingIn = SWING_INTERVAL;
            // A crit needs to land while falling, so jump first and strike on the way down.
            if (skill.crits() && bot.onGround() && bot.getRandom().nextInt(3) == 0) {
                input.setJump(true);
                lastNote = "going for a crit";
            }
            input.setAttack(true);
            lowerShield(bot);
        } else {
            input.setAttack(false);
            // Between swings, get behind the shield rather than standing there empty.
            if (skill.blocks() && dist < preferred + 1.5) raiseShield(bot);
        }
        return true;
    }

    // ── shield ──────────────────────────────────────────────────────────────────

    private void raiseShield(AiAssistantEntity bot) {
        try {
            ItemStack off = bot.getItemBySlot(EquipmentSlot.OFFHAND);
            if (!(off.getItem() instanceof ShieldItem)) return;
            if (bot.isUsingItem()) return;
            bot.startUsingItem(InteractionHand.OFF_HAND);
            lastNote = "shield up";
        } catch (Exception ignored) {
            // A shield that won't raise simply means taking the hit.
        }
    }

    private void lowerShield(AiAssistantEntity bot) {
        try {
            if (bot.isUsingItem()) bot.stopUsingItem();
        } catch (Exception ignored) {
            // Nothing to do.
        }
    }

    // ── bow ─────────────────────────────────────────────────────────────────────

    /**
     * Draws and looses a bow. Returns false when there is no bow to use, so the caller
     * falls through to melee.
     */
    private boolean drawBow(AiAssistantEntity bot, ServerLevel level, BotInput input, double dist) {
        try {
            ItemStack main = bot.getItemBySlot(EquipmentSlot.MAINHAND);
            boolean isBow = main.getItem() instanceof BowItem || main.getItem() instanceof CrossbowItem;
            if (!isBow && !bot.holdItemMatching("bow")) return false;
            main = bot.getItemBySlot(EquipmentSlot.MAINHAND);
            if (!(main.getItem() instanceof BowItem) && !(main.getItem() instanceof CrossbowItem)) {
                return false;
            }
            if (!hasArrows(bot)) return false;

            input.setForward(0);
            input.setStrafe(0);
            // Aim above the target: an arrow drops on the way, and more so further out.
            aimAhead(bot, input, dist);

            if (!bot.isUsingItem()) {
                bot.startUsingItem(InteractionHand.MAIN_HAND);
                drawTicks = 0;
                lastNote = "drawing";
                return true;
            }
            // A full draw is 20 ticks; anything less is a weak shot not worth taking.
            if (++drawTicks >= 20) {
                releaseBow(bot, level, main);
                drawTicks = 0;
                lastNote = "loosed an arrow";
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void releaseBow(AiAssistantEntity bot, ServerLevel level, ItemStack bow) {
        try {
            if (bow.getItem() instanceof BowItem b) {
                b.releaseUsing(bow, level, bot, 0);
            }
            bot.stopUsingItem();
        } catch (Exception ignored) {
            // If the release path misbehaves, drop back to melee next tick.
        }
    }

    private void stopBow(AiAssistantEntity bot) {
        drawTicks = 0;
    }

    private boolean hasArrows(AiAssistantEntity bot) {
        try {
            net.minecraft.world.SimpleContainer pack = bot.getInventory();
            for (int i = 0; i < pack.getContainerSize(); i++) {
                if (com.milkdromeda.blockpal.agent.BotApi.matches(pack.getItem(i), "arrow")) return true;
            }
        } catch (Exception ignored) {
            // No readable inventory: assume none.
        }
        return false;
    }

    // ── retreat ─────────────────────────────────────────────────────────────────

    /** Badly hurt: back away facing the threat, eat if there's anything to eat. */
    private boolean retreat(AiAssistantEntity bot, ServerLevel level, BotInput input, double dist) {
        lastNote = "falling back";
        input.setAttack(false);
        input.setForward(dist < 8 ? -1f : 0f);
        input.setStrafe(0);
        input.setSprint(false);
        raiseShield(bot);
        if (bot.getRandom().nextInt(40) == 0) bot.consumeBestFood(level);
        return true;
    }

    // ── aiming ──────────────────────────────────────────────────────────────────

    private void aimAt(AiAssistantEntity bot, BotInput input, LivingEntity at) {
        Vec3 eye = bot.getEyePosition();
        Vec3 to = at.getEyePosition();
        point(bot, input, eye, to.x, to.y, to.z);
    }

    /** Aims high to allow for arrow drop over the distance. */
    private void aimAhead(AiAssistantEntity bot, BotInput input, double dist) {
        Vec3 eye = bot.getEyePosition();
        Vec3 to = target.getEyePosition();
        point(bot, input, eye, to.x, to.y + dist * 0.09, to.z);
    }

    private void point(AiAssistantEntity bot, BotInput input, Vec3 eye, double x, double y, double z) {
        double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
        input.aim(yaw, pitch);
    }

    /** Drops the fight and everything held. */
    public void disengage(AiAssistantEntity bot, BotInput input) {
        target = null;
        drawTicks = 0;
        lowerShield(bot);
        input.releaseAll();
    }

    /** A short line for status readouts. */
    public String describe() {
        if (target == null) return "not fighting";
        return "fighting " + target.getName().getString()
                + (lastNote.isEmpty() ? "" : " — " + lastNote);
    }
}
