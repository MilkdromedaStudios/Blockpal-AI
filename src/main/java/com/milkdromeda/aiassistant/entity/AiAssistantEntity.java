package com.milkdromeda.aiassistant.entity;

import com.milkdromeda.aiassistant.ai.AiTaskManager;
import com.milkdromeda.aiassistant.ai.ChatIntent;
import com.milkdromeda.aiassistant.config.ModConfig;
import com.milkdromeda.aiassistant.entity.goal.*;
import com.milkdromeda.aiassistant.entity.goal.FollowOwnerGoal;
import com.milkdromeda.aiassistant.util.Locator;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class AiAssistantEntity extends PathfinderMob {

    public static final String DEFAULT_NAME = "Ethan";

    public enum Mode {
        IDLE, FOLLOWING, BUILDING, FIGHTING, GUARDING, EXECUTING
    }

    private Mode mode = Mode.IDLE;
    private String assistantName = DEFAULT_NAME;
    private UUID ownerUuid;
    private String pendingTask;
    private final AiTaskManager taskManager;
    private BuildGoal buildGoal;
    private int idleMessageTimer = 0;
    /** True while an "active analysis" classification is in flight (prevents floods). */
    private boolean analyzing = false;

    public AiAssistantEntity(EntityType<? extends AiAssistantEntity> type, Level level) {
        super(type, level);
        this.taskManager = new AiTaskManager(this);
        // Ensure a visible nametag from the moment the entity exists.
        setAssistantName(this.assistantName);
        // A helper should never despawn when the player wanders off.
        setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        buildGoal = new BuildGoal(this);
        ExecuteTaskGoal executeGoal = new ExecuteTaskGoal(this);

        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new CombatAssistGoal(this));
        goalSelector.addGoal(3, executeGoal);
        goalSelector.addGoal(4, buildGoal);
        goalSelector.addGoal(5, new com.milkdromeda.aiassistant.entity.goal.FollowOwnerGoal(this, 1.0, ModConfig.get().followDistance, 64.0));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.ARMOR, 4.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;

        taskManager.tick();

        if (idleMessageTimer == 20 && mode == Mode.IDLE) {
            messageOwner("Ready! Say \"" + assistantName + ", follow me\" in chat, or use /ai help.");
        }
        idleMessageTimer++;

        if (mode == Mode.FOLLOWING && getLastHurtByMob() != null) {
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

    public void giveTask(String task, ServerPlayer issuer) {
        if (!ModConfig.get().hasApiToken()) {
            issuer.sendSystemMessage(Component.literal(
                    "[" + assistantName + "] I need an API token before I can do that. Set one with /ai token <token>."));
            return;
        }
        pendingTask = task;
        mode = Mode.EXECUTING;
        taskManager.clearPlan();
        broadcastMessage("On it — working on: " + task);
        taskManager.requestPlan(task);
    }

    public void finishTask() {
        broadcastMessage("Done: " + (pendingTask != null ? pendingTask : "task complete"));
        pendingTask = null;
        mode = Mode.FOLLOWING;
    }

    /**
     * Reads a free-form chat message with the language model and, if it decides
     * the player needs the assistant, acts on it — without requiring the
     * assistant's name or any exact command words. Runs asynchronously; the
     * resulting action is applied back on the server thread.
     */
    public void analyzeChat(ServerPlayer sender, String message) {
        if (analyzing || level().isClientSide()) return;
        if (!ModConfig.get().hasApiToken()) return;
        analyzing = true;

        String context = "Speaker: " + sender.getName().getString()
                + "; distance to speaker: " + (int) Math.sqrt(distanceToSqr(sender)) + " blocks";

        taskManager.classify(message, context, getAssistantName()).whenComplete((intent, ex) -> {
            MinecraftServer server = getServer();
            if (server == null) { analyzing = false; return; }
            server.execute(() -> {
                analyzing = false;
                if (ex != null || intent == null || !intent.directed()) return;
                dispatchIntent(sender, intent);
            });
        });
    }

    /** Carries out the action the language model inferred from a chat message. */
    private void dispatchIntent(ServerPlayer sender, ChatIntent intent) {
        if (!isAlive()) return;
        switch (intent.action()) {
            case "come"   -> comeTo(sender);
            case "follow" -> followPlayer();
            case "stay"   -> stayHere();
            case "stop"   -> stopTask();
            case "locate" -> sender.sendSystemMessage(Component.literal(
                    "[" + assistantName + "] " + Locator.describe(sender, this)));
            case "task"   -> {
                if (intent.task() != null && !intent.task().isBlank()) {
                    giveTask(intent.task(), sender);
                }
            }
            default -> { /* none — stay quiet */ }
        }
    }

    // ---- Quick (no-API) behaviours used by commands and chat ----

    /** Calls the assistant to the player; teleports if it's far away so it always arrives. */
    public void comeTo(Player player) {
        taskManager.clearPlan();
        mode = Mode.FOLLOWING;
        if (distanceToSqr(player) > 16 * 16) {
            // Same approach FollowOwnerGoal uses when the owner gets too far.
            setPos(player.getX() + 1.0, player.getY(), player.getZ());
            getNavigation().stop();
        } else {
            getNavigation().moveTo(player, 1.2);
        }
        broadcastMessage("Coming!");
    }

    public void followPlayer() {
        taskManager.clearPlan();
        mode = Mode.FOLLOWING;
        broadcastMessage("Following you.");
    }

    /** Stops moving and guards the current spot (still defends against hostiles). */
    public void stayHere() {
        taskManager.clearPlan();
        getNavigation().stop();
        mode = Mode.GUARDING;
        broadcastMessage("Staying here and keeping watch.");
    }

    public void stopTask() {
        taskManager.clearPlan();
        getNavigation().stop();
        mode = Mode.FOLLOWING;
        broadcastMessage("Stopped. Standing by.");
    }

    public void broadcastMessage(String msg) {
        if (!level().isClientSide()) {
            level().players().forEach(p ->
                    p.sendSystemMessage(Component.literal("[" + assistantName + "] " + msg)));
        }
    }

    private void messageOwner(String msg) {
        Player owner = getOwnerPlayer();
        if (owner != null) {
            owner.sendSystemMessage(Component.literal("[" + assistantName + "] " + msg));
        } else {
            broadcastMessage(msg);
        }
    }

    // ---- NBT ----

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("AssistantName", assistantName);
        output.putString("Mode", mode.name());
        if (ownerUuid != null) output.store("OwnerUuid", UUIDUtil.STRING_CODEC, ownerUuid);
        if (pendingTask != null) output.putString("PendingTask", pendingTask);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setAssistantName(input.getStringOr("AssistantName", DEFAULT_NAME));
        String modeStr = input.getStringOr("Mode", "FOLLOWING");
        try { mode = Mode.valueOf(modeStr); } catch (IllegalArgumentException ignored) { mode = Mode.FOLLOWING; }
        input.read("OwnerUuid", UUIDUtil.STRING_CODEC).ifPresent(uuid -> ownerUuid = uuid);
        pendingTask = input.getString("PendingTask").orElse(null);
    }

    // ---- Getters / Setters ----

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public String getAssistantName() { return assistantName; }

    /** Sets the assistant's name and keeps the floating nametag in sync. */
    public void setAssistantName(String name) {
        this.assistantName = name;
        setCustomName(Component.literal(name));
        setCustomNameVisible(true);
    }

    public UUID getOwnerUuid() { return ownerUuid; }
    public void setOwnerUuid(UUID uuid) { this.ownerUuid = uuid; }
    public AiTaskManager getTaskManager() { return taskManager; }

    public boolean isOwnedBy(Player player) {
        return ownerUuid != null && ownerUuid.equals(player.getUUID());
    }

    public Player getOwnerPlayer() {
        if (ownerUuid == null) return null;
        if (level() instanceof ServerLevel sl) return sl.getPlayerByUUID(ownerUuid);
        return null;
    }

    @Override
    protected Component getTypeName() {
        return Component.literal(assistantName);
    }

    /**
     * Finds the assistant most relevant to a player within range: prefers one the
     * player owns, otherwise the nearest one.
     */
    public static AiAssistantEntity findFor(ServerPlayer player, double range) {
        AABB box = AABB.ofSize(player.position(), range * 2, range, range * 2);
        List<AiAssistantEntity> list = player.level()
                .getEntitiesOfClass(AiAssistantEntity.class, box, e -> true);
        return list.stream()
                .min(Comparator
                        .comparingInt((AiAssistantEntity a) -> a.isOwnedBy(player) ? 0 : 1)
                        .thenComparingDouble(a -> a.distanceToSqr(player)))
                .orElse(null);
    }
}
