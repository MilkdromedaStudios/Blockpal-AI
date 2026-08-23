package com.milkdromeda.blockpal.agent;

import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>The machine that runs an AI-written script</b>, one slice per game tick.
 *
 * <p>A compiled {@link AgentScript.Program} is a flat op list with jumps, so the runner
 * is just a program counter, a value stack and a variable map. That structure is what
 * makes it safe to run inside a tick loop: when the script calls something that takes
 * real time — {@code walk(40)}, {@code mine()}, {@code goTo(...)} — the runner parks on
 * that {@link BotApi.Task} and returns, and the server ticks on as usual. Nothing here
 * ever blocks a thread or sleeps.
 *
 * <p>Three budgets keep a bad generation harmless:
 * <ul>
 *   <li>{@link #maxOpsPerTick()} instructions per tick (a tight {@code while} loop
 *       burns its own bot's time slice, not the server's);</li>
 *   <li>{@code scriptMaxTicks} total run time from the config;</li>
 *   <li>and any error — bad argument, unknown action, division by nothing — stops the
 *       script with a readable message that gets fed back to the model next round, so
 *       it can correct itself.</li>
 * </ul>
 */
public class ScriptRunner {

    /**
      * Instructions executed per tick before the runner yields back to the game.
      * Set by {@link Tempo}: a tight budget made scripts that do arithmetic between
      * actions crawl, since each tick only advanced them a few hundred ops.
      */
    public static int maxOpsPerTick() { return Tempo.current().opsPerTick(); }

    /** Most log lines kept — this is what the model reads back as "what happened". */
    private static final int MAX_LOG = 40;

    private final AgentScript.Program program;
    private final List<Object> stack = new ArrayList<>();
    private final Map<String, Object> vars = new HashMap<>();
    private final List<String> log = new ArrayList<>();

    private int pc;
    private int ticksRun;
    private boolean finished;
    private String error = "";
    private BotApi.Task pending;

    /** The container this script currently has open (chest, furnace…), if any. */
    private Container openContainer;
    private BlockPos openContainerPos;

    public ScriptRunner(AgentScript.Program program) {
        this.program = program;
    }

    /** Compiles and wraps a script; throws {@link AgentScript.ScriptException} on bad syntax. */
    public static ScriptRunner of(String source) {
        return new ScriptRunner(AgentScript.compile(source));
    }

    // ── state ───────────────────────────────────────────────────────────────────

    public boolean isFinished() { return finished; }
    public boolean failed() { return !error.isEmpty(); }
    public String error() { return error; }
    public int ticksRun() { return ticksRun; }

    public void log(String line) {
        if (line == null || line.isBlank()) return;
        String trimmed = line.length() > 160 ? line.substring(0, 157) + "…" : line;
        // A loop that walks twenty times shouldn't fill the model's read-back with twenty
        // identical lines — count the repeat instead.
        if (!log.isEmpty()) {
            String last = log.get(log.size() - 1);
            if (last.equals(trimmed)) { log.set(log.size() - 1, trimmed + " (×2)"); return; }
            if (last.startsWith(trimmed + " (×")) {
                int n = repeatCount(last);
                log.set(log.size() - 1, trimmed + " (×" + (n + 1) + ")");
                return;
            }
        }
        if (log.size() >= MAX_LOG) log.remove(0);
        log.add(trimmed);
    }

    private static int repeatCount(String line) {
        try {
            int open = line.lastIndexOf("(×");
            return Integer.parseInt(line.substring(open + 2, line.length() - 1));
        } catch (Exception e) {
            return 1;
        }
    }

    /** Everything the script did, as the model will read it back. */
    public String logText() {
        if (log.isEmpty()) return error.isEmpty() ? "(the script ran with nothing to report)" : error;
        StringBuilder sb = new StringBuilder(String.join("\n", log));
        if (!error.isEmpty()) sb.append("\nSTOPPED: ").append(error);
        return sb.toString();
    }

    public Container openContainer() { return openContainer; }
    public BlockPos openContainerPos() { return openContainerPos; }

    public void setOpenContainer(Container container, BlockPos pos) {
        this.openContainer = container;
        this.openContainerPos = pos;
    }

    /** Stops the script now — buttons released, container closed. */
    public void cancel(String reason) {
        if (!finished) {
            finished = true;
            if (error.isEmpty() && reason != null && !reason.isBlank()) error = reason;
        }
        pending = null;
    }

    // ── execution ───────────────────────────────────────────────────────────────

    /**
     * Runs one tick's worth of the script.
     *
     * @return true when the script has finished (successfully or not)
     */
    public boolean tick(AiAssistantEntity bot, ServerLevel level, BotInput input) {
        if (finished) return true;

        int budget = ModConfig.get().scriptMaxTicks;
        if (budget > 0 && ++ticksRun > budget) {
            fail("ran out of time (" + budget + " ticks) — I'll look again and re-plan");
            return true;
        }

        // Parked on a held action? Keep holding it.
        if (pending != null) {
            boolean done;
            try {
                done = pending.tick(bot, level, input);
            } catch (Exception e) {
                fail("action failed: " + safeMessage(e));
                return true;
            }
            if (!done) return false;
            log(pending.summary());
            stack.add(pending.result());
            pending = null;
        }

        int executed = 0;
        int opBudget = maxOpsPerTick();
        while (executed++ < opBudget) {
            if (pc < 0 || pc >= program.ops().size()) { finish(); return true; }
            AgentScript.Op op = program.ops().get(pc++);
            try {
                if (!step(op, bot, level, input)) return false;   // parked on a task
            } catch (AgentScript.ScriptException e) {
                fail(e.getMessage());
                return true;
            } catch (Exception e) {
                fail(safeMessage(e));
                return true;
            }
            if (finished) return true;
        }
        return false;
    }

    /** @return false when the runner has parked on a held action. */
    private boolean step(AgentScript.Op op, AiAssistantEntity bot, ServerLevel level, BotInput input) {
        switch (op.code()) {
            case PUSH -> stack.add(op.operand());
            case LOAD -> stack.add(vars.get(String.valueOf(op.operand())));
            case STORE -> vars.put(String.valueOf(op.operand()), pop());
            case POP -> { if (!stack.isEmpty()) pop(); }
            case NEG -> stack.add(-toNumber(pop()));
            case NOT -> stack.add(!toBool(pop()));
            case BINOP -> stack.add(binop(String.valueOf(op.operand()), pop(), pop()));
            case JUMP -> pc = op.arg();
            case JUMP_IF_FALSE -> { if (!toBool(pop())) pc = op.arg(); }
            case HALT -> finish();
            case CALL -> {
                int argc = op.arg();
                List<Object> args = new ArrayList<>(argc);
                for (int i = 0; i < argc; i++) args.add(0, pop());
                Object result = BotApi.call(String.valueOf(op.operand()), args, bot, level, input, this);
                if (result instanceof BotApi.Task task) {
                    pending = task;
                    return false;
                }
                stack.add(result);
            }
        }
        return true;
    }

    private void finish() {
        finished = true;
        pending = null;
    }

    private void fail(String reason) {
        error = reason == null || reason.isBlank() ? "the script stopped unexpectedly" : reason;
        finish();
    }

    private static String safeMessage(Exception e) {
        String m = e.getMessage();
        return (m == null || m.isBlank()) ? e.getClass().getSimpleName() : m;
    }

    private Object pop() {
        if (stack.isEmpty()) return null;
        return stack.remove(stack.size() - 1);
    }

    // ── value semantics ─────────────────────────────────────────────────────────

    /** Note the operand order: the stack pops right-hand side first. */
    private static Object binop(String op, Object rhs, Object lhs) {
        switch (op) {
            case "&&": return toBool(lhs) && toBool(rhs);
            case "||": return toBool(lhs) || toBool(rhs);
            case "==": return equalValues(lhs, rhs);
            case "!=": return !equalValues(lhs, rhs);
            default: break;
        }
        // "+" concatenates when either side is text — the natural thing for say("got " + n).
        if ("+".equals(op) && (lhs instanceof String || rhs instanceof String)) {
            return asText(lhs) + asText(rhs);
        }
        double a = toNumber(lhs), b = toNumber(rhs);
        return switch (op) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "*" -> a * b;
            case "/" -> b == 0 ? 0.0 : a / b;
            case "%" -> b == 0 ? 0.0 : a % b;
            case "<" -> a < b;
            case ">" -> a > b;
            case "<=" -> a <= b;
            case ">=" -> a >= b;
            default -> throw new AgentScript.ScriptException("unknown operator '" + op + "'");
        };
    }

    private static boolean equalValues(Object a, Object b) {
        if (a instanceof String || b instanceof String) return asText(a).equals(asText(b));
        if (a instanceof Boolean || b instanceof Boolean) return toBool(a) == toBool(b);
        return toNumber(a) == toNumber(b);
    }

    private static double toNumber(Object o) {
        if (o instanceof Double d) return d;
        if (o instanceof Boolean b) return b ? 1 : 0;
        if (o instanceof String s) {
            try { return Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) { return 0; }
        }
        return 0;
    }

    private static boolean toBool(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof Double d) return d != 0;
        if (o instanceof String s) return !s.isBlank() && !"false".equalsIgnoreCase(s);
        return false;
    }

    private static String asText(Object o) {
        if (o == null) return "";
        if (o instanceof Double d) {
            return d == Math.floor(d) && !d.isInfinite() ? String.valueOf(d.longValue()) : String.valueOf(d);
        }
        return String.valueOf(o);
    }
}
