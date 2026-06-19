package com.milkdromeda.blockpal.entity;

import com.milkdromeda.blockpal.ai.AiTaskManager;
import com.milkdromeda.blockpal.ai.ChatIntent;
import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.entity.goal.*;
import com.milkdromeda.blockpal.entity.goal.FollowOwnerGoal;
import com.milkdromeda.blockpal.network.AiNetworking;
import com.milkdromeda.blockpal.util.Locator;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

public class AiAssistantEntity extends PathfinderMob {

    public static final String DEFAULT_NAME = "Ethan";

    public enum Mode {
        IDLE, FOLLOWING, BUILDING, FIGHTING, GUARDING, EXECUTING
    }

    /** Skin id, synced to clients so the renderer can pick the right texture. */
    private static final EntityDataAccessor<String> DATA_SKIN =
            SynchedEntityData.defineId(AiAssistantEntity.class, EntityDataSerializers.STRING);

    private static final Random RAND = new Random();

    private Mode mode = Mode.IDLE;
    private String assistantName = DEFAULT_NAME;
    private UUID ownerUuid;
    private String pendingTask;
    private final AiTaskManager taskManager;
    private BuildGoal buildGoal;
    private int idleMessageTimer = 0;
    /** Tick the current task started, used by the watchdog to stop runaway loops. */
    private int taskStartTick = 0;
    /** True while an "active analysis" classification is in flight (prevents floods). */
    private boolean analyzing = false;
    /** Earliest tick the next active-mode analysis may run (rate-limits API calls). */
    private int nextAnalyzeTick = 0;
    /** When true the bot self-directs: picks its own tasks without being asked. */
    private boolean autonomousMode = true;
    /** Countdown until the next autonomous self-plan. */
    private int autoThinkTimer = 2 * 20; // start planning 2 seconds after spawn
    /** Earliest server tick at which another plan request may be sent (prevents API floods). */
    private int nextPlanTick = 0;
    private static final int MIN_PLAN_INTERVAL = 30 * 20; // minimum 30 s between plan requests

    /** Backpack-style storage — 10 slots like a player's hotbar — on top of the
     *  worn armour and held weapon. Picked-up loot lands here, and the best gear
     *  it owns is auto-equipped from it so it can armour up and fight back. */
    public static final int INVENTORY_SIZE = 10;
    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
    private int equipReviewTimer = 0;
    private int eatTimer = 0;

    public AiAssistantEntity(EntityType<? extends AiAssistantEntity> type, Level level) {
        super(type, level);
        this.taskManager = new AiTaskManager(this);
        // Ensure a visible nametag from the moment the entity exists.
        setAssistantName(this.assistantName);
        // A helper should never despawn when the player wanders off.
        setPersistenceRequired();
        // Let it scoop up loot it walks over — routed into our inventory below.
        setCanPickUpLoot(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKIN, "default");
    }

    /**
     * Right-click control so the assistant is usable hands-free in <b>adventure
     * and creative</b> (where you can't punch/place to interact): tap to toggle
     * follow/stay, sneak-tap to open the settings menu. The sneak-to-open shortcut
     * can be disabled in settings (it can trip accidentally); when off, a sneak-tap
     * just toggles follow/stay like a normal tap.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide() && player instanceof ServerPlayer sp) {
            if (sp.isShiftKeyDown() && ModConfig.get().sneakToOpenMenu) {
                AiNetworking.openMenuFor(sp);
            } else if (mode == Mode.FOLLOWING) {
                stayHere();
            } else {
                followPlayer();
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void registerGoals() {
        buildGoal = new BuildGoal(this);
        ExecuteTaskGoal executeGoal = new ExecuteTaskGoal(this);

        goalSelector.addGoal(0, new FloatGoal(this));
        // Highest active priority: survive. Runs in EVERY mode and needs no API,
        // so the assistant never just stands there while a plan is generating.
        goalSelector.addGoal(1, new SurvivalReflexGoal(this));
        goalSelector.addGoal(2, executeGoal);
        goalSelector.addGoal(3, buildGoal);
        goalSelector.addGoal(4, new FollowOwnerGoal(this, 1.0, ModConfig.get().followDistance, 64.0));
        // Scavenge nearby loot when not busy fighting, building, or following.
        goalSelector.addGoal(5, new CollectItemsGoal(this));
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

        // Emergency kill switch: the entity stays in the world but does nothing —
        // no planning, no task execution, no gear management, no chat analysis.
        if (com.milkdromeda.blockpal.EmergencyState.isDisabled()) {
            if (mode == Mode.EXECUTING) {
                taskManager.clearPlan();
                pendingTask = null;
                mode = Mode.IDLE;
            }
            return;
        }

        taskManager.tick();

        if (level() instanceof ServerLevel serverLevel) {
            manageGear(serverLevel);
        }

        idleMessageTimer++;

        // Autonomous self-direction: always running — immediately re-plan when idle.
        if (autonomousMode && mode != Mode.EXECUTING && ModConfig.get().hasApiToken()) {
            if (--autoThinkTimer <= 0) {
                autoThinkTimer = 60; // safety gap in case the API is slow; resets properly below
                startSurvivalLoop();
            }
        }

        // Combat and retreat are handled reflexively by SurvivalReflexGoal in
        // every mode, so the assistant stays responsive even mid-plan.

        // Watchdog: if stuck too long, silently re-plan instead of going idle.
        if (mode == Mode.EXECUTING) {
            int limitTicks = ModConfig.get().maxTaskSeconds * 20;
            if (limitTicks > 0 && tickCount - taskStartTick > limitTicks) {
                taskManager.clearPlan();
                pendingTask = null;
                if (autonomousMode && ModConfig.get().hasApiToken()) {
                    startSurvivalLoop();
                } else {
                    mode = Mode.FOLLOWING;
                }
            }
        }

        if (mode == Mode.EXECUTING && !taskManager.isWaiting() && !taskManager.hasPlan()) {
            if (taskManager.isLooping() && tickCount >= nextPlanTick) {
                taskManager.loopAgain();
            } else if (!taskManager.isLooping()) {
                finishTask();
            }
            // else: still inside the rate-limit window — wait for the timer
        }
    }

    public void giveTask(String task, ServerPlayer issuer) {
        if (!ModConfig.get().hasApiToken()) {
            issuer.sendSystemMessage(Component.literal(
                    assistantName + ": \"I can't do that without an API token. Could you set one with /ai token <token>?\""));
            return;
        }
        pendingTask = task;
        mode = Mode.EXECUTING;
        taskStartTick = tickCount;
        taskManager.clearPlan();
        taskManager.requestPlan(task);
    }

    public void finishTask() {
        pendingTask = null;
        if (autonomousMode && ModConfig.get().hasApiToken()) {
            // Jump straight into the next survival task with no gap.
            startSurvivalLoop();
        } else {
            mode = Mode.FOLLOWING;
        }
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
        if (tickCount < nextAnalyzeTick) return;   // rate-limit: don't analyze every message
        nextAnalyzeTick = tickCount + 60;          // at most ~once every 3 seconds
        analyzing = true;

        String context = "Speaker: " + sender.getName().getString()
                + "; distance to speaker: " + (int) Math.sqrt(distanceToSqr(sender)) + " blocks";

        taskManager.classify(message, context, getAssistantName()).whenComplete((intent, ex) -> {
            MinecraftServer server = level().getServer();
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
                    assistantName + ": \"" + Locator.describe(sender, this) + "\""));
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
        broadcastMessage(pick("On my way!", "Coming!", "Be right there.", "Heading over now."));
    }

    public void followPlayer() {
        taskManager.clearPlan();
        mode = Mode.FOLLOWING;
        autonomousMode = false;
        broadcastMessage(pick("Sure, I'll stick with you.", "Right behind you!", "Lead the way.", "On it, staying close."));
    }

    /** Stops moving and guards the current spot (still defends against hostiles). */
    public void stayHere() {
        taskManager.clearPlan();
        getNavigation().stop();
        mode = Mode.GUARDING;
        autonomousMode = false;
        broadcastMessage(pick("Got it, I'll keep watch here.", "I'll hold this position.", "Staying put.", "Alright, keeping an eye on things."));
    }

    public void stopTask() {
        taskManager.clearPlan();
        getNavigation().stop();
        mode = Mode.FOLLOWING;
        autonomousMode = false;
        broadcastMessage(pick("Okay, stopping.", "Alright, I'll wait here.", "Got it.", "Sure, taking a break."));
    }

    /** Enters autonomous mode — the bot self-directs and picks its own tasks. */
    public void enterAutonomousMode() {
        autonomousMode = true;
        broadcastMessage(pick("Alright, I'll use my own judgment from here.",
                "Sure, I'll keep myself busy.",
                "Got it — I'll figure something out on my own!",
                "Okay, leaving it to me then."));
        autoThinkTimer = 20; // start immediately
    }

    /**
     * Kicks off a survival-loop task: chop wood, mine, gather, explore — whatever
     * makes the most sense given the current context. Silent — no chat message.
     * Rate-limited: will not send another API request within MIN_PLAN_INTERVAL ticks.
     */
    private void startSurvivalLoop() {
        if (tickCount < nextPlanTick) {
            // Too soon — back off and try again later.
            autoThinkTimer = nextPlanTick - tickCount + 20;
            mode = Mode.FOLLOWING;
            return;
        }
        String task = "You are a Minecraft survival player who never sits idle. "
                + "Look at the context and pick the single best task to do RIGHT NOW. "
                + "Priority: 1) chop the nearest tree for wood (MINE_AREA on wood logs), "
                + "2) mine stone or ore underground, "
                + "3) collect any dropped items nearby, "
                + "4) dig down to find ores, "
                + "5) explore in a new direction. "
                + "Always output a concrete 5-10 step plan. Never output a WAIT or STOP as the only action.";
        nextPlanTick = tickCount + MIN_PLAN_INTERVAL;
        mode = Mode.EXECUTING;
        taskStartTick = tickCount;
        taskManager.clearPlan();
        taskManager.requestPlan(task);
        // autoThinkTimer is only used when mode falls back out of EXECUTING;
        // set it long so we don't hammer the API if the plan arrives instantly.
        autoThinkTimer = MIN_PLAN_INTERVAL;
    }

    /** True if the given player is allowed to give this assistant orders. */
    public boolean isOwner(Player player) {
        return ownerUuid != null && ownerUuid.equals(player.getUUID());
    }

    /** Public hop — used by the JUMP action and for getting unstuck on parkour. */
    public void doJump() {
        if (onGround()) {
            this.jumpFromGround();
        }
    }

    public void broadcastMessage(String msg) {
        if (!level().isClientSide()) {
            level().players().forEach(p ->
                    p.sendSystemMessage(Component.literal(assistantName + ": \"" + msg + "\"")));
        }
    }

    private void messageOwner(String msg) {
        Player owner = getOwnerPlayer();
        if (owner != null) {
            owner.sendSystemMessage(Component.literal(assistantName + ": \"" + msg + "\""));
        } else {
            broadcastMessage(msg);
        }
    }

    /** Picks a random option from the supplied strings. */
    private static String pick(String... options) {
        return options[RAND.nextInt(options.length)];
    }

    // ---- Inventory & gear ----

    public SimpleContainer getInventory() { return inventory; }

    /** True if an item is worth grabbing: not junk, and we have room or it's an upgrade. */
    public boolean canTake(ItemStack stack) {
        if (ItemSorter.isJunk(stack)) return false;   // refuse poison-foods like spider eyes
        return inventory.canAddItem(stack) || isEquipUpgrade(stack);
    }

    /** True if the item beats whatever is currently worn/held in its natural slot. */
    private boolean isEquipUpgrade(ItemStack stack) {
        if (ItemSorter.weaponScore(stack) > ItemSorter.weaponScore(getItemBySlot(EquipmentSlot.MAINHAND))) {
            return true;
        }
        EquipmentSlot slot = ItemSorter.bestArmorSlot(stack);
        return slot != null
                && ItemSorter.armorScore(stack, slot) > ItemSorter.armorScore(getItemBySlot(slot), slot);
    }

    @Override
    public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
        return canTake(stack);
    }

    /** Routes everything the assistant walks over into its gear or backpack. */
    @Override
    protected void pickUpItem(ServerLevel level, ItemEntity itemEntity) {
        ItemStack stack = itemEntity.getItem();
        int before = stack.getCount();

        equipIfUpgrade(level, stack);                  // wear/wield it now if it's better
        ItemStack leftover = inventory.addItem(stack); // store the rest
        optimizeEquipment(level);                      // re-sort using the whole backpack

        if (before - leftover.getCount() > 0) {
            onItemPickup(itemEntity);
        }
        if (leftover.isEmpty()) {
            itemEntity.discard();
        } else {
            itemEntity.setItem(leftover);
        }
    }

    /** If the stack is a straight upgrade for some slot, equip one and stash the old piece. */
    private void equipIfUpgrade(ServerLevel level, ItemStack stack) {
        if (stack.isEmpty()) return;
        EquipmentSlot slot = null;
        if (ItemSorter.weaponScore(stack) > ItemSorter.weaponScore(getItemBySlot(EquipmentSlot.MAINHAND))) {
            slot = EquipmentSlot.MAINHAND;
        } else {
            EquipmentSlot armor = ItemSorter.bestArmorSlot(stack);
            if (armor != null
                    && ItemSorter.armorScore(stack, armor) > ItemSorter.armorScore(getItemBySlot(armor), armor)) {
                slot = armor;
            }
        }
        if (slot == null) return;
        equip(level, slot, stack.copyWithCount(1));
        stack.shrink(1);
    }

    /** Wears/wields the best gear it owns, swapping any displaced piece back into the backpack. */
    public void optimizeEquipment(ServerLevel level) {
        reslot(level, EquipmentSlot.MAINHAND, ItemSorter::weaponScore);
        for (EquipmentSlot slot : ItemSorter.ARMOR_SLOTS) {
            reslot(level, slot, s -> ItemSorter.armorScore(s, slot));
        }
    }

    /** Finds the best backpack item for a slot and equips it if it beats what's there. */
    private void reslot(ServerLevel level, EquipmentSlot slot, ToDoubleFunction<ItemStack> score) {
        double bestScore = score.applyAsDouble(getItemBySlot(slot));
        int bestIdx = -1;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.isEmpty()) continue;
            double sc = score.applyAsDouble(s);
            if (sc > 0 && sc > bestScore) { bestScore = sc; bestIdx = i; }
        }
        if (bestIdx < 0) return;
        ItemStack chosen = inventory.getItem(bestIdx).copyWithCount(1);
        inventory.removeItem(bestIdx, 1);
        equip(level, slot, chosen);
    }

    /** Equips an item, guarantees it drops on death, and stashes whatever it replaced. */
    private void equip(ServerLevel level, EquipmentSlot slot, ItemStack stack) {
        ItemStack displaced = getItemBySlot(slot);
        setItemSlot(slot, stack);
        setGuaranteedDrop(slot);
        String item = stack.getDisplayName().getString();
        broadcastMessage(pick("Nice, putting on " + item + ".",
                "Ooh, " + item + " — that's an upgrade.",
                "I'll wear this " + item + ".",
                item + "? Don't mind if I do."));
        if (!displaced.isEmpty()) {
            ItemStack overflow = inventory.addItem(displaced);
            if (!overflow.isEmpty()) spawnAtLocation(level, overflow);
        }
    }

    /** Server-side upkeep: keep the best gear on, toss junk, and eat/drink to heal when hurt. */
    private void manageGear(ServerLevel level) {
        if (--equipReviewTimer <= 0) {
            equipReviewTimer = 40;
            if (!inventory.isEmpty()) {   // nothing to re-equip or toss when empty
                optimizeEquipment(level);
                dropJunk(level);
            }
        }
        if (--eatTimer <= 0) {
            eatTimer = 30;
            consumeWhenHurt(level);
        }
    }

    /** When hurt, uses a consumable like a player would: a healing potion if it has one,
     *  otherwise the best non-harmful food — applying the item's real effects. */
    private void consumeWhenHurt(ServerLevel level) {
        if (getHealth() >= getMaxHealth() * 0.6f) return;
        int potion = findInInventory(ItemSorter::isBeneficialPotion);
        if (potion >= 0) { drink(level, potion); return; }
        int food = bestEdibleFood();
        if (food >= 0) eat(level, food);
    }

    /** Slot of the most nourishing food it can safely eat (skips spider eyes etc.), or -1. */
    private int bestEdibleFood() {
        int best = -1;
        double bestValue = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (!ItemSorter.isFood(s) || ItemSorter.isHarmfulToEat(s)) continue;
            double v = ItemSorter.foodValue(s);
            if (v > bestValue) { bestValue = v; best = i; }
        }
        return best;
    }

    private int findInInventory(Predicate<ItemStack> match) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (match.test(inventory.getItem(i))) return i;
        }
        return -1;
    }

    /** Eats one food from a slot: heals (its hunger analog) and applies its real effects. */
    private void eat(ServerLevel level, int slot) {
        ItemStack one = inventory.getItem(slot).copyWithCount(1);
        FoodProperties food = one.get(DataComponents.FOOD);
        if (food != null) heal(Math.max(1.0f, food.nutrition() * 0.6f + 1.0f));
        applyConsumeEffects(level, one);   // e.g. a golden apple's regeneration + absorption
        inventory.removeItem(slot, 1);
        swing(InteractionHand.MAIN_HAND);
    }

    /** Drinks one potion from a slot, applying its (beneficial) effects. */
    private void drink(ServerLevel level, int slot) {
        ItemStack one = inventory.getItem(slot).copyWithCount(1);
        PotionContents contents = one.get(DataComponents.POTION_CONTENTS);
        if (contents != null) contents.applyToLivingEntity(this, 1.0f);
        applyConsumeEffects(level, one);
        inventory.removeItem(slot, 1);
        swing(InteractionHand.MAIN_HAND);
    }

    /** Runs an item's on-consume effects on the assistant, exactly as eating/drinking would. */
    private void applyConsumeEffects(ServerLevel level, ItemStack one) {
        Consumable consumable = one.get(DataComponents.CONSUMABLE);
        if (consumable == null) return;
        try {
            consumable.onConsume(level, this, one);
        } catch (Exception ignored) {
            // Never let a quirky consumable break the tick.
        }
    }

    /** Throws out items it judges useless — e.g. spider eyes and other poison-foods. */
    private void dropJunk(ServerLevel level) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty() && ItemSorter.isJunk(s)) {
                String junk = s.getDisplayName().getString();
                broadcastMessage(pick("I don't need this " + junk + ", tossing it.",
                        "No thanks, " + junk + " is useless to me.",
                        "Dropping the " + junk + " — not worth carrying.",
                        junk + "? Trash. Gone."));
                spawnAtLocation(level, inventory.removeItemNoUpdate(i));
            }
        }
    }

    private NonNullList<ItemStack> inventorySnapshot() {
        NonNullList<ItemStack> list = NonNullList.withSize(inventory.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < inventory.getContainerSize(); i++) list.set(i, inventory.getItem(i));
        return list;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);   // worn gear (set to always drop)
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty()) spawnAtLocation(level, s);
        }
        inventory.clearContent();
    }

    /** Human-readable summary of what the assistant is carrying, for {@code /ai inventory}. */
    public String describeInventory() {
        StringBuilder sb = new StringBuilder();
        sb.append("§6=== ").append(assistantName).append("'s gear ===\n");
        sb.append("§eHeld: §f").append(slotName(EquipmentSlot.MAINHAND)).append("\n");
        sb.append("§eArmor: §f")
                .append(slotName(EquipmentSlot.HEAD)).append(", ")
                .append(slotName(EquipmentSlot.CHEST)).append(", ")
                .append(slotName(EquipmentSlot.LEGS)).append(", ")
                .append(slotName(EquipmentSlot.FEET)).append("\n");

        int used = 0;
        EnumMap<ItemSorter.Category, List<String>> byCat = new EnumMap<>(ItemSorter.Category.class);
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.isEmpty()) continue;
            used++;
            byCat.computeIfAbsent(ItemSorter.categorize(s), c -> new ArrayList<>())
                    .add(s.getCount() + "× " + s.getDisplayName().getString());
        }
        sb.append("§eBackpack (§f").append(used).append("§e/§f").append(INVENTORY_SIZE).append("§e):");
        if (used == 0) {
            sb.append(" §7empty");
        } else {
            for (ItemSorter.Category cat : ItemSorter.Category.values()) {
                List<String> items = byCat.get(cat);
                if (items != null) {
                    sb.append("\n  ").append(cat.label).append("§7: §f").append(String.join(", ", items));
                }
            }
        }
        return sb.toString();
    }

    private String slotName(EquipmentSlot slot) {
        ItemStack s = getItemBySlot(slot);
        return s.isEmpty() ? "§7—§f" : s.getDisplayName().getString();
    }

    // ---- NBT ----

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("AssistantName", assistantName);
        output.putString("Skin", getSkin());
        output.putString("Mode", mode.name());
        if (ownerUuid != null) output.store("OwnerUuid", UUIDUtil.STRING_CODEC, ownerUuid);
        if (pendingTask != null) output.putString("PendingTask", pendingTask);
        output.putBoolean("AutonomousMode", autonomousMode);
        ContainerHelper.saveAllItems(output.child("Inventory"), inventorySnapshot());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setAssistantName(input.getStringOr("AssistantName", DEFAULT_NAME));
        setSkin(input.getStringOr("Skin", "default"));
        String modeStr = input.getStringOr("Mode", "FOLLOWING");
        try { mode = Mode.valueOf(modeStr); } catch (IllegalArgumentException ignored) { mode = Mode.FOLLOWING; }
        input.read("OwnerUuid", UUIDUtil.STRING_CODEC).ifPresent(uuid -> ownerUuid = uuid);
        pendingTask = input.getString("PendingTask").orElse(null);
        autonomousMode = input.getBooleanOr("AutonomousMode", false);
        NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input.childOrEmpty("Inventory"), items);
        for (int i = 0; i < INVENTORY_SIZE; i++) inventory.setItem(i, items.get(i));
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

    /** Skin id: "default"/"steve", a "namespace:path.png" texture, or a name under
     *  {@code assets/blockpal/textures/entity/skins/<name>.png}. */
    public String getSkin() { return entityData.get(DATA_SKIN); }

    public void setSkin(String skin) {
        entityData.set(DATA_SKIN, (skin == null || skin.isBlank()) ? "default" : skin.trim());
    }

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
