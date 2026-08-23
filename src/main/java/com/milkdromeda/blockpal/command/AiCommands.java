package com.milkdromeda.blockpal.command;

import com.milkdromeda.blockpal.ModEntities;
import com.milkdromeda.blockpal.admin.AdminAccess;
import com.milkdromeda.blockpal.ai.AiConnection;
import com.milkdromeda.blockpal.ai.Personality;
import com.milkdromeda.blockpal.compat.BedrockSupport;
import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import com.milkdromeda.blockpal.entity.TrustEntry;
import com.milkdromeda.blockpal.minigame.GameMode;
import com.milkdromeda.blockpal.minigame.GameSession;
import com.milkdromeda.blockpal.minigame.MinigameManager;
import com.milkdromeda.blockpal.network.AdminStatsData;
import com.milkdromeda.blockpal.network.AdminSyncPayload;
import com.milkdromeda.blockpal.network.AiNetworking;
import com.milkdromeda.blockpal.network.ConfigData;
import com.milkdromeda.blockpal.network.ConfigSyncPayload;
import com.milkdromeda.blockpal.network.PossessionSyncPayload;
import com.milkdromeda.blockpal.possession.PossessionManager;
import com.milkdromeda.blockpal.util.Locator;
import com.milkdromeda.blockpal.voice.VoiceLinkManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import java.util.UUID;
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

                        // Possession mode: hand your own character's controls to your
                        // nearby companion. Opens a console on a Java client; on any
                        // client you can steer it with text ("/ai possess <what to do>").
                        .then(Commands.literal("possess")
                                .executes(AiCommands::possessStart)
                                .then(Commands.literal("stop").executes(AiCommands::possessStop))
                                .then(Commands.argument("instruction", StringArgumentType.greedyString())
                                        .executes(ctx -> possessDo(ctx, StringArgumentType.getString(ctx, "instruction")))))

                        // ── the ONE AI connection, and the MCP setup guide ──────────
                        .then(Commands.literal("connection")
                                .executes(AiCommands::connectionShow)
                                .then(connectionArgs()))
                        .then(mcpCommand())

                        // Look through the bot's eyes / hand it a script yourself.
                        .then(Commands.literal("look").executes(AiCommands::lookThroughEyes))
                        .then(Commands.literal("code")
                                .executes(AiCommands::codeHelp)
                                .then(Commands.literal("stop").executes(AiCommands::codeStop))
                                .then(Commands.argument("script", StringArgumentType.greedyString())
                                        .executes(ctx -> runCode(ctx, StringArgumentType.getString(ctx, "script")))))

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

                        // ── per-bot management & trust (owner) ───────────────────────
                        // List every companion you own, and choose who else may command
                        // each one. Trust is per-bot, so your companions can differ.
                        .then(Commands.literal("bots").executes(AiCommands::listBots))
                        .then(Commands.literal("trust")
                                .executes(AiCommands::trustShow)
                                .then(Commands.literal("list").executes(AiCommands::trustShow))
                                .then(Commands.literal("clear").executes(AiCommands::trustClear))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(ONLINE_PLAYERS_SUGGEST)
                                        .executes(ctx -> trustAdd(ctx, StringArgumentType.getString(ctx, "player")))))
                        .then(Commands.literal("untrust")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(TRUSTED_PLAYERS_SUGGEST)
                                        .executes(ctx -> trustRemove(ctx, StringArgumentType.getString(ctx, "player")))))

                        // ── voice: who hears your agent, and what it sounds like ─────
                        // By default only YOU hear your companion. Sharing lets a friend
                        // hear it too — and links your agents into one conversation
                        // (they take turns instead of talking over each other).
                        .then(Commands.literal("voice")
                                .executes(AiCommands::voiceStatus)
                                .then(Commands.literal("list").executes(AiCommands::voiceList))
                                .then(Commands.literal("share")
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests(ONLINE_PLAYERS_SUGGEST)
                                                .executes(ctx -> voiceShare(ctx, StringArgumentType.getString(ctx, "player")))))
                                .then(Commands.literal("unshare")
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .executes(ctx -> voiceUnshare(ctx, StringArgumentType.getString(ctx, "player")))))
                                .then(Commands.literal("clear").executes(AiCommands::voiceClear))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("voice", StringArgumentType.word())
                                                .suggests(VOICE_SUGGEST)
                                                .executes(ctx -> voiceSet(ctx, StringArgumentType.getString(ctx, "voice"))))))

                        // Give the nearby bot a personality (how it talks + the tone of its plans).
                        .then(Commands.literal("personality")
                                .executes(AiCommands::listPersonalities)
                                .then(Commands.literal("custom")
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(ctx -> setCustomPersonality(ctx, StringArgumentType.getString(ctx, "text")))))
                                .then(Commands.argument("personality", StringArgumentType.word())
                                        .suggests(PERSONALITY_SUGGEST)
                                        .executes(ctx -> setPersonality(ctx, StringArgumentType.getString(ctx, "personality")))))

                        // ── mini-games: play a game mode with your party and your bot ─
                        // Chained, Same Health, One Block, Fusion, and Growth (the AI
                        // village). Server-side, so Java and Bedrock players use it the same.
                        .then(Commands.literal("minigame")
                                .executes(AiCommands::minigameList)
                                .then(Commands.literal("list").executes(AiCommands::minigameList))
                                .then(Commands.literal("stop").executes(AiCommands::minigameStop))
                                .then(Commands.literal("start")
                                        .then(Commands.argument("mode", StringArgumentType.word())
                                                .suggests(MINIGAME_MODES)
                                                .executes(ctx -> minigameStart(ctx,
                                                        StringArgumentType.getString(ctx, "mode"))))))

                        // Configuration lives in the in-game panel now — no confusing
                        // per-setting commands. /ai menu (or /ai panel) opens it;
                        // /ai tutorial walks new players through everything.
                        // ── PVT: learning to act by watching ─────────────────────────
                        .then(Commands.literal("pvt")
                                .executes(AiCommands::pvtStatus)
                                .then(Commands.literal("status").executes(AiCommands::pvtStatus))
                                .then(Commands.literal("watch")
                                        .then(Commands.literal("on").executes(ctx -> pvtWatch(ctx, true)))
                                        .then(Commands.literal("off").executes(ctx -> pvtWatch(ctx, false))))
                                .then(Commands.literal("record")
                                        .then(Commands.literal("start").executes(ctx -> pvtRecord(ctx, true)))
                                        .then(Commands.literal("stop").executes(ctx -> pvtRecord(ctx, false))))
                                .then(Commands.literal("train").executes(AiCommands::pvtTrain))
                                .then(Commands.literal("clear").executes(AiCommands::pvtClear))
                                .then(Commands.literal("use")
                                        .then(Commands.literal("on").executes(ctx -> pvtUse(ctx, true)))
                                        .then(Commands.literal("off").executes(ctx -> pvtUse(ctx, false)))))

                        // ── how fast it acts, and how well it fights ─────────────────
                        .then(Commands.literal("speed")
                                .executes(AiCommands::speedShow)
                                .then(Commands.argument("tempo", StringArgumentType.word())
                                        .executes(AiCommands::speedSet)))

                        .then(Commands.literal("combat")
                                .executes(AiCommands::combatShow)
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .executes(AiCommands::combatSet)))

                        .then(Commands.literal("attack")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(AiCommands::attackPlayer)))

                        // ── lining up work ───────────────────────────────────────────
                        .then(Commands.literal("queue")
                                .executes(AiCommands::queueList)
                                .then(Commands.literal("list").executes(AiCommands::queueList))
                                .then(Commands.literal("clear").executes(AiCommands::queueClear))
                                .then(Commands.argument("task", StringArgumentType.greedyString())
                                        .executes(AiCommands::queueAdd)))

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
                                // Text-based AI config so admins on a Bedrock/vanilla
                                // client (no Java GUI) can still set the key, endpoint
                                // and default model. The visual panel covers the same.
                                .then(Commands.literal("token")
                                        .then(Commands.argument("token", StringArgumentType.greedyString())
                                                .executes(ctx -> adminSetToken(ctx, StringArgumentType.getString(ctx, "token")))))
                                .then(Commands.literal("apiurl")
                                        .then(Commands.argument("url", StringArgumentType.greedyString())
                                                .executes(ctx -> adminSetApiUrl(ctx, StringArgumentType.getString(ctx, "url")))))
                                // One-click provider presets: swap the endpoint + a matching
                                // default model for HuggingFace / ChatGPT / Claude / Gemini / Grok.
                                .then(providerCommand())
                                .then(Commands.literal("model")
                                        .then(Commands.argument("model", StringArgumentType.greedyString())
                                                .suggests(ALLOWED_MODELS_SUGGEST)
                                                .executes(ctx -> adminSetModel(ctx, StringArgumentType.getString(ctx, "model")))))
                                .then(Commands.literal("maxbots")
                                        .then(Commands.argument("count", IntegerArgumentType.integer(0, 50))
                                                .executes(ctx -> adminMaxBots(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "count")))))
                                .then(Commands.literal("pvp")
                                        .then(Commands.literal("on").executes(ctx -> adminPvp(ctx, true)))
                                        .then(Commands.literal("off").executes(ctx -> adminPvp(ctx, false))))
                                .then(Commands.literal("requirekey")
                                        .then(Commands.literal("on").executes(ctx -> adminRequireKey(ctx, true)))
                                        .then(Commands.literal("off").executes(ctx -> adminRequireKey(ctx, false))))
                                .then(Commands.literal("possession")
                                        .then(Commands.literal("on").executes(ctx -> adminPossession(ctx, true)))
                                        .then(Commands.literal("off").executes(ctx -> adminPossession(ctx, false))))
                                .then(Commands.literal("voice")
                                        .then(Commands.literal("on").executes(ctx -> adminVoice(ctx, true)))
                                        .then(Commands.literal("off").executes(ctx -> adminVoice(ctx, false))))
                                // Local Ollama (custom local models) — keyless local AI.
                                .then(Commands.literal("ollama")
                                        .executes(AiCommands::adminOllamaShow)
                                        .then(Commands.literal("on").executes(ctx -> adminOllama(ctx, true)))
                                        .then(Commands.literal("off").executes(ctx -> adminOllama(ctx, false)))
                                        .then(Commands.literal("url").then(Commands.argument("url", StringArgumentType.greedyString())
                                                .executes(ctx -> adminOllamaUrl(ctx, StringArgumentType.getString(ctx, "url")))))
                                        .then(Commands.literal("model").then(Commands.argument("model", StringArgumentType.greedyString())
                                                .executes(ctx -> adminOllamaModel(ctx, StringArgumentType.getString(ctx, "model")))))
                                        .then(Commands.literal("models")
                                                .executes(AiCommands::adminOllamaModelsShow)
                                                .then(Commands.literal("list").executes(AiCommands::adminOllamaModelsShow))
                                                .then(Commands.literal("add").then(Commands.argument("model", StringArgumentType.greedyString())
                                                        .executes(ctx -> adminOllamaModelsAdd(ctx, StringArgumentType.getString(ctx, "model")))))
                                                .then(Commands.literal("remove").then(Commands.argument("model", StringArgumentType.greedyString())
                                                        .executes(ctx -> adminOllamaModelsRemove(ctx, StringArgumentType.getString(ctx, "model")))))))
                                // Player2 (player2.game) — the easiest keyless local AI (just install its app).
                                .then(Commands.literal("player2")
                                        .executes(AiCommands::adminPlayer2Show)
                                        .then(Commands.literal("on").executes(ctx -> adminPlayer2(ctx, true)))
                                        .then(Commands.literal("off").executes(ctx -> adminPlayer2(ctx, false)))
                                        .then(Commands.literal("url").then(Commands.argument("url", StringArgumentType.greedyString())
                                                .executes(ctx -> adminPlayer2Url(ctx, StringArgumentType.getString(ctx, "url"))))))
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

    /**
     * The {@code /ai admin provider [<name>]} node: bare lists the presets and the
     * current one, each provider id (huggingface/chatgpt/claude/gemini/grok) switches
     * the endpoint + default model (and pre-fills a default key when the preset has one).
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> providerCommand() {
        var node = Commands.literal("provider").executes(AiCommands::adminProviderShow);
        for (com.milkdromeda.blockpal.ai.ProviderPreset p : com.milkdromeda.blockpal.ai.ProviderPreset.values()) {
            node.then(Commands.literal(p.id()).executes(ctx -> adminProvider(ctx, p)));
        }
        return node;
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
                "§f/ai possess §7— let it drive YOUR character (a console to type instructions)\n" +
                "§f/ai look §7— read what it can see right now (its own eyes, not the map)\n" +
                "§f/ai code <script> §7— hand it a script in the language its AI writes\n" +
                "§f/ai locate §7— find where it is\n" +
                "§f/ai inventory §7— see what it's carrying and wearing\n" +
                "§f/ai skin <name> §7— give it a skin (built-in, or your own PNG; see /aiskins)\n" +
                "§f/ai name <name> §7— rename it\n" +
                "§f/ai personality [<id>|custom <text>] §7— change how it talks & acts\n" +
                "§f/ai bots §7— list every companion you own (manage each separately)\n" +
                "§f/ai trust <player> §7· §funtrust <player> §7— let friends command this bot\n" +
                "§f/ai voice §7— hold §fV§7 to TALK to it; share/link voices, pick its voice\n" +
                "§f/ai <task> §7— tell it what to do (e.g. /ai build a 5x5 floor)\n" +
                "§f/ai queue <task> §7— line jobs up; it works through them in order\n" +
                "§f/ai speed [instant|fast|human] §7— how quickly it reacts\n" +
                "§f/ai combat [basic|skilled|expert] §7— how well it fights\n" +
                "§f/ai attack <player> §7— (owner) point it at someone, if this server allows it\n" +
                "§f/ai pvt §7— teach it by letting it watch you play (see below)\n" +
                "§f/ai dismiss §7— send it away\n" +
                "§f/ai minigame [start <mode>|list|stop] §7— play a game mode with your party & bot\n" +
                "§f/village start §7— play §fGrowth§7: an AI village that grows on its own (also §f/ai minigame start growth§7)\n" +
                "§6\n" +
                "§eSettings live in the panel — no confusing setting commands:\n" +
                "§f/ai panel §7— the unified menu (tabs: Settings · Admin · My Settings)\n" +
                "§f/ai connection §7— which ONE AI this server uses (only one at a time)\n" +
                "§f/ai mcp §7— connect Claude, ChatGPT, Grok or Gemini to this world\n" +
                "§f/ai mykey <token>§7 · §f/ai model <id>§7 · §f/ai mymenu §7— your own API key & model\n" +
                "§f/ai tutorial §7— a quick walkthrough of how to use Blockpal\n" +
                "§f/ai admin §7— (ops) admin panel & global controls\n" +
                "§6\n" +
                "§eTeach it by example (PVT — it learns from watching you play):\n" +
                "§f/ai pvt watch on §7— let it learn from how YOU play (opt-in, off by default)\n" +
                "§f/ai pvt status §7— what it has banked and whether it has learned anything yet\n" +
                "§f/ai pvt train §7— (ops) train a policy from what people have played\n" +
                "§f/ai pvt use on §7— (ops) let companions act on what they learned"
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
        if (!ensureCanManage(player, ai)) return 0;

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
        if (!ensureCanCommand(player, ai)) return 0;
        ai.comeTo(player);
        return 1;
    }

    private static int follow(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 128);
        if (ai == null) return noAi(player);
        if (!ensureCanCommand(player, ai)) return 0;
        ai.followPlayer();
        return 1;
    }

    private static int stay(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 128);
        if (ai == null) return noAi(player);
        if (!ensureCanCommand(player, ai)) return 0;
        ai.stayHere();
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 128);
        if (ai == null) return noAi(player);
        if (!ensureCanCommand(player, ai)) return 0;
        ai.stopTask();
        return 1;
    }

    // ── possession mode ───────────────────────────────────────────────────────

    /** Starts possessing yourself with your nearby companion (opens the console on Java). */
    private static int possessStart(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        PossessionManager.start(player);
        // On a client without the console GUI, point them at the text controls.
        if (!ServerPlayNetworking.canSend(player, PossessionSyncPayload.TYPE)) {
            player.sendSystemMessage(Component.literal(
                    "§7Steer it with §f/ai possess <what to do>§7 and end it with §f/ai possess stop§7."));
        }
        return 1;
    }

    private static int possessStop(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        PossessionManager.stop(player);
        return 1;
    }

    /** Queues a possession instruction (starting possession first if needed). */
    private static int possessDo(CommandContext<CommandSourceStack> ctx, String instruction) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        PossessionManager.queue(player, instruction);
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
        if (!ensureCanCommand(player, ai)) return 0;
        player.sendSystemMessage(Component.literal("§b" + ai.getAssistantName() + ": §f\"" + Locator.describe(player, ai) + "\""));
        return 1;
    }

    private static int inventory(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 128);
        if (ai == null) return noAi(player);
        if (!ensureCanCommand(player, ai)) return 0;
        player.sendSystemMessage(Component.literal(ai.describeInventory()));
        return 1;
    }

    private static int rename(CommandContext<CommandSourceStack> ctx, String newName) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 64);
        if (ai == null) return noAi(player);
        if (!ensureCanManage(player, ai)) return 0;

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
        if (!ensureCanManage(player, ai)) return 0;

        ai.setSkin(skin);
        player.sendSystemMessage(Component.literal(
                "§aSkin set to §f" + skin + "§a. §7Built-ins: default, robot, void, "
                        + "slate, ember, forest, amethyst. Drop your own PNG in "
                        + "config/blockpal/skins/ and run §f/aiskins list§7."));
        return 1;
    }

    // ── personality ─────────────────────────────────────────────────────────────

    /** Suggests the available personality ids. */
    // ── mini-games (/ai minigame …) ──────────────────────────────────────────────

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> MINIGAME_MODES =
            (ctx, builder) -> {
                for (GameMode m : GameMode.values()) builder.suggest(m.id);
                return builder.buildFuture();
            };

    private static int minigameList(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = getPlayer(ctx);
        if (p == null) return 0;
        StringBuilder sb = new StringBuilder("§6=== Mini-games ===");
        GameSession current = MinigameManager.sessionOf(p);
        if (current != null) {
            sb.append("\n§aYou're playing §f").append(current.mode.display)
                    .append("§7 — stop with §f/ai minigame stop§7.");
        }
        for (GameMode m : GameMode.values()) {
            sb.append("\n§e").append(m.id).append(" §7— ").append(m.desc);
        }
        sb.append("\n§7Start one for your party with §f/ai minigame start <mode>§7 (see §f/party§7).");
        final String out = sb.toString();
        p.sendSystemMessage(Component.literal(out));
        return 1;
    }

    private static int minigameStart(CommandContext<CommandSourceStack> ctx, String modeId) {
        ServerPlayer p = getPlayer(ctx);
        if (p == null) return 0;
        GameMode mode = GameMode.byId(modeId);
        if (mode == null) {
            p.sendSystemMessage(Component.literal(
                    "§cUnknown mode §f'" + modeId + "'§c. See §f/ai minigame list§c."));
            return 0;
        }
        MinigameManager.start(p, mode);
        return 1;
    }

    private static int minigameStop(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = getPlayer(ctx);
        if (p == null) return 0;
        MinigameManager.stop(p);
        return 1;
    }

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
        if (!ensureCanManage(player, ai)) return 0;
        ai.requestCustomPersonality(text, player);   // async safety check, then applies
        return 1;
    }

    private static int setPersonality(CommandContext<CommandSourceStack> ctx, String id) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;

        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 128);
        if (ai == null) return noAi(player);
        if (!ensureCanManage(player, ai)) return 0;

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

    // ── per-bot management & trust ──────────────────────────────────────────────

    /** Suggests online players (other than yourself) for /ai trust <player>. */
    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> ONLINE_PLAYERS_SUGGEST =
            (ctx, builder) -> {
                MinecraftServer server = ctx.getSource().getServer();
                ServerPlayer self = getPlayer(ctx);
                if (server != null) {
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        if (self == null || !p.getUUID().equals(self.getUUID())) {
                            builder.suggest(p.getName().getString());
                        }
                    }
                }
                return builder.buildFuture();
            };

    /** Suggests the players already trusted on your nearby owned bot, for /ai untrust. */
    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> TRUSTED_PLAYERS_SUGGEST =
            (ctx, builder) -> {
                ServerPlayer self = getPlayer(ctx);
                if (self != null) {
                    AiAssistantEntity ai = AiAssistantEntity.findOwnedFor(self, 64);
                    if (ai != null) {
                        for (TrustEntry e : ai.trustedEntries()) {
                            if (e.name() != null && !e.name().isBlank()) builder.suggest(e.name());
                        }
                    }
                }
                return builder.buildFuture();
            };

    /** Opens the visual Bots manager if the client supports it, else lists owned bots as text. */
    private static int listBots(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        MinecraftServer server = player.level().getServer();
        if (server == null) return 0;
        // Java client with the mod → open the visual panel (shows every bot + owner).
        if (AiNetworking.openBotsFor(player)) return 1;
        // Bedrock/vanilla fallback: a text list of the player's own bots.
        List<AiAssistantEntity> mine = AiAssistantEntity.ownedBy(server, player.getUUID());
        if (mine.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "§7You don't have any companions out. Summon one with §f/ai summon§7."));
            return 1;
        }
        StringBuilder sb = new StringBuilder("§6=== Your companions (" + mine.size() + ") ===");
        for (AiAssistantEntity ai : mine) {
            sb.append("\n§f").append(ai.getAssistantName())
                    .append(" §7— ").append(ai.getMode().name().toLowerCase(java.util.Locale.ROOT))
                    .append(" §7— ").append(ai.level().dimension().identifier().getPath())
                    .append(" §7@ ").append((int) ai.getX()).append(",").append((int) ai.getY()).append(",").append((int) ai.getZ())
                    .append(" §7— hp ").append((int) ai.getHealth()).append("/").append((int) ai.getMaxHealth())
                    .append(" §7— ").append(ai.getPersonalityLabel())
                    .append(" §7— trusted: §f").append(ai.trustedCount());
        }
        sb.append("\n§7Stand near one and use §f/ai name§7, §f/ai skin§7, §f/ai personality§7 or §f/ai trust§7 to manage it.");
        final String out = sb.toString();
        player.sendSystemMessage(Component.literal(out));
        return 1;
    }

    /** Shows who is trusted on your nearby owned bot. */
    private static int trustShow(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        AiAssistantEntity ai = AiAssistantEntity.findOwnedFor(player, 64);
        if (ai == null) return noOwnedAi(player);
        List<TrustEntry> list = ai.trustedEntries();
        StringBuilder sb = new StringBuilder(
                "§6=== " + ai.getAssistantName() + "'s trusted players (" + list.size() + ") ===");
        if (list.isEmpty()) sb.append("\n§7  (none yet — only you can command this companion)");
        for (TrustEntry e : list) {
            sb.append("\n§f  ").append(e.name() == null || e.name().isBlank()
                    ? e.uuid().toString() : e.name());
        }
        sb.append("\n§7Add with §f/ai trust <player>§7 (they must be online), remove with §f/ai untrust <player>§7.");
        final String out = sb.toString();
        player.sendSystemMessage(Component.literal(out));
        return 1;
    }

    /** Trusts an online player to command your nearby owned bot. */
    private static int trustAdd(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        AiAssistantEntity ai = AiAssistantEntity.findOwnedFor(player, 64);
        if (ai == null) return noOwnedAi(player);
        MinecraftServer server = player.level().getServer();
        ServerPlayer target = server == null ? null : server.getPlayerList().getPlayerByName(name);
        if (target == null) {
            player.sendSystemMessage(Component.literal(
                    "§cCan't find an online player named §f" + name + "§c — they must be online to be trusted."));
            return 0;
        }
        if (target.getUUID().equals(player.getUUID())) {
            player.sendSystemMessage(Component.literal(
                    "§7You already own §f" + ai.getAssistantName() + "§7 — no need to trust yourself."));
            return 0;
        }
        boolean added = ai.addTrust(target);
        player.sendSystemMessage(Component.literal(added
                ? "§aTrusted §f" + target.getName().getString() + "§a to command §f" + ai.getAssistantName() + "§a."
                : "§7" + target.getName().getString() + " was already trusted on " + ai.getAssistantName() + "."));
        if (added) {
            target.sendSystemMessage(Component.literal(
                    "§a" + player.getName().getString() + " trusted you to command their companion §f"
                            + ai.getAssistantName() + "§a."));
        }
        return 1;
    }

    /** Removes a player (online or by stored name) from your nearby owned bot's trust list. */
    private static int trustRemove(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        AiAssistantEntity ai = AiAssistantEntity.findOwnedFor(player, 64);
        if (ai == null) return noOwnedAi(player);
        UUID removed = ai.removeTrustByName(name);
        if (removed == null) {
            // Name may have changed since they were trusted — fall back to the online player's UUID.
            MinecraftServer server = player.level().getServer();
            ServerPlayer target = server == null ? null : server.getPlayerList().getPlayerByName(name);
            if (target != null && ai.removeTrust(target.getUUID()) != null) removed = target.getUUID();
        }
        final UUID done = removed;
        player.sendSystemMessage(Component.literal(done != null
                ? "§aRemoved §f" + name + "§a from §f" + ai.getAssistantName() + "§a's trusted players."
                : "§7" + name + " wasn't on " + ai.getAssistantName() + "'s trusted list."));
        return 1;
    }

    /** Clears the entire trust list of your nearby owned bot. */
    private static int trustClear(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        AiAssistantEntity ai = AiAssistantEntity.findOwnedFor(player, 64);
        if (ai == null) return noOwnedAi(player);
        int n = ai.clearTrust();
        player.sendSystemMessage(Component.literal(
                "§aCleared " + n + " trusted player" + (n == 1 ? "" : "s") + " from §f" + ai.getAssistantName() + "§a."));
        return 1;
    }

    // ── voice: sharing, linking and per-bot voices ──────────────────────────────

    /** Common TTS voice ids for tab-completion of /ai voice set. Any word is accepted. */
    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> VOICE_SUGGEST =
            (ctx, builder) -> {
                for (String v : new String[]{"alloy", "echo", "fable", "onyx", "nova", "shimmer", "coral", "verse", "ballad", "ash", "sage"}) {
                    builder.suggest(v);
                }
                return builder.buildFuture();
            };

    private static int voiceStatus(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        boolean on = ModConfig.get().allowVoice;
        AiAssistantEntity ai = AiAssistantEntity.findOwnedFor(player, 256);
        String botVoice = ai == null ? "" : ai.getVoiceId();
        player.sendSystemMessage(Component.literal(
                "§6=== Agent voice ===\n"
                + "§7Server voice: " + (on ? "§aON" : "§cOFF (an admin can /ai admin voice on)") + "\n"
                + (ai == null ? "§7No companion of yours is nearby.\n"
                        : "§7" + ai.getAssistantName() + "'s voice: §f"
                                + (botVoice.isBlank() ? "(your client default)" : botVoice) + "\n")
                + "§7Hold your talk key (default §fV§7, see /aivoice) to speak to YOUR companion —\n"
                + "§7it's private: only you hear it, unless you §f/ai voice share <player>§7.\n"
                + "§7Shared/linked agents take turns speaking — they never interrupt each other.\n"
                + "§f/ai voice list §7— who hears your agent · §f/ai voice set <id> §7— change its voice"));
        return 1;
    }

    /** Shows who this player shares their agent's voice with, and who shares with them. */
    private static int voiceList(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        MinecraftServer server = player.level().getServer();
        var shares = VoiceLinkManager.describeShares(server, player.getUUID());
        var heard = VoiceLinkManager.sharedTo(player.getUUID());
        StringBuilder sb = new StringBuilder("§6=== Your voice shares ===");
        sb.append("\n§eYour agent is heard by (" + shares.size() + "):");
        if (shares.isEmpty()) sb.append("\n§7  only you");
        shares.forEach((name, online) ->
                sb.append("\n§f  ").append(name).append(online ? "" : " §8(offline)"));
        sb.append("\n§eYou can also hear (" + heard.size() + "):");
        if (heard.isEmpty()) sb.append("\n§7  no one else's agent");
        for (java.util.UUID id : heard) sb.append("\n§f  ").append(VoiceLinkManager.nameOf(id)).append("§7's agent");
        sb.append("\n§7Share with §f/ai voice share <player>§7, stop with §f/ai voice unshare <player>§7.");
        final String out = sb.toString();
        player.sendSystemMessage(Component.literal(out));
        return 1;
    }

    /** Lets another (online) player hear your agent — and links your agents' conversations. */
    private static int voiceShare(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        MinecraftServer server = player.level().getServer();
        ServerPlayer target = server == null ? null : server.getPlayerList().getPlayerByName(name);
        if (target == null) {
            player.sendSystemMessage(Component.literal(
                    "§cCan't find an online player named §f" + name + "§c — they must be online to share with."));
            return 0;
        }
        if (target.getUUID().equals(player.getUUID())) {
            player.sendSystemMessage(Component.literal("§7You already hear your own agent."));
            return 0;
        }
        boolean added = VoiceLinkManager.share(player, target);
        player.sendSystemMessage(Component.literal(added
                ? "§aShared your agent's voice with §f" + target.getName().getString()
                        + "§a — your agents are now linked and will take turns speaking."
                : "§7" + target.getName().getString() + " already hears your agent."));
        if (added) {
            target.sendSystemMessage(Component.literal(
                    "§a" + player.getName().getString() + " shared their companion's voice with you — "
                            + "you'll now hear it too. §7(They can /ai voice unshare you any time.)"));
        }
        return 1;
    }

    private static int voiceUnshare(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        MinecraftServer server = player.level().getServer();
        ServerPlayer target = server == null ? null : server.getPlayerList().getPlayerByName(name);
        boolean removed = target != null
                && VoiceLinkManager.unshare(player.getUUID(), target.getUUID());
        player.sendSystemMessage(Component.literal(removed
                ? "§aStopped sharing your agent's voice with §f" + name + "§a."
                : "§7" + name + " wasn't hearing your agent (they must be online to unshare by name)."));
        return removed ? 1 : 0;
    }

    private static int voiceClear(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        int n = VoiceLinkManager.clearShares(player.getUUID());
        player.sendSystemMessage(Component.literal(
                "§aStopped sharing your agent's voice (" + n + " listener" + (n == 1 ? "" : "s")
                        + " removed) — it's private to you again."));
        return 1;
    }

    /** Gives your nearby owned bot its own TTS voice (owner/admin — it changes the bot). */
    private static int voiceSet(CommandContext<CommandSourceStack> ctx, String voice) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        AiAssistantEntity ai = AiAssistantEntity.findOwnedFor(player, 64);
        if (ai == null) return noOwnedAi(player);
        ai.setVoiceId(voice);
        player.sendSystemMessage(Component.literal(
                "§a" + ai.getAssistantName() + " now speaks with the §f" + ai.getVoiceId()
                        + "§a voice. §7(Try /ai voice set nova, onyx, shimmer…)"));
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
            player.sendSystemMessage(noGuiHint(player,
                    "§f/ai admin§e for text-based controls (e.g. §f/ai admin token <key>§e)"));
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
        if (!ensureCanCommand(player, ai)) return 0;

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
                "§f/ai admin token <token> §7— set the shared AI API key (no GUI needed)\n" +
                "§f/ai admin apiurl <url> §7— set the OpenAI-compatible API endpoint\n" +
                "§f/ai admin provider <name> §7— quick-switch: huggingface|chatgpt|claude|gemini|grok\n" +
                "§f/ai admin model <id> §7— set the server default model\n" +
                "§f/ai admin ollama on|off|url|model|models §7— use custom LOCAL models (Ollama)\n" +
                "§f/ai admin player2 on|off|url §7— easiest AI: Player2 (local app, or online w/ PLAYER2_KEY)\n" +
                "§f/ai admin requirekey on|off §7— make players use their own API key\n" +
                "§f/ai admin possession on|off §7— allow/deny possession mode (/ai possess)\n" +
                "§f/ai admin voice on|off §7— allow/deny agent voice (push-to-talk + speech)\n" +
                "§f/ai admin keylist add|remove|list <player> §7— who may use the shared key\n" +
                "§f/ai admin models add|remove|list <id> §7— models players may pick\n" +
                "§7Admin tier is set in the Admin panel (default: ops = 2).\n" +
                "§7On Bedrock/vanilla (no Java GUI), these text commands are the way to configure."), false);
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
            player.sendSystemMessage(noGuiHint(player,
                    "§f/ai admin stats§e and the §f/ai admin …§e commands"));
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

    private static int adminSetToken(CommandContext<CommandSourceStack> ctx, String token) {
        ModConfig cfg = ModConfig.get();
        cfg.setToken(token);
        // Setting a key means you want the key used. Without this the key would just sit
        // there while some other connection kept answering — the confusion the exclusive
        // connection setting exists to end.
        boolean switched = cfg.connection() != AiConnection.API_KEY;
        if (switched) cfg.setConnection(AiConnection.API_KEY);
        ModConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[Blockpal] Shared API token set ✓ §7(stored obfuscated, never shown to players)."
                        + (switched ? "\n§7AI connection switched to §fMy own API key§7 (only one runs at a time)." : "")
                        + "\n§7Heads-up: typing a token in chat can expose it — on a server you control, "
                        + "prefer the §fBLOCKPAL_API_TOKEN§7 env var (it's never written to disk)."), false);
        return 1;
    }

    private static int adminSetApiUrl(CommandContext<CommandSourceStack> ctx, String url) {
        ModConfig cfg = ModConfig.get();
        cfg.apiUrl = url.trim();
        ModConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[Blockpal] API URL set to §f" + cfg.apiUrl), false);
        return 1;
    }

    private static int adminProvider(CommandContext<CommandSourceStack> ctx,
                                     com.milkdromeda.blockpal.ai.ProviderPreset p) {
        ModConfig cfg = ModConfig.get();
        cfg.apiUrl = p.url();
        cfg.hfModel = com.milkdromeda.blockpal.ai.ModelIds.clean(p.model());
        cfg.addAllowedModel(cfg.hfModel);   // keep the preset's model selectable by players
        boolean setKey = false;
        if (p.hasDefaultKey()) {
            cfg.setToken(p.defaultKey());
            setKey = true;
        }
        ModConfig.save();
        final boolean didKey = setKey;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[Blockpal] Provider set to §f" + p.display() + "§a ✓\n"
                        + "§7• Endpoint: §f" + p.url() + "\n"
                        + "§7• Default model: §f" + cfg.hfModel + "\n"
                        + (didKey
                            ? "§7• A default API key was pre-filled §7(a public demo key — replace it with your "
                                + "own via §f/ai admin token <key>§7 or §f/ai mykey <key>§7)."
                            : "§7• Set the key for this provider with §f/ai admin token <key>§7 (or §f/ai mykey <key>§7).")),
                false);
        return 1;
    }

    private static int adminProviderShow(CommandContext<CommandSourceStack> ctx) {
        ModConfig cfg = ModConfig.get();
        com.milkdromeda.blockpal.ai.ProviderPreset current =
                com.milkdromeda.blockpal.ai.ProviderPreset.fromUrl(cfg.apiUrl);
        StringBuilder sb = new StringBuilder("§6=== AI providers ===\n");
        sb.append("§7Current endpoint: §f").append(cfg.apiUrl).append(" §7(")
                .append(current != null ? "§b" + current.display() : "§eCustom").append("§7)\n");
        for (com.milkdromeda.blockpal.ai.ProviderPreset p : com.milkdromeda.blockpal.ai.ProviderPreset.values()) {
            sb.append(p == current ? "§a▶ " : "§7• ").append("§f/ai admin provider ").append(p.id())
                    .append(" §7— ").append(p.display()).append("\n");
        }
        sb.append("§7Switching sets the endpoint + a default model; add that provider's key with "
                + "§f/ai admin token <key>§7.");
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int adminSetModel(CommandContext<CommandSourceStack> ctx, String model) {
        ModConfig cfg = ModConfig.get();
        // Pasted ids carry quotes/whitespace artifacts that read as a bare 400 later.
        String m = com.milkdromeda.blockpal.ai.ModelIds.clean(model);
        if (m.isBlank()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§c[Blockpal] That model id is empty."), false);
            return 0;
        }
        cfg.hfModel = m;
        cfg.addAllowedModel(m);   // keep the server default selectable by players
        ModConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[Blockpal] Server default model set to §f" + m), false);
        String advice = com.milkdromeda.blockpal.ai.ModelIds.advice(m);
        if (advice != null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§e[Blockpal] Heads-up: §7" + advice), false);
        }
        return 1;
    }

    private static int adminMaxBots(CommandContext<CommandSourceStack> ctx, int count) {
        ModConfig.get().maxBotsPerServer = count;   // arg already constrained to 0..50
        ModConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[Blockpal] Max bots per server = " + (count == 0 ? "unlimited" : count)), false);
        return 1;
    }

    // ── local Ollama (custom local models) ──────────────────────────────────────

    /**
     * Turning a provider on now <b>switches the whole connection to it</b> and turns the
     * others off — there is exactly one, always. ("off" falls back to the free service
     * rather than leaving the server with no AI by accident.)
     */
    private static int adminOllama(CommandContext<CommandSourceStack> ctx, boolean on) {
        return setConnection(ctx, on ? AiConnection.OLLAMA : AiConnection.FREE);
    }

    private static int adminOllamaUrl(CommandContext<CommandSourceStack> ctx, String url) {
        ModConfig cfg = ModConfig.get();
        cfg.ollamaUrl = url.trim();
        ModConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[Blockpal] Ollama URL set to §f" + cfg.ollamaUrl), false);
        return 1;
    }

    private static int adminOllamaModel(CommandContext<CommandSourceStack> ctx, String model) {
        ModConfig cfg = ModConfig.get();
        String m = com.milkdromeda.blockpal.ai.ModelIds.clean(model);
        if (m.isBlank()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§c[Blockpal] That model id is empty."), false);
            return 0;
        }
        cfg.ollamaModel = m;
        ModConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[Blockpal] Ollama default model set to §f" + m + " §7(run §follama pull " + m + "§7 first)."), false);
        return 1;
    }

    private static int adminOllamaShow(CommandContext<CommandSourceStack> ctx) {
        ModConfig cfg = ModConfig.get();
        StringBuilder sb = new StringBuilder("§6=== Local Ollama ===");
        sb.append("\n§7Status: ").append(cfg.ollamaEnabled ? "§aenabled" : "§7disabled");
        sb.append("\n§7URL: §f").append(cfg.ollamaUrl);
        sb.append("\n§7Default model: §f").append(cfg.ollamaModel);
        sb.append("\n§7Village model pool: §f")
          .append(cfg.ollamaModels.isEmpty() ? "(uses the default)" : String.join(", ", cfg.ollamaModels));
        sb.append("\n§7Toggle: §f/ai admin ollama on|off§7 · set: §furl <url>§7 · §fmodel <id>§7 · §fmodels add|remove <id>");
        final String out = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(out), false);
        return 1;
    }

    private static int adminOllamaModelsShow(CommandContext<CommandSourceStack> ctx) {
        return adminOllamaShow(ctx);
    }

    private static int adminOllamaModelsAdd(CommandContext<CommandSourceStack> ctx, String model) {
        boolean added = ModConfig.get().addOllamaModel(model);
        ModConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal(added
                ? "§a[Blockpal] Added §f" + com.milkdromeda.blockpal.ai.ModelIds.clean(model)
                        + "§a to the village model pool."
                : "§7[Blockpal] That model is already in the pool (or was empty)."), false);
        return 1;
    }

    private static int adminOllamaModelsRemove(CommandContext<CommandSourceStack> ctx, String model) {
        boolean removed = ModConfig.get().removeOllamaModel(model);
        ModConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal(removed
                ? "§a[Blockpal] Removed §f" + com.milkdromeda.blockpal.ai.ModelIds.clean(model) + "§a from the pool."
                : "§7[Blockpal] That model wasn't in the pool."), false);
        return 1;
    }

    // ── Player2 (player2.game) — easiest keyless AI, local or online ─────────────

    /** Same exclusivity rule as {@link #adminOllama}: turning Player2 on turns the rest off. */
    private static int adminPlayer2(CommandContext<CommandSourceStack> ctx, boolean on) {
        return setConnection(ctx, on ? AiConnection.PLAYER2 : AiConnection.FREE);
    }

    private static int adminPlayer2Url(CommandContext<CommandSourceStack> ctx, String url) {
        ModConfig cfg = ModConfig.get();
        cfg.player2OnlineUrl = url.trim();
        ModConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[Blockpal] Player2 online URL set to §f" + cfg.player2OnlineUrl), false);
        return 1;
    }

    private static int adminPlayer2Show(CommandContext<CommandSourceStack> ctx) {
        ModConfig cfg = ModConfig.get();
        boolean hasKey = !cfg.resolvePlayer2Key().isBlank();
        StringBuilder sb = new StringBuilder("§6=== Player2 (player2.game) ===");
        sb.append("\n§7Status: ").append(cfg.player2Enabled ? "§aenabled" : "§7disabled");
        sb.append("\n§7Mode: ").append(hasKey ? "§aONLINE (cloud)" : "§eLOCAL (app on localhost:4315)");
        sb.append("\n§7Key: ").append(hasKey
                ? (cfg.isPlayer2KeyFromEnv() ? "§aset ✓ (from PLAYER2_KEY env)" : "§aset ✓")
                : "§7not set — install the Player2 app, or set §fPLAYER2_KEY§7 for online");
        sb.append("\n§7Online URL: §f").append(cfg.player2OnlineUrl);
        sb.append("\n§7Local URL: §f").append(cfg.player2Url);
        sb.append("\n§7Model: §f").append(cfg.player2Model);
        sb.append("\n§7Toggle: §f/ai admin player2 on|off§7 · set online endpoint: §furl <url>");
        final String out = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(out), false);
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
        String m = com.milkdromeda.blockpal.ai.ModelIds.clean(model);
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
            player.sendSystemMessage(noGuiHint(player,
                    "§f/ai mykey <token>§e and §f/ai model <id>§e"));
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
                player.sendSystemMessage(noGuiHint(player,
                        "§f/ai admin§e for text-based controls (e.g. §f/ai admin token <key>§e)"));
                return 0;
            }
            AiNetworking.openAdminMenuFor(player);
            return 1;
        }
        if (!AiNetworking.openPlayerMenuFor(player)) {
            player.sendSystemMessage(noGuiHint(player,
                    "§f/ai mykey <token>§e and §f/ai model <id>§e"));
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

    // ── PVT: pre-video training ───────────────────────────────────────────────

    private static int pvtStatus(CommandContext<CommandSourceStack> ctx) {
        String status = com.milkdromeda.blockpal.pvt.PvtManager.status();
        for (String line : status.split("\n")) {
            ctx.getSource().sendSuccess(() -> Component.literal("§b" + line), false);
        }
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player != null) {
            boolean watching = com.milkdromeda.blockpal.pvt.PvtManager.hasConsented(player.getUUID());
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§7Your play is " + (watching ? "§abeing recorded§7 for training"
                            : "§7not recorded") + ". Change it with §f/ai pvt watch on|off§7."), false);
        }
        return 1;
    }

    /** A player opting their OWN play in or out. Never usable on anybody else. */
    private static int pvtWatch(CommandContext<CommandSourceStack> ctx, boolean on) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        if (!ModConfig.get().pvtEnabled) {
            player.sendSystemMessage(Component.literal(
                    "§cPVT is switched off on this server."));
            return 0;
        }
        com.milkdromeda.blockpal.pvt.PvtManager.setConsent(player.getUUID(), on);
        if (on) {
            com.milkdromeda.blockpal.pvt.PvtManager.startRecording(player);
            player.sendSystemMessage(Component.literal(
                    "§a[Blockpal] Watching how you play. What gets stored is which way you "
                            + "walked and where you looked \u2014 nothing else, and only while this is on. "
                            + "Turn it off any time with §f/ai pvt watch off§a."));
        } else {
            com.milkdromeda.blockpal.pvt.PvtManager.stopRecording(player.getUUID());
            player.sendSystemMessage(Component.literal(
                    "§7[Blockpal] No longer recording your play. What was already banked stays "
                            + "\u2014 an operator can delete it with §f/ai pvt clear§7."));
        }
        return 1;
    }

    private static int pvtRecord(CommandContext<CommandSourceStack> ctx, boolean start) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        if (start) {
            if (!com.milkdromeda.blockpal.pvt.PvtManager.hasConsented(player.getUUID())) {
                player.sendSystemMessage(Component.literal(
                        "§eSay it's alright first: §f/ai pvt watch on§e."));
                return 0;
            }
            String problem = com.milkdromeda.blockpal.pvt.PvtManager.startRecording(player);
            player.sendSystemMessage(Component.literal(problem.isEmpty()
                    ? "§a[Blockpal] Recording. Just play normally."
                    : "§e" + problem));
        } else {
            String summary = com.milkdromeda.blockpal.pvt.PvtManager.stopRecording(player.getUUID());
            player.sendSystemMessage(Component.literal(summary.isEmpty()
                    ? "§7Nothing was being recorded." : "§a[Blockpal] " + summary));
        }
        return 1;
    }

    private static int pvtTrain(CommandContext<CommandSourceStack> ctx) {
        if (!requireAdmin(ctx)) return 0;
        MinecraftServer server = ctx.getSource().getServer();
        CommandSourceStack source = ctx.getSource();
        boolean started = com.milkdromeda.blockpal.pvt.PvtManager.train(server, trainer -> {
            String message = trainer.error().isEmpty()
                    ? "§a[Blockpal] " + trainer.result()
                    : "§c[Blockpal] Training stopped: " + trainer.error();
            server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
        });
        source.sendSuccess(() -> Component.literal(started
                ? "§a[Blockpal] Training on what people have played. This runs in the "
                        + "background \u2014 the server keeps going. §7/ai pvt status§a for progress."
                : "§eTraining is already running. §7/ai pvt status"), false);
        return 1;
    }

    private static int pvtClear(CommandContext<CommandSourceStack> ctx) {
        if (!requireAdmin(ctx)) return 0;
        int removed = com.milkdromeda.blockpal.pvt.PvtManager.clearRecordings();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[Blockpal] Deleted " + removed + " recording file(s). The trained policy is "
                        + "kept \u2014 delete pvt/policy.bpn to drop that too."), false);
        return 1;
    }

    private static int pvtUse(CommandContext<CommandSourceStack> ctx, boolean on) {
        if (!requireAdmin(ctx)) return 0;
        if (on && !com.milkdromeda.blockpal.pvt.PvtManager.hasPolicy()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§eThere's no trained policy yet. Record some play "
                            + "(§f/ai pvt watch on§e), then §f/ai pvt train§e."), false);
            return 0;
        }
        ModConfig.get().aiLogicMode = on ? "pvt" : "code";
        ModConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal(on
                ? "§a[Blockpal] Companions now act from what they learned by watching, and "
                        + "still think with the model when they're unsure."
                : "§a[Blockpal] Back to look-think-write-a-script."), false);
        return 1;
    }

    // ── speed and fighting ────────────────────────────────────────────────────

    private static int speedShow(CommandContext<CommandSourceStack> ctx) {
        com.milkdromeda.blockpal.agent.Tempo t = com.milkdromeda.blockpal.agent.Tempo.current();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§b[Blockpal] Reaction speed: §f" + t.label() + "§7 (" + t.id() + ")\n"
                        + "§7Options: " + com.milkdromeda.blockpal.agent.Tempo.idList()
                        + " \u2014 set with §f/ai speed <one of those>"), false);
        return 1;
    }

    private static int speedSet(CommandContext<CommandSourceStack> ctx) {
        if (!requireAdmin(ctx)) return 0;
        String id = StringArgumentType.getString(ctx, "tempo");
        com.milkdromeda.blockpal.agent.Tempo t = com.milkdromeda.blockpal.agent.Tempo.byId(id);
        if (t == null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§cNo such speed. Try: " + com.milkdromeda.blockpal.agent.Tempo.idList()), false);
            return 0;
        }
        ModConfig.get().reactionSpeed = t.id();
        ModConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[Blockpal] Reaction speed: §f" + t.label()), false);
        return 1;
    }

    private static int combatShow(CommandContext<CommandSourceStack> ctx) {
        com.milkdromeda.blockpal.combat.CombatSkill s =
                com.milkdromeda.blockpal.combat.CombatSkill.current();
        boolean pvp = ModConfig.get().allowPvp;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§b[Blockpal] Combat skill: §f" + s.label() + "§7 (" + s.id() + ")\n"
                        + "§7Options: " + com.milkdromeda.blockpal.combat.CombatSkill.idList() + "\n"
                        + "§7Fighting players: " + (pvp ? "§eallowed §7(and only ever someone who "
                        + "started it)" : "§aoff")), false);
        return 1;
    }

    private static int combatSet(CommandContext<CommandSourceStack> ctx) {
        if (!requireAdmin(ctx)) return 0;
        String id = StringArgumentType.getString(ctx, "skill");
        com.milkdromeda.blockpal.combat.CombatSkill skill =
                com.milkdromeda.blockpal.combat.CombatSkill.byId(id);
        if (skill == null) {
            ctx.getSource().sendSuccess(() -> Component.literal("§cNo such skill level. Try: "
                    + com.milkdromeda.blockpal.combat.CombatSkill.idList()), false);
            return 0;
        }
        ModConfig.get().combatSkill = skill.id();
        ModConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[Blockpal] Combat skill: §f" + skill.label()), false);
        return 1;
    }

    private static int adminPvp(CommandContext<CommandSourceStack> ctx, boolean on) {
        ModConfig.get().allowPvp = on;
        ModConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal(on
                ? "§e[Blockpal] Companions may now fight players \u2014 but only someone who "
                        + "attacked them or their owner in the last ten seconds, or who their "
                        + "owner named with §f/ai attack§e. Never their owner or anyone trusted."
                : "§a[Blockpal] Companions will not raise a hand to a player."), false);
        return 1;
    }

    /** Points a companion at a specific person. Owner-only, and it still has to be allowed. */
    private static int attackPlayer(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 64);
        if (ai == null) return noAi(player);
        // Ordering violence is a management decision, not an everyday command.
        if (!ensureCanManage(player, ai)) return 0;

        String name = StringArgumentType.getString(ctx, "player");
        ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
        if (target == null) {
            player.sendSystemMessage(Component.literal("§cI can't see anyone called " + name + "."));
            return 0;
        }
        String refusal = com.milkdromeda.blockpal.combat.PvpRules.refusalReason(ai, target);
        if (!refusal.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "§b" + ai.getAssistantName() + ": §f\"No \u2014 " + refusal + "\""));
            return 0;
        }
        ai.setCombatOrder(target.getUUID());
        ai.broadcastMessage("Alright. Watching " + target.getName().getString() + ".");
        return 1;
    }

    // ── the work queue ────────────────────────────────────────────────────────

    private static int queueAdd(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 64);
        if (ai == null) return noAi(player);
        if (!ensureCanCommand(player, ai)) return 0;
        String task = StringArgumentType.getString(ctx, "task");
        if (!ai.queueTask(task)) {
            player.sendSystemMessage(Component.literal(
                    "§eIts list is full \u2014 §f/ai queue clear§e first."));
            return 0;
        }
        ai.broadcastMessage("Added to my list (" + ai.taskQueue().size() + " waiting): " + task);
        return 1;
    }

    private static int queueList(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 64);
        if (ai == null) return noAi(player);
        List<String> queue = ai.taskQueue();
        if (queue.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "§7Nothing lined up. Add something with §f/ai queue <what to do>§7."));
            return 1;
        }
        player.sendSystemMessage(Component.literal("§b" + ai.getAssistantName() + "'s list:"));
        for (int i = 0; i < queue.size(); i++) {
            int n = i + 1;
            String job = queue.get(i);
            player.sendSystemMessage(Component.literal("§7 " + n + ". §f" + job));
        }
        return 1;
    }

    private static int queueClear(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return 0;
        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 64);
        if (ai == null) return noAi(player);
        if (!ensureCanCommand(player, ai)) return 0;
        int n = ai.clearQueue();
        player.sendSystemMessage(Component.literal("§a[Blockpal] Cleared " + n + " queued job(s)."));
        return 1;
    }

    private static int adminPossession(CommandContext<CommandSourceStack> ctx, boolean on) {
        ModConfig.get().allowPossession = on;
        ModConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[Blockpal] Possession mode: " + (on ? "§aON" : "§7off")
                        + " §7(players hand their character to their own companion with §f/ai possess§7)"), false);
        return 1;
    }

    private static int adminVoice(CommandContext<CommandSourceStack> ctx, boolean on) {
        ModConfig.get().allowVoice = on;
        ModConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[Blockpal] Agent voice: " + (on ? "§aON" : "§7off")
                        + " §7(push-to-talk input and spoken replies for everyone on this server)"), false);
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
        String m = com.milkdromeda.blockpal.ai.ModelIds.clean(model);
        if (m.isBlank()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§c[Blockpal] That model id is empty."), false);
            return 0;
        }
        boolean added = ModConfig.get().addAllowedModel(m);
        ModConfig.save();
        ctx.getSource().sendSuccess(() -> Component.literal(added
                ? "§a[Blockpal] Added model §f" + m
                : "§7[Blockpal] That model is already allowed."), false);
        String advice = com.milkdromeda.blockpal.ai.ModelIds.advice(m);
        if (advice != null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§e[Blockpal] Heads-up: §7" + advice), false);
        }
        return 1;
    }

    private static int adminModelsRemove(CommandContext<CommandSourceStack> ctx, String model) {
        ModConfig cfg = ModConfig.get();
        String m = com.milkdromeda.blockpal.ai.ModelIds.clean(model);
        if (m.equals(cfg.hfModel)) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§c[Blockpal] Can't remove the server default model — change it with /ai admin model <id> first."), false);
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

    // ── the ONE AI connection ───────────────────────────────────────────────────

    /** One literal per {@link AiConnection}, so the picker tab-completes. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> connectionArgs() {
        var node = Commands.literal("set");
        for (AiConnection c : AiConnection.values()) {
            node.then(Commands.literal(c.id()).executes(ctx -> setConnection(ctx, c)));
        }
        return node;
    }

    private static int connectionShow(CommandContext<CommandSourceStack> ctx) {
        ModConfig cfg = ModConfig.get();
        AiConnection current = cfg.connection();
        StringBuilder sb = new StringBuilder("§6=== How this server's bots think ===");
        sb.append("\n§7Only §fone§7 connection can be on at a time — that's deliberate, so it's "
                + "always clear which AI is answering (and which one is being billed).");
        for (AiConnection c : AiConnection.values()) {
            sb.append("\n").append(c == current ? "§a▶ " : "§8  ").append("§f").append(c.id())
              .append(" §7— ").append(c.display());
            if (c == current) sb.append(" §a(active)");
        }
        sb.append("\n§7").append(current.blurb());
        if (current == AiConnection.MCP) {
            sb.append("\n§7MCP server: ").append(com.milkdromeda.blockpal.mcp.McpServer.status())
              .append(" §7— run §f/ai mcp§7 for setup instructions.");
        } else if (current == AiConnection.API_KEY && !cfg.hasApiToken()) {
            sb.append("\n§cNo API key is set yet — §f/ai admin token <token>§c.");
        }
        sb.append("\n§7Change it: §f/ai connection set <").append(AiConnection.idList()).append(">");
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int setConnection(CommandContext<CommandSourceStack> ctx, AiConnection connection) {
        if (!requireAdmin(ctx)) return 0;
        ModConfig cfg = ModConfig.get();
        AiConnection previous = cfg.connection();
        cfg.setConnection(connection);
        ModConfig.save();

        MinecraftServer server = ctx.getSource().getServer();
        if (server != null) com.milkdromeda.blockpal.mcp.McpServer.sync(server);
        if (connection == AiConnection.PLAYER2) {
            com.milkdromeda.blockpal.ai.HuggingFaceClient.warmPlayer2Local();
        }

        StringBuilder sb = new StringBuilder("§a[Blockpal] AI connection: §f" + connection.display());
        if (previous != connection) {
            sb.append(" §7(").append(previous.display()).append(" turned off — only one at a time)");
        }
        sb.append("\n§7").append(connection.blurb());
        switch (connection) {
            case MCP -> sb.append("\n§7").append(com.milkdromeda.blockpal.mcp.McpServer.status())
                    .append(" §7— run §f/ai mcp§7 to connect Claude, ChatGPT, Grok or Gemini.");
            case API_KEY -> {
                if (!cfg.hasApiToken()) sb.append("\n§cNo key set yet: §f/ai admin token <token>");
            }
            case OLLAMA -> sb.append("\n§7Endpoint: §f").append(cfg.ollamaUrl)
                    .append(" §7model §f").append(cfg.ollamaModel);
            case OFF -> sb.append("\n§7Companions still eat, fight and survive on their own — "
                    + "they just won't plan or chat with a model.");
            default -> { /* nothing extra to say */ }
        }
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    // ── MCP server (connect Claude / ChatGPT / Grok / Gemini) ───────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> mcpCommand() {
        return Commands.literal("mcp")
                .executes(AiCommands::mcpGuide)
                .then(Commands.literal("status").executes(AiCommands::mcpStatus))
                .then(Commands.literal("start").executes(ctx -> mcpRunning(ctx, true)))
                .then(Commands.literal("stop").executes(ctx -> mcpRunning(ctx, false)))
                .then(Commands.literal("token").executes(AiCommands::mcpToken))
                .then(Commands.literal("newtoken").executes(AiCommands::mcpNewToken))
                .then(Commands.literal("port")
                        .then(Commands.argument("port", IntegerArgumentType.integer(1024, 65535))
                                .executes(ctx -> mcpPort(ctx, IntegerArgumentType.getInteger(ctx, "port")))))
                .then(Commands.literal("remote")
                        .then(Commands.literal("on").executes(ctx -> mcpRemote(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> mcpRemote(ctx, false))));
    }

    /** Opens the visual setup guide, or prints the same thing for Bedrock/vanilla clients. */
    private static int mcpGuide(CommandContext<CommandSourceStack> ctx) {
        if (!requireAdmin(ctx)) return 0;
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return mcpStatus(ctx);
        if (AiNetworking.openMcpGuideFor(player)) return 1;
        player.sendSystemMessage(noGuiHint(player, "§f/ai mcp status§e and §f/ai mcp token"));
        return mcpStatus(ctx);
    }

    private static int mcpStatus(CommandContext<CommandSourceStack> ctx) {
        if (!requireAdmin(ctx)) return 0;
        ModConfig cfg = ModConfig.get();
        StringBuilder sb = new StringBuilder("§6=== Blockpal MCP server ===");
        sb.append("\n§7State: ").append(com.milkdromeda.blockpal.mcp.McpServer.isRunning()
                ? "§arunning" : "§7not running").append(" §8(").append(
                        com.milkdromeda.blockpal.mcp.McpServer.status()).append("§8)");
        if (!cfg.isMcpConnection()) {
            sb.append("\n§eThe AI connection is §f").append(cfg.connection().display())
              .append("§e — switch with §f/ai connection set mcp§e to use it.");
        }
        sb.append("\n§7Address: §f").append(com.milkdromeda.blockpal.mcp.McpServer.endpoint());
        sb.append("\n§7Older clients (SSE): §f").append(com.milkdromeda.blockpal.mcp.McpServer.sseEndpoint());
        sb.append("\n§7Reachable from: ").append(cfg.mcpAllowRemote
                ? "§eany machine that can reach this one" : "§athis machine only");
        sb.append("\n§7Token required: ").append(cfg.mcpRequireToken ? "§ayes" : "§cno");
        sb.append("\n§7See it with §f/ai mcp token§7 · roll a new one with §f/ai mcp newtoken");
        sb.append("\n§7Port: §f").append(cfg.mcpPort).append(" §7(§f/ai mcp port <n>§7)");
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int mcpToken(CommandContext<CommandSourceStack> ctx) {
        if (!requireAdmin(ctx)) return 0;
        String token = ModConfig.get().ensureMcpToken();
        // Deliberately sendSuccess(…, false): never broadcast a credential to other ops.
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§6MCP access token:\n§f" + token
                        + "\n§7Send it as §fAuthorization: Bearer <token>§7. Anyone with this token "
                        + "can drive your companions — don't paste it in chat or on stream."), false);
        return 1;
    }

    private static int mcpNewToken(CommandContext<CommandSourceStack> ctx) {
        if (!requireAdmin(ctx)) return 0;
        String token = ModConfig.get().regenerateMcpToken();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[Blockpal] New MCP token — the old one stopped working just now:\n§f" + token), false);
        return 1;
    }

    private static int mcpRunning(CommandContext<CommandSourceStack> ctx, boolean start) {
        if (!requireAdmin(ctx)) return 0;
        MinecraftServer server = ctx.getSource().getServer();
        if (start) {
            if (server != null) com.milkdromeda.blockpal.mcp.McpServer.start(server);
        } else {
            com.milkdromeda.blockpal.mcp.McpServer.stop();
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[Blockpal] MCP server: §f" + com.milkdromeda.blockpal.mcp.McpServer.status()), false);
        return 1;
    }

    private static int mcpPort(CommandContext<CommandSourceStack> ctx, int port) {
        if (!requireAdmin(ctx)) return 0;
        ModConfig cfg = ModConfig.get();
        cfg.mcpPort = port;
        ModConfig.save();
        MinecraftServer server = ctx.getSource().getServer();
        if (server != null) com.milkdromeda.blockpal.mcp.McpServer.sync(server);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a[Blockpal] MCP port §f" + port + "§a — " + com.milkdromeda.blockpal.mcp.McpServer.status()), false);
        return 1;
    }

    private static int mcpRemote(CommandContext<CommandSourceStack> ctx, boolean on) {
        if (!requireAdmin(ctx)) return 0;
        ModConfig cfg = ModConfig.get();
        cfg.mcpAllowRemote = on;
        ModConfig.save();
        MinecraftServer server = ctx.getSource().getServer();
        if (server != null) com.milkdromeda.blockpal.mcp.McpServer.sync(server);
        ctx.getSource().sendSuccess(() -> Component.literal(on
                ? "§e[Blockpal] MCP now listens on §fall network interfaces§e — needed for cloud AI "
                        + "apps (ChatGPT, AI Studio) reaching in through a tunnel. Keep the token on."
                : "§a[Blockpal] MCP now listens on §flocalhost only§a — safest, and all a desktop "
                        + "AI app on this machine needs."), false);
        return 1;
    }

    // ── look / code (the vision + script brain, by hand) ────────────────────────

    private static int lookThroughEyes(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 32);
        if (ai == null) return noAi(player);
        if (!ensureCanCommand(player, ai)) return 0;
        player.sendSystemMessage(Component.literal(
                "§6=== What " + ai.getAssistantName() + " can see ===\n§f" + ai.look().description()));
        return 1;
    }

    private static int codeHelp(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        player.sendSystemMessage(Component.literal(
                "§6=== /ai code ===\n"
                        + "§7Hand your companion a script in the same little language its AI writes.\n"
                        + "§7It presses the bot's keys and mouse — no teleporting, no conjuring blocks.\n"
                        + "§fExample: §a/ai code lookAt(100,64,20) goTo(100,64,22) mine()\n"
                        + "§fStop it: §a/ai code stop\n"
                        + "§7Full action list: §f/ai mcp§7 → the AI reads it with api_reference()."));
        return 1;
    }

    private static int codeStop(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 32);
        if (ai == null) return noAi(player);
        if (!ensureCanCommand(player, ai)) return 0;
        ai.brain().stop();
        player.sendSystemMessage(Component.literal("§a[Blockpal] " + ai.getAssistantName()
                + " let go of the controls."));
        return 1;
    }

    private static int runCode(CommandContext<CommandSourceStack> ctx, String script) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        AiAssistantEntity ai = AiAssistantEntity.findFor(player, 32);
        if (ai == null) return noAi(player);
        if (!ensureCanCommand(player, ai)) return 0;
        ai.prepareForScript();
        String error = ai.brain().runScript(script);
        if (!error.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c[Blockpal] " + error));
            return 0;
        }
        player.sendSystemMessage(Component.literal("§a[Blockpal] " + ai.getAssistantName()
                + " is running your script. §7Watch it, or stop it with §f/ai code stop§7."));
        return 1;
    }

    /** Server-wide settings are admin-only; says so plainly rather than failing silently. */
    private static boolean requireAdmin(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return true;          // console/command block: already privileged
        if (AdminAccess.isAdmin(player)) return true;
        player.sendSystemMessage(Component.literal(
                "§cThat's a server setting — you need to be an operator to change it."));
        return false;
    }

    /**
     * A client-appropriate explanation for why a visual menu can't open, plus the
     * text alternative to use. Bedrock players (via Geyser/Floodgate) can never run
     * the Java GUI, so we tell them so plainly instead of suggesting they "install
     * the mod on your client".
     */
    private static Component noGuiHint(ServerPlayer player, String textAlternative) {
        if (BedrockSupport.isBedrockPlayer(player)) {
            return Component.literal("§eYou're on Bedrock — the visual menus need a Java client, "
                    + "but everything works in chat: use " + textAlternative + "§e instead.");
        }
        return Component.literal("§eThis menu needs the Blockpal mod on your (Java) client. "
                + "Use " + textAlternative + "§e instead.");
    }

    private static int noAi(ServerPlayer player) {
        player.sendSystemMessage(Component.literal(
                "§cNo AI assistant nearby. Summon one with §f/ai summon"));
        return 0;
    }

    private static int noOwnedAi(ServerPlayer player) {
        player.sendSystemMessage(Component.literal(
                "§cStand near a companion you own. Summon one with §f/ai summon§c, "
                        + "or see all of yours with §f/ai bots§c."));
        return 0;
    }

    /**
     * Gate for giving a bot ORDERS (come/follow/stay/stop/locate/inventory/tasks):
     * the owner, anyone the owner trusts, or a server admin. Messages the player and
     * returns false when they're not allowed.
     */
    private static boolean ensureCanCommand(ServerPlayer player, AiAssistantEntity ai) {
        if (ai.canCommand(player) || AdminAccess.isAdmin(player)) return true;
        player.sendSystemMessage(Component.literal(
                "§cThat's not your companion. Its owner can let you command it with "
                        + "§f/ai trust " + player.getName().getString() + "§c."));
        return false;
    }

    /**
     * Gate for MANAGING a bot's identity (name/skin/personality), dismissing it, or
     * editing its trust list: the owner or a server admin only. Messages the player
     * and returns false when they're not allowed.
     */
    private static boolean ensureCanManage(ServerPlayer player, AiAssistantEntity ai) {
        if (ai.isOwner(player) || AdminAccess.isAdmin(player)) return true;
        String owner = ai.getOwnerName().isBlank() ? "its owner" : ai.getOwnerName();
        player.sendSystemMessage(Component.literal(
                "§cOnly " + owner + " can change " + ai.getAssistantName() + "."));
        return false;
    }
}
