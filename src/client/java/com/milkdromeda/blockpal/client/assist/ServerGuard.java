package com.milkdromeda.blockpal.client.assist;

import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.network.PossessionInputPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.util.List;
import java.util.Locale;

/**
 * Decides, on the client, where the AI is allowed to actually <b>drive the player</b>
 * (client-side possession). This is the safety brain that keeps the mod from getting
 * anyone banned.
 *
 * <p>The always-safe features — the private advice chat box and on-screen tips — never
 * touch the world and are allowed everywhere, so they aren't gated here. Only
 * <i>input automation</i> (walking/mining you) is, because many servers forbid
 * automation in their rules. The rules we bake in:
 *
 * <ul>
 *   <li><b>Singleplayer / your own LAN world</b> — always fine, it's your world.</li>
 *   <li><b>A server that runs Blockpal</b> — it handles possession itself
 *       (server-authoritative), so the client driver stands down.</li>
 *   <li><b>A known no-automation network</b> (Hypixel and friends) — hard-blocked,
 *       no matter the setting, because automation there is a bannable offence.</li>
 *   <li><b>Any other third-party server</b> — allowed only if the player has opted in
 *       ({@code allowClientPossession}), and always with an up-front warning that
 *       automation may be against that server's rules.</li>
 * </ul>
 */
public final class ServerGuard {

    private ServerGuard() {}

    /** Verdict for whether the AI may drive the local player right now. */
    public enum Driving {
        /** Fine to drive (your own world, or an opted-in third-party server). */
        ALLOWED,
        /** Fine, but the player should be warned it may break the server's rules. */
        ALLOWED_WITH_WARNING,
        /** This server runs Blockpal — use server-side {@code /ai possess} instead. */
        SERVER_HANDLES,
        /** A network that bans automation (e.g. Hypixel) — refused outright. */
        BLOCKED_ANTICHEAT,
        /** The player turned client-side possession off. */
        DISABLED
    }

    /**
     * Substrings of server addresses where in-client automation is a bannable offence.
     * Matching is conservative (substring, case-insensitive) and intentionally errs
     * toward blocking — the whole point is "never get banned, especially on Hypixel".
     */
    private static final List<String> ANTICHEAT_NETWORKS = List.of(
            "hypixel.net", "hypixel", "mineplex", "cubecraft", "cubecraftgames",
            "hivemc", "playhive", "the-hive", "wynncraft", "mccentral", "minemen",
            "pika-network", "pika-network.net", "gommehd", "manacube", "2b2t",
            "opblocks", "lunar.gg", "lunarnetwork", "grandtheftmc", "mcprison",
            "arkoo", "loverfella", "vanitymc", "purpleprison", "complex-gaming",
            "extremecraft", "blocksmc", "herobrine.org", "jartex", "origin realms");

    /** True when the current server also runs Blockpal (its possession channel is open). */
    public static boolean serverHasBlockpal() {
        try {
            return ClientPlayNetworking.canSend(PossessionInputPayload.TYPE);
        } catch (Exception e) {
            return false;
        }
    }

    /** True in singleplayer, or when you are the host of a LAN world (your own world). */
    public static boolean isOwnWorld() {
        Minecraft mc = Minecraft.getInstance();
        return mc.hasSingleplayerServer();
    }

    /** The address of the multiplayer server you're on, lowercased ("" in singleplayer). */
    public static String serverAddress() {
        ServerData sd = Minecraft.getInstance().getCurrentServer();
        return sd == null || sd.ip == null ? "" : sd.ip.toLowerCase(Locale.ROOT);
    }

    /** True when the current server is a known no-automation / anti-cheat network. */
    public static boolean isAnticheatNetwork() {
        String ip = serverAddress();
        if (ip.isEmpty()) return false;
        for (String bad : ANTICHEAT_NETWORKS) {
            if (ip.contains(bad)) return true;
        }
        return false;
    }

    /** The full verdict for driving the local player right now. */
    public static Driving driving() {
        if (isOwnWorld()) {
            return ModConfig.get().allowClientPossession ? Driving.ALLOWED : Driving.DISABLED;
        }
        if (isAnticheatNetwork()) return Driving.BLOCKED_ANTICHEAT;
        if (serverHasBlockpal()) return Driving.SERVER_HANDLES;
        if (!ModConfig.get().allowClientPossession) return Driving.DISABLED;
        return Driving.ALLOWED_WITH_WARNING;
    }

    /** A short, player-facing explanation for a verdict. */
    public static String explain(Driving d) {
        return switch (d) {
            case ALLOWED -> "§aReady — this is your own world.";
            case ALLOWED_WITH_WARNING -> "§e⚠ This server doesn't run Blockpal. Driving simulates your "
                    + "inputs — automation may be against its rules. Use at your own risk.";
            case SERVER_HANDLES -> "§bThis server runs Blockpal — use §f/ai possess§b instead "
                    + "(fully server-side, no input automation).";
            case BLOCKED_ANTICHEAT -> "§cBlocked here. §7\"" + serverAddress() + "\" is a network that bans "
                    + "automation (e.g. Hypixel). The AI will not drive you — but the advice chat still works.";
            case DISABLED -> "§7Client-side possession is turned off (enable it in the AI settings).";
        };
    }
}
