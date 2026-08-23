package com.milkdromeda.blockpal.agent;

import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Real crafting, from the game's own recipe book.</b>
 *
 * <p>Nothing here invents a recipe or conjures an item: candidate recipes come from the
 * server's loaded recipe manager, the ingredients come out of the bot's own backpack, and
 * the result is whatever vanilla's {@code assemble} produces. Data packs and other mods
 * that add recipes are therefore supported for free, and a recipe that was disabled is
 * genuinely unavailable to the bot too.
 *
 * <p><b>Why layouts are probed.</b> A shaped recipe cares where its ingredients sit in the
 * grid, and the recipe object exposes its ingredient list without a width. Rather than
 * guess at the shape — and quietly get planks-to-sticks wrong — candidate grids are tried
 * and handed to vanilla's own {@code matches}, which is the only thing that actually knows.
 * If none matches, the bot says it can't make that, which is the truthful answer.
 *
 * <p>Anything needing more than a 2×2 grid requires a <b>crafting table within reach</b>,
 * exactly as it would for a player.
 */
public final class BotCrafting {

    private BotCrafting() {}

    /** Grids tried against a recipe, smallest first. */
    private static final int[][] LAYOUTS = {
            {1, 1}, {1, 2}, {2, 1}, {2, 2}, {1, 3}, {3, 1}, {2, 3}, {3, 2}, {3, 3}
    };

    /** The result of trying to craft something. */
    public record Result(int crafted, String message) {}

    /**
     * Crafts up to {@code wanted} of an item.
     *
     * @param query loose item name, as everywhere else in the script API
     */
    public static Result craft(AiAssistantEntity bot, ServerLevel level, String query, int wanted) {
        if (query == null || query.isBlank()) return new Result(0, "craft what?");
        int target = Math.max(1, Math.min(512, wanted));

        var server = level.getServer();
        if (server == null) return new Result(0, "I can't reach the recipe book right now.");

        List<RecipeHolder<?>> candidates = new ArrayList<>();
        try {
            for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
                if (holder.value() instanceof CraftingRecipe) candidates.add(holder);
            }
        } catch (Exception e) {
            return new Result(0, "I couldn't read the recipe book.");
        }

        boolean sawRecipe = false;
        boolean neededTable = false;
        int made = 0;

        for (RecipeHolder<?> holder : candidates) {
            if (made >= target) break;
            if (!(holder.value() instanceof CraftingRecipe recipe)) continue;

            List<Ingredient> ingredients;
            try {
                ingredients = recipe.placementInfo().ingredients();
            } catch (Exception e) {
                continue;
            }
            if (ingredients.isEmpty() || ingredients.size() > 9) continue;

            Attempt attempt = attempt(bot, level, recipe, ingredients);
            if (attempt == null) continue;
            if (!BotApi.matches(attempt.result, query)) continue;

            sawRecipe = true;
            if (attempt.needsTable && !tableInReach(bot, level)) {
                neededTable = true;
                continue;
            }

            // Make it, and keep making it while the ingredients hold out.
            while (made < target) {
                Attempt again = attempt(bot, level, recipe, ingredients);
                if (again == null || !BotApi.matches(again.result, query)) break;
                if (again.needsTable && !tableInReach(bot, level)) break;
                if (!consume(bot, again.used)) break;
                ItemStack out = again.result.copy();
                made += out.getCount();
                ItemStack leftover = bot.getInventory().addItem(out);
                if (!leftover.isEmpty()) {
                    bot.spawnAtLocation(level, leftover);      // backpack full: it drops
                }
            }
        }

        if (made > 0) return new Result(made, "made " + made + " × " + query);
        if (neededTable) return new Result(0, "I need a crafting table within reach for that.");
        if (sawRecipe) return new Result(0, "I don't have the ingredients for " + query + ".");
        return new Result(0, "I don't know how to make " + query + ".");
    }

    /** A recipe that could be made right now, and what it would cost. */
    private record Attempt(ItemStack result, List<ItemStack> used, boolean needsTable) {}

    /**
     * Tries to satisfy a recipe from the backpack, then asks vanilla whether the grid it
     * built actually matches.
     */
    private static Attempt attempt(AiAssistantEntity bot, ServerLevel level,
                                   CraftingRecipe recipe, List<Ingredient> ingredients) {
        SimpleContainer pack = bot.getInventory();
        List<ItemStack> chosen = new ArrayList<>();
        // Reserve as we go, so a recipe needing two planks doesn't count one stack twice.
        int[] reserved = new int[pack.getContainerSize()];

        for (Ingredient ingredient : ingredients) {
            ItemStack found = ItemStack.EMPTY;
            for (int i = 0; i < pack.getContainerSize(); i++) {
                ItemStack slot = pack.getItem(i);
                if (slot.isEmpty() || slot.getCount() - reserved[i] <= 0) continue;
                if (!ingredient.test(slot)) continue;
                reserved[i]++;
                found = slot.copyWithCount(1);
                break;
            }
            if (found.isEmpty()) return null;        // can't satisfy this ingredient
            chosen.add(found);
        }

        for (int[] layout : LAYOUTS) {
            int w = layout[0], h = layout[1];
            if (w * h != chosen.size()) continue;
            try {
                CraftingInput input = CraftingInput.of(w, h, chosen);
                if (!recipe.matches(input, level)) continue;
                ItemStack result = recipe.assemble(input);
                if (result.isEmpty()) continue;
                return new Attempt(result, chosen, w > 2 || h > 2);
            } catch (Exception ignored) {
                // A recipe that dislikes this grid simply isn't this one.
            }
        }
        return null;
    }

    /** Removes the chosen ingredients from the backpack. */
    private static boolean consume(AiAssistantEntity bot, List<ItemStack> used) {
        SimpleContainer pack = bot.getInventory();
        for (ItemStack want : used) {
            boolean taken = false;
            for (int i = 0; i < pack.getContainerSize(); i++) {
                ItemStack slot = pack.getItem(i);
                if (slot.isEmpty() || !ItemStack.isSameItemSameComponents(slot, want)) continue;
                pack.removeItem(i, 1);
                taken = true;
                break;
            }
            if (!taken) return false;
        }
        pack.setChanged();
        return true;
    }

    /** Is there a crafting table close enough to use? */
    public static boolean tableInReach(AiAssistantEntity bot, ServerLevel level) {
        BlockPos centre = bot.blockPosition();
        int r = 4;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (!level.getBlockState(cursor).is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE)) {
                        continue;
                    }
                    if (bot.distanceToSqr(Vec3.atCenterOf(cursor)) <= 25) return true;
                }
            }
        }
        return false;
    }
}
