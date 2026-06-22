package com.milkdromeda.blockpal.entity;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;

import java.util.List;
import java.util.Set;

/**
 * Classifies and scores items for the assistant's inventory and auto-equip
 * logic. Everything is derived from an item's data components (attribute
 * modifiers, food, block-ness) rather than {@code instanceof} checks, so it
 * keeps working for modded and data-driven items.
 */
public final class ItemSorter {

    private ItemSorter() {}

    /** The four wearable armour slots, head-to-toe. */
    public static final List<EquipmentSlot> ARMOR_SLOTS =
            List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    /** Broad buckets used for the {@code /ai inventory} readout and sorting. */
    public enum Category {
        WEAPON("§cWeapons"),
        ARMOR("§9Armor"),
        TOOL("§eTools"),
        FOOD("§aFood"),
        ORE("§bOres & ingots"),
        BLOCK("§6Blocks"),
        MISC("§7Other");

        public final String label;
        Category(String label) { this.label = label; }
    }

    private static ItemAttributeModifiers mods(ItemStack stack) {
        return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
    }

    /** Bonus melee damage this item grants when held in the main hand (0 if none). */
    public static double weaponScore(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        return mods(stack).compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND);
    }

    /** Protection this item grants in a given armour slot (armour + ½ toughness). */
    public static double armorScore(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) return 0;
        ItemAttributeModifiers m = mods(stack);
        return m.compute(Attributes.ARMOR, 0.0, slot)
                + 0.5 * m.compute(Attributes.ARMOR_TOUGHNESS, 0.0, slot);
    }

    /** The armour slot this item is best worn in, or {@code null} if it isn't armour. */
    public static EquipmentSlot bestArmorSlot(ItemStack stack) {
        EquipmentSlot best = null;
        double bestScore = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            double s = armorScore(stack, slot);
            if (s > bestScore) { bestScore = s; best = slot; }
        }
        return best;
    }

    public static boolean isArmor(ItemStack stack) {
        return bestArmorSlot(stack) != null;
    }

    public static boolean isFood(ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.FOOD);
    }

    /** How filling this food is (0 if it isn't food). Drives how much it heals. */
    public static int nutrition(ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        return food == null ? 0 : food.nutrition();
    }

    /** Combined desirability of a food (nutrition + saturation), for picking the best one. */
    public static double foodValue(ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        return food == null ? 0 : food.nutrition() + food.saturation();
    }

    /** Effects that genuinely hurt the assistant if it eats/drinks them. */
    private static final Set<Holder<MobEffect>> DAMAGING_EFFECTS =
            Set.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.INSTANT_DAMAGE);

    /**
     * True if consuming this would apply a damaging effect to itself — e.g. a
     * spider eye (poison) or pufferfish. The assistant refuses to eat these and
     * treats them as junk to throw out.
     */
    public static boolean isHarmfulToEat(ItemStack stack) {
        Consumable consumable = stack.get(DataComponents.CONSUMABLE);
        if (consumable == null) return false;
        for (ConsumeEffect effect : consumable.onConsumeEffects()) {
            if (effect instanceof ApplyStatusEffectsConsumeEffect apply && apply.probability() > 0.2f) {
                for (MobEffectInstance inst : apply.effects()) {
                    if (DAMAGING_EFFECTS.contains(inst.getEffect()) && inst.getDuration() >= 20) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Items the assistant considers useless and will toss: things that only hurt it to use. */
    public static boolean isJunk(ItemStack stack) {
        return !stack.isEmpty() && isHarmfulToEat(stack);
    }

    /** A drinkable (non-splash, non-lingering) potion. */
    public static boolean isDrinkablePotion(ItemStack stack) {
        return stack.has(DataComponents.POTION_CONTENTS)
                && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals("potion");
    }

    /** A drinkable potion whose effects are purely helpful (worth drinking when hurt). */
    public static boolean isBeneficialPotion(ItemStack stack) {
        if (!isDrinkablePotion(stack)) return false;
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return false;
        boolean anyGood = false;
        for (MobEffectInstance inst : contents.getAllEffects()) {
            MobEffectCategory cat = inst.getEffect().value().getCategory();
            if (cat == MobEffectCategory.HARMFUL) return false;
            if (cat == MobEffectCategory.BENEFICIAL) anyGood = true;
        }
        return anyGood;
    }

    public static Category categorize(ItemStack stack) {
        if (stack.isEmpty()) return Category.MISC;
        if (isFood(stack)) return Category.FOOD;
        if (isArmor(stack)) return Category.ARMOR;

        double w = weaponScore(stack);
        if (w >= 3.0) return Category.WEAPON;   // swords / axes
        if (w > 0.0) return Category.TOOL;       // pickaxes / shovels / hoes

        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (path.contains("ore") || path.startsWith("raw_")
                || path.contains("ingot") || path.contains("nugget")) {
            return Category.ORE;
        }
        if (stack.getItem() instanceof BlockItem) return Category.BLOCK;
        return Category.MISC;
    }
}
