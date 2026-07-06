package com.milkdromeda.blockpal.client.assist;

import com.milkdromeda.blockpal.ai.ActionPlan;
import com.milkdromeda.blockpal.ai.ActionStep;
import com.milkdromeda.blockpal.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Client-side possession: the AI driving <b>your own</b> character on a server that
 * does not run Blockpal, by simulating your keyboard/mouse inputs. Runs entirely on
 * your client from typed instructions — no server mod needed to be driven.
 *
 * <h3>Why this can't get you banned for cheating (by design)</h3>
 * It only ever <em>simulates the normal inputs you could press yourself</em> (walk,
 * jump, sneak, mine the block under your crosshair, use/place, walk over drops), so
 * the packets the server sees are ordinary player packets — there is no reach/speed/
 * fly hack here. Crucially it is limited to <b>basic survival tasks</b>:
 * <ul>
 *   <li>It <b>never attacks players or mobs</b> — the attack key is released the moment
 *       your crosshair is on any entity, so it can give <b>no PvP or combat advantage</b>.</li>
 *   <li>It <b>never types in chat</b> and <b>never runs commands</b> from a plan.</li>
 * </ul>
 * On top of that {@link ServerGuard} hard-blocks it on networks that forbid automation
 * (Hypixel &amp; co.). The unavoidable honest caveat remains: automation is against
 * <i>some</i> servers' rules even when it isn't a cheat, so on a non-Blockpal server
 * this is opt-in and warned — use it in your own worlds by default.
 */
public final class ClientPossession {

    /** The only actions the off-server driver will ever perform (a safe, non-combat subset). */
    private static final Set<ActionStep.ActionType> ALLOWED = EnumSet.of(
            ActionStep.ActionType.MOVE_TO, ActionStep.ActionType.BREAK_BLOCK,
            ActionStep.ActionType.MINE_AREA, ActionStep.ActionType.PLACE_BLOCK,
            ActionStep.ActionType.USE_BLOCK, ActionStep.ActionType.COLLECT_ITEM,
            ActionStep.ActionType.JUMP, ActionStep.ActionType.SET_SNEAK,
            ActionStep.ActionType.LOOK_AT, ActionStep.ActionType.WAIT,
            ActionStep.ActionType.STOP);

    private static final double REACH = 4.4;
    private static final double REACH_SQR = REACH * REACH;
    private static final double ARRIVE_SQR = 1.4 * 1.4;
    private static final int STEP_TIMEOUT = 200;   // ~10s per step
    private static final int PLAN_COOLDOWN = 60;   // ~3s between plan requests
    /** Stop driving (for safety) below this fraction of max health. */
    private static final double SAFETY_HEALTH = 0.30;

    private static boolean active;
    private static String pendingInstruction;
    private static boolean waitingForApi;
    private static CompletableFuture<ActionPlan> future;
    private static ActionPlan plan;
    private static ActionStep step;
    private static int stepTimer;
    private static int waitRemaining;
    private static int tickCounter;
    private static int lastRequestTick = -PLAN_COOLDOWN;
    private static boolean sneakHeld;
    private static boolean drivingKeys;
    private static final Queue<BlockPos> mineQueue = new ArrayDeque<>();

    private ClientPossession() {}

    public static boolean isActive() {
        return active;
    }

    // ── control ───────────────────────────────────────────────────────────────────

    /** Begins (or continues) driving, queueing {@code instruction}. Returns a status line. */
    public static String start(String instruction) {
        ServerGuard.Driving verdict = ServerGuard.driving();
        if (verdict != ServerGuard.Driving.ALLOWED && verdict != ServerGuard.Driving.ALLOWED_WITH_WARNING) {
            return ServerGuard.explain(verdict);
        }
        if (!ClientAi.available()) {
            return "§cNo AI configured — add a key with §f/ai mykey <token>§c or enable the free AI in §f/ai menu§c.";
        }
        active = true;
        if (instruction != null && !instruction.isBlank()) {
            pendingInstruction = instruction.trim();
            plan = null;
            resetStep();
        }
        return verdict == ServerGuard.Driving.ALLOWED_WITH_WARNING
                ? ServerGuard.explain(verdict)
                : "§aDriving — tell me what to do.";
    }

    /** Ends driving and hands control back to you. */
    public static void stop() {
        active = false;
        pendingInstruction = null;
        plan = null;
        waitingForApi = false;
        future = null;
        sneakHeld = false;
        drivingKeys = false;
        resetStep();
        releaseAllKeys();
    }

    // ── per-tick (client thread) ────────────────────────────────────────────────────

    /** Wired to {@code ClientTickEvents.END_CLIENT_TICK} in the client initializer. */
    public static void tick() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { return; }

        // Re-check the guard live — if you changed servers or it flipped, stand down.
        ServerGuard.Driving verdict = ServerGuard.driving();
        if (verdict != ServerGuard.Driving.ALLOWED && verdict != ServerGuard.Driving.ALLOWED_WITH_WARNING) {
            PossessionLog.add(ServerGuard.explain(verdict));
            stop();
            return;
        }

        // Safety: never drive you into a fight or off a cliff at low health.
        if (p.getHealth() < p.getMaxHealth() * SAFETY_HEALTH) {
            if (plan != null) PossessionLog.add("§ePaused — your health is low. Say what to do once you've recovered.");
            plan = null;
            idle();
            return;
        }

        tickCounter++;

        // 1) Adopt a finished plan request.
        if (waitingForApi && future != null && future.isDone()) {
            waitingForApi = false;
            ActionPlan got = null;
            try { got = future.get(); } catch (Exception ignored) {}
            future = null;
            plan = sanitize(got);
            resetStep();
            if (plan != null && plan.description != null) PossessionLog.add("§7Plan: " + plan.description);
            else if (got != null) PossessionLog.add("§7Nothing safe to do for that — try rephrasing (mining/gathering only).");
        }

        // 2) Kick off a pending instruction (rate-limited, one in flight).
        if (pendingInstruction != null && !waitingForApi
                && tickCounter - lastRequestTick >= PLAN_COOLDOWN) {
            requestPlan(p, pendingInstruction);
            pendingInstruction = null;
        }

        // 3) Execute the current plan against you — but ONLY touch your inputs while a
        // plan is actually running. When idle (awaiting an instruction or a reply) we
        // leave your keys alone so you can still move around yourself.
        if (plan != null) {
            resetKeys(mc);              // clear last tick's inputs; the step re-asserts what it needs
            executePlan(mc, p);
            drivingKeys = true;
        } else {
            idle();
        }
    }

    /** Releases our simulated inputs exactly once when we transition to idle. */
    private static void idle() {
        if (drivingKeys) {
            sneakHeld = false;
            releaseAllKeys();
            drivingKeys = false;
        }
    }

    private static void requestPlan(LocalPlayer p, String instruction) {
        lastRequestTick = tickCounter;
        waitingForApi = true;
        plan = null;
        resetStep();
        PossessionLog.add("§7Thinking about: §f" + instruction);
        future = ClientAi.plan(wrap(instruction), context(p));
    }

    /** Frames the task so the model plans only safe, non-combat actions. */
    private static String wrap(String instruction) {
        return "You are in CLIENT POSSESSION MODE, remotely controlling a Minecraft player's own body on a "
                + "vanilla server. You may ONLY use these actions: MOVE_TO, BREAK_BLOCK, MINE_AREA, PLACE_BLOCK, "
                + "USE_BLOCK, COLLECT_ITEM, JUMP, SET_SNEAK, LOOK_AT, WAIT, STOP. You must NOT attack players or "
                + "mobs, must NOT use ATTACK_NEAREST, must NOT run commands, and must NOT chat. Keep it to basic "
                + "survival tasks like mining, digging, gathering and walking. Coordinates are absolute world "
                + "coordinates from the context. Keep the plan short (a few concrete steps) and do not loop. "
                + "Instruction: " + instruction;
    }

    /** Drops any disallowed steps a plan may contain — belt-and-braces over the prompt. */
    private static ActionPlan sanitize(ActionPlan in) {
        if (in == null) return null;
        java.util.List<ActionStep> kept = new java.util.ArrayList<>();
        ActionStep s;
        while ((s = in.poll()) != null) {
            if (ALLOWED.contains(s.type())) kept.add(s);
        }
        if (kept.isEmpty()) return null;
        return new ActionPlan(in.thinking, in.description, kept, false);
    }

    // ── execution ───────────────────────────────────────────────────────────────────

    private static void executePlan(Minecraft mc, LocalPlayer p) {
        if (waitRemaining > 0) { waitRemaining--; return; }
        if (step == null) {
            step = plan.poll();
            stepTimer = 0;
            mineQueue.clear();
            if (step == null) {
                plan = null;
                PossessionLog.add("§aDone — waiting for your next instruction.");
                return;
            }
        }
        boolean done;
        try {
            done = executeStep(mc, p, step);
        } catch (Exception e) {
            done = true;   // never let a quirky step wedge the driver
        }
        stepTimer++;
        if (done || stepTimer > STEP_TIMEOUT) {
            step = null;
            mineQueue.clear();
            waitRemaining = Math.max(1, ModConfig.get().actionTickDelay);
        }
    }

    private static boolean executeStep(Minecraft mc, LocalPlayer p, ActionStep s) {
        return switch (s.type()) {
            case MOVE_TO, COLLECT_ITEM -> {
                double x = s.getDouble("x", p.getX()), y = s.getDouble("y", p.getY()), z = s.getDouble("z", p.getZ());
                yield steerTowards(mc, p, x, y, z);
            }
            case BREAK_BLOCK -> breakBlock(mc, p,
                    s.getInt("x", (int) p.getX()), s.getInt("y", (int) p.getY()), s.getInt("z", (int) p.getZ()));
            case MINE_AREA -> mineArea(mc, p, s);
            case PLACE_BLOCK, USE_BLOCK -> {
                int x = s.getInt("x", (int) p.getX()), y = s.getInt("y", (int) p.getY()), z = s.getInt("z", (int) p.getZ());
                if (blockDistSqr(p, x, y, z) > REACH_SQR) { steerTowards(mc, p, x + 0.5, y, z + 0.5); yield false; }
                face(p, x + 0.5, y + 0.5, z + 0.5);
                mc.options.keyUse.setDown(true);   // read next tick as a use/place click
                yield true;
            }
            case JUMP -> { mc.options.keyJump.setDown(true); yield true; }
            case SET_SNEAK -> { sneakHeld = s.getBool("value", true); yield true; }
            case LOOK_AT -> {
                face(p, s.getDouble("x", p.getX()), s.getDouble("y", p.getEyeY()), s.getDouble("z", p.getZ()));
                yield true;
            }
            case WAIT -> { waitRemaining = Math.max(1, s.getInt("ticks", 20)); step = null; yield true; }
            case STOP -> { plan = null; PossessionLog.add("§7Stopped."); yield true; }
            // Anything else (ATTACK_NEAREST/RUN_COMMAND/CHAT/FOLLOW_PLAYER) is filtered out
            // by sanitize() and can't reach here, but fail closed just in case.
            default -> true;
        };
    }

    /** Walks toward a point by holding "forward" while facing it; jumps to climb/unstick. */
    private static boolean steerTowards(Minecraft mc, LocalPlayer p, double tx, double ty, double tz) {
        face(p, tx, ty, tz);
        double dx = tx - p.getX(), dz = tz - p.getZ();
        double horizSqr = dx * dx + dz * dz;
        if (horizSqr < ARRIVE_SQR) return true;
        mc.options.keyUp.setDown(true);
        boolean needUp = ty > p.getY() + 0.6;
        if (p.onGround() && (needUp || p.horizontalCollision)) mc.options.keyJump.setDown(true);
        return false;
    }

    private static boolean breakBlock(Minecraft mc, LocalPlayer p, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (mc.level.getBlockState(pos).isAir()) return true;      // already gone
        if (blockDistSqr(p, x, y, z) > REACH_SQR) {
            steerTowards(mc, p, x + 0.5, y + 0.5, z + 0.5);
            return false;
        }
        face(p, x + 0.5, y + 0.5, z + 0.5);
        // SAFETY: only hold attack when the crosshair is on a BLOCK. If it's on an
        // entity (or nothing), release — we must never land a hit on a player or mob.
        HitResult hit = mc.hitResult;
        boolean onTargetBlock = hit instanceof BlockHitResult bhr
                && bhr.getType() == HitResult.Type.BLOCK
                && bhr.getBlockPos().equals(pos);
        if (onTargetBlock) mc.options.keyAttack.setDown(true);
        return mc.level.getBlockState(pos).isAir();
    }

    private static boolean mineArea(Minecraft mc, LocalPlayer p, ActionStep s) {
        if (mineQueue.isEmpty()) {
            int x1 = s.getInt("x1", (int) p.getX()), y1 = s.getInt("y1", (int) p.getY()), z1 = s.getInt("z1", (int) p.getZ());
            int x2 = s.getInt("x2", x1), y2 = s.getInt("y2", y1), z2 = s.getInt("z2", z1);
            int minX = Math.min(x1, x2), minY = Math.min(y1, y2), minZ = Math.min(z1, z2);
            int maxX = Math.min(Math.max(x1, x2), minX + 5);   // cap the box so a bad plan can't queue thousands
            int maxY = Math.min(Math.max(y1, y2), minY + 5);
            int maxZ = Math.min(Math.max(z1, z2), minZ + 5);
            for (int x = minX; x <= maxX; x++)
                for (int y = minY; y <= maxY; y++)
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos bp = new BlockPos(x, y, z);
                        if (!mc.level.getBlockState(bp).isAir()) mineQueue.add(bp);
                    }
            if (mineQueue.isEmpty()) return true;
        }
        BlockPos next = mineQueue.peek();
        if (next == null) return true;
        boolean gone = breakBlock(mc, p, next.getX(), next.getY(), next.getZ());
        if (gone) mineQueue.poll();
        return mineQueue.isEmpty();
    }

    // ── input / look helpers ─────────────────────────────────────────────────────────

    /** Clears all inputs we manage, keeping the sneak toggle asserted. */
    private static void resetKeys(Minecraft mc) {
        Options o = mc.options;
        o.keyUp.setDown(false);
        o.keyDown.setDown(false);
        o.keyLeft.setDown(false);
        o.keyRight.setDown(false);
        o.keyJump.setDown(false);
        o.keyAttack.setDown(false);
        o.keyUse.setDown(false);
        o.keyShift.setDown(sneakHeld);
    }

    private static void releaseAllKeys() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;
        Options o = mc.options;
        o.keyUp.setDown(false);
        o.keyDown.setDown(false);
        o.keyLeft.setDown(false);
        o.keyRight.setDown(false);
        o.keyJump.setDown(false);
        o.keyAttack.setDown(false);
        o.keyUse.setDown(false);
        o.keyShift.setDown(false);
    }

    private static void face(LocalPlayer p, double tx, double ty, double tz) {
        double dx = tx - p.getX(), dy = ty - p.getEyeY(), dz = tz - p.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
        p.setXRot(Math.max(-90f, Math.min(90f, pitch)));
    }

    private static double blockDistSqr(LocalPlayer p, int x, int y, int z) {
        return p.distanceToSqr(x + 0.5, y + 0.5, z + 0.5);
    }

    private static void resetStep() {
        step = null;
        stepTimer = 0;
        waitRemaining = 0;
        mineQueue.clear();
    }

    // ── context ─────────────────────────────────────────────────────────────────────

    private static String context(LocalPlayer p) {
        BlockPos pos = p.blockPosition();
        StringBuilder sb = new StringBuilder();
        sb.append("You are controlling player ").append(p.getName().getString()).append(".\n");
        sb.append("Player position: ").append(pos.getX()).append(",").append(pos.getY()).append(",").append(pos.getZ()).append("\n");
        try {
            sb.append("Dimension: ").append(p.level().dimension().identifier().getPath()).append("\n");
        } catch (Exception ignored) {}
        sb.append("Health: ").append((int) p.getHealth()).append("/").append((int) p.getMaxHealth()).append("\n");
        sb.append("Only mining/gathering/movement is available (no combat, no commands, no chat).");
        return sb.toString();
    }
}
