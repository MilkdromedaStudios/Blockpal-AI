package com.milkdromeda.aiassistant.ai;

import com.milkdromeda.aiassistant.entity.AiAssistantEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

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

    public boolean hasPlan() {
        return currentPlan != null && !currentPlan.isEmpty();
    }

    public boolean isWaiting() {
        return waitingForApi;
    }

    public void clearPlan() {
        currentPlan = null;
        waitingForApi = false;
        if (pendingFuture != null) {
            pendingFuture.cancel(true);
            pendingFuture = null;
        }
    }

    public String getPlanDescription() {
        return currentPlan != null ? currentPlan.description : "none";
    }

    private String buildContext() {
        BlockPos pos = entity.getBlockPos();
        StringBuilder sb = new StringBuilder();
        sb.append("AI position: ").append(pos.getX()).append(", ").append(pos.getY()).append(", ").append(pos.getZ()).append("\n");

        if (entity.getWorld() instanceof ServerWorld sw) {
            List<ServerPlayerEntity> players = sw.getPlayers();
            if (!players.isEmpty()) {
                sb.append("Nearby players: ");
                players.stream().limit(5).forEach(p ->
                        sb.append(p.getName().getString())
                                .append("@").append(p.getBlockPos().getX())
                                .append(",").append(p.getBlockPos().getY())
                                .append(",").append(p.getBlockPos().getZ()).append(" "));
                sb.append("\n");
            }

            Box searchBox = Box.of(entity.getPos(), 20, 10, 20);
            List<HostileEntity> nearby = sw.getEntitiesByClass(HostileEntity.class, searchBox, Entity::isAlive);
            if (!nearby.isEmpty()) {
                sb.append("Hostile mobs nearby: ").append(nearby.size()).append("\n");
            }
        }

        sb.append("Health: ").append((int) entity.getHealth())
                .append("/").append((int) entity.getMaxHealth());
        return sb.toString();
    }
}
