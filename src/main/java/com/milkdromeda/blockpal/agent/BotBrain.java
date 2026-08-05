package com.milkdromeda.blockpal.agent;

import com.milkdromeda.blockpal.ai.AiConnection;
import com.milkdromeda.blockpal.ai.HuggingFaceClient;
import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import com.milkdromeda.blockpal.vision.BotVision;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * <b>Look → think → write code → press the buttons.</b> This is the loop that replaced
 * the old "ask for a JSON action list and execute it" planner.
 *
 * <p>Every round the bot:
 * <ol>
 *   <li><b>looks</b> — {@link BotVision} renders what its eyes can actually see into a
 *       small picture plus a written scene;</li>
 *   <li><b>thinks</b> — the picture, the scene, its goal and what happened last round go
 *       to the model, which answers with a short {@link AgentScript} program;</li>
 *   <li><b>acts</b> — {@link ScriptRunner} runs that program through {@link BotInput},
 *       which is only ever movement keys and mouse buttons.</li>
 * </ol>
 *
 * <p>It is <i>less</i> capable than the old planner on paper — a model has to work out
 * how to walk to a tree rather than naming coordinates it was handed — and that is the
 * trade being made deliberately: nothing here reaches through walls, teleports, or
 * conjures blocks. What the bot achieves, it achieved with a player's controls.
 *
 * <p>When the AI connection is {@link AiConnection#MCP} (an outside app drives through
 * the MCP server) or {@code off}, no thinking happens here at all — but the
 * {@link SurvivalBrain} still runs, so the companion keeps itself alive either way.
 */
public class BotBrain {

    /** Minimum ticks between two thinking rounds — one look/plan every few seconds. */
    private static final int THINK_INTERVAL = 100;   // 5 s
    /** Extra pause after a failed round, so a broken model can't be asked 20×/second. */
    private static final int ERROR_BACKOFF = 200;    // 10 s

    private static final HuggingFaceClient CLIENT = new HuggingFaceClient();

    private final AiAssistantEntity bot;
    private final BotInput input = new BotInput();
    private final SurvivalBrain survival = new SurvivalBrain();
    /** Recent outcomes, fed back to the model so it can see whether its last idea worked. */
    private final Deque<String> journal = new ArrayDeque<>();

    private ScriptRunner runner;
    private String goal = "";
    private String lastThought = "";
    private int thinkCooldown = 40;
    private boolean thinking;
    private boolean controlsDisabled;

    public BotBrain(AiAssistantEntity bot) {
        this.bot = bot;
    }

    // ── state ───────────────────────────────────────────────────────────────────

    public BotInput input() { return input; }

    /** True while an AI-written (or hand-written) script is driving this bot. */
    public boolean isScripting() {
        return runner != null && !runner.isFinished();
    }

    /** True when this brain — script or survival reflex — is holding the controls. */
    public boolean isDriving() {
        return isScripting() || survival.isActing();
    }

    /** What the bot is currently trying to do, in the player's own words. */
    public String goal() { return goal; }

    public void setGoal(String goal) {
        this.goal = goal == null ? "" : goal.trim();
        thinkCooldown = 0;      // a new instruction gets thought about immediately
    }

    public String lastThought() { return lastThought; }

    /** The last few things that happened, newest last — also what the model reads back. */
    public String journalText() {
        return journal.isEmpty() ? "(nothing yet)" : String.join("\n", journal);
    }

    private void note(String line) {
        if (line == null || line.isBlank()) return;
        while (journal.size() >= 6) journal.removeFirst();
        journal.addLast(line.length() > 300 ? line.substring(0, 297) + "…" : line);
    }

    // ── running code ────────────────────────────────────────────────────────────

    /**
     * Compiles and starts a script, replacing anything already running.
     *
     * @return "" when it started, or a readable compile error
     */
    public String runScript(String source) {
        stopScript();
        try {
            runner = ScriptRunner.of(source);
            return "";
        } catch (AgentScript.ScriptException e) {
            runner = null;
            return e.getMessage();
        } catch (Exception e) {
            runner = null;
            return "I couldn't read that script.";
        }
    }

    /** Stops any running script and lets go of every button. */
    public void stopScript() {
        if (runner != null) runner.cancel("");
        runner = null;
        input.releaseAll();
        releaseControls();
    }

    /** Full stop: script, goal and pending thought. */
    public void stop() {
        goal = "";
        stopScript();
    }

    /** The log of the script that is running (or just ran). */
    public String scriptLog() {
        return runner == null ? "(no script has run yet)" : runner.logText();
    }

    // ── per-tick ────────────────────────────────────────────────────────────────

    /** Called from the entity's own tick, on the server thread. */
    public void tick(ServerLevel level) {
        if (isScripting()) {
            takeControls();
            boolean done = runner.tick(bot, level, input);
            input.tick(bot, level);
            if (done) {
                String log = runner.logText();
                note("I ran a script: " + log);
                if (runner.failed()) thinkCooldown = Math.max(thinkCooldown, 20);
                runner = null;
                input.releaseAll();
                releaseControls();
            }
            return;
        }

        // Nothing scripted: keep-alive reflexes get a look in.
        if (ModConfig.get().survivalBrain && survival.tick(bot, level, input)) {
            takeControls();
            input.tick(bot, level);
            return;
        }
        releaseControls();

        if (thinkCooldown > 0) thinkCooldown--;
        else maybeThink(level);
    }

    /**
     * While the brain drives, the vanilla goal system must not fight it for the same
     * controls — otherwise a stroll goal would steer mid-script. The flags are handed
     * back the moment the script ends, so ordinary following/combat resumes.
     */
    private void takeControls() {
        if (controlsDisabled) return;
        controlsDisabled = true;
        bot.setManualControl(true);
    }

    private void releaseControls() {
        if (!controlsDisabled) return;
        controlsDisabled = false;
        bot.setManualControl(false);
    }

    // ── thinking ────────────────────────────────────────────────────────────────

    private void maybeThink(ServerLevel level) {
        if (thinking) return;
        ModConfig cfg = ModConfig.get();
        if (!"code".equalsIgnoreCase(cfg.aiLogicMode)) return;          // classic planner instead
        AiConnection connection = cfg.connection();
        if (!connection.usesModel()) return;                            // MCP / off: not our job
        if (!bot.isAutonomous() && goal.isEmpty()) return;              // told to stand still
        if (com.milkdromeda.blockpal.EmergencyState.isDisabled()) return;

        HuggingFaceClient.ApiAuth auth = bot.resolveAuth();
        if (auth == null || !auth.usable()) return;

        thinkCooldown = THINK_INTERVAL;
        thinking = true;

        BotVision.Snapshot look = bot.look();
        String task = goal.isEmpty() ? SELF_DIRECTED : goal;

        CLIENT.requestCode(task, look.description(), look.dataUri(), journalText(),
                        bot.describeCarrying(), auth, bot.getPlanStyle())
                .whenComplete((reply, ex) -> {
                    MinecraftServer server = level.getServer();
                    if (server == null) { thinking = false; return; }
                    server.execute(() -> {
                        thinking = false;
                        if (!bot.isAlive()) return;
                        if (ex != null || reply == null) {
                            thinkCooldown = ERROR_BACKOFF;
                            return;
                        }
                        applyReply(reply);
                    });
                });
    }

    private void applyReply(HuggingFaceClient.CodeReply reply) {
        if (!reply.error().isBlank()) {
            note("I couldn't think straight: " + reply.error());
            thinkCooldown = ERROR_BACKOFF;
            return;
        }
        lastThought = reply.thinking();
        if (!reply.say().isBlank()) bot.broadcastMessage(reply.say());
        if (reply.code().isBlank()) {
            note("I decided to do nothing this time.");
            return;
        }
        String error = runScript(reply.code());
        if (!error.isEmpty()) {
            // Hand the compile error back next round — the model usually fixes it.
            note("My script didn't compile: " + error);
            thinkCooldown = ERROR_BACKOFF;
        } else {
            note("Plan: " + (reply.thinking().isBlank() ? "(no reasoning given)" : reply.thinking()));
        }
    }

    /** What the bot pursues when nobody has given it an instruction. */
    private static final String SELF_DIRECTED =
            "No one has given me an order. Keep myself alive and useful like a survival "
            + "player would: stay fed and safe, gather wood, stone, food or ore, store what "
            + "I don't need in a chest if there's one nearby, and stay near my owner. "
            + "Pick ONE small thing to do right now and do it.";
}
