package com.milkdromeda.blockpal.ai;

import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AiTaskManager {
    private static final HuggingFaceClient CLIENT = new HuggingFaceClient();

    private final AiAssistantEntity entity;
    private ActionPlan currentPlan;
    private boolean waitingForApi = false;
    private CompletableFuture<ActionPlan> pendingFuture;
    private String lastTask;
    private boolean lastLoop = false;
    private int loopCooldown = 0;

    public AiTaskManager(AiAssistantEntity entity) {
        this.entity = entity;
    }

    private int lastRequestTick = -600;

    public void requestPlan(String task) {
        if (waitingForApi) return;
        // Hard rate-limit: never fire two plan requests within 5 seconds regardless of caller.
        int now = (int)(entity.level().getGameTime() & Integer.MAX_VALUE);
        if (now - lastRequestTick < 100) return; // 100 ticks = 5 s
        lastRequestTick = now;
        lastTask = task;
        waitingForApi = true;
        pendingFuture = CLIENT.requestPlan(task, buildContext());
    }

    /** Whether the current/last activity asked to keep going after its steps run out. */
    public boolean isLooping() {
        return lastLoop && lastTask != null;
    }

    /** Re-requests the looping task with fresh context (throttled), keeping the activity alive. */
    public void loopAgain() {
        if (waitingForApi || lastTask == null) return;
        if (loopCooldown > 0) { loopCooldown--; return; }
        loopCooldown = 10 * 20; // 10 s minimum between loop iterations
        String task = lastTask;
        currentPlan = null;
        waitingForApi = true;
        pendingFuture = CLIENT.requestPlan(task, buildContext());
    }

    /** Asks the language model to interpret a free-form chat message. */
    public CompletableFuture<ChatIntent> classify(String message, String context, String assistantName) {
        return CLIENT.classifyMessage(message, context, assistantName);
    }

    public void tick() {
        if (waitingForApi && pendingFuture != null && pendingFuture.isDone()) {
            waitingForApi = false;
            try {
                currentPlan = pendingFuture.get();
                lastLoop = currentPlan != null && currentPlan.loop;
            } catch (Exception e) {
                currentPlan = null;
            }
            pendingFuture = null;
        }
    }

    public ActionStep pollNextStep() {
        if (currentPlan == null || currentPlan.isEmpty()) return null;
        return currentPlan.poll();
    }

    public boolean hasPlan() { return currentPlan != null && !currentPlan.isEmpty(); }
    public boolean isWaiting() { return waitingForApi; }

    public void clearPlan() {
        currentPlan = null;
        waitingForApi = false;
        lastLoop = false;
        lastTask = null;
        loopCooldown = 0;
        if (pendingFuture != null) { pendingFuture.cancel(true); pendingFuture = null; }
    }

    public String getPlanDescription() {
        return currentPlan != null ? currentPlan.description : "none";
    }

    private String buildContext() {
        BlockPos pos = entity.blockPosition();
        StringBuilder sb = new StringBuilder();
        sb.append("AI position: ").append(pos.getX()).append(",").append(pos.getY()).append(",").append(pos.getZ()).append("\n");

        if (entity.level() instanceof ServerLevel sl) {
            List<ServerPlayer> players = sl.players();
            if (!players.isEmpty()) {
                sb.append("Players: ");
                players.stream().limit(5).forEach(p ->
                        sb.append(p.getName().getString())
                                .append("@").append(p.blockPosition().getX())
                                .append(",").append(p.blockPosition().getY())
                                .append(",").append(p.blockPosition().getZ()).append(" "));
                sb.append("\n");
            }

            AABB box = AABB.ofSize(entity.position(), 24, 12, 24);
            List<Monster> nearby = sl.getEntitiesOfClass(Monster.class, box, Entity::isAlive);
            if (!nearby.isEmpty()) {
                Monster m = nearby.get(0);
                sb.append("Hostile mobs nearby: ").append(nearby.size())
                        .append(" (nearest ").append(m.getType().toShortString())
                        .append(" @").append(m.blockPosition().getX()).append(",")
                        .append(m.blockPosition().getY()).append(",")
                        .append(m.blockPosition().getZ()).append(")\n");
            }

            long t = sl.getOverworldClockTime() % 24000L;
            sb.append("Time: ").append(t >= 13000 && t < 23000 ? "night" : "day").append("\n");

            String interactables = scanInteractables(sl);
            if (!interactables.isEmpty()) {
                sb.append("Interactables (use USE_BLOCK or wire with redstone): ")
                        .append(interactables).append("\n");
            }
        }

        sb.append("Health: ").append((int) entity.getHealth()).append("/").append((int) entity.getMaxHealth());
        return sb.toString();
    }

    /** Lists nearby interactive/redstone blocks so the planner can solve puzzles and wire things. */
    private String scanInteractables(ServerLevel sl) {
        BlockPos center = entity.blockPosition();
        int r = 6;
        StringBuilder sb = new StringBuilder();
        int found = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r && found < 12; dx++) {
            for (int dy = -3; dy <= 3 && found < 12; dy++) {
                for (int dz = -r; dz <= r && found < 12; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = sl.getBlockState(cursor);
                    if (state.isAir()) continue;
                    Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    String path = id.getPath();
                    if (isInteresting(path)) {
                        sb.append(path).append("@").append(cursor.getX()).append(",")
                                .append(cursor.getY()).append(",").append(cursor.getZ()).append("; ");
                        found++;
                    }
                }
            }
        }
        return sb.toString().trim();
    }

    private boolean isInteresting(String path) {
        return path.contains("lever") || path.contains("button") || path.contains("door")
                || path.contains("pressure_plate") || path.contains("redstone")
                || path.contains("repeater") || path.contains("comparator") || path.contains("observer")
                || path.contains("piston") || path.contains("dispenser") || path.contains("dropper")
                || path.contains("hopper") || path.contains("chest") || path.contains("lectern")
                || path.contains("note_block") || path.contains("target") || path.contains("tripwire")
                || path.contains("lamp") || path.contains("bell");
    }
}
