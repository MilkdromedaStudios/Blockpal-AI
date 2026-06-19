package com.milkdromeda.blockpal.command;

import com.milkdromeda.blockpal.ModEntities;
import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import com.milkdromeda.blockpal.network.ConfigData;
import com.milkdromeda.blockpal.network.ConfigSyncPayload;
import com.milkdromeda.blockpal.util.Locator;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;

public class AiCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
                dispatcher.register(Commands.literal("ai")
                        .requires(src -> true)
                        .executes(AiCommands::help)

                        // ── friendly everyday commands ───────────────────────────────
                        .then(Commands.literal("help").executes(AiCommands::help))

                        .then(Commands.literal("summon")
                                .executes(ctx -> summon(ctx, ModConfig.get().defaultName))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> summon(ctx, StringArgumentType.getString(ctx, "name")))))

                        .then(Commands.literal("dismiss").executes(AiCommands::dismiss))

                        // movement commands accept optional trailing text ("follow me")
                        .then(actionCommand("come",   AiCommands::come))
                        .then(actionCommand("follow", AiCommands::follow))
                        .then(actionCommand("stay",   AiCommands::stay))

                        .then(Commands.literal("stop").executes(AiCommands::stop))

                        .then(Commands.literal("resume").executes(AiCommands::resume))
                        .then(Commands.literal("enable").executes(AiCommands::resume))

                        .then(Commands.literal("locate").executes(AiCommands::locate))
                        .then(Commands.literal("where").executes(AiCommands::locate))

                        .then(Commands.literal("inventory").executes(AiCommands::inventory))
                        .then(Commands.literal("inv").executes(AiCommands::inventory))

                        .then(Commands.literal("name")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> rename(ctx, StringArgumentType.getString(ctx, "name")))))

                        .then(Commands.literal("skin")
                                .then(Commands.argument("skin", StringArgumentType.greedyString())
                                        .executes(ctx -> setSkin(ctx, StringArgumentType.getString(ctx, "skin")))))

                        .then(Commands.literal("token")
                                .then(Commands.argument("token", StringArgumentType.greedyString())
                                        .executes(ctx -> setToken(ctx, StringArgumentType.getString(ctx, "token")))))

                        .then(Commands.literal("listen")
                                .executes(AiCommands::showListen)
                                .then(Commands.literal("on").executes(ctx -> setListen(ctx, true)))
                                .then(Commands.literal("off").executes(ctx -> setListen(ctx, false))))

                        .then(Commands.literal("active")
                                .executes(AiCommands::showActive)
                                .then(Commands.literal("on").executes(ctx -> setActive(ctx, true)))
                                .then(Commands.literal("off").executes(ctx -> setActive(ctx, false))))

                        .then(Commands.literal("commands")
                                .executes(AiCommands::showCommandsSetting)
                                .then(Commands.literal("on").executes(ctx -> setAllowCommands(ctx, true)))
                                .then(Commands.literal("off").executes(ctx -> setAllowCommands(ctx, false))))

                        // open the real settings screen (client must have the mod)
                        .then(Commands.literal("menu").executes(AiCommands::openMenu))
                        .then(Commands.literal("config").executes(AiCommands::openMenu))

                        // ── advanced settings ────────────────────────────────────────
                        // One generic setter covers every config value (tab-complete the
                        // key) so the command surface stays small. /ai settings alone lists
                        // the current values.
                        .then(Commands.literal("settings")
                                .executes(AiCommands::showSettings)
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(SETTING_KEYS_SUGGEST)
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(ctx -> applySetting(ctx,
                                                        StringArgumentType.getString(ctx, "key"),
                                                        StringArgumentType.getString(ctx, "value"))))))

                        // ── /ai <task> — natural language, must be last (greedy) ──────
                        .then(Commands.argument("task", StringArgumentType.greedyString())
                                .executes(ctx -> doTask(ctx, StringArgumentType.getString(ctx, "task"))))
                )
        );
    }

    /** A literal action command that also accepts (and ignores) trailing text like "me". */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> actionCommand(
            String literal, java.util.function.ToIntFunction<CommandContext<CommandSourceStack>> handler) {
        return Commands.literal(literal)
                .executes(handler::applyAsInt)
                .then(Commands.argument("rest", StringArgumentType.greedyString())
                        .executes(handler::applyAsInt));
    }

    // ── help ───────────────────────────────────────────────────────────────────

    private static int help(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        player.sendSystemMessage(Component.literal(
                "§6=== Your Blockpal ===\n" +
                "§eJust talk in chat (no slash, no exact words needed):\n" +
                "§7  \"follow me\"   \"come here\"   \"stay\"   \"stop\"   \"where are you\"\n" +
                "§7  \"clear these trees\"   \"build a redstone door\"   \"solve this puzzle\"\n" +
                "§7It fights back while it thinks, runs commands, and keeps going on patrols.\n" +
                "§6\n" +
                "§eCommands:\n" +
                "§f/ai menu §7— open the settings screen\n" +
                "§f/ai summon [name] §7— bring a new assistant into the world\n" +
                "§f/ai skin <name> §7— give it a skin (built-in, or your own PNG; see /aiskins)\n" +
                "§f/ai come §7— call it over to you\n" +
                "§f/ai follow §7— have it follow you\n" +
                "§f/ai stay §7— hold position and keep watch\n" +
                "§f/ai stop §7— cancel what it's doing\n" +
                "§f/ai locate §7— find where it is\n" +
                "§f/ai inventory §7— see what it's carrying and wearing\n" +
                "§f/ai <task> §7— tell it what to do (e.g. /ai build a 5x5 floor)\n" +
                "§f/ai name <name> §7— rename it\n" +
                "§f/ai token <token> §7— set your AI service token\n" +
                "§f/ai listen on|off §7— toggle chat listening\n" +
                "§f/ai active on|off §7— toggle proactive analysis of every message\n" +
                "§f/ai commands on|off §7— let it run commands (/setblock, /fill, redstone…)\n" +
                "§f/ai dismiss §7— send it away\n" +
                "§f/ai settings §7— list settings; §f/ai settings <key> <value>§7 changes any one"
        ));
        return 1;
    }

    // ── summon / dismiss ────────────────────────────────────────────────────────

    private static int summon(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        ServerLevel level = player.level();
        AiAssistantEntity entity = ModEntities.AI_ASSISTANT.create(level, EntitySpawnReason.COMMAND);
        if (entity == null) return 0;

        entity.setAssistantName(name);
        entity.setSkin(ModConfig.get().defaultSkin);
        entity.setOwnerUuid(player.getUUID());
        entity.setPos(player.getX() + 1.5, player.getY(), player.getZ());
        entity.setMode(AiAssistantEntity.Mode.FOLLOWING);
        level.addFreshEntity(entity);

        player.sendSystemMessage(Component.literal(
                "§a" + name + ": §f\"Hey, I'm here! Talk to me in chat or use §e/ai help§f for commands.\""));
        return 1;
    }

    private static int dismiss(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 128);
        if (ai == null) return noAi(player);

        String name = ai.getAssistantName();
        ai.discard();
        player.sendSystemMessage(Component.literal("§7" + name + " has been dismissed. Bring it back with /ai summon."));
        return 1;
    }

    // ── movement / quick actions ──────────────────────────────────────────────

    private static int come(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 256);
        if (ai == null) return noAi(player);
        ai.comeTo(player);
        return 1;
    }

    private static int follow(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 128);
        if (ai == null) return noAi(player);
        ai.followPlayer();
        return 1;
    }

    private static int stay(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 128);
        if (ai == null) return noAi(player);
        ai.stayHere();
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 128);
        if (ai == null) return noAi(player);
        ai.stopTask();
        return 1;
    }

    private static int locate(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 512);
        if (ai == null) {
            player.sendSystemMessage(Component.literal(
                    "§cI can't find your assistant nearby. It may be in an unloaded area — try /ai summon."));
            return 0;
        }
        player.sendSystemMessage(Component.literal("§b" + ai.getAssistantName() + ": §f\"" + Locator.describe(player, ai) + "\""));
        return 1;
    }

    private static int inventory(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 128);
        if (ai == null) return noAi(player);
        player.sendSystemMessage(Component.literal(ai.describeInventory()));
        return 1;
    }

    private static int rename(CommandContext<CommandSourceStack> ctx, String newName) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 64);
        if (ai == null) return noAi(player);

        String old = ai.getAssistantName();
        ai.setAssistantName(newName);
        player.sendSystemMessage(Component.literal("§aRenamed §f" + old + " §a→ §f" + newName));
        return 1;
    }

    private static int setSkin(CommandContext<CommandSourceStack> ctx, String skin) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 64);
        if (ai == null) return noAi(player);

        ai.setSkin(skin);
        player.sendSystemMessage(Component.literal(
                "§aSkin set to §f" + skin + "§a. §7Built-ins: default, robot, void, "
                        + "slate, ember, forest, amethyst. Drop your own PNG in "
                        + "config/blockpal/skins/ and run §f/aiskins list§7."));
        return 1;
    }

    // ── token / listen ──────────────────────────────────────────────────────────

    private static int setToken(CommandContext<CommandSourceStack> ctx, String token) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        ModConfig.get().hfToken = token.trim();
        ModConfig.save();
        player.sendSystemMessage(Component.literal("§aAPI token saved ✓ §7Your assistant can now take tasks."));
        return 1;
    }

    private static int showListen(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        boolean on = ModConfig.get().chatListening;
        player.sendSystemMessage(Component.literal(
                "§eChat listening is " + (on ? "§aON" : "§cOFF") + "§e. Use /ai listen on|off to change it."));
        return 1;
    }

    private static int resume(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        if (!com.milkdromeda.blockpal.EmergencyState.isDisabled()) {
            player.sendSystemMessage(Component.literal("§eThe AI assistant is already active."));
            return 1;
        }
        com.milkdromeda.blockpal.EmergencyState.setDisabled(false);
        player.sendSystemMessage(Component.literal(
                "§a[AI] AI assistant re-enabled. §7It will auto-disable again if your frame-rate collapses."));
        return 1;
    }

    private static int setListen(CommandContext<CommandSourceStack> ctx, boolean on) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        ModConfig.get().chatListening = on;
        ModConfig.save();
        player.sendSystemMessage(Component.literal(on
                ? "§aChat listening ON §7— just talk to your assistant in chat."
                : "§cChat listening OFF §7— use /ai commands instead."));
        return 1;
    }

    private static int showActive(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        boolean on = ModConfig.get().activeMode;
        player.sendSystemMessage(Component.literal(
                "§eActive analysis is " + (on ? "§aON" : "§cOFF")
                        + "§e. Use /ai active on|off to change it."));
        return 1;
    }

    private static int setActive(CommandContext<CommandSourceStack> ctx, boolean on) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        ModConfig.get().activeMode = on;
        ModConfig.save();
        player.sendSystemMessage(Component.literal(on
                ? "§aActive analysis ON §7— I'll read every message and help when it sounds like you need me."
                : "§cActive analysis OFF §7— I'll only respond when addressed by name or a command word."));
        return 1;
    }

    private static int showCommandsSetting(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        ModConfig cfg = ModConfig.get();
        player.sendSystemMessage(Component.literal(
                "§eRunning commands is " + (cfg.allowCommands ? "§aON" : "§cOFF")
                        + "§e (permission level " + cfg.commandPermissionLevel
                        + "). Use /ai commands on|off."));
        return 1;
    }

    private static int setAllowCommands(CommandContext<CommandSourceStack> ctx, boolean on) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        ModConfig.get().allowCommands = on;
        ModConfig.save();
        player.sendSystemMessage(Component.literal(on
                ? "§aCommand execution ON §7— I can now use /setblock, /fill, /give, etc. (level "
                        + ModConfig.get().commandPermissionLevel + ")."
                : "§cCommand execution OFF §7— I'll stick to moving, building and fighting by hand."));
        return 1;
    }

    // ── config menu ───────────────────────────────────────────────────────────

    private static int openMenu(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        if (!ServerPlayNetworking.canSend(player, ConfigSyncPayload.TYPE)) {
            player.sendSystemMessage(Component.literal(
                    "§eThe settings menu needs the Blockpal mod on your client. "
                            + "Use §f/ai settings§e here instead."));
            return 0;
        }
        ServerPlayNetworking.send(player, new ConfigSyncPayload(ConfigData.fromConfig()));
        player.sendSystemMessage(Component.literal("§7Opening the Blockpal menu…"));
        return 1;
    }

    // ── advanced settings ─────────────────────────────────────────────────────

    private static int showSettings(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        ModConfig cfg = ModConfig.get();
        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 128);
        String aiName = ai != null ? ai.getAssistantName() : "none nearby";
        String mode   = ai != null ? ai.getMode().name() : "-";

        player.sendSystemMessage(Component.literal(
                "§6=== Blockpal Settings ===\n" +
                "§eAssistant:      §f" + aiName + "  (mode: " + mode + ")\n" +
                "§eChat listening: §f" + (cfg.chatListening ? "on" : "off") + "\n" +
                "§eActive analysis:§f " + (cfg.activeMode ? "on" : "off") + "\n" +
                "§eRun commands:   §f" + (cfg.allowCommands ? "on (level " + cfg.commandPermissionLevel + ")" : "off") + "\n" +
                "§eModel:          §f" + cfg.hfModel + "\n" +
                "§eAPI URL:        §f" + cfg.apiUrl + "\n" +
                "§eAPI token:      §f" + (cfg.hasApiToken() ? "set ✓" : "§cnot set — /ai token <token>") + "\n" +
                "§eTemperature:    §f" + cfg.temperature + "\n" +
                "§eMax tokens:     §f" + cfg.maxNewTokens + "\n" +
                "§eFollow dist:    §f" + cfg.followDistance + "\n" +
                "§eGuard radius:   §f" + cfg.guardRadius + "\n" +
                "§eMax task secs:  §f" + (cfg.maxTaskSeconds == 0 ? "unlimited" : cfg.maxTaskSeconds) + "\n" +
                "§eSneak→menu:     §f" + (cfg.sneakToOpenMenu ? "on" : "off") + "\n" +
                "§7Tip: open the full menu with §f/ai menu§7"
                        + (cfg.sneakToOpenMenu ? " (or sneak-right-click the assistant)" : "") + ". "
                        + "Or change any value with §f/ai settings <key> <value>§7 (tab-complete the key)."
        ));
        return 1;
    }

    /** Every key accepted by {@code /ai settings <key> <value>}. */
    private static final String[] SETTING_KEYS = {
            "name", "skin", "model", "api_url", "token", "temperature", "max_tokens",
            "follow_distance", "guard_radius", "command_level", "max_task_seconds",
            "action_tick_delay", "flee_health", "chat_listening", "active_mode",
            "allow_commands", "debug_logging", "sneak_menu", "preset"
    };

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> SETTING_KEYS_SUGGEST =
            (ctx, builder) -> {
                String remaining = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
                for (String key : SETTING_KEYS) {
                    if (key.startsWith(remaining)) builder.suggest(key);
                }
                return builder.buildFuture();
            };

    /** Generic setter for any config value, parsing {@code raw} to the key's type. */
    private static int applySetting(CommandContext<CommandSourceStack> ctx, String key, String raw) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        ModConfig cfg = ModConfig.get();
        key = key.toLowerCase(java.util.Locale.ROOT).trim();
        String value = raw.trim();

        try {
            switch (key) {
                case "name"              -> cfg.defaultName = require(value);
                case "skin"              -> cfg.defaultSkin = require(value);
                case "model"             -> cfg.hfModel = require(value);
                case "api_url"           -> cfg.apiUrl = require(value);
                case "token"             -> cfg.hfToken = value;
                case "temperature"       -> cfg.temperature = clampD(parseD(value), 0.0, 2.0);
                case "max_tokens"        -> cfg.maxNewTokens = clampI(parseI(value), 32, 2048);
                case "follow_distance"   -> cfg.followDistance = clampD(parseD(value), 1.0, 32.0);
                case "guard_radius"      -> cfg.guardRadius = clampD(parseD(value), 4.0, 64.0);
                case "command_level"     -> cfg.commandPermissionLevel = clampI(parseI(value), 0, 4);
                case "max_task_seconds"  -> cfg.maxTaskSeconds = clampI(parseI(value), 0, 3600);
                case "action_tick_delay" -> cfg.actionTickDelay = clampI(parseI(value), 0, 40);
                case "flee_health"       -> cfg.fleeHealthPercent = clampD(parseD(value), 0.0, 1.0);
                case "chat_listening"    -> cfg.chatListening = parseBool(value);
                case "active_mode"       -> cfg.activeMode = parseBool(value);
                case "allow_commands"    -> cfg.allowCommands = parseBool(value);
                case "debug_logging"     -> cfg.debugLogging = parseBool(value);
                case "sneak_menu"        -> cfg.sneakToOpenMenu = parseBool(value);
                case "preset"            -> applyPreset(cfg, value);
                default -> {
                    player.sendSystemMessage(Component.literal(
                            "§cUnknown setting §f" + key + "§c. Valid keys: §7" + String.join(", ", SETTING_KEYS)));
                    return 0;
                }
            }
        } catch (NumberFormatException e) {
            player.sendSystemMessage(Component.literal("§cInvalid value §f" + value + "§c for §f" + key + "§c."));
            return 0;
        } catch (IllegalArgumentException e) {
            player.sendSystemMessage(Component.literal("§c" + e.getMessage()));
            return 0;
        }

        ModConfig.save();
        String shown = key.equals("token") ? (value.isBlank() ? "cleared" : "set ✓") : value;
        player.sendSystemMessage(Component.literal("§a[Settings] §f" + key + " §7= §f" + shown));
        return 1;
    }

    /** Applies a performance preset's values to the live config (mirrors the GUI button). */
    private static void applyPreset(ModConfig cfg, String preset) {
        switch (preset.toLowerCase(java.util.Locale.ROOT)) {
            case "opus" -> {
                cfg.chatListening = true; cfg.activeMode = true;
                cfg.temperature = 0.8; cfg.maxNewTokens = 1024;
                cfg.actionTickDelay = 2; cfg.maxTaskSeconds = 600; cfg.fleeHealthPercent = 0.2;
                cfg.performancePreset = "opus";
            }
            case "potato" -> {
                cfg.chatListening = true; cfg.activeMode = false;
                cfg.temperature = 0.5; cfg.maxNewTokens = 256;
                cfg.actionTickDelay = 20; cfg.maxTaskSeconds = 120; cfg.fleeHealthPercent = 0.25;
                cfg.performancePreset = "potato";
            }
            case "normal" -> {
                cfg.chatListening = true; cfg.activeMode = true;
                cfg.temperature = 0.7; cfg.maxNewTokens = 512;
                cfg.actionTickDelay = 8; cfg.maxTaskSeconds = 300; cfg.fleeHealthPercent = 0.25;
                cfg.performancePreset = "normal";
            }
            default -> throw new IllegalArgumentException("Preset must be normal, opus or potato.");
        }
    }

    private static String require(String v) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Value can't be empty.");
        return v;
    }

    private static double parseD(String v) { return Double.parseDouble(v); }
    private static int parseI(String v) { return Integer.parseInt(v); }

    private static boolean parseBool(String v) {
        return switch (v.toLowerCase(java.util.Locale.ROOT)) {
            case "on", "true", "yes", "1", "enable", "enabled" -> true;
            case "off", "false", "no", "0", "disable", "disabled" -> false;
            default -> throw new IllegalArgumentException("Use on/off (or true/false) for " + v + ".");
        };
    }

    private static double clampD(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    private static int clampI(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    // ── /ai <task> ────────────────────────────────────────────────────────────

    private static int doTask(CommandContext<CommandSourceStack> ctx, String task) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 128);
        if (ai == null) return noAi(player);

        if (!ModConfig.get().hasApiToken()) {
            player.sendSystemMessage(Component.literal(
                    "§c[AI] No API token set yet. Run: §f/ai token <your_token>"));
            return 0;
        }

        ai.giveTask(task, player);
        return 1;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static ServerPlayer getPlayer(CommandContext<CommandSourceStack> ctx) {
        try { return ctx.getSource().getPlayerOrException(); } catch (Exception e) { return null; }
    }

    private static int noAi(ServerPlayer player) {
        player.sendSystemMessage(Component.literal(
                "§cNo AI assistant nearby. Summon one with §f/ai summon"));
        return 0;
    }
}
