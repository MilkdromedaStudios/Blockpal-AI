package com.milkdromeda.blockpal.network;

import com.milkdromeda.blockpal.admin.AdminAccess;
import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: everything the personal "{@code /ai mymenu}" screen needs — the
 * list of models the player may choose from, their current model, whether model
 * choice is allowed, whether they already have a personal key, whether the server
 * requires their own key (and if they're whitelisted to skip that), and the nearby
 * bot's personality (a built-in {@code currentPersonality} id or a free
 * {@code currentCustom} text, plus whether {@code allowCustom} is permitted).
 */
public record PlayerPrefsSyncPayload(
        List<String> allowedModels,
        String currentModel,
        boolean canChooseModel,
        boolean hasPersonalKey,
        boolean requireOwnKey,
        boolean whitelisted,
        boolean isAdmin,
        String currentPersonality,
        String currentCustom,
        boolean allowCustom
) implements CustomPacketPayload {

    public static final Type<PlayerPrefsSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("blockpal", "player_prefs_sync"));

    public static final StreamCodec<FriendlyByteBuf, PlayerPrefsSyncPayload> CODEC =
            StreamCodec.of(PlayerPrefsSyncPayload::write, PlayerPrefsSyncPayload::read);

    /** Builds the snapshot for a specific player from current config and their nearby bot. */
    public static PlayerPrefsSyncPayload forPlayer(ServerPlayer player) {
        ModConfig cfg = ModConfig.get();

        // The personality shown is that of the player's nearest owned bot (if any);
        // otherwise fall back to the server default so the picker has something to show.
        AiAssistantEntity bot = AiAssistantEntity.findFor(player, 256);
        String personalityId = cfg.defaultPersonality;
        String custom = "";
        if (bot != null) {
            if (bot.isCustomPersonality()) {
                custom = bot.getCustomStyle();
            } else {
                personalityId = bot.getPersonality().id();
            }
        }

        return new PlayerPrefsSyncPayload(
                new ArrayList<>(cfg.allowedModels),
                cfg.resolveModelFor(player.getUUID()),
                cfg.allowPlayerModelChoice,
                cfg.hasPlayerToken(player.getUUID()),
                cfg.requireOwnApiKey,
                cfg.isKeyWhitelisted(player.getName().getString(), player.getUUID()),
                AdminAccess.isAdmin(player),
                personalityId,
                custom,
                cfg.allowCustomPersonality);
    }

    private static void write(FriendlyByteBuf buf, PlayerPrefsSyncPayload d) {
        buf.writeInt(d.allowedModels.size());
        for (String m : d.allowedModels) buf.writeUtf(m);
        buf.writeUtf(d.currentModel == null ? "" : d.currentModel);
        buf.writeBoolean(d.canChooseModel);
        buf.writeBoolean(d.hasPersonalKey);
        buf.writeBoolean(d.requireOwnKey);
        buf.writeBoolean(d.whitelisted);
        buf.writeBoolean(d.isAdmin);
        buf.writeUtf(d.currentPersonality == null ? "" : d.currentPersonality);
        buf.writeUtf(d.currentCustom == null ? "" : d.currentCustom);
        buf.writeBoolean(d.allowCustom);
    }

    private static PlayerPrefsSyncPayload read(FriendlyByteBuf buf) {
        int n = buf.readInt();
        List<String> models = new ArrayList<>();
        for (int i = 0; i < n; i++) models.add(buf.readUtf());
        String current = buf.readUtf();
        return new PlayerPrefsSyncPayload(models, current,
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                buf.readUtf(), buf.readUtf(), buf.readBoolean());
    }

    @Override
    public Type<PlayerPrefsSyncPayload> type() {
        return TYPE;
    }
}
