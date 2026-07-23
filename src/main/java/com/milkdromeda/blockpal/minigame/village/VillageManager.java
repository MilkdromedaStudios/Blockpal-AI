package com.milkdromeda.blockpal.minigame.village;

import com.milkdromeda.blockpal.ModEntities;
import com.milkdromeda.blockpal.ai.HuggingFaceClient;
import com.milkdromeda.blockpal.ai.Personality;
import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Runs the "Growth" mini-game: an AI-driven village that grows or collapses on its own.
 *
 * <p>Villagers are Blockpal bots ({@link AiAssistantEntity}), each spawned with a
 * different {@link VillageRole role}, a different {@link Personality personality} and a
 * different (small) language model — so they genuinely "think differently". A daily
 * simulation (running at 2× speed) turns their roles into food, houses, knowledge,
 * morale and defence; the village births new villagers when it thrives and loses them
 * when it starves or is overrun. Villagers narrate what they're doing — from their own
 * model when one is reachable, from a role pool otherwise — so their intelligence shows.
 *
 * <p>The player can watch or {@code /village join} a role. If the village dies out the
 * player wins; if it grows as big as ever they may {@code /village surrender}.
 *
 * <p>Everything is server-side (a single tick hook), so Java and Bedrock players both
 * play it. It is deliberately conservative about lag: at most one villager narrates
 * every few seconds (asynchronously), the day sim runs a couple of times a minute, and
 * building is capped per day.
 */
public final class VillageManager {

    private VillageManager() {}

    private static final HuggingFaceClient CLIENT = new HuggingFaceClient();
    private static final Random RNG = new Random();

    /** Sim ticks per village "day". simTicks advances 2/real-tick, so this is ~22 s real. */
    private static final int DAY_SIM_TICKS = 900;
    /** Real ticks between one villager narrating / acting. */
    private static final int THINK_INTERVAL = 100;
    /** Villagers are kept within this radius of the village centre. */
    private static final double TETHER_RADIUS = 30.0;
    /** Cap on hut placement per day (lag safety). */
    private static final int MAX_HUTS_PER_DAY = 2;

    private static final String[] SKINS = {
            "default", "robot", "ember", "void", "slate", "forest", "amethyst"
    };
    private static final String[] NAMES = {
            "Aria", "Bo", "Cy", "Dex", "Esa", "Fen", "Gio", "Hana", "Ivo", "Jun",
            "Kai", "Lel", "Mira", "Nox", "Oda", "Pia", "Quen", "Rell", "Sora", "Tovi",
            "Uma", "Vmax", "Wyn", "Xan", "Yara", "Zeb"
    };

    private static final Set<VillageGame> GAMES = new HashSet<>();
    private static int thinkClock = 0;

    /** Wires the per-tick simulation. Called once from the mod initializer. */
    public static void registerEvents() {
        ServerTickEvents.END_SERVER_TICK.register(VillageManager::tick);
    }

    // ── lookups ──────────────────────────────────────────────────────────────────────

    /** The game this player founded or joined, or null. */
    public static VillageGame gameOf(ServerPlayer player) {
        UUID id = player.getUUID();
        for (VillageGame g : GAMES) {
            if (!g.ended && (g.founder.equals(id) || g.participants.contains(id))) return g;
        }
        return null;
    }

    private static VillageGame gameFoundedBy(UUID id) {
        for (VillageGame g : GAMES) if (!g.ended && g.founder.equals(id)) return g;
        return null;
    }

    // ── start / stop ───────────────────────────────────────────────────────────────

    /** Starts a Growth game for {@code founder} (the village grows around where they stand). */
    public static void start(ServerPlayer founder) {
        MinecraftServer server = founder.level().getServer();
        if (server == null) return;
        if (gameOf(founder) != null) {
            msg(founder, "§cYou're already in a village game — end it with §f/village stop§c first.");
            return;
        }
        if (!(founder.level() instanceof ServerLevel level)) return;
        ModConfig cfg = ModConfig.get();

        BlockPos center = founder.blockPosition();
        VillageGame game = new VillageGame(founder.getUUID(), founder.getName().getString(),
                level.dimension(), center, cfg.villageTargetPopulation);
        GAMES.add(game);

        int start = Math.max(1, cfg.villageStartPopulation);
        for (int i = 0; i < start; i++) spawnVillager(level, game);
        game.peakPopulation = game.population();
        // Give the settlers something to live in from day one.
        placeHut(level, game);

        broadcast(server, game,
                "§6[Village] §fGrowth §astarted! §7An AI village of §f" + game.population()
                        + "§7 is taking root. Days run at §f2×§7 speed.");
        broadcast(server, game,
                "§7Watch them build, farm, teach and trade. If it §fdies out §7you win; "
                        + "if it grows §fas big as ever §7you may §f/village surrender§7.");
        broadcast(server, game, "§7Join in with §f/village join <role>§7 · check §f/village status§7.");
        if (!cfg.aiAvailableFor(game.founder, game.founderName)) {
            msg(founder, "§7(No language model is set, so villagers speak from role scripts. "
                    + "Enable a local model with §f/ai admin ollama on§7 for smarter, varied talk.)");
        }
    }

    /** {@code /village stop}: the founder ends the game and the villagers are dismissed. */
    public static void stop(ServerPlayer player) {
        VillageGame g = gameOf(player);
        if (g == null) { msg(player, "§7You're not in a village game."); return; }
        MinecraftServer server = player.level().getServer();
        if (!g.founder.equals(player.getUUID())) {
            g.participants.remove(player.getUUID());
            g.playerRoles.remove(player.getUUID());
            msg(player, "§7You left the village.");
            return;
        }
        endGame(server, g, "§6[Village] §7" + player.getName().getString() + " ended the village game.", true);
    }

    /** {@code /village surrender}: only once the village has grown as big as ever. */
    public static void surrender(ServerPlayer player) {
        VillageGame g = gameOf(player);
        if (g == null) { msg(player, "§7You're not in a village game."); return; }
        if (!g.surrenderOffered) {
            msg(player, "§7You can only surrender once the village has grown as big as ever (pop "
                    + g.targetPopulation + "). It's at §f" + g.peakPopulation + "§7 so far — keep watching.");
            return;
        }
        MinecraftServer server = player.level().getServer();
        endGame(server, g, "§6[Village] §e" + player.getName().getString()
                + " surrendered — the village grew beyond them and thrives on. §aThe village wins!", true);
    }

    /** {@code /village join <role>}: take part in the village's daily work. */
    public static void join(ServerPlayer player, String roleId) {
        VillageGame g = gameOf(player);
        if (g == null) { msg(player, "§7No village here — start one with §f/village start§7."); return; }
        VillageRole role = roleId == null || roleId.isBlank() ? VillageRole.SCHOLAR : VillageRole.byId(roleId);
        if (role == null) {
            msg(player, "§cUnknown role §f'" + roleId + "'§c. Try: builder, farmer, teacher, trader, guard, scholar.");
            return;
        }
        g.participants.add(player.getUUID());
        g.playerRoles.put(player.getUUID(), role);
        MinecraftServer server = player.level().getServer();
        broadcast(server, g, "§6[Village] §f" + player.getName().getString()
                + " §7joined as a §f" + role.display + "§7 — one of the people now.");
    }

    /** {@code /village leave}: stop contributing (the village plays on without you). */
    public static void leave(ServerPlayer player) {
        VillageGame g = gameOf(player);
        if (g == null || !g.participants.contains(player.getUUID())) {
            msg(player, "§7You haven't joined a village role.");
            return;
        }
        g.participants.remove(player.getUUID());
        g.playerRoles.remove(player.getUUID());
        msg(player, "§7You stepped back from village life.");
    }

    public static void status(ServerPlayer player) {
        VillageGame g = gameOf(player);
        if (g == null) {
            msg(player, "§7No village here. Start one with §f/village start§7.");
            return;
        }
        Map<VillageRole, Integer> counts = roleCounts(g, player.level().getServer());
        StringBuilder sb = new StringBuilder("§6=== Village · Day " + g.day + " ===");
        sb.append("\n§7Population: §f").append(g.population()).append("§7/").append(g.targetPopulation)
          .append("  §7peak: §f").append(g.peakPopulation);
        sb.append("\n§7Food: §f").append((int) g.food)
          .append("  §7Houses: §f").append(g.houses)
          .append("  §7Morale: §f").append((int) (g.morale * 100)).append('%')
          .append("  §7Knowledge: §f").append(g.knowledge);
        sb.append("\n§7Workforce: ");
        for (VillageRole r : VillageRole.values()) {
            sb.append("§f").append(counts.getOrDefault(r, 0)).append(' ').append(r.display).append("§7  ");
        }
        VillageRole mine = g.playerRoles.get(player.getUUID());
        sb.append("\n§7Your role: §f").append(mine == null ? "(watching)" : mine.display);
        if (g.surrenderOffered) sb.append("\n§eThe village has grown as big as ever — §f/village surrender§e to concede.");
        msg(player, sb.toString());
    }

    public static void handleDisconnect(ServerPlayer player) {
        // Villages persist (villagers are persistent entities); just stop counting the
        // player's contribution while they're away.
        for (VillageGame g : GAMES) {
            g.participants.remove(player.getUUID());
        }
    }

    // ── per-tick simulation ──────────────────────────────────────────────────────────

    private static void tick(MinecraftServer server) {
        if (GAMES.isEmpty()) return;
        thinkClock++;
        for (VillageGame g : new ArrayList<>(GAMES)) {
            if (g.ended) continue;
            ServerLevel level = server.getLevel(g.dimension);
            if (level == null) continue;

            Map<UUID, AiAssistantEntity> live = pruneAndCollect(server, g);
            tether(g, live);

            g.simTicks += 2;   // 2× speed clock
            if (g.simTicks >= (long) (g.day + 1) * DAY_SIM_TICKS) {
                onNewDay(server, level, g, live);
            }

            if (thinkClock % THINK_INTERVAL == 0 && !g.villagers.isEmpty()) {
                narrateOne(server, level, g, live);
            }
        }
    }

    /**
     * Drops villagers whose entity has vanished/died (counting each as a loss), and
     * returns a UUID→entity map of the survivors for this tick.
     */
    private static Map<UUID, AiAssistantEntity> pruneAndCollect(MinecraftServer server, VillageGame g) {
        Map<UUID, AiAssistantEntity> byId = new HashMap<>();
        for (AiAssistantEntity e : AiAssistantEntity.all(server)) byId.put(e.getUUID(), e);
        Map<UUID, AiAssistantEntity> live = new HashMap<>();
        List<UUID> gone = new ArrayList<>();
        for (UUID id : g.villagers.keySet()) {
            AiAssistantEntity e = byId.get(id);
            if (e != null && e.isAlive()) live.put(id, e);
            else gone.add(id);
        }
        for (UUID id : gone) g.villagers.remove(id);
        return live;
    }

    /** Keeps villagers near the village centre so the settlement stays coherent. */
    private static void tether(VillageGame g, Map<UUID, AiAssistantEntity> live) {
        double cx = g.center.getX() + 0.5, cy = g.center.getY(), cz = g.center.getZ() + 0.5;
        for (AiAssistantEntity e : live.values()) {
            double dx = e.getX() - cx, dz = e.getZ() - cz;
            if (dx * dx + dz * dz > TETHER_RADIUS * TETHER_RADIUS) {
                e.getNavigation().stop();
                e.teleportTo(cx + (RNG.nextDouble() - 0.5) * 6, cy, cz + (RNG.nextDouble() - 0.5) * 6);
            }
        }
    }

    // ── the daily simulation (growth / decline) ──────────────────────────────────────

    private static void onNewDay(MinecraftServer server, ServerLevel level, VillageGame g,
                                 Map<UUID, AiAssistantEntity> live) {
        g.day++;
        int pop = g.population();

        Map<VillageRole, Integer> counts = roleCounts(g, server);
        int builders = counts.getOrDefault(VillageRole.BUILDER, 0);
        int farmers  = counts.getOrDefault(VillageRole.FARMER, 0);
        int teachers = counts.getOrDefault(VillageRole.TEACHER, 0);
        int traders  = counts.getOrDefault(VillageRole.TRADER, 0);
        int guards   = counts.getOrDefault(VillageRole.GUARD, 0);
        int scholars = counts.getOrDefault(VillageRole.SCHOLAR, 0);

        // Teamwork: a village with a broad mix of roles cooperates better — the
        // villagers specialise and work together, so a balanced settlement out-produces
        // a lopsided one and its people are happier pulling together.
        int distinctRoles = 0;
        for (VillageRole r : VillageRole.values()) if (counts.getOrDefault(r, 0) > 0) distinctRoles++;
        double teamwork = 1.0 + distinctRoles * 0.04;   // up to +24% with all six roles filled

        // Teaching/scholarship compounds into efficiency (capped so it can't run away).
        g.knowledge += teachers + scholars;
        double efficiency = (1.0 + Math.min(0.6, g.knowledge * 0.015)) * teamwork;

        // Food: farmers produce, everyone eats.
        double production = (farmers * 3.0 + 1.0) * efficiency;
        double consumption = pop * 1.0;
        g.food += production - consumption;
        boolean starving = g.food < 0;
        if (starving) g.food = 0;

        // Morale.
        boolean defended = guards >= 1 || pop == 0;
        double dm = (starving ? -0.16 : 0.06) + (defended ? 0.02 : -0.09) + traders * 0.03
                + (distinctRoles >= 4 ? 0.03 : 0.0);   // a village pulling together lifts spirits
        g.morale = clamp(g.morale + dm, 0.0, 1.0);

        // Houses: builders expand the settlement (and we physically raise a hut or two).
        int houseGain = Math.min(MAX_HUTS_PER_DAY, builders / 2 + (playerHas(g, server, VillageRole.BUILDER) ? 1 : 0));
        for (int i = 0; i < houseGain; i++) placeHut(level, g);
        int capacity = g.houses + 2;

        // A night raid on an undefended village.
        boolean raided = false;
        if (guards == 0 && pop > 0 && RNG.nextInt(100) < 22) {
            g.morale = clamp(g.morale - 0.1, 0, 1);
            raided = RNG.nextBoolean();
            if (raided) removeVillager(server, g, live, "§c" + " was lost to a night raid");
        }

        // Growth: a thriving, fed, housed and hopeful village welcomes new people.
        int totalCap = Math.max(g.targetPopulation * 2, capacity);
        boolean thriving = pop > 0 && g.food >= pop && g.morale > 0.5 && pop < capacity && pop < totalCap;
        if (thriving) {
            int births = (g.morale > 0.8 && g.food > pop * 2.0) ? 2 : 1;
            births = Math.min(births, Math.min(capacity - pop, totalCap - pop));
            for (int i = 0; i < births; i++) {
                spawnVillager(level, g);
                g.food = Math.max(0, g.food - 3);   // raising a newcomer costs food
            }
            if (births > 0) {
                broadcast(server, g, "§a[Village] §7Day " + g.day + ": the village welcomes §f" + births
                        + "§7 new " + (births == 1 ? "settler" : "settlers") + " — it's growing.");
            }
        }

        // Decline: starvation or despair takes someone.
        if (g.population() > 0 && (starving || g.morale < 0.25)) {
            removeVillager(server, g, live,
                    starving ? "§c starved as the granary ran dry" : "§c left — spirits had broken");
            g.morale = clamp(g.morale + 0.05, 0, 1);   // a hard lesson steadies the rest a little
        }

        g.peakPopulation = Math.max(g.peakPopulation, g.population());

        // Day summary — the village showing its state and reasoning.
        broadcast(server, g, "§6[Village] §7Day §f" + g.day + "§7 — pop §f" + g.population() + "§7/"
                + g.targetPopulation + " · food §f" + (int) g.food + " §7· houses §f" + g.houses
                + " §7· morale §f" + (int) (g.morale * 100) + "% §7· knowledge §f" + g.knowledge
                + (raided ? " §c(raided!)" : ""));

        // Surrender becomes available once it's grown as big as ever.
        if (!g.surrenderOffered && g.peakPopulation >= g.targetPopulation) {
            g.surrenderOffered = true;
            broadcast(server, g, "§e[Village] The village has grown as big as ever (§f" + g.peakPopulation
                    + "§e). You may §f/village surrender §eto concede it its triumph.");
        }

        // Win: the village has died out — the player outlasted it.
        if (g.population() == 0) {
            endGame(server, g, "§6[Village] §7The village has died out after §f" + g.day
                    + "§7 days. §aYou outlasted them — you win!", false);
        }
    }

    /** Removes one villager (a death), narrating it. */
    private static void removeVillager(MinecraftServer server, VillageGame g,
                                       Map<UUID, AiAssistantEntity> live, String reasonSuffix) {
        UUID victim = null;
        for (UUID id : g.villagers.keySet()) { victim = id; break; }
        if (victim == null) return;
        VillageGame.VillagerInfo info = g.villagers.remove(victim);
        AiAssistantEntity e = live.remove(victim);
        String name = info != null ? info.name() : (e != null ? e.getAssistantName() : "A villager");
        if (e != null) e.discard();
        broadcast(server, g, "§7[Village] §f" + name + reasonSuffix + "§7. (pop " + g.population() + ")");
    }

    // ── spawning villagers ───────────────────────────────────────────────────────────

    private static void spawnVillager(ServerLevel level, VillageGame g) {
        AiAssistantEntity e = ModEntities.AI_ASSISTANT.create(level, EntitySpawnReason.COMMAND);
        if (e == null) return;

        VillageRole role = VillageRole.forIndex(g.roleCursor++);
        Personality[] ps = Personality.values();
        Personality persona = ps[Math.floorMod(g.roleCursor, ps.length)];
        String base = NAMES[Math.floorMod(g.nameCursor++, NAMES.length)];
        String name = base + " the " + role.display;

        e.setAssistantName(name);
        e.setSkin(SKINS[Math.floorMod(g.roleCursor, SKINS.length)]);
        e.setPersonality(persona);
        e.setOwnerUuid(g.founder);
        e.setOwnerName(g.founderName);
        e.setAutonomousMode(false);                        // hand-driven — never self-plans (no API storms)
        e.setMode(AiAssistantEntity.Mode.GUARDING);        // stays home, still defends & gathers

        // Each villager gets its own model → they "think differently".
        HuggingFaceClient.ApiAuth auth = villagerAuth(g);
        e.setAiOverride(auth);

        double ang = RNG.nextDouble() * Math.PI * 2, r = 2 + RNG.nextDouble() * 4;
        e.setPos(g.center.getX() + 0.5 + Math.cos(ang) * r,
                 g.center.getY(),
                 g.center.getZ() + 0.5 + Math.sin(ang) * r);
        level.addFreshEntity(e);

        g.villagers.put(e.getUUID(), new VillageGame.VillagerInfo(role, auth, name));
    }

    /**
     * Builds this villager's model/endpoint. It uses the same key + endpoint the founder
     * resolves to, but — when that endpoint is a local Ollama with a pool of models —
     * hands each villager a different local model so they "think differently". On any
     * other provider (a real key, Player2, the free service) it keeps the resolved model
     * (e.g. gpt-oss-120b), since those endpoints only serve their own model ids; the
     * villagers still differ by personality and temperature.
     */
    private static HuggingFaceClient.ApiAuth villagerAuth(VillageGame g) {
        ModConfig cfg = ModConfig.get();
        HuggingFaceClient.ApiAuth base = HuggingFaceClient.ApiAuth.resolveFor(g.founder, g.founderName);
        boolean onOllama = cfg.ollamaEnabled && base.url() != null && base.url().equals(cfg.ollamaUrl);
        if (onOllama && !cfg.ollamaModels.isEmpty()) {
            String model = cfg.ollamaModels.get(Math.floorMod(g.modelCursor++, cfg.ollamaModels.size()));
            return new HuggingFaceClient.ApiAuth(base.token(), model, base.url(), base.free(), base.local());
        }
        return base;
    }

    // ── narration (the villagers showing their intelligence) ─────────────────────────

    private static void narrateOne(MinecraftServer server, ServerLevel level, VillageGame g,
                                   Map<UUID, AiAssistantEntity> live) {
        List<UUID> ids = new ArrayList<>(g.villagers.keySet());
        if (ids.isEmpty()) return;
        UUID id = ids.get(Math.floorMod(g.thinkerCursor++, ids.size()));
        VillageGame.VillagerInfo info = g.villagers.get(id);
        AiAssistantEntity e = live.get(id);
        if (info == null || e == null) return;

        // Keep them looking busy: a short stroll to a nearby spot.
        double ang = RNG.nextDouble() * Math.PI * 2, r = 2 + RNG.nextDouble() * 8;
        e.getNavigation().moveTo(g.center.getX() + 0.5 + Math.cos(ang) * r,
                g.center.getY(), g.center.getZ() + 0.5 + Math.sin(ang) * r, 0.9);

        // Role-driven social events (teach / trade) — visible cooperation between villagers.
        if (info.role() == VillageRole.TEACHER && ids.size() > 1) {
            g.knowledge += 1;
            String pupil = otherName(g, id);
            broadcast(server, g, "§b" + info.name() + ": §f\"Passing what I know to " + pupil
                    + " — that's how a village gets smarter.\"");
            return;
        }
        if (info.role() == VillageRole.TRADER && ids.size() > 1) {
            g.morale = clamp(g.morale + 0.02, 0, 1);
            String partner = otherName(g, id);
            broadcast(server, g, "§b" + info.name() + ": §f\"Struck a fair trade with " + partner
                    + " — we both come out ahead.\"");
            return;
        }

        // Cooperation: two villagers pair up on a shared job — the AIs working together,
        // and getting it done faster than either would alone.
        if (ids.size() > 1 && RNG.nextInt(100) < 25) {
            String partner = otherName(g, id);
            String verb = switch (info.role()) {
                case BUILDER -> "raise the walls";
                case FARMER  -> "work the fields";
                case TEACHER -> "train the youngsters";
                case TRADER  -> "run the market";
                case GUARD   -> "hold the watch";
                case SCHOLAR -> "plan the expansion";
            };
            switch (info.role()) {
                case BUILDER -> placeHut(level, g);                        // two hands, a house rises
                case FARMER  -> g.food += 2;
                case SCHOLAR, TEACHER -> g.knowledge += 1;
                default -> g.morale = clamp(g.morale + 0.02, 0, 1);
            }
            broadcast(server, g, "§d" + info.name() + " §7& §d" + partner + ": §f\"Teaming up to "
                    + verb + " — together we get twice as much done.\"");
            return;
        }

        // Otherwise: let the villager's OWN model write a short line (async), or fall back
        // to the role pool. Only ~60% of the time hit the model, to keep API use light.
        boolean useModel = info.auth() != null && info.auth().usable() && RNG.nextInt(100) < 60;
        if (!useModel) {
            broadcast(server, g, "§b" + info.name() + ": §f\"" + info.role().thought(RNG) + "\"");
            return;
        }
        String goal = g.population() < g.targetPopulation ? "grow and thrive" : "keep thriving";
        String prompt = "You are " + info.name() + ", a " + info.role().display
                + " in a small Minecraft village on day " + g.day + " (population " + g.population()
                + ", aiming for " + g.targetPopulation + "). In ONE short first-person sentence (max 16 words), "
                + "say what you're doing right now to help the village " + goal
                + ", showing your intelligence. No quotation marks.";
        CompletableFuture<String> fut = CLIENT.requestChat(List.of(), prompt, info.auth());
        fut.whenComplete((line, ex) -> server.execute(() -> {
            if (g.ended) return;
            String say = (ex != null || line == null || line.isBlank() || line.startsWith("("))
                    ? info.role().thought(RNG) : line.trim();
            if (say.length() > 140) say = say.substring(0, 140);
            broadcast(server, g, "§b" + info.name() + ": §f\"" + say + "\"");
        }));
    }

    private static String otherName(VillageGame g, UUID exclude) {
        for (Map.Entry<UUID, VillageGame.VillagerInfo> en : g.villagers.entrySet()) {
            if (!en.getKey().equals(exclude)) return en.getValue().name();
        }
        return "a neighbour";
    }

    // ── building ─────────────────────────────────────────────────────────────────────

    /** Raises a small 3×3 hut near the centre and bumps the house count. */
    private static void placeHut(ServerLevel level, VillageGame g) {
        int slot = g.houseCursor++;
        int gx = g.center.getX() + ((slot % 5) - 2) * 5;
        int gz = g.center.getZ() + (((slot / 5) % 5) - 2) * 5;
        int y = g.center.getY();
        BlockState floor = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState wall = Blocks.COBBLESTONE.defaultBlockState();
        BlockState roof = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(level, gx + dx, y - 1, gz + dz, floor);       // floor
                set(level, gx + dx, y + 3, gz + dz, roof);        // roof
                boolean edge = dx == -1 || dx == 1 || dz == -1 || dz == 1;
                for (int dy = 0; dy <= 2; dy++) {
                    if (edge) set(level, gx + dx, y + dy, gz + dz, wall);
                    else set(level, gx + dx, y + dy, gz + dz, air);   // hollow interior
                }
            }
        }
        // Carve a doorway on the south face.
        set(level, gx, y, gz + 1, air);
        set(level, gx, y + 1, gz + 1, air);
        g.houses++;
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlockAndUpdate(new BlockPos(x, y, z), state);
    }

    // ── shared helpers ───────────────────────────────────────────────────────────────

    private static Map<VillageRole, Integer> roleCounts(VillageGame g, MinecraftServer server) {
        Map<VillageRole, Integer> counts = new HashMap<>();
        for (VillageGame.VillagerInfo info : g.villagers.values()) {
            counts.merge(info.role(), 1, Integer::sum);
        }
        if (server != null) {
            for (UUID pid : g.participants) {
                VillageRole r = g.playerRoles.get(pid);
                ServerPlayer p = server.getPlayerList().getPlayer(pid);
                if (r != null && p != null && p.level().dimension().equals(g.dimension)) {
                    counts.merge(r, 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    private static boolean playerHas(VillageGame g, MinecraftServer server, VillageRole role) {
        if (server == null) return false;
        for (UUID pid : g.participants) {
            if (role.equals(g.playerRoles.get(pid))) {
                ServerPlayer p = server.getPlayerList().getPlayer(pid);
                if (p != null && p.level().dimension().equals(g.dimension)) return true;
            }
        }
        return false;
    }

    private static void endGame(MinecraftServer server, VillageGame g, String message, boolean removeVillagers) {
        if (g.ended) { GAMES.remove(g); return; }
        g.ended = true;
        if (message != null) broadcast(server, g, message);
        if (removeVillagers && server != null) {
            Map<UUID, AiAssistantEntity> byId = new HashMap<>();
            for (AiAssistantEntity e : AiAssistantEntity.all(server)) byId.put(e.getUUID(), e);
            for (UUID id : g.villagers.keySet()) {
                AiAssistantEntity e = byId.get(id);
                if (e != null) e.discard();
            }
        }
        g.villagers.clear();
        GAMES.remove(g);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    /** Broadcasts to the founder and everyone who joined a role (online). */
    private static void broadcast(MinecraftServer server, VillageGame g, String message) {
        if (server == null) return;
        Component c = Component.literal(message);
        Set<UUID> to = new HashSet<>(g.participants);
        to.add(g.founder);
        for (UUID u : to) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) p.sendSystemMessage(c);
        }
    }

    private static void msg(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
    }

    /** The role ids, for command tab-completion. */
    public static List<String> roleIds() {
        List<String> ids = new ArrayList<>();
        for (VillageRole r : VillageRole.values()) ids.add(r.id.toLowerCase(Locale.ROOT));
        return ids;
    }
}
