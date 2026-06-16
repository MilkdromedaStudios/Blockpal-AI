package com.milkdromeda.aiassistant.ai;

import com.milkdromeda.aiassistant.entity.AiAssistantEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AiTaskManager {
    private static final HuggingFaceClient CLIENT = new HuggingFaceClient();

    private final AiAssistantEntity entity;
    private ActionPlan currentPlan;
    private boolean waitingForApi = false;
    private CompletableFuture<ActionPlan> pendingFuture;

    public AiTaskManager(AiAssistantEntity entity) {
        this.entity = entity;
    }

    public void requestPlan(String task) {
        if (waitingForApi) return;
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

            AABB box = AABB.ofSize(entity.position(), 20, 10, 20);
            List<Monster> nearby = sl.getEntitiesOfClass(Monster.class, box, Entity::isAlive);
            if (!nearby.isEmpty()) sb.append("Hostile mobs nearby: ").append(nearby.size()).append("\n");
        }

        sb.append("Health: ").append((int) entity.getHealth()).append("/").append((int) entity.getMaxHealth());
        return sb.toString();
    }
}
