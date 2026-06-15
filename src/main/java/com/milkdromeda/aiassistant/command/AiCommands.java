package com.milkdromeda.aiassistant.command;

import com.milkdromeda.aiassistant.ModEntities;
import com.milkdromeda.aiassistant.config.ModConfig;
import com.milkdromeda.aiassistant.entity.AiAssistantEntity;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

import java.util.List;

public class AiCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> {
            dispatcher.register(CommandManager.literal("aiassistant")
                    .requires(src -> src.hasPermissionLevel(0))

                    // /aiassistant summon [name]
                    .then(CommandManager.literal("summon")
                            .executes(ctx -> summon(ctx, "ARIA"))
                            .then(CommandManager.argument("name", StringArgumentType.word())
                                    .executes(ctx -> summon(ctx, StringArgumentType.getString(ctx, "name")))))

                    // /aiassistant dismiss [name]
                    .then(CommandManager.literal("dismiss")
                            .executes(ctx -> dismiss(ctx, "ARIA"))
                            .then(CommandManager.argument("name", StringArgumentType.word())
                                    .executes(ctx -> dismiss(ctx, StringArgumentType.getString(ctx, "name")))))

                    // /aiassistant task <description>
                    .then(CommandManager.literal("task")
                            .then(CommandManager.argument("description", StringArgumentType.greedyString())
                                    .executes(ctx -> giveTask(ctx, StringArgumentType.getString(ctx, "description")))))

                    // /aiassistant follow [player_name]
                    .then(CommandManager.literal("follow")
                            .executes(ctx -> setFollow(ctx, null))
                            .then(CommandManager.argument("player", StringArgumentType.word())
                                    .executes(ctx -> setFollow(ctx, StringArgumentType.getString(ctx, "player")))))

                    // /aiassistant come
                    .then(CommandManager.literal("come")
                            .executes(AiCommands::come))

                    // /aiassistant guard
                    .then(CommandManager.literal("guard")
                            .executes(AiCommands::guard))

                    // /aiassistant stop
                    .then(CommandManager.literal("stop")
                            .executes(AiCommands::stop))

                    // /aiassistant status
                    .then(CommandManager.literal("status")
                            .executes(AiCommands::status))

                    // /aiassistant build <structure>
                    .then(CommandManager.literal("build")
                            .then(CommandManager.argument("structure", StringArgumentType.greedyString())
                                    .executes(ctx -> buildStructure(ctx, StringArgumentType.getString(ctx, "structure")))))

                    // /aiassistant config <key> <value>
                    .then(CommandManager.literal("config")
                            .requires(src -> src.hasPermissionLevel(2))
                            .then(CommandManager.argument("key", StringArgumentType.word())
                                    .then(CommandManager.argument("value", StringArgumentType.greedyString())
                                            .executes(ctx -> setConfig(ctx,
                                                    StringArgumentType.getString(ctx, "key"),
                                                    StringArgumentType.getString(ctx, "value"))))))

                    // /aiassistant name <newname>
                    .then(CommandManager.literal("name")
                            .then(CommandManager.argument("newname", StringArgumentType.word())
                                    .executes(ctx -> rename(ctx, StringArgumentType.getString(ctx, "newname")))))
            );
        });
    }

    private static int summon(CommandContext<ServerCommandSource> ctx, String name) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try { player = src.getPlayerOrThrow(); } catch (Exception e) { return 0; }

        ServerWorld world = player.getServerWorld();
        AiAssistantEntity entity = ModEntities.AI_ASSISTANT.create(world, SpawnReason.COMMAND);
        if (entity == null) return 0;

        entity.setAssistantName(name);
        entity.setOwnerUuid(player.getUuid());
        entity.setPosition(player.getX() + 1.5, player.getY(), player.getZ());
        entity.setMode(AiAssistantEntity.Mode.FOLLOWING);
        world.spawnEntity(entity);

        src.sendFeedback(() -> Text.literal("Summoned AI assistant '" + name + "'"), false);
        return 1;
    }

    private static int dismiss(CommandContext<ServerCommandSource> ctx, String name) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try { player = src.getPlayerOrThrow(); } catch (Exception e) { return 0; }

        List<AiAssistantEntity> assistants = findAssistants(player, 32);
        AiAssistantEntity target = assistants.stream()
                .filter(a -> a.getAssistantName().equalsIgnoreCase(name))
                .findFirst().orElse(null);

        if (target == null) {
            src.sendFeedback(() -> Text.literal("No assistant named '" + name + "' nearby"), false);
            return 0;
        }

        target.discard();
        src.sendFeedback(() -> Text.literal("Dismissed '" + name + "'"), false);
        return 1;
    }

    private static int giveTask(CommandContext<ServerCommandSource> ctx, String desc) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try { player = src.getPlayerOrThrow(); } catch (Exception e) { return 0; }

        if (!ModConfig.get().hasApiToken()) {
            src.sendFeedback(() -> Text.literal(
                    "[AI-Assistant] Set your HuggingFace token first: /aiassistant config hf_token <token>"), false);
            return 0;
        }

        AiAssistantEntity assistant = findNearestAssistant(player, 64);
        if (assistant == null) {
            src.sendFeedback(() -> Text.literal("No AI assistant nearby. Use /aiassistant summon first."), false);
            return 0;
        }

        assistant.giveTask(desc, player);
        src.sendFeedback(() -> Text.literal("[" + assistant.getAssistantName() + "] Task received: " + desc), false);
        return 1;
    }

    private static int setFollow(CommandContext<ServerCommandSource> ctx, String targetName) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try { player = src.getPlayerOrThrow(); } catch (Exception e) { return 0; }

        AiAssistantEntity assistant = findNearestAssistant(player, 64);
        if (assistant == null) { src.sendFeedback(() -> Text.literal("No assistant nearby."), false); return 0; }

        if (targetName != null) {
            ServerPlayerEntity target = player.getServer().getPlayerManager().getPlayer(targetName);
            if (target != null) assistant.setOwnerUuid(target.getUuid());
        }

        assistant.setMode(AiAssistantEntity.Mode.FOLLOWING);
        String displayName = targetName != null ? targetName : player.getName().getString();
        src.sendFeedback(() -> Text.literal("[" + assistant.getAssistantName() + "] Now following " + displayName), false);
        return 1;
    }

    private static int come(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try { player = src.getPlayerOrThrow(); } catch (Exception e) { return 0; }

        AiAssistantEntity assistant = findNearestAssistant(player, 128);
        if (assistant == null) { src.sendFeedback(() -> Text.literal("No assistant found."), false); return 0; }

        assistant.setMode(AiAssistantEntity.Mode.FOLLOWING);
        assistant.setOwnerUuid(player.getUuid());
        src.sendFeedback(() -> Text.literal("[" + assistant.getAssistantName() + "] Coming to you!"), false);
        return 1;
    }

    private static int guard(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try { player = src.getPlayerOrThrow(); } catch (Exception e) { return 0; }

        AiAssistantEntity assistant = findNearestAssistant(player, 32);
        if (assistant == null) { src.sendFeedback(() -> Text.literal("No assistant nearby."), false); return 0; }

        assistant.setMode(AiAssistantEntity.Mode.GUARDING);
        src.sendFeedback(() -> Text.literal("[" + assistant.getAssistantName() + "] Guarding this position."), false);
        return 1;
    }

    private static int stop(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try { player = src.getPlayerOrThrow(); } catch (Exception e) { return 0; }

        AiAssistantEntity assistant = findNearestAssistant(player, 64);
        if (assistant == null) { src.sendFeedback(() -> Text.literal("No assistant nearby."), false); return 0; }

        assistant.getTaskManager().clearPlan();
        assistant.setMode(AiAssistantEntity.Mode.IDLE);
        src.sendFeedback(() -> Text.literal("[" + assistant.getAssistantName() + "] Stopped."), false);
        return 1;
    }

    private static int status(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try { player = src.getPlayerOrThrow(); } catch (Exception e) { return 0; }

        AiAssistantEntity assistant = findNearestAssistant(player, 128);
        if (assistant == null) { src.sendFeedback(() -> Text.literal("No assistant found."), false); return 0; }

        ModConfig cfg = ModConfig.get();
        src.sendFeedback(() -> Text.literal(
                "=== AI Assistant Status ===\n" +
                "Name:    " + assistant.getAssistantName() + "\n" +
                "Mode:    " + assistant.getMode().name() + "\n" +
                "Task:    " + assistant.getTaskManager().getPlanDescription() + "\n" +
                "Health:  " + (int)assistant.getHealth() + "/" + (int)assistant.getMaxHealth() + "\n" +
                "Model:   " + cfg.hfModel + "\n" +
                "HF key:  " + (cfg.hasApiToken() ? "set ✓" : "NOT SET - use /aiassistant config hf_token <token>")
        ), false);
        return 1;
    }

    private static int buildStructure(CommandContext<ServerCommandSource> ctx, String structure) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try { player = src.getPlayerOrThrow(); } catch (Exception e) { return 0; }

        AiAssistantEntity assistant = findNearestAssistant(player, 64);
        if (assistant == null) { src.sendFeedback(() -> Text.literal("No assistant nearby."), false); return 0; }

        // Build via HuggingFace task (treated as a planning task)
        assistant.giveTask("Build: " + structure, player);
        src.sendFeedback(() -> Text.literal("[" + assistant.getAssistantName() + "] Planning to build: " + structure), false);
        return 1;
    }

    private static int setConfig(CommandContext<ServerCommandSource> ctx, String key, String value) {
        ServerCommandSource src = ctx.getSource();
        ModConfig cfg = ModConfig.get();
        switch (key.toLowerCase()) {
            case "hf_token", "hftoken"       -> cfg.hfToken = value;
            case "model", "hf_model"          -> cfg.hfModel = value;
            case "max_tokens", "maxnewtokens" -> { try { cfg.maxNewTokens = Integer.parseInt(value); } catch (NumberFormatException ignored) {} }
            case "temperature"                -> { try { cfg.temperature = Double.parseDouble(value); } catch (NumberFormatException ignored) {} }
            case "debug"                      -> cfg.debugLogging = Boolean.parseBoolean(value);
            case "follow_distance"            -> { try { cfg.followDistance = Double.parseDouble(value); } catch (NumberFormatException ignored) {} }
            case "guard_radius"               -> { try { cfg.guardRadius = Double.parseDouble(value); } catch (NumberFormatException ignored) {} }
            default -> { src.sendFeedback(() -> Text.literal("Unknown config key: " + key), false); return 0; }
        }
        ModConfig.save();
        src.sendFeedback(() -> Text.literal("Set '" + key + "' = '" + value + "'"), false);
        return 1;
    }

    private static int rename(CommandContext<ServerCommandSource> ctx, String newName) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try { player = src.getPlayerOrThrow(); } catch (Exception e) { return 0; }

        AiAssistantEntity assistant = findNearestAssistant(player, 32);
        if (assistant == null) { src.sendFeedback(() -> Text.literal("No assistant nearby."), false); return 0; }

        String old = assistant.getAssistantName();
        assistant.setAssistantName(newName);
        src.sendFeedback(() -> Text.literal("Renamed '" + old + "' to '" + newName + "'"), false);
        return 1;
    }

    // ---- Helpers ----

    private static AiAssistantEntity findNearestAssistant(ServerPlayerEntity player, double range) {
        return findAssistants(player, range).stream().findFirst().orElse(null);
    }

    private static List<AiAssistantEntity> findAssistants(ServerPlayerEntity player, double range) {
        Box box = Box.of(player.getPos(), range * 2, range, range * 2);
        return player.getServerWorld()
                .getEntitiesByClass(AiAssistantEntity.class, box, e -> true);
    }
}
