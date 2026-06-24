package com.milkdromeda.blockpal.ai;

import com.milkdromeda.blockpal.config.ModConfig;
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
        // Resolve the owner's key/model. No key (e.g. bring-your-own-key is on and
        // they haven't set one) → say so instead of firing a doomed request.
        HuggingFaceClient.ApiAuth auth = authForOwner();
        if (!auth.hasToken()) {
            entity.broadcastMessage(needKeyMessage());
            return;
        }
        lastTask = task;
        waitingForApi = true;
        pendingFuture = CLIENT.requestPlan(task, buildContext(), auth, entity.getPlanStyle());
    }

    /** The token + model a request from this bot should use, based on its owner. */
    private HuggingFaceClient.ApiAuth authForOwner() {
        ModConfig cfg = ModConfig.get();
        java.util.UUID owner = entity.getOwnerUuid();
        return new HuggingFaceClient.ApiAuth(
                cfg.resolveTokenFor(owner, entity.getOwnerName()),
                cfg.resolveModelFor(owner));
    }

    private String needKeyMessage() {
        if (ModConfig.get().requireOwnApiKey) {
            return "I need your own API key for that — set it with /ai mykey <token> "
                    + "(ask an admin if you think you should be exempt).";
        }
        return "I don't have an API key to use — an admin can set one with /ai token <token>.";
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
        HuggingFaceClient.ApiAuth auth = authForOwner();
        if (!auth.hasToken()) return;   // no key → quietly stop looping
        String task = lastTask;
        currentPlan = null;
        waitingForApi = true;
        pendingFuture = CLIENT.requestPlan(task, buildContext(), auth, entity.getPlanStyle());
    }

    /** Asks the language model to interpret a free-form chat message. */
    public CompletableFuture<ChatIntent> classify(String message, String context, String assistantName) {
        return CLIENT.classifyMessage(message, context, assistantName, authForOwner());
    }

    /** Asks the language model to safety-check a player-written custom personality. */
    public CompletableFuture<HuggingFaceClient.Moderation> moderatePersonality(String text) {
        return CLIENT.moderatePersonality(text, authForOwner());
    }

    /** Whether this bot's owner has a usable API key (needed to moderate custom text). */
    public boolean hasApiKey() {
        return authForOwner().hasToken();
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
