package com.milkdromeda.blockpal.command;

import com.milkdromeda.blockpal.ModEntities;
import com.milkdromeda.blockpal.admin.AdminAccess;
import com.milkdromeda.blockpal.ai.Personality;
import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import com.milkdromeda.blockpal.network.AdminStatsData;
import com.milkdromeda.blockpal.network.AdminSyncPayload;
import com.milkdromeda.blockpal.network.AiNetworking;
import com.milkdromeda.blockpal.network.ConfigData;
import com.milkdromeda.blockpal.network.ConfigSyncPayload;
import com.milkdromeda.blockpal.util.Locator;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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

                        // Give the nearby bot a personality (how it talks + the tone of its plans).
                        .then(Commands.literal("personality")
                                .executes(AiCommands::listPersonalities)
                                .then(Commands.literal("custom")
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(ctx -> setCustomPersonality(ctx, StringArgumentType.getString(ctx, "text")))))
                                .then(Commands.argument("personality", StringArgumentType.word())
                                        .suggests(PERSONALITY_SUGGEST)
                                        .executes(ctx -> setPersonality(ctx, StringArgumentType.getString(ctx, "personality")))))

                        // Configuration lives in the in-game panel now — no confusing
                        // per-setting commands. /ai menu (or /ai panel) opens it;
                        // /ai tutorial walks new players through everything.
                        .then(Commands.literal("menu").executes(AiCommands::openMenu))
                        .then(Commands.literal("config").executes(AiCommands::openMenu))
                        .then(Commands.literal("tutorial").executes(AiCommands::openTutorial))

                        // ── personal API key & model (open to everyone) ──────────────
                        // Each player can set their own API key (so a server can bill
                        // players to their own keys) and pick their bot's model.
                        .then(Commands.literal("mykey")
                                .executes(AiCommands::myKeyStatus)
                                .then(Commands.literal("clear").executes(AiCommands::myKeyClear))
                                .then(Commands.argument("token", StringArgumentType.greedyString())
                                        .executes(ctx -> setMyKey(ctx, StringArgumentType.getString(ctx, "token")))))
                        .then(Commands.literal("mymenu").executes(AiCommands::openPlayerMenu))
                        .then(Commands.literal("panel").executes(AiCommands::openPanel))
                        .then(Commands.literal("models").executes(AiCommands::listModels))
                        .then(Commands.literal("model")
                                .executes(AiCommands::listModels)
                                .then(Commands.argument("model", StringArgumentType.greedyString())
                                        .suggests(ALLOWED_MODELS_SUGGEST)
                                        .executes(ctx -> setMyModel(ctx, StringArgumentType.getString(ctx, "model")))))

                        // ── /ai admin — global controls, ops only ────────────────────
                        // The whole subtree is hidden from (and refused to) anyone
                        // below the configured admin permission level.
                        .then(Commands.literal("admin")
                                .requires(AdminAccess::isAdmin)
                                .executes(AiCommands::adminHelp)
                                .then(Commands.literal("help").executes(AiCommands::adminHelp))
                                .then(Commands.literal("menu").executes(AiCommands::adminMenu))
                                .then(Commands.literal("stats").executes(AiCommands::adminStats))
                                .then(Commands.literal("list").executes(AiCommands::adminList))
                                .then(Commands.literal("killall").executes(AiCommands::adminKillAll))
                                .then(Commands.literal("disable").executes(AiCommands::adminDisable))
                                .then(Commands.literal("enable").executes(AiCommands::adminEnable))
                                .then(Commands.literal("reload").executes(AiCommands::adminReload))
                                .then(Commands.literal("maxbots")
                                        .then(Commands.argument("count", IntegerArgumentType.integer(0, 50))
                                                .executes(ctx -> adminMaxBots(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "count")))))
                                .then(Commands.literal("requirekey")
                                        .then(Commands.literal("on").executes(ctx -> adminRequireKey(ctx, true)))
                                        .then(Commands.literal("off").executes(ctx -> adminRequireKey(ctx, false))))
                                .then(Commands.literal("keylist")
                                        .executes(AiCommands::adminKeyListShow)
                                        .then(Commands.literal("list").executes(AiCommands::adminKeyListShow))
                                        .then(Commands.literal("add").then(Commands.argument("player", StringArgumentType.word())
                                                .executes(ctx -> adminKeyListAdd(ctx, StringArgumentType.getString(ctx, "player")))))
                                        .then(Commands.literal("remove").then(Commands.argument("player", StringArgumentType.word())
                                                .executes(ctx -> adminKeyListRemove(ctx, StringArgumentType.getString(ctx, "player"))))))
                                .then(Commands.literal("models")
                                        .executes(AiCommands::adminModelsShow)
                                        .then(Commands.literal("list").executes(AiCommands::adminModelsShow))
                                        .then(Commands.literal("add").then(Commands.argument("model", StringArgumentType.greedyString())
                                                .executes(ctx -> adminModelsAdd(ctx, StringArgumentType.getString(ctx, "model")))))
                                        .then(Commands.literal("remove").then(Commands.argument("model", StringArgumentType.greedyString())
                                                .suggests(ALLOWED_MODELS_SUGGEST)
                                                .executes(ctx -> adminModelsRemove(ctx, StringArgumentType.getString(ctx, "model")))))))

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
                "§f/ai summon [name] §7— bring a new assistant into the world\n" +
                "§f/ai come §7· §ffollow §7· §fstay §7· §fstop §7— basic orders\n" +
                "§f/ai locate §7— find where it is\n" +
                "§f/ai inventory §7— see what it's carrying and wearing\n" +
                "§f/ai skin <name> §7— give it a skin (built-in, or your own PNG; see /aiskins)\n" +
                "§f/ai name <name> §7— rename it\n" +
                "§f/ai personality [<id>|custom <text>] §7— change how it talks & acts\n" +
                "§f/ai <task> §7— tell it what to do (e.g. /ai build a 5x5 floor)\n" +
                "§f/ai dismiss §7— send it away\n" +
                "§6\n" +
                "§eSettings live in the panel — no confusing setting commands:\n" +
                "§f/ai panel §7— the unified menu (tabs: Settings · Admin · My Settings)\n" +
                "§f/ai mykey <token>§7 · §f/ai model <id>§7 · §f/ai mymenu §7— your own API key & model\n" +
                "§f/ai tutorial §7— a quick walkthrough of how to use Blockpal\n" +
                "§f/ai admin §7— (ops) admin panel & global controls"
        ));
        return 1;
    }

    // ── summon / dismiss ────────────────────────────────────────────────────────

    private static int summon(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        // Enforce the owner-set, server-wide bot cap (anti-grief / anti-lag).
        MinecraftServer server = player.level().getServer();
        int max = ModConfig.get().maxBotsPerServer;
        if (server != null && max > 0 && AiAssistantEntity.countAll(server) >= max) {
            player.sendSystemMessage(Component.literal(
                    "§cThis server is at its Blockpal limit (" + max + " bots). "
                            + "An admin can raise it with §f/ai admin maxbots <n>§c, "
                            + "or clear some with §f/ai admin killall§c."));
            return 0;
        }

        ServerLevel level = player.level();
        AiAssistantEntity entity = ModEntities.AI_ASSISTANT.create(level, EntitySpawnReason.COMMAND);
        if (entity == null) return 0;

        entity.setAssistantName(name);
        entity.setSkin(ModConfig.get().defaultSkin);
        entity.setOwner(player);
        entity.setPos(player.getX() + 1.5, player.getY(), player.getZ());
        entity.setMode(AiAssistantEntity.Mode.FOLLOWING);
        level.addFreshEntity(entity);

        player.sendSystemMessage(Component.literal(
                "§a" + name + ": §f\"" + entity.getPersonality().greet() + "\""));
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

    // ── personality ─────────────────────────────────────────────────────────────

    /** Suggests the available personality ids. */
    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> PERSONALITY_SUGGEST =
            (ctx, builder) -> {
                for (Personality p : Personality.values()) builder.suggest(p.id());
                return builder.buildFuture();
            };

    private static int listPersonalities(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 128);
        Personality current = ai != null ? ai.getPersonality() : Personality.fromConfig();
        boolean isCustom = ai != null && ai.isCustomPersonality();

        StringBuilder sb = new StringBuilder("§6=== Personalities ===");
        for (Personality p : Personality.values()) {
            sb.append("\n").append((!isCustom && p == current) ? "§a➤ §f" : "§7  §f").append(p.id())
                    .append(" §7— ").append(p.desc());
        }
        if (ModConfig.get().allowCustomPersonality) {
            sb.append("\n").append(isCustom ? "§a➤ §f" : "§7  §f").append("custom")
                    .append(" §7— your own description (AI-checked): §f/ai personality custom <text>");
        }
        if (ai != null) {
            sb.append("\n§7Give §f").append(ai.getAssistantName())
                    .append("§7 a new one with §f/ai personality <id>§7.");
        } else {
            sb.append("\n§7(No bot nearby — the server default is §f")
                    .append(Personality.fromConfig().id()).append("§7.)");
        }
        final String out = sb.toString();
        player.sendSystemMessage(Component.literal(out));
        return 1;
    }

    private static int setCustomPersonality(CommandContext<CommandSourceStack> ctx, String text) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 128);
        if (ai == null) return noAi(player);
        ai.requestCustomPersonality(text, player);   // async safety check, then applies
        return 1;
    }

    private static int setPersonality(CommandContext<CommandSourceStack> ctx, String id) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 128);
        if (ai == null) return noAi(player);

        Personality p = Personality.byId(id);
        if (p == null) {
            player.sendSystemMessage(Component.literal(
                    "§cUnknown personality §f'" + id + "'§c. See §f/ai personality§c for the list."));
            return 0;
        }
        ai.setPersonality(p);
        player.sendSystemMessage(Component.literal(
                "§a" + ai.getAssistantName() + " is now §f" + p.display() + "§a — " + p.desc()));
        ai.broadcastMessage(p.greet());   // a line in the new voice, so the change is felt
        return 1;
    }

    // ── re-enable after the FPS kill switch ─────────────────────────────────────

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

    // ── config menu ───────────────────────────────────────────────────────────

    private static int openMenu(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        if (denyIfNotAdmin(ctx)) return 0;

        if (!ServerPlayNetworking.canSend(player, ConfigSyncPayload.TYPE)) {
            player.sendSystemMessage(Component.literal(
                    "§eThe settings menu needs the Blockpal mod on your client. "
                            + "On a vanilla client, use §f/ai admin§e for text-based controls."));
            return 0;
        }
        ServerPlayNetworking.send(player, new ConfigSyncPayload(ConfigData.fromConfig()));
        player.sendSystemMessage(Component.literal("§7Opening the Blockpal menu…"));
        return 1;
    }

    /** Suggests the server's allowed models for model arguments. */
    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> ALLOWED_MODELS_SUGGEST =
            (ctx, builder) -> {
                for (String m : ModConfig.get().allowedModels) builder.suggest(m);
                return builder.buildFuture();
            };

    // ── /ai <task> ────────────────────────────────────────────────────────────

    private static int doTask(CommandContext<CommandSourceStack> ctx, String task) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 128);
        if (ai == null) return noAi(player);

        if (!ai.hasUsableApiKey()) {
            player.sendSystemMessage(Component.literal(ModConfig.get().requireOwnApiKey
                    ? "§c[AI] You need your own API key — set it in §f/ai mymenu§c or with §f/ai mykey <token>§c."
                    : "§c[AI] No API key set yet. An admin can add one in §f/ai menu§c (AI tab)."));
            return 0;
        }

        ai.giveTask(task, player);
        return 1;
    }

    // ── admin (/ai admin …) — ops only ──────────────────────────────────────────

    private static int adminHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§6=== Blockpal Admin (ops only) ===\n" +
                "§f/ai admin menu §7— open the visual admin panel\n" +
                "§f/ai admin stats §7— bots, players, FPS, mod status\n" +
                "§f/ai admin list §7— every bot and where it is\n" +
                "§f/ai admin killall §7— remove all bots on the server\n" +
                "§f/ai admin maxbots <0-50> §7— cap bots per server (0 = unlimited)\n" +
                "§f/ai admin disable§7 / §fenable §7— turn all bots off / on\n" +
                "§f/ai admin reload §7— reload config from disk\n" +
                "§f/ai admin requirekey on|off §7— make players use their own API key\n" +
                "§f/ai admin keylist add|remove|list <player> §7— who may use the shared key\n" +
                "§f/ai admin models add|remove|list <id> §7— models players may pick\n" +
                "§7Admin tier: §f/ai settings admin_level <0-4>§7 (default: ops = 2)"), false);
        return 1;
    }

    private static int adminMenu(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§7Run §f/ai admin stats§7 from the console for a text summary."), false);
            return 0;
        }
        if (!ServerPlayNetworking.canSend(player, AdminSyncPayload.TYPE)) {
            player.sendSystemMessage(Component.literal(
                    "§eThe admin menu needs the Blockpal mod on your client. "
                            + "Use §f/ai admin stats§e instead."));
            return 0;
        }
        AiNetworking.openAdminMenuFor(player);
        player.sendSystemMessage(Component.literal("§7Opening the Blockpal admin menu…"));
        return 1;
    }

    private static int adminStats(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        if (server == null) return 0;
        AdminStatsData d = AdminStatsData.gather(server);

        StringBuilder sb = new StringBuilder("§6=== Blockpal Admin — Stats ===");
        sb.append("\n§eBots:           §f").append(d.totalBots()).append(" §7/ ")
                .append(d.maxBots() == 0 ? "unlimited" : d.maxBots());
        sb.append("\n§eMod status:     ").append(d.modDisabled()
                ? "§cDISABLED §7(/ai resume)" : "§aactive");
        sb.append("\n§eAllow commands: §f").append(d.allowCommands()
                ? "on (lvl " + d.commandLevel() + ")" : "off");
        sb.append("\n§eAdmin level:    §f").append(d.adminLevel());
        sb.append("\n§eAPI token:      §f").append(d.tokenSet()
                ? ("set ✓" + (d.tokenFromEnv() ? " §7(from env)" : "")) : "§cnot set");
        sb.append("\n§ePlayers online (§f").append(d.players().size()).append("§e):");
        if (d.players().isEmpty()) sb.append(" §7none");
        for (AdminStatsData.PlayerRow p : d.players()) {
            sb.append("\n§f  ").append(p.name()).append(" §7bots:§f ").append(p.bots())
                    .append(" §7fps:§f ").append(p.fps() < 0 ? "?" : p.fps());
        }
        sb.append("\n§7Open the visual panel with §f/ai admin menu§7.");

        final String out = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(out), false);
        return 1;
    }

    private static int adminList(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        if (server == null) return 0;
        AdminStatsData d = AdminStatsData.gather(server);

        StringBuilder sb = new StringBuilder("§6=== Blockpal — Bots (" + d.bots().size() + ") ===");
        if (d.bots().isEmpty()) sb.append("\n§7No bots currently loaded in the world.");
        for (AdminStatsData.BotRow b : d.bots()) {
            sb.append("\n§f").append(b.name()).append(" §7(").append(b.owner()).append(") §7— ")
                    .append(b.mode().toLowerCase(java.util.Locale.ROOT)).append(" §7— ").append(b.dim())
                    .append(" §7— hp ").append(b.health())
                    .append(" §7@ ").append(b.x()).append(",").append(b.y()).append(",").append(b.z());
        }
        final String out = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(out), false);
        return 1;
    }

    private static int adminKillAll(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        if (server == null) return 0;
        int n = AiAssistantEntity.killAll(server);
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "§c[Blockpal] An admin removed all bots (" + n + ")."), false);
        return 1;
    }

    private static int adminDisable(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        if (server == null) return 0;
        com.milkdromeda.blockpal.EmergencyState.setDisabled(true);
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "§c[Blockpal] Bots disabled by an admin. Use §e/ai resume§c (or /ai admin enable) to re-enable."), false);
        return 1;
    }

    private static int adminEnable(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        if (server == null) return 0;
        com.milkdromeda.blockpal.EmergencyState.setDisabled(false);
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "§a[Blockpal] Bots re-enabled by an admin."), false);
        return 1;
    }

    private static int adminReload(CommandContext<CommandSourceStack> ctx) {
        ModConfig.load();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[Blockpal] Reloaded config from disk."), false);
        return 1;
    }

    private static int adminMaxBots(CommandContext<CommandSourceStack> ctx, int count) {
        ModConfig.get().maxBotsPerServer = count;   // arg already constrained to 0..50
        ModConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[Blockpal] Max bots per server = " + (count == 0 ? "unlimited" : count)), false);
        return 1;
    }

    // ── personal API key & model (any player manages their own) ─────────────────

    private static int myKeyStatus(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        ModConfig cfg = ModConfig.get();
        boolean has = cfg.hasPlayerToken(player.getUUID());
        StringBuilder sb = new StringBuilder("§6Your Blockpal API key: ")
                .append(has ? "§aset ✓" : "§7not set");
        if (cfg.requireOwnApiKey) {
            boolean wl = cfg.isKeyWhitelisted(player.getName().getString(), player.getUUID());
            sb.append("\n§7This server asks players to use their own key")
                    .append(wl ? " — but you're whitelisted to use the shared key." : ".");
            if (!has && !wl) sb.append("\n§eSet one with §f/ai mykey <token>§e to use AI features.");
        } else {
            sb.append("\n§7The server provides a shared key; set your own to use it (and your own bill) instead.");
        }
        final String out = sb.toString();
        player.sendSystemMessage(Component.literal(out));
        return 1;
    }

    private static int setMyKey(CommandContext<CommandSourceStack> ctx, String token) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        ModConfig.get().setPlayerToken(player.getUUID(), token);
        ModConfig.save();
        player.sendSystemMessage(Component.literal(
                "§aSaved your personal API key ✓ §7(stored obfuscated, never shown to others).\n"
                        + "§7Heads-up: typing a token in chat can expose it — consider §f/ai mymenu§7 instead."));
        return 1;
    }

    private static int myKeyClear(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        ModConfig.get().setPlayerToken(player.getUUID(), "");
        ModConfig.save();
        player.sendSystemMessage(Component.literal("§aCleared your personal API key."));
        return 1;
    }

    private static int listModels(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        ModConfig cfg = ModConfig.get();
        String current = cfg.resolveModelFor(player.getUUID());
        StringBuilder sb = new StringBuilder("§6Available models:");
        for (String m : cfg.allowedModels) {
            sb.append("\n").append(m.equals(current) ? "§a➤ " : "§7  ").append(m);
        }
        if (!cfg.allowPlayerModelChoice) {
            sb.append("\n§7(Model choice is off here — everyone uses §f").append(cfg.hfModel).append("§7.)");
        } else {
            sb.append("\n§7Pick one with §f/ai model <id>§7 or §f/ai mymenu§7.");
        }
        final String out = sb.toString();
        player.sendSystemMessage(Component.literal(out));
        return 1;
    }

    private static int setMyModel(CommandContext<CommandSourceStack> ctx, String model) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        ModConfig cfg = ModConfig.get();
        if (!cfg.allowPlayerModelChoice) {
            player.sendSystemMessage(Component.literal("§cThis server doesn't allow choosing your own model."));
            return 0;
        }
        String m = model.trim();
        if (!cfg.isModelAllowed(m)) {
            player.sendSystemMessage(Component.literal(
                    "§cThat model isn't on the allowed list — see §f/ai models§c."));
            return 0;
        }
        cfg.setPlayerModel(player.getUUID(), m);
        ModConfig.save();
        player.sendSystemMessage(Component.literal("§aYour bot will now use §f" + m + "§a."));
        return 1;
    }

    private static int openPlayerMenu(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        if (!AiNetworking.openPlayerMenuFor(player)) {
            player.sendSystemMessage(Component.literal(
                    "§eThat menu needs the Blockpal mod on your client. Use §f/ai mykey§e and §f/ai model§e instead."));
            return 0;
        }
        return 1;
    }

    /** One entry point to the unified panel: the admin panel for ops, else the personal one. */
    private static int openPanel(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        if (AdminAccess.isAdmin(player)) {
            if (!ServerPlayNetworking.canSend(player, AdminSyncPayload.TYPE)) {
                player.sendSystemMessage(Component.literal("§eThe panel needs the Blockpal mod on your client."));
                return 0;
            }
            AiNetworking.openAdminMenuFor(player);
            return 1;
        }
        if (!AiNetworking.openPlayerMenuFor(player)) {
            player.sendSystemMessage(Component.literal(
                    "§eThe panel needs the Blockpal mod on your client. Use §f/ai mykey§e and §f/ai model§e instead."));
            return 0;
        }
        return 1;
    }

    /** Opens the how-to tutorial screen, or prints a text version on a vanilla client. */
    private static int openTutorial(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        if (!AiNetworking.openTutorialFor(player)) {
            player.sendSystemMessage(Component.literal(TUTORIAL_TEXT));
        }
        return 1;
    }

    private static final String TUTORIAL_TEXT =
            "§6=== Welcome to Blockpal ===\n" +
            "§71) §fSpawn your companion: §a/ai summon\n" +
            "§72) §fJust talk in chat — \"follow me\", \"come\", \"stay\", \"stop\", or ask it to build/mine/fight.\n" +
            "§73) §fGive a task directly: §a/ai <task>§7 (e.g. /ai build a 5x5 floor).\n" +
            "§74) §fSettings are all in one panel: §a/ai panel§7 (tabs: Settings · Admin · My Settings).\n" +
            "§75) §fAI needs a key: an admin sets one in the panel, or bring your own with §a/ai mykey <token>§7.\n" +
            "§7Open this again any time with §a/ai tutorial§7.";

    // ── admin: bring-your-own-key controls & the model list ─────────────────────

    private static int adminRequireKey(CommandContext<CommandSourceStack> ctx, boolean on) {
        ModConfig.get().requireOwnApiKey = on;
        ModConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[Blockpal] Players must use their own API key: " + (on ? "§eON" : "§7off")
                        + (on ? " §7(exempt trusted players with §f/ai admin keylist add <player>§7)" : "")), false);
        return 1;
    }

    private static int adminKeyListShow(CommandContext<CommandSourceStack> ctx) {
        java.util.List<String> wl = ModConfig.get().ownKeyWhitelist;
        StringBuilder sb = new StringBuilder("§6Own-key whitelist (may use the shared key) — "
                + wl.size() + " entr" + (wl.size() == 1 ? "y" : "ies") + ":");
        if (wl.isEmpty()) sb.append("\n§7  (empty — everyone must bring their own key when required)");
        for (String e : wl) sb.append("\n§f  ").append(e);
        final String out = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(out), false);
        return 1;
    }

    private static int adminKeyListAdd(CommandContext<CommandSourceStack> ctx, String pl) {
        boolean added = ModConfig.get().addKeyWhitelist(pl);
        ModConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal(added
                ? "§a[Blockpal] Added §f" + pl + "§a to the own-key whitelist."
                : "§7[Blockpal] §f" + pl + "§7 was already whitelisted."), false);
        return 1;
    }

    private static int adminKeyListRemove(CommandContext<CommandSourceStack> ctx, String pl) {
        boolean removed = ModConfig.get().removeKeyWhitelist(pl);
        ModConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal(removed
                ? "§a[Blockpal] Removed §f" + pl + "§a from the own-key whitelist."
                : "§7[Blockpal] §f" + pl + "§7 wasn't on the whitelist."), false);
        return 1;
    }

    private static int adminModelsShow(CommandContext<CommandSourceStack> ctx) {
        ModConfig cfg = ModConfig.get();
        StringBuilder sb = new StringBuilder("§6Allowed models (" + cfg.allowedModels.size() + "):");
        for (String m : cfg.allowedModels) {
            sb.append("\n§f  ").append(m).append(m.equals(cfg.hfModel) ? " §7(server default)" : "");
        }
        sb.append("\n§7Add/remove with §f/ai admin models add|remove <id>§7. Player choice: ")
                .append(cfg.allowPlayerModelChoice ? "§aon" : "§7off");
        final String out = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(out), false);
        return 1;
    }

    private static int adminModelsAdd(CommandContext<CommandSourceStack> ctx, String model) {
        boolean added = ModConfig.get().addAllowedModel(model);
        ModConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal(added
                ? "§a[Blockpal] Added model §f" + model.trim()
                : "§7[Blockpal] That model is already allowed."), false);
        return 1;
    }

    private static int adminModelsRemove(CommandContext<CommandSourceStack> ctx, String model) {
        ModConfig cfg = ModConfig.get();
        String m = model.trim();
        if (m.equals(cfg.hfModel)) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§c[Blockpal] Can't remove the server default model — change it with /ai settings model <id> first."), false);
            return 0;
        }
        boolean removed = cfg.removeAllowedModel(m);
        ModConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal(removed
                ? "§a[Blockpal] Removed model §f" + m
                : "§7[Blockpal] That model wasn't on the list."), false);
        return 1;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Politely refuses a config change for non-admins; true when it denied. */
    private static boolean denyIfNotAdmin(CommandContext<CommandSourceStack> ctx) {
        if (AdminAccess.isAdmin(ctx.getSource())) return false;
        ServerPlayer player = getPlayer(ctx);
        if (player != null) {
            player.sendSystemMessage(Component.literal(
                    "§cOnly server admins can change Blockpal's settings. Ask an operator "
                            + "(or have one raise §f/ai settings admin_level§c)."));
        }
        return true;
    }

    private static ServerPlayer getPlayer(CommandContext<CommandSourceStack> ctx) {
        try { return ctx.getSource().getPlayerOrException(); } catch (Exception e) { return null; }
    }

    private static int noAi(ServerPlayer player) {
        player.sendSystemMessage(Component.literal(
                "§cNo AI assistant nearby. Summon one with §f/ai summon"));
        return 0;
    }
}
