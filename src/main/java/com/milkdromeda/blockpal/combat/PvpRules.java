package com.milkdromeda.blockpal.combat;

import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * <b>The one place that decides whether a companion may raise its hand to a person.</b>
 *
 * <p>Until now the answer was a flat no, enforced by a filter in the input driver: a bot
 * that swings at people is a griefing tool, and "my friend's mod hit me" is not a bug
 * report anyone wants. Making it possible at all needs the permission to be narrow,
 * checked in exactly one place, and hard to turn on by accident — so this class exists
 * rather than an {@code instanceof Player} check moving somewhere else.
 *
 * <p>Every one of these must hold before a swing is allowed:
 * <ul>
 *   <li>{@code allowPvp} is on. It is off by default, ops-only, and an upgrade never
 *       turns it on.</li>
 *   <li>The target is not the bot's owner, and not anybody its owner trusts.</li>
 *   <li>The target is playing — not in creative, not spectating, not invulnerable.</li>
 *   <li>And the bot was <b>provoked</b>: that player has hurt it or its owner in the last
 *       ten seconds, or the owner explicitly named them with {@code /ai attack}.</li>
 * </ul>
 *
 * <p>So there is no configuration in which a companion picks a fight. It defends, or it
 * does what its owner directly told it to do about a specific person.
 *
 * <p><b>Vanilla still has the final say.</b> A server with PvP switched off refuses
 * player-to-player damage in {@code Player}'s own hurt path regardless of anything here,
 * so a bot on such a server can swing and nothing will land.
 */
public final class PvpRules {

    private PvpRules() {}

    /** How long after being hit a player still counts as an aggressor, in ticks. */
    public static final int PROVOCATION_TICKS = 200;   // 10 seconds

    /**
     * May {@code bot} attack {@code target}? The only question the input driver asks
     * before it lets a swing reach a person.
     */
    public static boolean mayAttack(AiAssistantEntity bot, LivingEntity target) {
        if (!(target instanceof Player player)) return true;      // mobs are unchanged
        if (bot == null) return false;
        if (!ModConfig.get().allowPvp) return false;

        // Never the people it belongs to.
        if (bot.isOwnedBy(player)) return false;
        if (bot.isTrusted(player.getUUID())) return false;

        // Never someone who isn't really playing.
        if (player.isSpectator() || player.isCreative()) return false;
        try {
            if (player.getAbilities().invulnerable) return false;
        } catch (Exception ignored) {
            // Unreadable abilities: treat as ordinary and fall through to provocation.
        }
        if (!player.isAlive()) return false;

        return isProvokedBy(bot, player);
    }

    /**
     * Has this player given the bot a reason? Either they hit it, they hit its owner, or
     * the owner named them.
     */
    public static boolean isProvokedBy(AiAssistantEntity bot, Player player) {
        if (player.getUUID().equals(bot.getCombatOrder())) return true;
        if (recentlyHurtBy(bot, player)) return true;
        Player owner = bot.getOwnerPlayer();
        return owner != null && recentlyHurtBy(owner, player);
    }

    /** True when {@code attacker} hurt {@code victim} within the provocation window. */
    public static boolean recentlyHurtBy(LivingEntity victim, Player attacker) {
        try {
            LivingEntity last = victim.getLastHurtByMob();
            if (last == null || !last.getUUID().equals(attacker.getUUID())) return false;
            int age = victim.tickCount - victim.getLastHurtByMobTimestamp();
            return age >= 0 && age <= PROVOCATION_TICKS;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Why a bot won't fight someone, in words — so {@code /ai attack} can explain itself
     * instead of silently doing nothing.
     *
     * @return "" when the attack is allowed
     */
    public static String refusalReason(AiAssistantEntity bot, Player target) {
        if (!ModConfig.get().allowPvp) {
            return "fighting players is switched off on this server (an operator can turn "
                    + "it on with /ai admin pvp on).";
        }
        if (bot.isOwnedBy(target)) return "that's my owner.";
        if (bot.isTrusted(target.getUUID())) return "I trust them — they're on my list.";
        if (target.isSpectator()) return "they're spectating.";
        if (target.isCreative()) return "they're in creative mode.";
        if (!target.isAlive()) return "they're not alive.";
        return "";
    }
}
