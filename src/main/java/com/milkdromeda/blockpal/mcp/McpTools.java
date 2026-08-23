package com.milkdromeda.blockpal.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.milkdromeda.blockpal.agent.BotApi;
import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import com.milkdromeda.blockpal.vision.BotVision;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * The tools an outside AI gets when it connects over MCP — and nothing more.
 *
 * <p>The shape of the toolset is deliberate: it mirrors what a <i>player</i> can do, in
 * the order a player would do it. {@code look} shows the bot's actual field of view as a
 * picture, {@code observe} reads out what it can sense, and {@code run_code} writes a
 * short program in Blockpal's own scripting language that presses movement keys and
 * mouse buttons. There is no "place block at coordinates" tool and no world dump,
 * because there is no such button on a keyboard.
 *
 * <p>Everything here hops onto the server thread before touching the world, and hops
 * back off before answering, so a slow AI client can never stall the game.
 */
public final class McpTools {

    private McpTools() {}

    /** Longest a {@code run_code} call may block waiting for the script to end. */
    private static final int MAX_WAIT_SECONDS = 45;

    /** Which bot subsequent calls act on, when the caller doesn't name one. */
    private static volatile UUID selected;

    /** Shown to the AI when it connects — how to work with this world. */
    public static String instructions() {
        return """
            You are connected to a live Minecraft world through Blockpal. You control a \
            companion bot that stands in the world as a character.

            Work in a loop: look() to see through its eyes, observe() to read what it \
            senses, then run_code() with a short script that walks, turns, mines, places \
            and uses things. The script language is small on purpose — call \
            api_reference() once at the start to learn it.

            The bot has a player's limits. It cannot teleport, cannot see through walls, \
            cannot place a block it isn't carrying, and can only reach about 4.5 blocks. \
            To do anything, walk there first. If a script doesn't achieve what you \
            expected, look again — the world will have changed.
            """;
    }

    // ── catalogue ───────────────────────────────────────────────────────────────

    public static JsonArray catalogue() {
        JsonArray tools = new JsonArray();

        tools.add(tool("list_bots",
                "List every Blockpal companion in the world, with its owner, position and state.",
                props()));

        tools.add(tool("select_bot",
                "Choose which companion the other tools act on. Give its name or its owner's name.",
                props().add("bot", "string", "Companion name or owner name.", true)));

        tools.add(tool("look",
                "See through the bot's eyes: a picture of its actual field of view, plus a written "
                + "description of the same scene. This is the only way to find out what is around it.",
                props().add("bot", "string", "Which companion (optional).", false)));

        tools.add(tool("observe",
                "Read the bot's senses as text: position, facing, health, what it carries, what is "
                + "within reach, and what its last script did.",
                props().add("bot", "string", "Which companion (optional).", false)));

        tools.add(tool("api_reference",
                "The full list of actions a script may use. Read this before writing code.",
                props()));

        tools.add(tool("run_code",
                "Run a Blockpal script on the bot. The script presses its movement keys and mouse "
                + "buttons — it cannot teleport or edit the world directly. Returns what happened.",
                props().add("code", "string", "The script to run.", true)
                       .add("wait_seconds", "number",
                               "Wait up to this long for the script to finish (0-45, default 20).", false)
                       .add("bot", "string", "Which companion (optional).", false)));

        tools.add(tool("script_status",
                "Is a script still running, and what has it logged so far?",
                props().add("bot", "string", "Which companion (optional).", false)));

        tools.add(tool("stop",
                "Stop whatever the bot is doing and let go of every key.",
                props().add("bot", "string", "Which companion (optional).", false)));

        tools.add(tool("say",
                "Make the companion say something out loud in the game chat.",
                props().add("message", "string", "What to say.", true)
                       .add("bot", "string", "Which companion (optional).", false)));

        tools.add(tool("inventory",
                "What the companion is wearing, holding and carrying.",
                props().add("bot", "string", "Which companion (optional).", false)));

        tools.add(tool("pvt_status",
                "How pre-video training is going: whether a learned policy exists, how much "
                        + "recorded play has been banked, and whether training is running. PVT is the "
                        + "layer that learns to act from watching people play, so a companion can act "
                        + "reflexively without asking a model anything.",
                props()));

        tools.add(tool("pvt_train",
                "Train (or retrain) the policy on the play recorded so far. Runs in the background "
                        + "on the server and returns immediately — poll pvt_status for progress.",
                props()));

        tools.add(tool("combat_status",
                "What the companion is fighting, how skilled it is, and whether this server lets "
                        + "companions fight players at all.",
                props().add("bot", "string", "Which companion (optional).", false)));

        tools.add(tool("queue_task",
                "Add a job to a companion's list. It works through them in order once whatever it "
                        + "is doing now is finished.",
                props().add("task", "string", "What to do, in plain language.", true)
                       .add("bot", "string", "Which companion (optional).", false)));

        return tools;
    }

    // ── dispatch ────────────────────────────────────────────────────────────────

    /** Runs one tool call and returns the MCP {@code result} object. Never throws. */
    public static JsonObject call(MinecraftServer server, String tool, JsonObject args) {
        if (tool == null) return text("No tool name was given.", true);
        // The action reference is just documentation — answer it even while the world is
        // still loading, so a client that connects early can still learn the language.
        if ("api_reference".equalsIgnoreCase(tool)) return text(BotApi.reference(), false);
        if (server == null) return text("The Minecraft server isn't running yet.", true);
        try {
            return switch (tool.toLowerCase(Locale.ROOT)) {
                case "list_bots" -> onServer(server, () -> text(listBots(server), false));
                case "select_bot" -> onServer(server, () -> selectBot(server, str(args, "bot")));
                case "look" -> look(server, str(args, "bot"));
                case "observe" -> onServer(server, () -> {
                    AiAssistantEntity bot = resolve(server, str(args, "bot"));
                    return bot == null ? noBot() : text(observe(bot), false);
                });
                case "api_reference" -> text(BotApi.reference(), false);
                case "pvt_status" -> text(com.milkdromeda.blockpal.pvt.PvtManager.status(), false);
                case "pvt_train" -> {
                    boolean started = com.milkdromeda.blockpal.pvt.PvtManager.train(server, null);
                    yield text(started
                            ? "Training started in the background. Poll pvt_status for progress."
                            : "Training is already running — poll pvt_status.", false);
                }
                case "combat_status" -> onServer(server, () -> {
                    AiAssistantEntity bot = resolve(server, str(args, "bot"));
                    if (bot == null) return noBot();
                    return text(bot.brain().combat().describe()
                            + "\nSkill: " + com.milkdromeda.blockpal.combat.CombatSkill.current().label()
                            + "\nMay fight players: "
                            + (com.milkdromeda.blockpal.config.ModConfig.get().allowPvp
                                    ? "yes, but only someone who started it" : "no"), false);
                });
                case "queue_task" -> onServer(server, () -> {
                    AiAssistantEntity bot = resolve(server, str(args, "bot"));
                    if (bot == null) return noBot();
                    String task = str(args, "task");
                    if (!bot.queueTask(task)) {
                        return text("Its list is full — it can hold 16 jobs.", true);
                    }
                    return text("Queued. " + bot.getAssistantName() + " has "
                            + bot.taskQueue().size() + " job(s) waiting.", false);
                });
                case "run_code" -> runCode(server, str(args, "bot"), str(args, "code"),
                        (int) num(args, "wait_seconds", 20));
                case "script_status" -> onServer(server, () -> {
                    AiAssistantEntity bot = resolve(server, str(args, "bot"));
                    if (bot == null) return noBot();
                    return text((bot.brain().isScripting() ? "Still running.\n" : "Finished.\n")
                            + bot.brain().scriptLog(), false);
                });
                case "stop" -> onServer(server, () -> {
                    AiAssistantEntity bot = resolve(server, str(args, "bot"));
                    if (bot == null) return noBot();
                    bot.brain().stop();
                    bot.stopTask();
                    return text("Stopped. " + bot.getAssistantName() + " is standing by.", false);
                });
                case "say" -> onServer(server, () -> {
                    AiAssistantEntity bot = resolve(server, str(args, "bot"));
                    if (bot == null) return noBot();
                    String message = str(args, "message");
                    if (message == null || message.isBlank()) return text("Nothing to say.", true);
                    bot.broadcastMessage(message);
                    return text("Said it.", false);
                });
                case "inventory" -> onServer(server, () -> {
                    AiAssistantEntity bot = resolve(server, str(args, "bot"));
                    return bot == null ? noBot()
                            : text(strip(bot.describeInventory()), false);
                });
                default -> text("There is no tool called '" + tool + "'.", true);
            };
        } catch (Exception e) {
            return text("That didn't work: " + String.valueOf(e.getMessage()), true);
        }
    }

    // ── tool bodies ─────────────────────────────────────────────────────────────

    private static String listBots(MinecraftServer server) {
        List<AiAssistantEntity> bots = AiAssistantEntity.all(server);
        if (bots.isEmpty()) {
            return "There are no companions in the world yet. A player summons one with /ai summon.";
        }
        StringBuilder sb = new StringBuilder("Companions in this world:\n");
        for (AiAssistantEntity bot : bots) {
            sb.append("• ").append(bot.getAssistantName())
              .append(" — owner ").append(bot.getOwnerName().isBlank() ? "unknown" : bot.getOwnerName())
              .append(", ").append(bot.getMode().name().toLowerCase(Locale.ROOT))
              .append(", ").append((int) bot.getHealth()).append("/").append((int) bot.getMaxHealth())
              .append(" health, at ").append(bot.blockPosition().getX()).append(",")
              .append(bot.blockPosition().getY()).append(",").append(bot.blockPosition().getZ())
              .append(" in ").append(bot.level().dimension().identifier().getPath());
            if (bot.getUUID().equals(selected)) sb.append("  ← selected");
            sb.append('\n');
        }
        return sb.toString();
    }

    private static JsonObject selectBot(MinecraftServer server, String name) {
        AiAssistantEntity bot = find(server, name);
        if (bot == null) return text("No companion matches '" + name + "'. Try list_bots.", true);
        selected = bot.getUUID();
        return text("Now controlling " + bot.getAssistantName()
                + " (owner " + bot.getOwnerName() + ").", false);
    }

    /** A picture through the bot's eyes plus the written scene — the AI's only sense of place. */
    private static JsonObject look(MinecraftServer server, String botName) {
        BotVision.Snapshot snapshot = onServerValue(server, () -> {
            AiAssistantEntity bot = resolve(server, botName);
            return bot == null ? null : bot.look();
        });
        if (snapshot == null) return noBot();

        JsonObject result = new JsonObject();
        JsonArray content = new JsonArray();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("type", "text");
        textPart.addProperty("text", snapshot.description());
        content.add(textPart);
        if (snapshot.hasImage()) {
            JsonObject image = new JsonObject();
            image.addProperty("type", "image");
            image.addProperty("data", snapshot.pngBase64());
            image.addProperty("mimeType", "image/png");
            content.add(image);
        }
        result.add("content", content);
        result.addProperty("isError", false);
        return result;
    }

    private static String observe(AiAssistantEntity bot) {
        StringBuilder sb = new StringBuilder();
        sb.append(bot.look().description());
        sb.append("Mode: ").append(bot.getMode().name().toLowerCase(Locale.ROOT));
        sb.append(bot.brain().isScripting() ? " (a script is running)" : " (idle)").append('\n');
        sb.append("Carrying: ").append(bot.describeCarrying()).append('\n');
        String goal = bot.brain().goal();
        if (!goal.isBlank()) sb.append("Current instruction: ").append(goal).append('\n');
        sb.append("Recently: ").append(bot.brain().journalText()).append('\n');
        return sb.toString();
    }

    /**
     * Starts a script and, unless told otherwise, waits for it to finish so the AI gets
     * the outcome in the same turn. The wait happens on the HTTP thread — the game keeps
     * ticking throughout, which is exactly how the script makes progress.
     */
    private static JsonObject runCode(MinecraftServer server, String botName, String code, int waitSeconds) {
        if (code == null || code.isBlank()) return text("No code was given.", true);
        String error = onServerValue(server, () -> {
            AiAssistantEntity bot = resolve(server, botName);
            if (bot == null) return "\0no-bot";
            bot.prepareForScript();
            return bot.brain().runScript(code);
        });
        if ("\0no-bot".equals(error)) return noBot();
        if (error == null) return text("Couldn't reach the bot.", true);
        if (!error.isEmpty()) {
            return text("That script didn't compile: " + error
                    + "\nCall api_reference() to check the actions and try again.", true);
        }

        int wait = Math.max(0, Math.min(MAX_WAIT_SECONDS, waitSeconds));
        long deadline = System.currentTimeMillis() + wait * 1000L;
        boolean running = true;
        while (wait > 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            Boolean stillRunning = onServerValue(server, () -> {
                AiAssistantEntity bot = resolve(server, botName);
                return bot != null && bot.brain().isScripting();
            });
            if (stillRunning == null || !stillRunning) { running = false; break; }
        }

        String log = onServerValue(server, () -> {
            AiAssistantEntity bot = resolve(server, botName);
            return bot == null ? "(the bot is gone)" : bot.brain().scriptLog();
        });
        String prefix = running && wait > 0
                ? "Still running after " + wait + "s — call script_status later.\n"
                : "Script finished.\n";
        return text(prefix + log + "\n\nCall look() again to see the result.", false);
    }

    // ── bot resolution ──────────────────────────────────────────────────────────

    private static AiAssistantEntity resolve(MinecraftServer server, String name) {
        if (name != null && !name.isBlank()) {
            AiAssistantEntity named = find(server, name);
            if (named != null) return named;
        }
        List<AiAssistantEntity> all = AiAssistantEntity.all(server);
        if (selected != null) {
            for (AiAssistantEntity bot : all) {
                if (bot.getUUID().equals(selected)) return bot;
            }
        }
        return all.isEmpty() ? null : all.get(0);
    }

    private static AiAssistantEntity find(MinecraftServer server, String name) {
        if (name == null || name.isBlank()) return null;
        String needle = name.trim().toLowerCase(Locale.ROOT);
        for (AiAssistantEntity bot : AiAssistantEntity.all(server)) {
            if (bot.getAssistantName().toLowerCase(Locale.ROOT).equals(needle)
                    || bot.getOwnerName().toLowerCase(Locale.ROOT).equals(needle)
                    || bot.getStringUUID().equalsIgnoreCase(needle)) {
                return bot;
            }
        }
        for (AiAssistantEntity bot : AiAssistantEntity.all(server)) {
            if (bot.getAssistantName().toLowerCase(Locale.ROOT).contains(needle)) return bot;
        }
        return null;
    }

    private static JsonObject noBot() {
        return text("No companion is out in the world. Ask a player to run /ai summon, "
                + "then call list_bots again.", true);
    }

    // ── server-thread marshalling ───────────────────────────────────────────────

    private static JsonObject onServer(MinecraftServer server, Supplier<JsonObject> work) {
        JsonObject result = onServerValue(server, work);
        return result == null ? text("The server didn't answer in time.", true) : result;
    }

    /** Runs {@code work} on the server thread and waits briefly for its value. */
    private static <T> T onServerValue(MinecraftServer server, Supplier<T> work) {
        if (server.isSameThread()) return work.get();
        CompletableFuture<T> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                future.complete(work.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    // ── JSON helpers ────────────────────────────────────────────────────────────

    private static JsonObject text(String body, boolean isError) {
        JsonObject part = new JsonObject();
        part.addProperty("type", "text");
        part.addProperty("text", body == null ? "" : body);
        JsonArray content = new JsonArray();
        content.add(part);
        JsonObject result = new JsonObject();
        result.add("content", content);
        result.addProperty("isError", isError);
        return result;
    }

    private static JsonObject tool(String name, String description, Props props) {
        JsonObject t = new JsonObject();
        t.addProperty("name", name);
        t.addProperty("description", description);
        t.add("inputSchema", props.schema());
        return t;
    }

    private static Props props() { return new Props(); }

    /** Tiny builder for the JSON Schema an MCP tool advertises. */
    private static final class Props {
        private final JsonObject properties = new JsonObject();
        private final JsonArray required = new JsonArray();

        Props add(String name, String type, String description, boolean isRequired) {
            JsonObject p = new JsonObject();
            p.addProperty("type", type);
            p.addProperty("description", description);
            properties.add(name, p);
            if (isRequired) required.add(name);
            return this;
        }

        JsonObject schema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            schema.add("required", required);
            return schema;
        }
    }

    private static String str(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }

    private static double num(JsonObject o, String key, double fallback) {
        try {
            return o != null && o.has(key) && o.get(key).isJsonPrimitive()
                    ? o.get(key).getAsDouble() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Strips Minecraft's §-colour codes — an AI reading them just sees noise. */
    private static String strip(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }
}
