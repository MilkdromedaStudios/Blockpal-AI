package com.milkdromeda.blockpal.admin;

import com.milkdromeda.blockpal.config.ModConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;

/**
 * Single source of truth for "is this player allowed to administer Blockpal".
 *
 * <p>Admin powers (the admin menu, killing/managing all bots, and changing any
 * server-wide setting) require a permission level of at least
 * {@link ModConfig#adminPermissionLevel} (default {@code 2} = ops). This is what
 * stops a player with the mod from re-writing the server's API token, API URL or
 * command-permission tier from their client — those changes flow through here.
 *
 * <p>The level is configurable so a server whose trusted staff are op'd below the
 * full operator tier can still let them in: {@code /ai settings admin_level <0-4>}.
 */
public final class AdminAccess {

    private AdminAccess() {}

    public static int level() {
        return ModConfig.get().adminPermissionLevel;
    }

    public static boolean isAdmin(CommandSourceStack src) {
        return meetsLevel(src.permissions(), level());
    }

    public static boolean isAdmin(ServerPlayer player) {
        return meetsLevel(player.permissions(), level());
    }

    /**
     * MC 26.2 uses capability-based permissions instead of plain integer levels.
     * We map the configured vanilla op level (0-4) to the matching cumulative
     * "commands" permission tier and ask the player's {@link PermissionSet} for it.
     * Higher op levels include the lower command tiers, so this behaves like the
     * classic {@code hasPermission(level)} check. Level 0 means "everyone".
     */
    private static boolean meetsLevel(PermissionSet perms, int level) {
        return switch (Math.max(0, Math.min(4, level))) {
            case 0 -> true;
            case 1 -> perms.hasPermission(Permissions.COMMANDS_MODERATOR);
            case 2 -> perms.hasPermission(Permissions.COMMANDS_GAMEMASTER);
            case 3 -> perms.hasPermission(Permissions.COMMANDS_ADMIN);
            default -> perms.hasPermission(Permissions.COMMANDS_OWNER);
        };
    }
}
