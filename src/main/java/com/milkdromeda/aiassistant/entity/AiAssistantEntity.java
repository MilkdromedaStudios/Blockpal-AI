package com.milkdromeda.aiassistant.entity;

import com.milkdromeda.aiassistant.ai.AiTaskManager;
import com.milkdromeda.aiassistant.config.ModConfig;
import com.milkdromeda.aiassistant.entity.goal.*;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.UUID;

public class AiAssistantEntity extends PathAwareEntity {

    public enum Mode {
        IDLE, FOLLOWING, BUILDING, FIGHTING, GUARDING, EXECUTING
    }

    private Mode mode = Mode.IDLE;
    private String assistantName = "ARIA";
    private UUID ownerUuid;
    private String pendingTask;
    private final AiTaskManager taskManager;
    private BuildGoal buildGoal;
    private int idleMessageTimer = 0;

    public AiAssistantEntity(EntityType<? extends AiAssistantEntity> type, World world) {
        super(type, world);
        this.taskManager = new AiTaskManager(this);
    }

    @Override
    protected void initGoals() {
        buildGoal = new BuildGoal(this);
        ExecuteTaskGoal executeGoal = new ExecuteTaskGoal(this);

        goalSelector.add(1, new SwimGoal(this));
        goalSelector.add(2, new CombatAssistGoal(this));
        goalSelector.add(3, executeGoal);
        goalSelector.add(4, buildGoal);
        goalSelector.add(5, new com.milkdromeda.aiassistant.entity.goal.FollowOwnerGoal(
                this, 1.0, ModConfig.get().followDistance, 64.0));
        goalSelector.add(6, new LookAroundGoal(this));
        goalSelector.add(7, new WanderAroundFarGoal(this, 0.8));
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 20.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.ATTACK_DAMAGE, 6.0)
                .add(EntityAttributes.FOLLOW_RANGE, 40.0)
                .add(EntityAttributes.ARMOR, 4.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;

        taskManager.tick();

        if (idleMessageTimer == 20 && mode == Mode.IDLE) {
            broadcastMessage("Ready! Use /aiassistant task <description>");
        }
        idleMessageTimer++;

        if (mode == Mode.FOLLOWING && getAttacker() != null) {
            mode = Mode.GUARDING;
        }

        if (getHealth() < getMaxHealth() * 0.25f && mode == Mode.FIGHTING) {
            broadcastMessage("Taking heavy damage, retreating!");
            mode = Mode.FOLLOWING;
        }

        if (mode == Mode.EXECUTING && !taskManager.isWaiting() && !taskManager.hasPlan()) {
            finishTask();
        }
    }

    public void giveTask(String task, ServerPlayerEntity issuer) {
        if (!ModConfig.get().hasApiToken()) {
            issuer.sendMessage(Text.literal(
                    "[AI-Assistant] No HuggingFace API token. Use /aiassistant config hf_token <token>"), false);
            return;
        }
        pendingTask = task;
        mode = Mode.EXECUTING;
        taskManager.clearPlan();
        broadcastMessage("Thinking about: " + task);
        taskManager.requestPlan(task);
    }

    public void finishTask() {
        broadcastMessage("Task complete: " + (pendingTask != null ? pendingTask : "done"));
        pendingTask = null;
        mode = Mode.FOLLOWING;
    }

    public void startBuilding(int x, int y, int z, String blockId) {
        buildGoal.clearTasks();
        buildGoal.queueBlock(x, y, z, blockId);
        mode = Mode.BUILDING;
    }

    public void queueBuildBlock(int x, int y, int z, String blockId) {
        buildGoal.queueBlock(x, y, z, blockId);
    }

    public void broadcastMessage(String msg) {
        if (!getWorld().isClient) {
            getWorld().getPlayers().forEach(p ->
                    p.sendMessage(Text.literal("[" + assistantName + "] " + msg), false));
        }
    }

    // ---- NBT ----

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("AssistantName", assistantName);
        nbt.putString("Mode", mode.name());
        if (ownerUuid != null) nbt.putUuid("OwnerUuid", ownerUuid);
        if (pendingTask != null) nbt.putString("PendingTask", pendingTask);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("AssistantName")) assistantName = nbt.getString("AssistantName");
        if (nbt.contains("Mode")) {
            try { mode = Mode.valueOf(nbt.getString("Mode")); } catch (IllegalArgumentException ignored) {}
        }
        if (nbt.containsUuid("OwnerUuid")) ownerUuid = nbt.getUuid("OwnerUuid");
        if (nbt.contains("PendingTask")) pendingTask = nbt.getString("PendingTask");
    }

    // ---- Getters / Setters ----

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public String getAssistantName() { return assistantName; }
    public void setAssistantName(String name) { this.assistantName = name; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public void setOwnerUuid(UUID uuid) { this.ownerUuid = uuid; }
    public AiTaskManager getTaskManager() { return taskManager; }

    public PlayerEntity getOwnerPlayer() {
        if (ownerUuid == null) return null;
        if (getWorld() instanceof ServerWorld sw) {
            return sw.getPlayerByUuid(ownerUuid);
        }
        return null;
    }

    @Override
    protected Text getDefaultName() {
        return Text.literal(assistantName);
    }
}
