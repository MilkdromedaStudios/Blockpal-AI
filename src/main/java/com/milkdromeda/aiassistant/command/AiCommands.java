package com.milkdromeda.aiassistant.command;

import com.milkdromeda.aiassistant.ModEntities;
import com.milkdromeda.aiassistant.config.ModConfig;
import com.milkdromeda.aiassistant.entity.AiAssistantEntity;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;

public class AiCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
                dispatcher.register(Commands.literal("ai")
                        .requires(src -> true)

                        // /ai summon [name]
                        .then(Commands.literal("summon")
                                .executes(ctx -> summon(ctx, "ARIA"))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> summon(ctx, StringArgumentType.getString(ctx, "name")))))

                        // /ai stop
                        .then(Commands.literal("stop")
                                .executes(AiCommands::stop))

                        // /ai settings
                        .then(Commands.literal("settings")
                                .executes(AiCommands::showSettings)

                                .then(Commands.literal("hf_token")
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(ctx -> setSetting(ctx, "hf_token",
                                                        StringArgumentType.getString(ctx, "value")))))

                                .then(Commands.literal("model")
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(ctx -> setSetting(ctx, "model",
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
                                                        DoubleArgumentType.getDouble(ctx, "value")))))

                                .then(Commands.literal("name")
                                        .then(Commands.argument("value", StringArgumentType.word())
                                                .executes(ctx -> renameAssistant(ctx,
                                                        StringArgumentType.getString(ctx, "value"))))))

                        // /ai <task> — must be last (greedy)
                        .then(Commands.argument("task", StringArgumentType.greedyString())
                                .executes(ctx -> doTask(ctx, StringArgumentType.getString(ctx, "task"))))
                )
        );
    }

    // ── /ai summon ─────────────────────────────────────────────────────────────

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

        player.sendSystemMessage(header(name)
                .append(val("Summoned! Type "))
                .append(Component.literal("/ai <task>").withStyle(Style.EMPTY.withColor(0x55FF55)))
                .append(val(" to give me instructions.")));
        return 1;
    }

    // ── /ai stop ───────────────────────────────────────────────────────────────

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        AiAssistantEntity ai = nearest(player, 128);
        if (ai == null) return noAi(player);

        ai.getTaskManager().clearPlan();
        ai.setMode(AiAssistantEntity.Mode.FOLLOWING);
        player.sendSystemMessage(header(ai.getAssistantName())
                .append(val("Stopped — standing by.")));
        return 1;
    }

    // ── /ai settings ───────────────────────────────────────────────────────────

    private static int showSettings(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        ModConfig cfg = ModConfig.get();
        AiAssistantEntity ai = nearest(player, 128);
        String aiName   = ai != null ? ai.getAssistantName() : "—";
        String modeStr  = ai != null ? ai.getMode().name()   : "—";
        String taskStr  = ai != null ? ai.getTaskManager().getPlanDescription() : "—";
        boolean hasToken = cfg.hasApiToken();

        player.sendSystemMessage(
            section("⚙ AI Assistant Settings")
            .append(row("Assistant",    aiName + " §7(mode: §f" + modeStr + "§7)"))
            .append(row("Task",         taskStr))
            .append(divider())
            .append(row("HF model",     cfg.hfModel))
            .append(row("HF token",     hasToken ? "§aset ✓" : "§cnot set"))
            .append(row("Temperature",  String.valueOf(cfg.temperature)))
            .append(row("Max tokens",   String.valueOf(cfg.maxNewTokens)))
            .append(row("Follow dist",  String.valueOf(cfg.followDistance)))
            .append(row("Guard radius", String.valueOf(cfg.guardRadius)))
            .append(divider())
            .append(hint(!hasToken
                    ? "Set your token: /ai settings hf_token <token>"
                    : "Use /ai settings <key> <value> to change settings."))
        );
        return 1;
    }

    private static int setSetting(CommandContext<CommandSourceStack> ctx, String key, String value) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        ModConfig cfg = ModConfig.get();

        switch (key) {
            case "hf_token" -> cfg.hfToken = value;
            case "model"    -> cfg.hfModel  = value;
            default -> { player.sendSystemMessage(err("Unknown setting: " + key)); return 0; }
        }

        ModConfig.save();
        String display = key.equals("hf_token") ? "●●●●●●●●" : value;
        player.sendSystemMessage(ok("✓ " + key + " = " + display));
        return 1;
    }

    private static int setSettingDouble(CommandContext<CommandSourceStack> ctx, String key, double value) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        ModConfig cfg = ModConfig.get();

        switch (key) {
            case "temperature"     -> cfg.temperature    = value;
            case "follow_distance" -> cfg.followDistance = value;
            case "guard_radius"    -> cfg.guardRadius    = value;
        }

        ModConfig.save();
        player.sendSystemMessage(ok("✓ " + key + " = " + value));
        return 1;
    }

    private static int setSettingInt(CommandContext<CommandSourceStack> ctx, String key, int value) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        ModConfig cfg = ModConfig.get();

        if ("max_tokens".equals(key)) cfg.maxNewTokens = value;

        ModConfig.save();
        player.sendSystemMessage(ok("✓ " + key + " = " + value));
        return 1;
    }

    private static int renameAssistant(CommandContext<CommandSourceStack> ctx, String newName) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        AiAssistantEntity ai = nearest(player, 32);
        if (ai == null) return noAi(player);

        String old = ai.getAssistantName();
        ai.setAssistantName(newName);
        player.sendSystemMessage(ok("✓ Renamed '" + old + "' → '" + newName + "'"));
        return 1;
    }

    // ── /ai <task> ─────────────────────────────────────────────────────────────

    private static int doTask(CommandContext<CommandSourceStack> ctx, String task) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        AiAssistantEntity ai = nearest(player, 128);
        if (ai == null) return noAi(player);

        if (!ModConfig.get().hasApiToken()) {
            player.sendSystemMessage(err("No HuggingFace token — run: /ai settings hf_token <token>"));
            return 0;
        }

        ai.giveTask(task, player);
        return 1;
    }

    // ── Component helpers ─────────────────────────────────────────────────────

    /** Gold bold "[Name] " prefix. */
    private static MutableComponent header(String name) {
        return Component.literal("[")
                .withStyle(Style.EMPTY.withColor(0x888888))
                .append(Component.literal(name).withStyle(Style.EMPTY.withColor(0xF0A000).withBold(true)))
                .append(Component.literal("] ").withStyle(Style.EMPTY.withColor(0x888888)));
    }

    private static MutableComponent section(String title) {
        return Component.literal("\n§8▬▬▬ §6§l" + title + " §8▬▬▬\n");
    }

    private static MutableComponent row(String label, String value) {
        return Component.literal("  §e" + label + ": §f" + value + "\n");
    }

    private static MutableComponent divider() {
        return Component.literal("  §8· · · · · · · · · · · · · · ·\n");
    }

    private static MutableComponent hint(String text) {
        return Component.literal("  §7" + text + "\n");
    }

    private static MutableComponent val(String text) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(0xCCCCCC));
    }

    private static MutableComponent ok(String text) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(0x55FF55));
    }

    private static MutableComponent err(String text) {
        return Component.literal("✗ " + text).withStyle(Style.EMPTY.withColor(0xFF5555));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static ServerPlayer getPlayer(CommandContext<CommandSourceStack> ctx) {
        try { return ctx.getSource().getPlayerOrException(); } catch (Exception e) { return null; }
    }

    private static AiAssistantEntity nearest(ServerPlayer player, double range) {
        AABB box = AABB.ofSize(player.position(), range * 2, range, range * 2);
        List<AiAssistantEntity> list = player.level()
                .getEntitiesOfClass(AiAssistantEntity.class, box, e -> true);
        return list.stream()
                .min(Comparator.comparingDouble(a -> a.distanceToSqr(player)))
                .orElse(null);
    }

    private static int noAi(ServerPlayer player) {
        player.sendSystemMessage(err("No AI assistant nearby. Spawn one with /ai summon"));
        return 0;
    }
}
