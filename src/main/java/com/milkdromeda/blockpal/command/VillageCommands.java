package com.milkdromeda.blockpal.command;

import com.milkdromeda.blockpal.minigame.village.VillageManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The {@code /village} commands for the "Growth" mini-game — start an AI village,
 * join a role, check on it, surrender, or stop. Server-side, so Java and Bedrock
 * players use them the same.
 */
public final class VillageCommands {

    private VillageCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
                dispatcher.register(Commands.literal("village")
                        .requires(src -> true)
                        .executes(VillageCommands::status)
                        .then(Commands.literal("start").executes(VillageCommands::start))
                        .then(Commands.literal("status").executes(VillageCommands::status))
                        .then(Commands.literal("surrender").executes(VillageCommands::surrender))
                        .then(Commands.literal("stop").executes(VillageCommands::stop))
                        .then(Commands.literal("leave").executes(VillageCommands::leave))
                        .then(Commands.literal("join")
                                .executes(ctx -> join(ctx, ""))
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .suggests(ROLES)
                                        .executes(ctx -> join(ctx, StringArgumentType.getString(ctx, "role")))))));
    }

    private static final SuggestionProvider<CommandSourceStack> ROLES = (ctx, builder) -> {
        for (String r : VillageManager.roleIds()) builder.suggest(r);
        return builder.buildFuture();
    };

    private static int start(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = player(ctx);
        if (p == null) return 0;
        VillageManager.start(p);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = player(ctx);
        if (p == null) return 0;
        VillageManager.status(p);
        return 1;
    }

    private static int join(CommandContext<CommandSourceStack> ctx, String role) {
        ServerPlayer p = player(ctx);
        if (p == null) return 0;
        VillageManager.join(p, role);
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = player(ctx);
        if (p == null) return 0;
        VillageManager.leave(p);
        return 1;
    }

    private static int surrender(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = player(ctx);
        if (p == null) return 0;
        VillageManager.surrender(p);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = player(ctx);
        if (p == null) return 0;
        VillageManager.stop(p);
        return 1;
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) {
        try { return ctx.getSource().getPlayerOrException(); } catch (Exception e) { return null; }
    }
}
