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
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

import java.util.List;

public class AiCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
                dispatcher.register(CommandManager.literal("ai")
                        .requires(src -> src.hasPermissionLevel(0))

                        // /ai summon [name]
                        .then(CommandManager.literal("summon")
                                .executes(ctx -> summon(ctx, "ARIA"))
                                .then(CommandManager.argument("name", StringArgumentType.word())
                                        .executes(ctx -> summon(ctx,
                                                StringArgumentType.getString(ctx, "name")))))

                        // /ai stop
                        .then(CommandManager.literal("stop")
                                .executes(AiCommands::stop))

                        // /ai <anything> — the main command
                        .then(CommandManager.argument("task", StringArgumentType.greedyString())
                                .executes(ctx -> doTask(ctx,
                                        StringArgumentType.getString(ctx, "task"))))
                )
        );
    }

    // ── /ai summon [name] ─────────────────────────────────────────────────────

    private static int summon(CommandContext<ServerCommandSource> ctx, String name) {
        ServerPlayerEntity player = getPlayer(ctx);
        if (player == null) return 0;

        AiAssistantEntity entity = ModEntities.AI_ASSISTANT.create(
                player.getServerWorld(), SpawnReason.COMMAND);
        if (entity == null) return 0;

        entity.setAssistantName(name);
        entity.setOwnerUuid(player.getUuid());
        entity.setPosition(player.getX() + 1.5, player.getY(), player.getZ());
        entity.setMode(AiAssistantEntity.Mode.FOLLOWING);
        player.getServerWorld().spawnEntity(entity);

        player.sendMessage(Text.literal("[" + name + "] I'm here! Tell me what to do with /ai <task>"), false);
        return 1;
    }

    // ── /ai stop ──────────────────────────────────────────────────────────────

    private static int stop(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = getPlayer(ctx);
        if (player == null) return 0;

        AiAssistantEntity ai = nearest(player, 128);
        if (ai == null) {
            player.sendMessage(Text.literal("No AI assistant nearby. Summon one with /ai summon"), false);
            return 0;
        }

        ai.getTaskManager().clearPlan();
        ai.setMode(AiAssistantEntity.Mode.FOLLOWING);
        player.sendMessage(Text.literal("[" + ai.getAssistantName() + "] Stopped. Standing by."), false);
        return 1;
    }

    // ── /ai <task> ────────────────────────────────────────────────────────────

    private static int doTask(CommandContext<ServerCommandSource> ctx, String task) {
        ServerPlayerEntity player = getPlayer(ctx);
        if (player == null) return 0;

        AiAssistantEntity ai = nearest(player, 128);
        if (ai == null) {
            player.sendMessage(Text.literal(
                    "No AI assistant nearby. Summon one first with /ai summon"), false);
            return 0;
        }

        if (!ModConfig.get().hasApiToken()) {
            // Allow a special shorthand: /ai config <key> <value>
            if (task.startsWith("config ")) {
                return handleInlineConfig(task.substring(7), player);
            }
            player.sendMessage(Text.literal(
                    "[AI] No HuggingFace token set. Use: /ai config hf_token <your_token>"), false);
            return 0;
        }

        ai.giveTask(task, player);
        return 1;
    }

    // ── inline /ai config <key> <value> ──────────────────────────────────────
    // (Only reachable when no HF token is set, to allow first-time setup)

    private static int handleInlineConfig(String args, ServerPlayerEntity player) {
        String[] parts = args.split(" ", 2);
        if (parts.length < 2) {
            player.sendMessage(Text.literal("Usage: /ai config <key> <value>"), false);
            return 0;
        }
        String key = parts[0];
        String value = parts[1];
        ModConfig cfg = ModConfig.get();

        switch (key.toLowerCase()) {
            case "hf_token"        -> cfg.hfToken = value;
            case "model"           -> cfg.hfModel = value;
            case "temperature"     -> { try { cfg.temperature = Double.parseDouble(value); } catch (NumberFormatException ignored) {} }
            case "max_tokens"      -> { try { cfg.maxNewTokens = Integer.parseInt(value); } catch (NumberFormatException ignored) {} }
            case "follow_distance" -> { try { cfg.followDistance = Double.parseDouble(value); } catch (NumberFormatException ignored) {} }
            case "guard_radius"    -> { try { cfg.guardRadius = Double.parseDouble(value); } catch (NumberFormatException ignored) {} }
            default -> {
                player.sendMessage(Text.literal("Unknown key: " + key
                        + " — valid: hf_token, model, temperature, max_tokens, follow_distance, guard_radius"), false);
                return 0;
            }
        }

        ModConfig.save();
        player.sendMessage(Text.literal("[AI] Set '" + key + "' = '" + value + "'"), false);
        return 1;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static ServerPlayerEntity getPlayer(CommandContext<ServerCommandSource> ctx) {
        try { return ctx.getSource().getPlayerOrThrow(); } catch (Exception e) { return null; }
    }

    private static AiAssistantEntity nearest(ServerPlayerEntity player, double range) {
        Box box = Box.of(player.getPos(), range * 2, range, range * 2);
        List<AiAssistantEntity> list = player.getServerWorld()
                .getEntitiesByClass(AiAssistantEntity.class, box, e -> true);
        return list.isEmpty() ? null : list.get(0);
    }
}
