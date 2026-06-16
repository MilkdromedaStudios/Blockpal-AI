package com.milkdromeda.aiassistant.command;

import com.milkdromeda.aiassistant.ModEntities;
import com.milkdromeda.aiassistant.config.ModConfig;
import com.milkdromeda.aiassistant.entity.AiAssistantEntity;
import com.milkdromeda.aiassistant.network.ConfigData;
import com.milkdromeda.aiassistant.network.ConfigSyncPayload;
import com.milkdromeda.aiassistant.util.Locator;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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

                        .then(Commands.literal("locate").executes(AiCommands::locate))
                        .then(Commands.literal("where").executes(AiCommands::locate))

                        .then(Commands.literal("name")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> rename(ctx, StringArgumentType.getString(ctx, "name")))))

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

                        // open the real settings screen (client must have the mod)
                        .then(Commands.literal("menu").executes(AiCommands::openMenu))
                        .then(Commands.literal("config").executes(AiCommands::openMenu))

                        // ── advanced settings ────────────────────────────────────────
                        .then(Commands.literal("settings")
                                .executes(AiCommands::showSettings)

                                .then(Commands.literal("model")
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(ctx -> setStringSetting(ctx, "model",
                                                        StringArgumentType.getString(ctx, "value")))))

                                .then(Commands.literal("api_url")
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(ctx -> setStringSetting(ctx, "api_url",
                                                        StringArgumentType.getString(ctx, "value")))))

                                .then(Commands.literal("temperature")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 2.0))
                                                .executes(ctx -> setSettingDouble(ctx, "temperature",
                                                        DoubleArgumentType.getDouble(ctx, "value")))))

                                .then(Commands.literal("max_tokens")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(32, 2048))
                                                .executes(ctx -> setSettingInt(ctx, "max_tokens",
                                                        IntegerArgumentType.getInteger(ctx, "value")))))

                                .then(Commands.literal("follow_distance")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(1.0, 32.0))
                                                .executes(ctx -> setSettingDouble(ctx, "follow_distance",
                                                        DoubleArgumentType.getDouble(ctx, "value")))))

                                .then(Commands.literal("guard_radius")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(4.0, 64.0))
                                                .executes(ctx -> setSettingDouble(ctx, "guard_radius",
                                                        DoubleArgumentType.getDouble(ctx, "value"))))))

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
                "§6=== Your AI Assistant ===\n" +
                "§eJust talk in chat (no slash, no exact words needed):\n" +
                "§7  \"follow me\"   \"come here\"   \"stay\"   \"stop\"   \"where are you\"\n" +
                "§7  \"can you clear these trees?\"   \"I need a shelter\"\n" +
                "§6\n" +
                "§eCommands:\n" +
                "§f/ai menu §7— open the settings screen (default key: K)\n" +
                "§f/ai summon [name] §7— bring a new assistant into the world\n" +
                "§f/ai come §7— call it over to you\n" +
                "§f/ai follow §7— have it follow you\n" +
                "§f/ai stay §7— hold position and keep watch\n" +
                "§f/ai stop §7— cancel what it's doing\n" +
                "§f/ai locate §7— find where it is\n" +
                "§f/ai <task> §7— tell it what to do (e.g. /ai build a 5x5 floor)\n" +
                "§f/ai name <name> §7— rename it\n" +
                "§f/ai token <token> §7— set your AI service token\n" +
                "§f/ai listen on|off §7— toggle chat listening\n" +
                "§f/ai active on|off §7— toggle proactive analysis of every message\n" +
                "§f/ai dismiss §7— send it away\n" +
                "§f/ai settings §7— advanced configuration"
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
        entity.setOwnerUuid(player.getUUID());
        entity.setPos(player.getX() + 1.5, player.getY(), player.getZ());
        entity.setMode(AiAssistantEntity.Mode.FOLLOWING);
        level.addFreshEntity(entity);

        player.sendSystemMessage(Component.literal(
                "§a[" + name + "] §fReady to help! Just talk to me in chat, or use §e/ai help§f for commands."));
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
        player.sendSystemMessage(Component.literal("§b[" + ai.getAssistantName() + "] §f" + Locator.describe(player, ai)));
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

    // ── config menu ───────────────────────────────────────────────────────────

    private static int openMenu(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        if (!ServerPlayNetworking.canSend(player, ConfigSyncPayload.TYPE)) {
            player.sendSystemMessage(Component.literal(
                    "§eThe settings menu needs the AI Assistant mod on your client. "
                            + "Use §f/ai settings§e here instead."));
            return 0;
        }
        ServerPlayNetworking.send(player, new ConfigSyncPayload(ConfigData.fromConfig()));
        player.sendSystemMessage(Component.literal("§7Opening the AI Assistant menu… (default key: K)"));
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
                "§6=== AI Assistant Settings ===\n" +
                "§eAssistant:      §f" + aiName + "  (mode: " + mode + ")\n" +
                "§eChat listening: §f" + (cfg.chatListening ? "on" : "off") + "\n" +
                "§eActive analysis:§f " + (cfg.activeMode ? "on" : "off") + "\n" +
                "§eModel:          §f" + cfg.hfModel + "\n" +
                "§eAPI URL:        §f" + cfg.apiUrl + "\n" +
                "§eAPI token:      §f" + (cfg.hasApiToken() ? "set ✓" : "§cnot set — /ai token <token>") + "\n" +
                "§eTemperature:    §f" + cfg.temperature + "\n" +
                "§eMax tokens:     §f" + cfg.maxNewTokens + "\n" +
                "§eFollow dist:    §f" + cfg.followDistance + "\n" +
                "§eGuard radius:   §f" + cfg.guardRadius + "\n" +
                "§7Tip: open the full menu with §f/ai menu§7 or the §fK§7 key. "
                        + "Or change one value with /ai settings <key> <value>."
        ));
        return 1;
    }

    private static int setStringSetting(CommandContext<CommandSourceStack> ctx, String key, String value) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        ModConfig cfg = ModConfig.get();

        switch (key) {
            case "model"   -> cfg.hfModel = value.trim();
            case "api_url" -> cfg.apiUrl = value.trim();
            default -> { player.sendSystemMessage(Component.literal("Unknown setting: " + key)); return 0; }
        }

        ModConfig.save();
        player.sendSystemMessage(Component.literal("§a[Settings] §f" + key + " §7= §f" + value));
        return 1;
    }

    private static int setSettingDouble(CommandContext<CommandSourceStack> ctx, String key, double value) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        ModConfig cfg = ModConfig.get();

        switch (key) {
            case "temperature"     -> cfg.temperature = value;
            case "follow_distance" -> cfg.followDistance = value;
            case "guard_radius"    -> cfg.guardRadius = value;
        }

        ModConfig.save();
        player.sendSystemMessage(Component.literal("§a[Settings] §f" + key + " §7= §f" + value));
        return 1;
    }

    private static int setSettingInt(CommandContext<CommandSourceStack> ctx, String key, int value) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        ModConfig cfg = ModConfig.get();

        if ("max_tokens".equals(key)) cfg.maxNewTokens = value;

        ModConfig.save();
        player.sendSystemMessage(Component.literal("§a[Settings] §f" + key + " §7= §f" + value));
        return 1;
    }

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
