package com.milkdromeda.blockpal;

import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Registry;

public class ModEntities {
    public static EntityType<AiAssistantEntity> AI_ASSISTANT;

    public static void register() {
        ResourceKey<EntityType<?>> key = ResourceKey.create(
                Registries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath("blockpal", "ai_assistant"));

        AI_ASSISTANT = Registry.register(
                BuiltInRegistries.ENTITY_TYPE,
                key,
                EntityType.Builder.<AiAssistantEntity>of(AiAssistantEntity::new, MobCategory.CREATURE)
                        .sized(0.6f, 1.8f)
                        .clientTrackingRange(10)
                        .updateInterval(3)
                        .build(key)
        );
    }
}
