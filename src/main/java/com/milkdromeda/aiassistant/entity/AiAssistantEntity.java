package com.milkdromeda.aiassistant.entity;

import com.milkdromeda.aiassistant.ai.AiTaskManager;
import com.milkdromeda.aiassistant.config.ModConfig;
import com.milkdromeda.aiassistant.entity.goal.*;
import com.milkdromeda.aiassistant.entity.goal.FollowOwnerGoal;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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

import java.util.UUID;

public class AiAssistantEntity extends PathfinderMob {

    // Colours used consistently across all messages
    public static final int COL_NAME   = 0xF0A000; // gold
    public static final int COL_OK     = 0x55FF55; // green
    public static final int COL_ERR    = 0xFF5555; // red
    public static final int COL_INFO   = 0xAAAAAA; // grey
    public static final int COL_COMBAT = 0xFF6B00; // orange

    public enum Mode { IDLE, FOLLOWING, BUILDING, FIGHTING, GUARDING, EXECUTING }

    // Synced to client so the HUD can read them
    private static final EntityDataAccessor<String> DATA_NAME =
            SynchedEntityData.defineId(AiAssistantEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_MODE =
            SynchedEntityData.defineId(AiAssistantEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_TASK =
            SynchedEntityData.defineId(AiAssistantEntity.class, EntityDataSerializers.STRING);

    private Mode mode = Mode.IDLE;
    private String assistantName = "ARIA";
    private UUID ownerUuid;
    private String pendingTask;
    private final AiTaskManager taskManager;
    private BuildGoal buildGoal;
    private int particleTick = 0;

    public AiAssistantEntity(EntityType<? extends AiAssistantEntity> type, Level level) {
        super(type, level);
        this.taskManager = new AiTaskManager(this);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_NAME, "ARIA");
        builder.define(DATA_MODE, "IDLE");
        builder.define(DATA_TASK, "");
    }

    @Override
    protected void registerGoals() {
        buildGoal = new BuildGoal(this);
        ExecuteTaskGoal executeGoal = new ExecuteTaskGoal(this);

        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new CombatAssistGoal(this));
        goalSelector.addGoal(3, executeGoal);
        goalSelector.addGoal(4, buildGoal);
        goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.0, ModConfig.get().followDistance, 64.0));
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

        // Refresh the name tag every second
        if (tickCount % 20 == 0) refreshNameTag();

        // Particle effects while thinking / executing
        if (level() instanceof ServerLevel sl) {
            particleTick++;
            if (mode == Mode.EXECUTING && particleTick % 8 == 0) {
                sl.sendParticles(ParticleTypes.ENCHANT,
                        getX(), getY() + 2.0, getZ(), 4, 0.4, 0.4, 0.4, 0.05);
            } else if (mode == Mode.FIGHTING && particleTick % 5 == 0) {
                sl.sendParticles(ParticleTypes.CRIT,
                        getX(), getY() + 1.0, getZ(), 3, 0.3, 0.3, 0.3, 0.1);
            }
        }

        if (mode == Mode.FOLLOWING && getLastHurtByMob() != null) {
            setMode(Mode.GUARDING);
        }

        if (getHealth() < getMaxHealth() * 0.25f && mode == Mode.FIGHTING) {
            say("Taking heavy damage, retreating!", COL_ERR);
            setMode(Mode.FOLLOWING);
        }

        if (mode == Mode.EXECUTING && !taskManager.isWaiting() && !taskManager.hasPlan()) {
            finishTask();
        }
    }

    public void giveTask(String task, ServerPlayer issuer) {
        pendingTask = task;
        setMode(Mode.EXECUTING);
        taskManager.clearPlan();
        entityData.set(DATA_TASK, task);
        say("On it! Planning: §o" + task, COL_NAME);
        taskManager.requestPlan(task);
    }

    public void finishTask() {
        say("Done: " + (pendingTask != null ? pendingTask : "task complete") + " ✓", COL_OK);
        pendingTask = null;
        entityData.set(DATA_TASK, "");
        setMode(Mode.FOLLOWING);
    }

    // ── Messaging helpers ──────────────────────────────────────────────────────

    /** Send a styled chat message to all players (server-side only). */
    public void broadcastMessage(String msg) {
        broadcastMessage(msg, 0xFFFFFF);
    }

    public void say(String msg) { broadcastMessage(msg, 0xFFFFFF); }

    public void say(String msg, int color) {
        if (level().isClientSide()) return;
        Component out = buildMessage(msg, color);
        level().players().forEach(p -> p.sendSystemMessage(out));
    }

    /** Send only to the owner; falls back to broadcast if owner is offline. */
    public void broadcastMessage(String msg, int color) {
        if (level().isClientSide()) return;
        Component out = buildMessage(msg, color);
        Player owner = getOwnerPlayer();
        if (owner != null) owner.sendSystemMessage(out);
        else level().players().forEach(p -> p.sendSystemMessage(out));
    }

    /** Show an action-bar notification to the owner (or all if offline). */
    public void actionBar(String msg) {
        if (level().isClientSide()) return;
        Component out = buildMessage(msg, COL_INFO);
        ClientboundSetActionBarTextPacket pkt = new ClientboundSetActionBarTextPacket(out);
        Player owner = getOwnerPlayer();
        if (owner instanceof ServerPlayer sp) sp.connection.send(pkt);
        else level().players().forEach(p -> { if (p instanceof ServerPlayer sp) sp.connection.send(pkt); });
    }

    private Component buildMessage(String msg, int color) {
        return Component.literal("[")
                .append(Component.literal(assistantName)
                        .withStyle(Style.EMPTY.withColor(COL_NAME).withBold(true)))
                .append(Component.literal("] ")
                        .withStyle(Style.EMPTY.withColor(0x888888)))
                .append(Component.literal(msg)
                        .withStyle(Style.EMPTY.withColor(color)));
    }

    // ── Name tag ──────────────────────────────────────────────────────────────

    private void refreshNameTag() {
        String modeIcon = switch (mode) {
            case FOLLOWING  -> "◈";
            case EXECUTING  -> "⟳";
            case BUILDING   -> "⚒";
            case FIGHTING   -> "⚔";
            case GUARDING   -> "🛡";
            case IDLE       -> "…";
        };
        int modeColor = switch (mode) {
            case FIGHTING  -> COL_COMBAT;
            case EXECUTING -> 0xAA55FF;
            case BUILDING  -> 0x5555FF;
            case GUARDING  -> 0x55FFFF;
            case FOLLOWING -> COL_OK;
            case IDLE      -> COL_INFO;
        };
        Component nameTag = Component.literal(modeIcon + " ")
                .withStyle(Style.EMPTY.withColor(modeColor))
                .append(Component.literal(assistantName)
                        .withStyle(Style.EMPTY.withColor(COL_NAME).withBold(true)));
        setCustomName(nameTag);
        setCustomNameVisible(true);
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

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
        assistantName = input.getStringOr("AssistantName", "ARIA");
        String modeStr = input.getStringOr("Mode", "FOLLOWING");
        try { mode = Mode.valueOf(modeStr); } catch (IllegalArgumentException ignored) { mode = Mode.FOLLOWING; }
        input.read("OwnerUuid", UUIDUtil.STRING_CODEC).ifPresent(uuid -> ownerUuid = uuid);
        pendingTask = input.getString("PendingTask").orElse(null);

        // Sync data into entity accessors after load
        entityData.set(DATA_NAME, assistantName);
        entityData.set(DATA_MODE, mode.name());
        entityData.set(DATA_TASK, pendingTask != null ? pendingTask : "");
        refreshNameTag();
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Mode getMode() { return mode; }

    public void setMode(Mode mode) {
        this.mode = mode;
        entityData.set(DATA_MODE, mode.name());
        refreshNameTag();
    }

    public String getAssistantName() { return assistantName; }

    public void setAssistantName(String name) {
        this.assistantName = name;
        entityData.set(DATA_NAME, name);
        refreshNameTag();
    }

    public UUID getOwnerUuid() { return ownerUuid; }
    public void setOwnerUuid(UUID uuid) { this.ownerUuid = uuid; }
    public AiTaskManager getTaskManager() { return taskManager; }

    /** Read synced name from entity data (safe on client). */
    public String getSyncedName() { return entityData.get(DATA_NAME); }
    /** Read synced mode string from entity data (safe on client). */
    public String getSyncedMode() { return entityData.get(DATA_MODE); }
    /** Read synced task from entity data (safe on client). */
    public String getSyncedTask() { return entityData.get(DATA_TASK); }

    public Player getOwnerPlayer() {
        if (ownerUuid == null) return null;
        if (level() instanceof ServerLevel sl) return sl.getPlayerByUUID(ownerUuid);
        return null;
    }

    @Override
    protected Component getTypeName() {
        return Component.literal(assistantName);
    }
}
