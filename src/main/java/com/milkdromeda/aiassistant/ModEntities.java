package com.milkdromeda.aiassistant;

import com.milkdromeda.aiassistant.entity.AiAssistantEntity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ModEntities {
    public static EntityType<AiAssistantEntity> AI_ASSISTANT;

    public static void register() {
        RegistryKey<EntityType<?>> key = RegistryKey.of(
                RegistryKeys.ENTITY_TYPE,
                Identifier.of("ai-assistant", "ai_assistant"));

        AI_ASSISTANT = Registry.register(
                Registries.ENTITY_TYPE,
                key,
                EntityType.Builder.<AiAssistantEntity>create(AiAssistantEntity::new, SpawnGroup.CREATURE)
                        .dimensions(0.6f, 1.8f)
                        .maxTrackingRange(80)
                        .trackingTickInterval(3)
                        .build(key)
        );
    }
}
