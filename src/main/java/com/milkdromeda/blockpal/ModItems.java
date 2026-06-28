package com.milkdromeda.blockpal;

import com.milkdromeda.blockpal.item.AiManualItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

public class ModItems {

    public static Item AI_MANUAL;

    public static void register() {
        AI_MANUAL = Registry.register(
                BuiltInRegistries.ITEM,
                Identifier.fromNamespaceAndPath("blockpal", "ai_manual"),
                new AiManualItem(new Item.Properties().stacksTo(1))
        );
    }
}
