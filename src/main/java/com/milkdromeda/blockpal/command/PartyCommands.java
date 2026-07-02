package com.milkdromeda.blockpal.command;

import com.milkdromeda.blockpal.party.PartyManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The {@code /party} commands — the social layer for playing together (and, soon,
 * for minigames). Open to everyone and entirely server-side, so Java and Bedrock
 * players use the exact same commands.
 */
public final class PartyCommands {

    private PartyCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
                dispatcher.register(Commands.literal("party")
                        .requires(src -> true)
                        .executes(PartyCommands::status)
                        .then(Commands.literal("list").executes(PartyCommands::list))
                        .then(Commands.literal("accept").executes(PartyCommands::accept))
                        .then(Commands.literal("deny").executes(PartyCommands::deny))
                        .then(Commands.literal("leave").executes(PartyCommands::leave))
                        .then(Commands.literal("disband").executes(PartyCommands::disband))
                        .then(Commands.literal("invite")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(ONLINE_PLAYERS)
                                        .executes(ctx -> invite(ctx, StringArgumentType.getString(ctx, "player")))))
                        .then(Commands.literal("kick")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(PARTY_MEMBERS)
                                        .executes(ctx -> kick(ctx, StringArgumentType.getString(ctx, "player")))))));
    }

    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYERS = (ctx, builder) -> {
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayer self = player(ctx);
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (self == null || !p.getUUID().equals(self.getUUID())) builder.suggest(p.getName().getString());
            }
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> PARTY_MEMBERS = (ctx, builder) -> {
        ServerPlayer self = player(ctx);
        if (self != null) {
            var party = PartyManager.partyOf(self);
            if (party != null) {
                for (var uuid : party.memberUuids()) {
                    if (!uuid.equals(self.getUUID())) builder.suggest(party.nameOf(uuid));
                }
            }
        }
        return builder.buildFuture();
    };

    // ── handlers ──

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = player(ctx);
        if (p == null) return 0;
        PartyManager.list(p);   // doubles as "you're not in a party" hint
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = player(ctx);
        if (p == null) return 0;
        PartyManager.list(p);
        return 1;
    }

    private static int invite(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer p = player(ctx);
        if (p == null) return 0;
        MinecraftServer server = p.level().getServer();
        ServerPlayer target = server == null ? null : server.getPlayerList().getPlayerByName(name);
        if (target == null) {
            p.sendSystemMessage(Component.literal("§cCan't find an online player named §f" + name + "§c."));
            return 0;
        }
        PartyManager.invite(p, target);
        return 1;
    }

    private static int accept(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = player(ctx);
        if (p == null) return 0;
        PartyManager.accept(p);
        return 1;
    }

    private static int deny(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = player(ctx);
        if (p == null) return 0;
        PartyManager.deny(p);
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = player(ctx);
        if (p == null) return 0;
        PartyManager.leave(p);
        return 1;
    }

    private static int kick(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer p = player(ctx);
        if (p == null) return 0;
        PartyManager.kick(p, name);
        return 1;
    }

    private static int disband(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = player(ctx);
        if (p == null) return 0;
        PartyManager.disband(p);
        return 1;
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) {
        try { return ctx.getSource().getPlayerOrException(); } catch (Exception e) { return null; }
    }
}
