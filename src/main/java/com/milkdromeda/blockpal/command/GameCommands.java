package com.milkdromeda.blockpal.command;

import com.milkdromeda.blockpal.minigame.GameMode;
import com.milkdromeda.blockpal.minigame.GameSession;
import com.milkdromeda.blockpal.minigame.MinigameManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The {@code /game} commands — start a mini-game on your party (or solo), see the
 * modes, and stop. Server-side, so Java and Bedrock players use them the same.
 */
public final class GameCommands {

    private GameCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
                dispatcher.register(Commands.literal("game")
                        .requires(src -> true)
                        .executes(GameCommands::list)
                        .then(Commands.literal("list").executes(GameCommands::list))
                        .then(Commands.literal("stop").executes(GameCommands::stop))
                        .then(Commands.literal("start")
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests(MODES)
                                        .executes(ctx -> start(ctx, StringArgumentType.getString(ctx, "mode")))))));
    }

    private static final SuggestionProvider<CommandSourceStack> MODES = (ctx, builder) -> {
        for (GameMode m : GameMode.values()) builder.suggest(m.id);
        return builder.buildFuture();
    };

    private static int list(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = player(ctx);
        if (p == null) return 0;
        StringBuilder sb = new StringBuilder("§6=== Mini-games ===");
        GameSession current = MinigameManager.sessionOf(p);
        if (current != null) {
            sb.append("\n§aYou're playing §f").append(current.mode.display)
                    .append("§7 — stop with §f/game stop§7.");
        }
        for (GameMode m : GameMode.values()) {
            sb.append("\n§e").append(m.id).append(" §7— ").append(m.desc);
        }
        sb.append("\n§7Start one for your party with §f/game start <mode>§7 (see §f/party§7).");
        final String out = sb.toString();
        p.sendSystemMessage(Component.literal(out));
        return 1;
    }

    private static int start(CommandContext<CommandSourceStack> ctx, String modeId) {
        ServerPlayer p = player(ctx);
        if (p == null) return 0;
        GameMode mode = GameMode.byId(modeId);
        if (mode == null) {
            p.sendSystemMessage(Component.literal("§cUnknown mode §f'" + modeId + "'§c. See §f/game list§c."));
            return 0;
        }
        MinigameManager.start(p, mode);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = player(ctx);
        if (p == null) return 0;
        MinigameManager.stop(p);
        return 1;
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) {
        try { return ctx.getSource().getPlayerOrException(); } catch (Exception e) { return null; }
    }
}
