package com.milkdromeda.blockpal.network;

import com.milkdromeda.blockpal.AiAssistantMod;
import com.milkdromeda.blockpal.EmergencyState;
import com.milkdromeda.blockpal.admin.AdminAccess;
import com.milkdromeda.blockpal.admin.PlayerStatsTracker;
import com.milkdromeda.blockpal.ai.Personality;
import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Locale;

/**
 * Wires up the client ⇄ server packets that back the in-game config and admin
 * menus. Payload types are registered on both sides (this runs from the common
 * mod initializer); the server-bound receivers are registered here too.
 *
 * <p><b>Security:</b> every server-bound packet that changes state re-checks the
 * sender's permission here. The client UI also hides admin controls from
 * non-admins, but that is only cosmetic — a modified client could forge any
 * packet, so the authoritative check lives on the server.
 */
public final class AiNetworking {

    private AiNetworking() {}

    /** Registers all payload types. Safe to call on client and server. */
    public static void registerPayloads() {
        PayloadTypeRegistry.serverboundPlay().register(ConfigRequestPayload.TYPE, ConfigRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ConfigUpdatePayload.TYPE, ConfigUpdatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(EmergencyDisablePayload.TYPE, EmergencyDisablePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AdminActionPayload.TYPE, AdminActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClientStatsPayload.TYPE, ClientStatsPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PlayerPrefsPayload.TYPE, PlayerPrefsPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BotListRequestPayload.TYPE, BotListRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BotActionPayload.TYPE, BotActionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ConfigSyncPayload.TYPE, ConfigSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AdminSyncPayload.TYPE, AdminSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PlayerPrefsSyncPayload.TYPE, PlayerPrefsSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenTutorialPayload.TYPE, OpenTutorialPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BotListSyncPayload.TYPE, BotListSyncPayload.CODEC);
    }

    /** Sends the current config to a player so their client opens the settings menu. */
    public static void openMenuFor(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, ConfigSyncPayload.TYPE)) {
            ServerPlayNetworking.send(player, new ConfigSyncPayload(ConfigData.fromConfig()));
        }
    }

    /** Sends a fresh admin snapshot so an admin's client opens the admin menu. */
    public static void openAdminMenuFor(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server != null && ServerPlayNetworking.canSend(player, AdminSyncPayload.TYPE)) {
            ServerPlayNetworking.send(player, new AdminSyncPayload(AdminStatsData.gather(server)));
        }
    }

    /** Opens the personal preferences screen for a player. @return false if their client can't show it. */
    public static boolean openPlayerMenuFor(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, PlayerPrefsSyncPayload.TYPE)) return false;
        ServerPlayNetworking.send(player, PlayerPrefsSyncPayload.forPlayer(player));
        return true;
    }

    /** Opens (or refreshes) the visual Bots manager panel for a player. @return false if their client can't show it. */
    public static boolean openBotsFor(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null || !ServerPlayNetworking.canSend(player, BotListSyncPayload.TYPE)) return false;
        ServerPlayNetworking.send(player, new BotListSyncPayload(BotListData.gather(server, player)));
        return true;
    }

    /** Opens the how-to tutorial screen for a player. @return false if their client can't show it. */
    public static boolean openTutorialFor(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, OpenTutorialPayload.TYPE)) return false;
        ServerPlayNetworking.send(player, new OpenTutorialPayload());
        return true;
    }

    /** Registers the handlers that run on the (integrated or dedicated) server. */
    public static void registerServerReceivers() {
        // A client asked for the current config — reply with a sync so it can open the menu.
        ServerPlayNetworking.registerGlobalReceiver(ConfigRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.level().getServer();
            if (server == null) return;
            server.execute(() ->
                    ServerPlayNetworking.send(player, new ConfigSyncPayload(ConfigData.fromConfig())));
        });

        // A client saved settings from the menu — these are server-wide, so only an
        // admin may apply them. A non-admin packet is refused (and the client is
        // re-synced with the real values). This is the main anti-exploit gate: it
        // stops a modified client rewriting the token, API URL or command perms.
        ServerPlayNetworking.registerGlobalReceiver(ConfigUpdatePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                if (!AdminAccess.isAdmin(player)) {
                    player.sendSystemMessage(Component.literal(
                            "§c[Blockpal] You don't have permission to change Blockpal's settings."));
                    AiAssistantMod.LOGGER.warn("Rejected config update from non-admin {} ({})",
                            player.getName().getString(), player.getUUID());
                    ServerPlayNetworking.send(player, new ConfigSyncPayload(ConfigData.fromConfig()));
                    return;
                }
                payload.data().applyTo(ModConfig.get());
                ModConfig.save();
                player.sendSystemMessage(Component.literal("§a[Blockpal] Settings saved ✓"));
            });
        });

        // The client's frame-rate guardian tripped (or cleared) the emergency kill switch.
        ServerPlayNetworking.registerGlobalReceiver(EmergencyDisablePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                boolean was = EmergencyState.isDisabled();
                EmergencyState.setDisabled(payload.disabled());
                if (payload.disabled() && !was) {
                    AiAssistantMod.LOGGER.info("Emergency disable tripped by {} at {} FPS",
                            player.getName().getString(), payload.fps());
                    server.getPlayerList().broadcastSystemMessage(Component.literal(
                            "§c[Blockpal] Frame-rate critically low (" + payload.fps()
                                    + " FPS) — bots auto-disabled to protect the game. "
                                    + "Run §e/ai resume§c once things recover."), false);
                } else if (!payload.disabled() && was) {
                    server.getPlayerList().broadcastSystemMessage(Component.literal(
                            "§a[Blockpal] Bots re-enabled."), false);
                }
            });
        });

        // Admin menu action button. Re-checked against the sender's permission.
        ServerPlayNetworking.registerGlobalReceiver(AdminActionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                if (!AdminAccess.isAdmin(player)) {
                    player.sendSystemMessage(Component.literal(
                            "§c[Blockpal] You don't have permission to use the admin menu."));
                    AiAssistantMod.LOGGER.warn("Rejected admin action '{}' from non-admin {} ({})",
                            payload.action(), player.getName().getString(), player.getUUID());
                    return;
                }
                boolean resync = handleAdminAction(server, player, payload);
                // Re-sync only for one-shot actions (kill all, enable/disable, refresh).
                // In-place setting toggles update their own widget, so re-opening the
                // screen for them would just reset the scroll position.
                if (resync && ServerPlayNetworking.canSend(player, AdminSyncPayload.TYPE)) {
                    ServerPlayNetworking.send(player, new AdminSyncPayload(AdminStatsData.gather(server)));
                }
            });
        });

        // Lightweight per-player client stats (FPS). No response; just record it.
        // ConcurrentHashMap-backed, so it's safe to write from the network thread.
        ServerPlayNetworking.registerGlobalReceiver(ClientStatsPayload.TYPE, (payload, context) ->
                PlayerStatsTracker.report(context.player().getUUID(), payload.fps()));

        // A player saved their personal preferences (model / own API key) from the
        // /ai mymenu screen. No admin check needed — it only ever affects the sender.
        ServerPlayNetworking.registerGlobalReceiver(PlayerPrefsPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                ModConfig cfg = ModConfig.get();
                if (cfg.allowPlayerModelChoice && payload.model() != null && !payload.model().isBlank()
                        && cfg.isModelAllowed(payload.model())) {
                    cfg.setPlayerModel(player.getUUID(), payload.model());
                }
                if (payload.clearKey()) {
                    cfg.setPlayerToken(player.getUUID(), "");
                } else if (payload.token() != null && !payload.token().isBlank()) {
                    cfg.setPlayerToken(player.getUUID(), payload.token());
                }
                ModConfig.save();

                // Personality applies to the player's nearest owned bot. A custom text
                // is safety-checked (async) by the bot before it's applied; a built-in
                // id is applied immediately. Both blank = a no-op refresh, so we skip.
                applyPersonality(player, payload);

                player.sendSystemMessage(Component.literal("§a[Blockpal] Saved your preferences ✓"));
                // Re-sync so the screen shows the saved state.
                if (ServerPlayNetworking.canSend(player, PlayerPrefsSyncPayload.TYPE)) {
                    ServerPlayNetworking.send(player, PlayerPrefsSyncPayload.forPlayer(player));
                }
            });
        });

        // A client opened/refreshed the Bots panel — reply with the current bot list.
        ServerPlayNetworking.registerGlobalReceiver(BotListRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.level().getServer();
            if (server == null) return;
            server.execute(() ->
                    ServerPlayNetworking.send(player, new BotListSyncPayload(BotListData.gather(server, player))));
        });

        // A client triggered an action on a specific bot from the Bots panel. The
        // sender's permission for THAT bot is re-checked here, then the panel is re-synced.
        ServerPlayNetworking.registerGlobalReceiver(BotActionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.level().getServer();
            if (server == null) return;
            server.execute(() -> {
                AiAssistantEntity bot = findBot(server, payload.entityId());
                if (bot == null) {
                    player.sendSystemMessage(Component.literal("§cThat companion is no longer available."));
                } else {
                    applyBotAction(player, bot, payload.action(), payload.arg());
                }
                if (ServerPlayNetworking.canSend(player, BotListSyncPayload.TYPE)) {
                    ServerPlayNetworking.send(player, new BotListSyncPayload(BotListData.gather(server, player)));
                }
            });
        });
    }

    /** Finds a live bot by its network id across every dimension, or null. */
    private static AiAssistantEntity findBot(MinecraftServer server, int entityId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(entityId);
            if (e instanceof AiAssistantEntity ai && ai.isAlive()) return ai;
        }
        return null;
    }

    /** Applies a Bots-panel action after re-checking the sender's permission for this bot. */
    private static void applyBotAction(ServerPlayer player, AiAssistantEntity bot, String action, String arg) {
        String a = action == null ? "" : action.toLowerCase(Locale.ROOT);
        boolean admin = AdminAccess.isAdmin(player);
        boolean canCommand = bot.canCommand(player) || admin;
        boolean canManage = bot.isOwner(player) || admin;
        String name = bot.getAssistantName();
        switch (a) {
            case "come" -> {
                if (!canCommand) { denyCommand(player); return; }
                if (bot.level() == player.level()) {
                    bot.comeTo(player);
                } else {
                    player.sendSystemMessage(Component.literal(
                            "§e" + name + " is in another dimension — go there or use /ai come."));
                }
            }
            case "follow" -> { if (!canCommand) { denyCommand(player); return; } bot.followPlayer(); }
            case "stay"   -> { if (!canCommand) { denyCommand(player); return; } bot.stayHere(); }
            case "stop"   -> { if (!canCommand) { denyCommand(player); return; } bot.stopTask(); }
            case "dismiss" -> {
                if (!canManage) { denyManage(player, bot); return; }
                bot.discard();
                player.sendSystemMessage(Component.literal("§7Dismissed " + name + "."));
            }
            case "rename" -> {
                if (!canManage) { denyManage(player, bot); return; }
                String nm = arg == null ? "" : arg.trim();
                if (nm.isEmpty()) return;
                if (nm.length() > 32) nm = nm.substring(0, 32);
                bot.setAssistantName(nm);
                player.sendSystemMessage(Component.literal("§aRenamed §f" + name + " §a→ §f" + nm));
            }
            case "skin" -> {
                if (!canManage) { denyManage(player, bot); return; }
                bot.setSkin(arg);
                player.sendSystemMessage(Component.literal("§a" + name + "'s skin set to §f" + bot.getSkin()));
            }
            case "personality" -> {
                if (!canManage) { denyManage(player, bot); return; }
                Personality p = Personality.byId(arg);
                if (p == null) return;
                bot.setPersonality(p);
                player.sendSystemMessage(Component.literal("§a" + name + " is now §f" + p.display()));
            }
            default -> { /* unknown action — ignore */ }
        }
    }

    private static void denyCommand(ServerPlayer player) {
        player.sendSystemMessage(Component.literal(
                "§cYou're not allowed to command that companion. Its owner can /ai trust you."));
    }

    private static void denyManage(ServerPlayer player, AiAssistantEntity bot) {
        String owner = bot.getOwnerName().isBlank() ? "its owner" : bot.getOwnerName();
        player.sendSystemMessage(Component.literal(
                "§cOnly " + owner + " can manage " + bot.getAssistantName() + "."));
    }

    /** Applies a personality choice from the My Settings panel to the player's nearby bot. */
    private static void applyPersonality(ServerPlayer player, PlayerPrefsPayload payload) {
        String pid = payload.personality();
        String custom = payload.customPersonality();
        boolean wantsBuiltin = pid != null && !pid.isBlank();
        boolean wantsCustom = custom != null && !custom.isBlank();
        if (!wantsBuiltin && !wantsCustom) return;   // panel-switch no-op

        AiAssistantEntity bot = AiAssistantEntity.findFor(player, 256);
        if (bot == null) {
            player.sendSystemMessage(Component.literal(
                    "§eStand near your companion to change its personality."));
            return;
        }
        if (wantsCustom) {
            bot.requestCustomPersonality(custom, player);   // async safety check, then applies
        } else {
            com.milkdromeda.blockpal.ai.Personality p =
                    com.milkdromeda.blockpal.ai.Personality.byId(pid);
            if (p != null) bot.setPersonality(p);
        }
    }

    /** Performs an admin action. @return true if the menu should be re-synced afterwards. */
    private static boolean handleAdminAction(MinecraftServer server, ServerPlayer who, AdminActionPayload payload) {
        String action = payload.action() == null ? "" : payload.action().toLowerCase(Locale.ROOT);
        ModConfig cfg = ModConfig.get();
        int value = payload.value();
        switch (action) {
            // ── one-shot actions (broadcast + re-sync the panel) ──
            case "killall" -> {
                int n = AiAssistantEntity.killAll(server);
                AiAssistantMod.LOGGER.info("[Admin] {} removed all bots ({})", who.getName().getString(), n);
                server.getPlayerList().broadcastSystemMessage(Component.literal(
                        "§c[Blockpal] An admin removed all bots (" + n + ")."), false);
                return true;
            }
            case "disable" -> {
                EmergencyState.setDisabled(true);
                AiAssistantMod.LOGGER.info("[Admin] {} disabled Blockpal", who.getName().getString());
                server.getPlayerList().broadcastSystemMessage(Component.literal(
                        "§c[Blockpal] Bots disabled by an admin. Use §e/ai resume§c to re-enable."), false);
                return true;
            }
            case "enable" -> {
                EmergencyState.setDisabled(false);
                AiAssistantMod.LOGGER.info("[Admin] {} re-enabled Blockpal", who.getName().getString());
                server.getPlayerList().broadcastSystemMessage(Component.literal(
                        "§a[Blockpal] Bots re-enabled by an admin."), false);
                return true;
            }
            case "refresh" -> { return true; }

            // ── in-place setting toggles (saved silently; widget self-updates) ──
            case "maxbots" -> {
                cfg.maxBotsPerServer = Math.max(0, Math.min(50, value));
                ModConfig.save();
                AiAssistantMod.LOGGER.info("[Admin] {} set max bots = {}", who.getName().getString(), cfg.maxBotsPerServer);
                return false;
            }
            case "adminlevel" -> {
                cfg.adminPermissionLevel = Math.max(0, Math.min(4, value));
                ModConfig.save();
                return false;
            }
            case "commandlevel" -> {
                cfg.commandPermissionLevel = Math.max(0, Math.min(4, value));
                ModConfig.save();
                return false;
            }
            case "allowcommands" -> {
                cfg.allowCommands = value != 0;
                ModConfig.save();
                return false;
            }
            case "requirekey" -> {
                cfg.requireOwnApiKey = value != 0;
                ModConfig.save();
                return false;
            }
            case "modelchoice" -> {
                cfg.allowPlayerModelChoice = value != 0;
                ModConfig.save();
                return false;
            }
            default -> {
                AiAssistantMod.LOGGER.warn("Unknown admin action '{}' from {}", action, who.getName().getString());
                return false;
            }
        }
    }
}
