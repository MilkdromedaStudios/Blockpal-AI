package com.milkdromeda.blockpal.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.List;
import java.util.UUID;

/**
 * One player a bot's owner has <b>trusted</b> to command that bot — by UUID, with
 * the player's last-seen username kept alongside so the trust list reads nicely
 * and offline players can be removed by name.
 *
 * <p>Trust is stored per bot (in the entity's NBT, see {@code AiAssistantEntity}),
 * so each companion can have its own set of trusted players — part of managing
 * bots individually rather than server-wide.
 */
public record TrustEntry(UUID uuid, String name) {

    public static final Codec<TrustEntry> CODEC = RecordCodecBuilder.create(in -> in.group(
            UUIDUtil.STRING_CODEC.fieldOf("uuid").forGetter(TrustEntry::uuid),
            Codec.STRING.optionalFieldOf("name", "").forGetter(TrustEntry::name)
    ).apply(in, TrustEntry::new));

    public static final Codec<List<TrustEntry>> LIST_CODEC = CODEC.listOf();
}
