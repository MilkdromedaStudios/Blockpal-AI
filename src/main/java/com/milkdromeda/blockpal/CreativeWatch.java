package com.milkdromeda.blockpal;

import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import com.milkdromeda.blockpal.network.AiNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>"Your companion can't follow you up there."</b>
 *
 * <p>Since 3.25.0 a Blockpal companion never teleports — not to its owner, not when it
 * falls behind, not ever. That is the right call for immersion, but it has one obvious
 * consequence a player deserves to be told about rather than discover: the moment you
 * switch to <b>creative</b> and fly off, the walking companion you left behind is a
 * walking companion you left behind.
 *
 * <p>So the first time a player enters creative (or spectator) with a bot out, they get a
 * warning — a proper screen on a Java client, a chat notice on Bedrock/vanilla. It is
 * shown once per switch into creative, not once per tick, and never at all if they turn
 * {@code creativeModeWarning} off.
 */
public final class CreativeWatch {

    private CreativeWatch() {}

    /** Players currently in a flying game mode who have already been told. */
    private static final Set<UUID> WARNED = ConcurrentHashMap.newKeySet();

    /** Checked a couple of times a second — cheap, and a game-mode switch is rare. */
    private static final int CHECK_INTERVAL = 20;

    private static int counter;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++counter < CHECK_INTERVAL) return;
            counter = 0;
            if (!ModConfig.get().creativeModeWarning) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                boolean flying = player.isCreative() || player.isSpectator();
                if (!flying) {
                    WARNED.remove(player.getUUID());     // re-arm for the next switch
                    continue;
                }
                if (WARNED.contains(player.getUUID())) continue;
                // Only worth saying if they actually have a companion out.
                if (AiAssistantEntity.countOwnedBy(server, player.getUUID()) == 0) continue;
                WARNED.add(player.getUUID());
                warn(player);
            }
        });
    }

    private static void warn(ServerPlayer player) {
        if (AiNetworking.openCreativeWarningFor(player)) return;   // Java client: a real screen
        player.sendSystemMessage(Component.literal(
                "§e[Blockpal] §fYou're in creative and your companion is still on foot.\n"
                        + "§7Companions never teleport — they walk, swim and climb like you do. If you fly "
                        + "off, yours will be left behind and will keep walking toward you.\n"
                        + "§7Use §f/ai come§7 when you land, or §f/ai stay§7 to park it somewhere safe."));
    }

    /** A player who logs out stops being "already warned". */
    public static void handleDisconnect(ServerPlayer player) {
        if (player != null) WARNED.remove(player.getUUID());
    }
}
