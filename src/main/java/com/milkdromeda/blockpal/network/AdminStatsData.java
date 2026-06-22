package com.milkdromeda.blockpal.network;

import com.milkdromeda.blockpal.EmergencyState;
import com.milkdromeda.blockpal.admin.PlayerStatsTracker;
import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A serializable snapshot of server-wide Blockpal state for the admin menu:
 * totals, the live config knobs an admin cares about, a per-player row (bot count
 * + reported FPS), and a row per bot (name, owner, mode, dimension, health, pos).
 *
 * <p>Built entirely on the server with {@link #gather(MinecraftServer)} and shipped
 * to the admin's client to render. Lists are capped so a very busy server can't
 * produce an oversized packet.
 */
public record AdminStatsData(
        int totalBots,
        int maxBots,
        boolean modDisabled,
        int adminLevel,
        boolean allowCommands,
        int commandLevel,
        boolean tokenSet,
        boolean tokenFromEnv,
        boolean requireOwnKey,
        boolean allowModelChoice,
        int allowedModelCount,
        int keyWhitelistCount,
        List<PlayerRow> players,
        List<BotRow> bots
) {
    public record PlayerRow(String name, int bots, int fps) {}
    public record BotRow(String name, String owner, String mode, String dim,
                         int health, int x, int y, int z) {}

    private static final int MAX_ROWS = 100;

    public static final StreamCodec<FriendlyByteBuf, AdminStatsData> STREAM_CODEC =
            StreamCodec.of(AdminStatsData::write, AdminStatsData::read);

    /** Snapshots the current server state for an admin client. */
    public static AdminStatsData gather(MinecraftServer server) {
        ModConfig cfg = ModConfig.get();
        List<AiAssistantEntity> allBots = AiAssistantEntity.all(server);

        List<BotRow> botRows = new ArrayList<>();
        for (AiAssistantEntity ai : allBots) {
            if (botRows.size() >= MAX_ROWS) break;
            String dim = ai.level() instanceof ServerLevel sl ? sl.dimension().identifier().getPath() : "?";
            botRows.add(new BotRow(
                    ai.getAssistantName(),
                    ownerName(server, ai.getOwnerUuid()),
                    ai.getMode().name(),
                    dim,
                    (int) Math.ceil(ai.getHealth()),
                    ai.getBlockX(), ai.getBlockY(), ai.getBlockZ()));
        }

        List<PlayerRow> playerRows = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (playerRows.size() >= MAX_ROWS) break;
            playerRows.add(new PlayerRow(
                    p.getName().getString(),
                    AiAssistantEntity.countOwnedBy(server, p.getUUID()),
                    PlayerStatsTracker.fps(p.getUUID())));
        }

        return new AdminStatsData(
                allBots.size(),
                cfg.maxBotsPerServer,
                EmergencyState.isDisabled(),
                cfg.adminPermissionLevel,
                cfg.allowCommands,
                cfg.commandPermissionLevel,
                cfg.hasApiToken(),
                cfg.isTokenFromEnv(),
                cfg.requireOwnApiKey,
                cfg.allowPlayerModelChoice,
                cfg.allowedModels.size(),
                cfg.ownKeyWhitelist.size(),
                playerRows,
                botRows);
    }

    private static String ownerName(MinecraftServer server, UUID uuid) {
        if (uuid == null) return "—";
        ServerPlayer p = server.getPlayerList().getPlayer(uuid);
        if (p != null) return p.getName().getString();
        return uuid.toString().substring(0, 8);   // owner offline — short id
    }

    private static void write(FriendlyByteBuf buf, AdminStatsData d) {
        buf.writeInt(d.totalBots);
        buf.writeInt(d.maxBots);
        buf.writeBoolean(d.modDisabled);
        buf.writeInt(d.adminLevel);
        buf.writeBoolean(d.allowCommands);
        buf.writeInt(d.commandLevel);
        buf.writeBoolean(d.tokenSet);
        buf.writeBoolean(d.tokenFromEnv);
        buf.writeBoolean(d.requireOwnKey);
        buf.writeBoolean(d.allowModelChoice);
        buf.writeInt(d.allowedModelCount);
        buf.writeInt(d.keyWhitelistCount);
        buf.writeInt(d.players.size());
        for (PlayerRow r : d.players) {
            buf.writeUtf(r.name());
            buf.writeInt(r.bots());
            buf.writeInt(r.fps());
        }
        buf.writeInt(d.bots.size());
        for (BotRow r : d.bots) {
            buf.writeUtf(r.name());
            buf.writeUtf(r.owner());
            buf.writeUtf(r.mode());
            buf.writeUtf(r.dim());
            buf.writeInt(r.health());
            buf.writeInt(r.x());
            buf.writeInt(r.y());
            buf.writeInt(r.z());
        }
    }

    private static AdminStatsData read(FriendlyByteBuf buf) {
        int totalBots = buf.readInt();
        int maxBots = buf.readInt();
        boolean modDisabled = buf.readBoolean();
        int adminLevel = buf.readInt();
        boolean allowCommands = buf.readBoolean();
        int commandLevel = buf.readInt();
        boolean tokenSet = buf.readBoolean();
        boolean tokenFromEnv = buf.readBoolean();
        boolean requireOwnKey = buf.readBoolean();
        boolean allowModelChoice = buf.readBoolean();
        int allowedModelCount = buf.readInt();
        int keyWhitelistCount = buf.readInt();

        int pc = buf.readInt();
        List<PlayerRow> players = new ArrayList<>();
        for (int i = 0; i < pc; i++) {
            players.add(new PlayerRow(buf.readUtf(), buf.readInt(), buf.readInt()));
        }
        int bc = buf.readInt();
        List<BotRow> bots = new ArrayList<>();
        for (int i = 0; i < bc; i++) {
            bots.add(new BotRow(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));
        }
        return new AdminStatsData(totalBots, maxBots, modDisabled, adminLevel, allowCommands,
                commandLevel, tokenSet, tokenFromEnv, requireOwnKey, allowModelChoice,
                allowedModelCount, keyWhitelistCount, players, bots);
    }
}
