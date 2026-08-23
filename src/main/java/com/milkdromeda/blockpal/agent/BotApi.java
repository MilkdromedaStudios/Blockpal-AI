package com.milkdromeda.blockpal.agent;

import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import com.milkdromeda.blockpal.vision.BotVision;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * <b>Everything an AI-written script is allowed to do</b> — the bot's controls and its
 * senses, and nothing else.
 *
 * <p>Two kinds of function live here:
 * <ul>
 *   <li><b>Instant</b> ones answer from what the bot can perceive right now
 *       ({@code health()}, {@code blockAt(...)}, {@code count("coal")}); they return a
 *       value straight away.</li>
 *   <li><b>Held</b> ones return a {@link Task} that occupies the bot for a while
 *       ({@code walk(20)}, {@code mine()}, {@code goTo(x,y,z)}). {@link ScriptRunner}
 *       parks on them across ticks, so the world keeps running normally.</li>
 * </ul>
 *
 * <p>Note what is <i>not</i> here: no teleport, no set-block, no give, no
 * see-through-walls query. Anything the bot changes about the world it changes by
 * holding a mouse button while standing next to it — including chests, which are opened
 * within arm's reach and emptied a stack at a time, with the lid animation and sound a
 * player would make.
 */
public final class BotApi {

    private BotApi() {}

    /** A held action that occupies the bot for one or more ticks. */
    public interface Task {
        /** @return true when the action is finished. */
        boolean tick(AiAssistantEntity bot, ServerLevel level, BotInput input);

        /** The value the script sees once it completes (may be null). */
        default Object result() { return null; }

        /** A short line for the script log, or "" to stay quiet. */
        default String summary() { return ""; }
    }

    /** name → (minimum args, maximum args). Also the catalogue the AI is shown. */
    private static final Map<String, int[]> FUNCTIONS = new LinkedHashMap<>();

    private static void fn(String name, int min, int max) {
        FUNCTIONS.put(name, new int[]{min, max});
    }

    static {
        // ── senses ──
        fn("x", 0, 0); fn("y", 0, 0); fn("z", 0, 0);
        fn("yaw", 0, 0); fn("pitch", 0, 0);
        fn("health", 0, 0); fn("maxhealth", 0, 0);
        fn("time", 0, 0); fn("light", 0, 0);
        fn("blockat", 3, 3);
        fn("lookingat", 0, 0);
        fn("distanceto", 3, 3);
        fn("find", 1, 2);
        fn("findentity", 1, 2);
        fn("nearestitem", 0, 1);
        fn("ownerdistance", 0, 0);
        fn("ownerx", 0, 0); fn("ownery", 0, 0); fn("ownerz", 0, 0);
        fn("count", 1, 1);
        fn("has", 1, 1);
        fn("holding", 0, 0);
        fn("onground", 0, 0);
        fn("posx", 1, 1); fn("posy", 1, 1); fn("posz", 1, 1);   // parse "x,y,z" strings
        // ── instant controls ──
        fn("say", 1, 1);
        fn("log", 1, 1);
        fn("select", 1, 1);
        fn("sneak", 1, 1);
        fn("sprint", 1, 1);
        fn("drop", 1, 2);
        fn("equipbest", 0, 0);
        fn("eat", 0, 0);
        // ── held actions ──
        fn("wait", 1, 1);
        fn("walk", 1, 1);
        fn("walkback", 1, 1);
        fn("strafeleft", 1, 1);
        fn("straferight", 1, 1);
        fn("jump", 0, 0);
        fn("look", 2, 2);
        fn("lookat", 3, 3);
        fn("turn", 1, 2);
        fn("goto", 3, 4);
        fn("mine", 0, 1);
        fn("mineat", 3, 3);
        fn("attack", 0, 1);
        fn("use", 0, 0);
        fn("useat", 3, 3);
        fn("place", 3, 3);
        fn("collect", 0, 2);
        fn("followowner", 0, 1);
        // ── containers (chests, barrels, furnaces, hoppers, shulkers) ──
        fn("opencontainer", 3, 3);
        fn("closecontainer", 0, 0);
        fn("containerlist", 0, 0);
        fn("take", 1, 2);
        fn("takeall", 0, 0);
        fn("put", 1, 2);
        fn("putall", 0, 1);
        // ── knowing more about the world ──
        fn("inventorylist", 0, 0);
        fn("hasspace", 0, 0);
        fn("nearestplayer", 0, 1);
        fn("playername", 0, 1);
        fn("isday", 0, 0);
        fn("israining", 0, 0);
        fn("dimension", 0, 0);
        fn("armor", 0, 0);
        fn("cansee", 3, 3);
        // ── remembering things between sessions ──
        fn("remember", 2, 2);
        fn("recall", 1, 1);
        fn("forget", 1, 1);
        fn("setwaypoint", 1, 1);
        fn("waypoint", 1, 1);
        fn("waypoints", 0, 0);
        fn("gotowaypoint", 1, 1);
        // ── making things ──
        fn("craft", 1, 2);
        fn("torch", 0, 0);
        // ── bigger jobs ──
        fn("tunnel", 1, 1);
        fn("bridge", 1, 1);
        fn("pillarup", 1, 1);
        fn("stairsdown", 1, 1);
        fn("minevein", 3, 3);
        fn("harvest", 0, 1);
        fn("plant", 0, 1);
        fn("defend", 0, 1);
        fn("sleep", 0, 0);
    }

    public static boolean isFunction(String name) {
        return FUNCTIONS.containsKey(name);
    }

    /**
     * Checks an argument count at <b>compile</b> time.
     *
     * <p>Catching {@code goTo(1)} while the script is still being read means the model
     * gets one clear "that takes 3 arguments" and can rewrite it — rather than the bot
     * starting the script, walking somewhere, and failing halfway through.
     *
     * @return a readable complaint, or null when the call is fine
     */
    public static String checkArity(String name, int argc) {
        int[] arity = FUNCTIONS.get(name);
        if (arity == null) return "there is no action called '" + name + "'";
        if (argc < arity[0] || argc > arity[1]) {
            String expected = arity[0] == arity[1]
                    ? String.valueOf(arity[0]) : arity[0] + "-" + arity[1];
            return name + "() takes " + expected + " argument(s), but you gave " + argc;
        }
        return null;
    }

    /**
     * The API reference handed to the model in its system prompt. Kept in one place so
     * the prompt can never drift from what {@link #call} actually accepts.
     */
    public static String reference() {
        return """
            SENSES (return a value immediately)
              x() y() z()                  my block position
              yaw() pitch()                where I'm looking (yaw: 0=south, 90=west, 180=north, 270=east)
              health() maxHealth()         my health
              time()                       0-24000 world time (13000-23000 is night)
              light()                      light level where I stand, 0-15
              onGround()                   am I standing on something
              blockAt(x, y, z)             block name there, e.g. "oak_log", "air"
              lookingAt()                  "name x,y,z" the crosshair is on, or ""
              distanceTo(x, y, z)          blocks away
              find("chest", radius)        nearest matching block -> "x,y,z" or ""
              findEntity("zombie", radius) nearest matching creature -> "x,y,z" or ""
              nearestItem(radius)          nearest dropped item -> "x,y,z" or ""
              posX(s) posY(s) posZ(s)      pull a number out of an "x,y,z" string
              ownerDistance() ownerX() ownerY() ownerZ()
              count("coal")                how many I carry
              has("axe")                   do I carry one
              holding()                    what's in my hand

            CONTROLS (instant)
              say("text")                  talk out loud
              log("text")                  note something in my own log
              select("pickaxe")            put a carried item in my hand
              sneak(true/false) sprint(true/false)
              drop("dirt", 10)             throw items away
              equipBest()                  wear/wield the best gear I carry
              eat()                        eat food I'm carrying

            ACTIONS (take time — I keep doing them while the script waits)
              wait(ticks)                  20 ticks = 1 second
              walk(ticks) walkBack(ticks) strafeLeft(ticks) strafeRight(ticks)
              jump()
              look(yaw, pitch) lookAt(x, y, z) turn(dYaw, dPitch)
              goTo(x, y, z)                walk there (real pathfinding, no teleporting)
              mine()                       hold left-click until what I'm aiming at breaks
              mineAt(x, y, z)              walk into reach, aim, and mine that block
              attack(ticks)                swing at whatever is in front of me
              use()                        right-click what I'm aiming at
              useAt(x, y, z)               aim at that block and right-click it
              place(x, y, z)               place the held block against that spot
              collect(radius)              walk over and pick up nearby dropped items
              followOwner(ticks)           walk to my owner

            CONTAINERS (I must be within reach — I open it like a player would)
              openContainer(x, y, z)       chest, barrel, furnace, hopper, shulker...
              containerList()              what's inside, as text
              take("coal", 8) takeAll()    move items into my backpack
              put("raw_iron", 8) putAll()  move items out of my backpack (furnace fuel
                                           and inputs go to the right slot automatically)
              closeContainer()

            KNOWING MORE
              inventoryList()              everything I'm carrying, as text
              hasSpace()                   is there room in my backpack
              nearestPlayer(radius)        -> "x,y,z" of the closest person
              playerName(radius)           their name
              isDay() isRaining()          weather and time of day
              dimension()                  "overworld", "the_nether", ...
              armor()                      my armour points
              canSee(x, y, z)              is there a clear line to that block

            REMEMBERING (kept when the world is saved)
              remember("key", "value")     write a note to myself
              recall("key")                read it back  ("" if I never wrote it)
              forget("key")
              setWaypoint("home")          note where I'm standing
              waypoint("home")             -> "x,y,z"    waypoints() lists them
              goToWaypoint("home")         walk back there

            MAKING THINGS
              craft("stick", 8)            real recipes, from my own ingredients;
                                           anything bigger than 2x2 needs a crafting
                                           table within reach, like it would for you
              torch()                      put down a light where I'm standing

            BIGGER JOBS (these take a while — I keep at them)
              tunnel(20)                   dig a corridor two blocks high ahead of me
              stairsDown(15)               dig down as a staircase, checking for liquid
              bridge(12)                   walk forward laying blocks across a gap
              pillarUp(10)                 jump-and-place my way upwards
              mineVein(x, y, z)            follow a seam of ore, not just one block
              harvest(radius)              break every fully grown crop nearby
              plant(radius)                sow my seeds on empty farmland
              defend(ticks)                fight what's attacking us
              sleep()                      find a bed and get in it

            LANGUAGE
              let n = 3                    variables
              if <test> { } else { }       conditions ( == != < > <= >= && || ! )
              repeat 5 { }                 loops
              while <test> { }             loops (they are cut off if they run too long)
              break / continue
              # comment
            """;
    }

    // ── dispatch ────────────────────────────────────────────────────────────────

    /**
     * Runs one API call. Returns an immediate value, or a {@link Task} the runner will
     * tick until it finishes.
     */
    public static Object call(String name, List<Object> args, AiAssistantEntity bot,
                              ServerLevel level, BotInput input, ScriptRunner runner) {
        int[] arity = FUNCTIONS.get(name);
        if (arity == null) throw new AgentScript.ScriptException("no action called '" + name + "'");
        if (args.size() < arity[0] || args.size() > arity[1]) {
            throw new AgentScript.ScriptException(name + "() takes " + arity[0]
                    + (arity[0] == arity[1] ? "" : "-" + arity[1]) + " argument(s), got " + args.size());
        }

        return switch (name) {
            // ── senses ──
            case "x" -> (double) bot.blockPosition().getX();
            case "y" -> (double) bot.blockPosition().getY();
            case "z" -> (double) bot.blockPosition().getZ();
            case "yaw" -> (double) Math.round(bot.getYRot());
            case "pitch" -> (double) Math.round(bot.getXRot());
            case "health" -> (double) Math.round(bot.getHealth());
            case "maxhealth" -> (double) Math.round(bot.getMaxHealth());
            case "time" -> (double) (level.getOverworldClockTime() % 24000L);
            case "light" -> (double) level.getMaxLocalRawBrightness(bot.blockPosition());
            case "onground" -> bot.onGround();
            case "blockat" -> BotVision.blockName(level.getBlockState(pos(args, 0)));
            case "lookingat" -> lookingAt(bot, level);
            case "distanceto" -> Math.sqrt(bot.distanceToSqr(
                    num(args, 0) + 0.5, num(args, 1) + 0.5, num(args, 2) + 0.5));
            case "find" -> findBlock(bot, level, str(args, 0), args.size() > 1 ? (int) num(args, 1) : 12);
            case "findentity" -> findEntity(bot, level, str(args, 0),
                    args.size() > 1 ? num(args, 1) : 24);
            case "nearestitem" -> nearestItem(bot, level, args.isEmpty() ? 12 : num(args, 0));
            case "ownerdistance" -> ownerDistance(bot);
            case "ownerx" -> ownerCoord(bot, 0);
            case "ownery" -> ownerCoord(bot, 1);
            case "ownerz" -> ownerCoord(bot, 2);
            case "count" -> (double) countItem(bot, str(args, 0));
            case "has" -> countItem(bot, str(args, 0)) > 0;
            case "holding" -> itemName(bot.getItemBySlot(EquipmentSlot.MAINHAND));
            case "posx" -> coordFrom(str(args, 0), 0);
            case "posy" -> coordFrom(str(args, 0), 1);
            case "posz" -> coordFrom(str(args, 0), 2);

            // ── instant controls ──
            case "say" -> { bot.broadcastMessage(str(args, 0)); yield null; }
            case "log" -> { runner.log(str(args, 0)); yield null; }
            case "select" -> bot.holdItemMatching(str(args, 0));
            case "sneak" -> { input.setSneak(bool(args, 0)); yield null; }
            case "sprint" -> { input.setSprint(bool(args, 0)); yield null; }
            case "drop" -> (double) dropItem(bot, level, str(args, 0),
                    args.size() > 1 ? (int) num(args, 1) : 64);
            case "equipbest" -> { bot.optimizeEquipment(level); yield null; }
            case "eat" -> bot.consumeBestFood(level);

            // ── held actions ──
            case "wait" -> new HoldTask(ticks(args, 0), 0, 0, false, false, "waiting");
            case "walk" -> new HoldTask(ticks(args, 0), 1, 0, false, false, "walking forward");
            case "walkback" -> new HoldTask(ticks(args, 0), -1, 0, false, false, "backing up");
            case "strafeleft" -> new HoldTask(ticks(args, 0), 0, 1, false, false, "stepping left");
            case "straferight" -> new HoldTask(ticks(args, 0), 0, -1, false, false, "stepping right");
            case "jump" -> new JumpTask();
            case "look" -> new AimTask((float) num(args, 0), (float) num(args, 1));
            case "lookat" -> new AimAtTask(num(args, 0) + 0.5, num(args, 1) + 0.5, num(args, 2) + 0.5);
            case "turn" -> new AimTask(bot.getYRot() + (float) num(args, 0),
                    bot.getXRot() + (args.size() > 1 ? (float) num(args, 1) : 0f));
            case "goto" -> new GoToTask(num(args, 0), num(args, 1), num(args, 2),
                    args.size() > 3 ? ticks(args, 3) : 400);
            case "mine" -> new MineTask(args.isEmpty() ? 300 : ticks(args, 0));
            case "mineat" -> new MineAtTask(pos(args, 0));
            case "attack" -> new HoldTask(args.isEmpty() ? 20 : ticks(args, 0), 0, 0, true, false, "attacking");
            case "use" -> new HoldTask(6, 0, 0, false, true, "using");
            case "useat" -> new UseAtTask(pos(args, 0), false);
            case "place" -> new UseAtTask(pos(args, 0), true);
            case "collect" -> new CollectTask(args.isEmpty() ? 8 : num(args, 0), 200);
            case "followowner" -> new FollowOwnerTask(args.isEmpty() ? 100 : ticks(args, 0));

            // ── containers ──
            case "opencontainer" -> openContainer(bot, level, pos(args, 0), runner);
            case "closecontainer" -> { closeContainer(level, runner); yield null; }
            case "containerlist" -> describeContainer(runner);
            case "take" -> (double) moveItems(bot, runner, level, str(args, 0),
                    args.size() > 1 ? (int) num(args, 1) : 64, true);
            case "takeall" -> (double) moveItems(bot, runner, level, "", 64 * 64, true);
            case "put" -> (double) moveItems(bot, runner, level, str(args, 0),
                    args.size() > 1 ? (int) num(args, 1) : 64, false);
            case "putall" -> (double) moveItems(bot, runner, level,
                    args.isEmpty() ? "" : str(args, 0), 64 * 64, false);

            // ── knowing more about the world ──
            case "inventorylist" -> BotSkills.inventoryList(bot);
            case "hasspace" -> BotSkills.hasSpace(bot);
            case "nearestplayer" -> BotSkills.nearestPlayer(bot, level,
                    args.isEmpty() ? 24 : num(args, 0));
            case "playername" -> BotSkills.nearestPlayerName(bot, level,
                    args.isEmpty() ? 24 : num(args, 0));
            case "isday" -> BotSkills.isDay(level);
            case "israining" -> level.isRaining();
            case "dimension" -> BotSkills.dimensionName(level);
            case "armor" -> (double) bot.getArmorValue();
            case "cansee" -> BotSkills.canSee(bot, level, pos(args, 0));

            // ── remembering things between sessions ──
            case "remember" -> { bot.remember(str(args, 0), str(args, 1)); yield null; }
            case "recall" -> bot.recall(str(args, 0));
            case "forget" -> bot.forget(str(args, 0));
            case "setwaypoint" -> BotSkills.setWaypoint(bot, str(args, 0));
            case "waypoint" -> BotSkills.getWaypoint(bot, str(args, 0));
            case "waypoints" -> BotSkills.waypointList(bot);
            case "gotowaypoint" -> goToWaypoint(bot, str(args, 0), runner);

            // ── making things ──
            case "craft" -> craft(bot, level, runner, str(args, 0),
                    args.size() > 1 ? (int) num(args, 1) : 1);
            case "torch" -> { runner.log(BotSkills.placeTorch(bot, level, input)); yield null; }

            // ── bigger jobs ──
            case "tunnel" -> new BotSkills.TunnelTask(bot, (int) num(args, 0));
            case "bridge" -> new BotSkills.BridgeTask(bot, (int) num(args, 0));
            case "pillarup" -> new BotSkills.PillarTask((int) num(args, 0));
            case "stairsdown" -> new BotSkills.StairsDownTask(bot, (int) num(args, 0));
            case "minevein" -> new BotSkills.MineVeinTask(pos(args, 0));
            case "harvest" -> new BotSkills.HarvestTask(args.isEmpty() ? 8 : num(args, 0));
            case "plant" -> new BotSkills.PlantTask(args.isEmpty() ? 8 : num(args, 0));
            case "defend" -> new BotSkills.DefendTask(args.isEmpty() ? 200 : ticks(args, 0));
            case "sleep" -> new BotSkills.SleepTask();

            default -> throw new AgentScript.ScriptException("no action called '" + name + "'");
        };
    }

    /** Walks to a named waypoint, or logs that there isn't one. */
    private static Object goToWaypoint(AiAssistantEntity bot, String name, ScriptRunner runner) {
        String at = BotSkills.getWaypoint(bot, name);
        if (at.isBlank()) {
            runner.log("I don't have a waypoint called '" + name + "'");
            return false;
        }
        return goToTask(coordFrom(at, 0), coordFrom(at, 1), coordFrom(at, 2), 600);
    }

    /** Crafts, and puts the outcome in the script log either way. */
    private static Object craft(AiAssistantEntity bot, ServerLevel level, ScriptRunner runner,
                                String what, int count) {
        BotCrafting.Result result = BotCrafting.craft(bot, level, what, count);
        runner.log(result.message());
        return (double) result.crafted();
    }

    // ── held actions, as building blocks for bigger jobs ────────────────────────

    /**
     * The single-block actions, exposed so {@link BotSkills} can compose jobs out of
     * them rather than reimplementing walking-into-reach-and-mining for every skill.
     */
    public static Task mineAtTask(BlockPos target) { return new MineAtTask(target); }

    public static Task useAtTask(BlockPos target, boolean place) {
        return new UseAtTask(target, place);
    }

    public static Task goToTask(double x, double y, double z, int maxTicks) {
        return new GoToTask(x, y, z, maxTicks);
    }

    // ── held action implementations ─────────────────────────────────────────────

    /**
     * Holds a set of buttons down for a fixed number of ticks — the shape almost every
     * simple action takes ({@code walk}, {@code attack}, {@code wait}, {@code use}).
     */
    private static final class HoldTask implements Task {
        private final int total;
        private final float forward, strafe;
        private final boolean attack, use;
        private final String what;
        private int elapsed;

        HoldTask(int total, float forward, float strafe, boolean attack, boolean use, String what) {
            this.total = total; this.forward = forward; this.strafe = strafe;
            this.attack = attack; this.use = use; this.what = what;
        }

        @Override
        public boolean tick(AiAssistantEntity bot, ServerLevel level, BotInput input) {
            input.setForward(forward);
            input.setStrafe(strafe);
            input.setAttack(attack);
            input.setUse(use);
            if (++elapsed >= Math.max(1, total)) {
                input.setForward(0);
                input.setStrafe(0);
                input.setAttack(false);
                input.setUse(false);
                return true;
            }
            return false;
        }

        @Override public String summary() { return what; }
    }

    /** A jump is a single tick of the space bar. */
    private static final class JumpTask implements Task {
        @Override
        public boolean tick(AiAssistantEntity bot, ServerLevel level, BotInput input) {
            input.setJump(true);
            return true;
        }
        @Override public String summary() { return "jumped"; }
    }

    /** Turns the view to an absolute yaw/pitch at a human turn rate. */
    private static final class AimTask implements Task {
        private final float yaw, pitch;
        private int ticks;

        AimTask(float yaw, float pitch) { this.yaw = yaw; this.pitch = pitch; }

        @Override
        public boolean tick(AiAssistantEntity bot, ServerLevel level, BotInput input) {
            input.aim(yaw, pitch);
            return ++ticks >= 12 || (Math.abs(net.minecraft.util.Mth.wrapDegrees(bot.getYRot() - yaw)) < 3
                    && Math.abs(bot.getXRot() - pitch) < 3);
        }
    }

    /** Turns to look at a world position. */
    private static final class AimAtTask implements Task {
        private final double tx, ty, tz;
        private int ticks;

        AimAtTask(double x, double y, double z) { tx = x; ty = y; tz = z; }

        @Override
        public boolean tick(AiAssistantEntity bot, ServerLevel level, BotInput input) {
            aimAt(bot, input, tx, ty, tz);
            return ++ticks >= 12;
        }
    }

    /** Walks to a position with real pathfinding — no teleport, and it can fail. */
    private static final class GoToTask implements Task {
        private final double tx, ty, tz;
        private final int maxTicks;
        private int ticks;
        private int repath;
        private String outcome = "";

        GoToTask(double x, double y, double z, int maxTicks) {
            tx = x + 0.5; ty = y; tz = z + 0.5; this.maxTicks = maxTicks;
        }

        @Override
        public boolean tick(AiAssistantEntity bot, ServerLevel level, BotInput input) {
            input.setNavigating(true);
            if (bot.distanceToSqr(tx, ty, tz) < 2.6) {
                outcome = "arrived";
                return finish(bot, input);
            }
            if (++ticks > maxTicks) {
                outcome = "couldn't get there in time";
                return finish(bot, input);
            }
            if (repath-- <= 0 || bot.getNavigation().isDone()) {
                repath = 10;
                boolean ok = bot.getNavigation().moveTo(tx, ty, tz, 1.0);
                if (!ok && ticks > 20) {
                    outcome = "no path from here";
                    return finish(bot, input);
                }
            }
            return false;
        }

        private boolean finish(AiAssistantEntity bot, BotInput input) {
            input.setNavigating(false);
            bot.getNavigation().stop();
            return true;
        }

        @Override public Object result() { return outcome; }
        @Override public String summary() { return outcome; }
    }

    /** Holds left-click until the aimed block breaks (or we give up). */
    private static final class MineTask implements Task {
        private final int maxTicks;
        private int ticks;
        private String outcome = "";

        MineTask(int maxTicks) { this.maxTicks = maxTicks; }

        @Override
        public boolean tick(AiAssistantEntity bot, ServerLevel level, BotInput input) {
            input.setAttack(true);
            String event = input.consumeEvent();
            if (event.startsWith("broke") || event.startsWith("can't")) {
                outcome = event;
                input.setAttack(false);
                return true;
            }
            if (++ticks > maxTicks) {
                outcome = "gave up mining";
                input.setAttack(false);
                return true;
            }
            return false;
        }

        @Override public Object result() { return outcome; }
        @Override public String summary() { return outcome; }
    }

    /** Walks into reach of a block, aims at it, then mines it. */
    private static final class MineAtTask implements Task {
        private final BlockPos target;
        private final GoToTask walk;
        private final MineTask mine = new MineTask(300);
        private int phase;
        private int aimTicks;

        MineAtTask(BlockPos target) {
            this.target = target;
            this.walk = new GoToTask(target.getX(), target.getY(), target.getZ(), 200);
        }

        @Override
        public boolean tick(AiAssistantEntity bot, ServerLevel level, BotInput input) {
            if (level.getBlockState(target).isAir()) return true;
            double reach = BotInput.REACH - 0.5;
            if (phase == 0) {
                if (bot.distanceToSqr(Vec3.atCenterOf(target)) <= reach * reach) { phase = 1; return false; }
                if (walk.tick(bot, level, input)) phase = 1;
                return false;
            }
            if (phase == 1) {
                aimAt(bot, input, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
                if (++aimTicks >= 8) phase = 2;
                return false;
            }
            return mine.tick(bot, level, input);
        }

        @Override public Object result() { return mine.result(); }
        @Override public String summary() { return String.valueOf(mine.result()); }
    }

    /** Aims at a block and right-clicks it (optionally to place what's held against it). */
    private static final class UseAtTask implements Task {
        private final BlockPos target;
        private final boolean place;
        private int ticks;

        UseAtTask(BlockPos target, boolean place) { this.target = target; this.place = place; }

        @Override
        public boolean tick(AiAssistantEntity bot, ServerLevel level, BotInput input) {
            // When placing, aim at the solid neighbour so the block lands in the gap —
            // the same "click the face next to it" a player does.
            BlockPos aim = place ? supportFor(level, target) : target;
            if (aim == null) return true;
            aimAt(bot, input, aim.getX() + 0.5, aim.getY() + 0.5, aim.getZ() + 0.5);
            if (++ticks < 6) return false;
            input.setUse(true);
            if (ticks >= 10) { input.setUse(false); return true; }
            return false;
        }

        private static BlockPos supportFor(ServerLevel level, BlockPos target) {
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                BlockPos n = target.relative(d);
                if (!level.getBlockState(n).isAir() && !level.getBlockState(n).canBeReplaced()) return n;
            }
            return target;
        }
    }

    /** Walks over dropped items to pick them up — no vacuum, it has to go there. */
    private static final class CollectTask implements Task {
        private final double radius;
        private final int maxTicks;
        private int ticks;

        CollectTask(double radius, int maxTicks) { this.radius = radius; this.maxTicks = maxTicks; }

        @Override
        public boolean tick(AiAssistantEntity bot, ServerLevel level, BotInput input) {
            input.setNavigating(true);
            ItemEntity nearest = nearestItemEntity(bot, level, radius);
            if (nearest == null || ++ticks > maxTicks) {
                input.setNavigating(false);
                bot.getNavigation().stop();
                return true;
            }
            if (ticks % 10 == 1) bot.getNavigation().moveTo(nearest, 1.1);
            aimAt(bot, input, nearest.getX(), nearest.getY(), nearest.getZ());
            return false;
        }

        @Override public String summary() { return "picked up what I could reach"; }
    }

    /** Walks back to the owner (never teleports, however far behind it is). */
    private static final class FollowOwnerTask implements Task {
        private final int maxTicks;
        private int ticks;

        FollowOwnerTask(int maxTicks) { this.maxTicks = maxTicks; }

        @Override
        public boolean tick(AiAssistantEntity bot, ServerLevel level, BotInput input) {
            Player owner = bot.getOwnerPlayer();
            if (owner == null || ++ticks > maxTicks || bot.distanceToSqr(owner) < 9) {
                input.setNavigating(false);
                bot.getNavigation().stop();
                return true;
            }
            input.setNavigating(true);
            if (ticks % 10 == 1) bot.getNavigation().moveTo(owner, 1.1);
            return false;
        }
    }

    /** Shared aim helper: point the view at a world position. */
    private static void aimAt(AiAssistantEntity bot, BotInput input, double x, double y, double z) {
        Vec3 eye = bot.getEyePosition();
        double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, flat)));
        input.aim(yaw, pitch);
    }

    // ── containers ──────────────────────────────────────────────────────────────

    /**
     * Opens a container the bot is standing next to — chest, barrel, furnace, hopper,
     * dropper, shulker box, anything with slots. {@code getContainerAt} resolves double
     * chests properly, so a large chest behaves as one 54-slot container.
     */
    private static Object openContainer(AiAssistantEntity bot, ServerLevel level, BlockPos pos,
                                        ScriptRunner runner) {
        if (bot.distanceToSqr(Vec3.atCenterOf(pos)) > BotInput.REACH * BotInput.REACH * 1.6) {
            runner.log("that container is too far away to reach");
            return false;
        }
        Container container = HopperBlockEntity.getContainerAt(level, pos);
        if (container == null) {
            runner.log("there's nothing with slots at " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
            return false;
        }
        closeContainer(level, runner);
        runner.setOpenContainer(container, pos);
        BlockState state = level.getBlockState(pos);
        try {
            // The lid animation + click: chests listen for block event 1 with a viewer count.
            level.blockEvent(pos, state.getBlock(), 1, 1);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.CHEST_OPEN,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.0f);
        } catch (Exception ignored) {
            // Not every container animates; that's fine.
        }
        return true;
    }

    private static void closeContainer(ServerLevel level, ScriptRunner runner) {
        BlockPos pos = runner.openContainerPos();
        if (pos == null) return;
        try {
            BlockState state = level.getBlockState(pos);
            level.blockEvent(pos, state.getBlock(), 1, 0);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.CHEST_CLOSE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.0f);
        } catch (Exception ignored) {
            // Best-effort.
        }
        runner.setOpenContainer(null, null);
    }

    private static String describeContainer(ScriptRunner runner) {
        Container c = runner.openContainer();
        if (c == null) return "(nothing open)";
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) continue;
            if (shown++ > 0) sb.append(", ");
            sb.append(s.getCount()).append("× ").append(itemName(s));
            if (shown >= 20) { sb.append(", …"); break; }
        }
        return shown == 0 ? "(empty)" : sb.toString();
    }

    /**
     * Moves items between the open container and the backpack.
     *
     * <p>Putting items in respects {@code canPlaceItem}, which is how coal ends up in a
     * furnace's fuel slot and ore in its input slot without the script having to know
     * anything about furnace slot numbers.
     *
     * @param take true to take from the container, false to put into it
     * @return how many items actually moved
     */
    private static int moveItems(AiAssistantEntity bot, ScriptRunner runner, ServerLevel level,
                                 String query, int wanted, boolean take) {
        Container container = runner.openContainer();
        if (container == null) {
            runner.log("I haven't opened a container — call openContainer(x,y,z) first");
            return 0;
        }
        BlockPos pos = runner.openContainerPos();
        if (pos != null && bot.distanceToSqr(Vec3.atCenterOf(pos)) > BotInput.REACH * BotInput.REACH * 1.6) {
            runner.log("I walked out of reach of the container");
            return 0;
        }
        SimpleContainer pack = bot.getInventory();
        int moved = 0;

        if (take) {
            for (int i = 0; i < container.getContainerSize() && moved < wanted; i++) {
                ItemStack s = container.getItem(i);
                if (s.isEmpty() || !matches(s, query)) continue;
                int n = Math.min(s.getCount(), wanted - moved);
                ItemStack chunk = s.copyWithCount(n);
                ItemStack leftover = pack.addItem(chunk);
                int actually = n - leftover.getCount();
                if (actually <= 0) break;                    // backpack is full
                container.removeItem(i, actually);
                moved += actually;
            }
        } else {
            for (int i = 0; i < pack.getContainerSize() && moved < wanted; i++) {
                ItemStack s = pack.getItem(i);
                if (s.isEmpty() || !matches(s, query)) continue;
                int n = Math.min(s.getCount(), wanted - moved);
                int inserted = insert(container, s.copyWithCount(n));
                if (inserted <= 0) continue;
                pack.removeItem(i, inserted);
                moved += inserted;
            }
        }
        if (moved > 0) {
            container.setChanged();
            bot.getInventory().setChanged();
        }
        return moved;
    }

    /** Puts a stack into the first slot that will accept it (fuel slots included). */
    private static int insert(Container container, ItemStack stack) {
        int inserted = 0;
        // Merge into matching stacks first, exactly like shift-clicking would.
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
                ItemStack slot = container.getItem(i);
                if (pass == 0) {
                    if (slot.isEmpty() || !ItemStack.isSameItemSameComponents(slot, stack)) continue;
                    int room = Math.min(slot.getMaxStackSize(), container.getMaxStackSize()) - slot.getCount();
                    if (room <= 0) continue;
                    int n = Math.min(room, stack.getCount());
                    slot.grow(n);
                    stack.shrink(n);
                    inserted += n;
                } else {
                    if (!slot.isEmpty() || !container.canPlaceItem(i, stack)) continue;
                    int n = Math.min(stack.getMaxStackSize(), stack.getCount());
                    container.setItem(i, stack.copyWithCount(n));
                    stack.shrink(n);
                    inserted += n;
                }
            }
        }
        return inserted;
    }

    // ── sense helpers ───────────────────────────────────────────────────────────

    private static String lookingAt(AiAssistantEntity bot, ServerLevel level) {
        BlockHitResult hit = BotInput.rayBlock(bot, level);
        if (hit == null || hit.getType() == HitResult.Type.MISS) return "";
        BlockPos p = hit.getBlockPos();
        return BotVision.blockName(level.getBlockState(p)) + " "
                + p.getX() + "," + p.getY() + "," + p.getZ();
    }

    /** Nearest matching block within radius, searched the way eyes would sweep a room. */
    private static String findBlock(AiAssistantEntity bot, ServerLevel level, String query, int radius) {
        int r = Math.max(1, Math.min(24, radius));
        BlockPos centre = bot.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -Math.min(r, 8); dy <= Math.min(r, 8); dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) continue;
                    if (!BotVision.blockName(state).contains(clean(query))) continue;
                    double d = cursor.distSqr(centre);
                    if (d < bestDist) { bestDist = d; best = cursor.immutable(); }
                }
            }
        }
        return best == null ? "" : best.getX() + "," + best.getY() + "," + best.getZ();
    }

    private static String findEntity(AiAssistantEntity bot, ServerLevel level, String query, double radius) {
        double r = Math.max(2, Math.min(48, radius));
        AABB box = AABB.ofSize(bot.position(), r * 2, Math.min(r, 16) * 2, r * 2);
        String needle = clean(query);
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : level.getEntities(bot, box, Entity::isAlive)) {
            String kind = e instanceof Player p ? p.getName().getString().toLowerCase(Locale.ROOT)
                    : e.getType().toShortString();
            if (!needle.isEmpty() && !kind.contains(needle)) continue;
            double d = e.distanceToSqr(bot);
            if (d < bestDist) { bestDist = d; best = e; }
        }
        return best == null ? ""
                : (int) best.getX() + "," + (int) best.getY() + "," + (int) best.getZ();
    }

    private static String nearestItem(AiAssistantEntity bot, ServerLevel level, double radius) {
        ItemEntity item = nearestItemEntity(bot, level, radius);
        return item == null ? ""
                : (int) item.getX() + "," + (int) item.getY() + "," + (int) item.getZ();
    }

    private static ItemEntity nearestItemEntity(AiAssistantEntity bot, ServerLevel level, double radius) {
        double r = Math.max(1, Math.min(32, radius));
        AABB box = AABB.ofSize(bot.position(), r * 2, r, r * 2);
        ItemEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive()
                && bot.canTake(e.getItem()))) {
            double d = e.distanceToSqr(bot);
            if (d < bestDist) { bestDist = d; best = e; }
        }
        return best;
    }

    private static double ownerDistance(AiAssistantEntity bot) {
        Player owner = bot.getOwnerPlayer();
        return owner == null ? -1 : Math.sqrt(bot.distanceToSqr(owner));
    }

    private static double ownerCoord(AiAssistantEntity bot, int axis) {
        Player owner = bot.getOwnerPlayer();
        if (owner == null) return 0;
        return switch (axis) {
            case 0 -> Math.floor(owner.getX());
            case 1 -> Math.floor(owner.getY());
            default -> Math.floor(owner.getZ());
        };
    }

    private static int countItem(AiAssistantEntity bot, String query) {
        int n = 0;
        SimpleContainer pack = bot.getInventory();
        for (int i = 0; i < pack.getContainerSize(); i++) {
            ItemStack s = pack.getItem(i);
            if (!s.isEmpty() && matches(s, query)) n += s.getCount();
        }
        ItemStack held = bot.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!held.isEmpty() && matches(held, query)) n += held.getCount();
        return n;
    }

    private static int dropItem(AiAssistantEntity bot, ServerLevel level, String query, int wanted) {
        SimpleContainer pack = bot.getInventory();
        int dropped = 0;
        for (int i = 0; i < pack.getContainerSize() && dropped < wanted; i++) {
            ItemStack s = pack.getItem(i);
            if (s.isEmpty() || !matches(s, query)) continue;
            int n = Math.min(s.getCount(), wanted - dropped);
            bot.spawnAtLocation(level, pack.removeItem(i, n));
            dropped += n;
        }
        return dropped;
    }

    /** Loose item matching: "coal" matches minecraft:coal and coal_ore; ids match exactly. */
    public static boolean matches(ItemStack stack, String query) {
        if (query == null || query.isBlank()) return true;
        String q = clean(query);
        String id = itemId(stack);
        return id.equals(q) || id.endsWith(":" + q) || itemName(stack).contains(q);
    }

    public static String itemId(ItemStack stack) {
        try {
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return id == null ? "" : id.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** The item's registry path, e.g. {@code iron_pickaxe}. */
    public static String itemName(ItemStack stack) {
        if (stack.isEmpty()) return "nothing";
        String id = itemId(stack);
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    private static String clean(String s) {
        String q = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
        int colon = q.indexOf(':');
        return colon >= 0 ? q.substring(colon + 1) : q;
    }

    /** Pulls one number out of an "x,y,z" string returned by find()/findEntity(). */
    private static double coordFrom(String s, int index) {
        if (s == null || s.isBlank()) return 0;
        String[] parts = s.trim().split("\\s*,\\s*");
        if (index >= parts.length) return 0;
        try {
            return Double.parseDouble(parts[index].trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ── argument coercion ───────────────────────────────────────────────────────

    private static double num(List<Object> args, int i) {
        Object o = args.get(i);
        if (o instanceof Double d) return d;
        if (o instanceof Boolean b) return b ? 1 : 0;
        if (o instanceof String s) {
            try { return Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) { return 0; }
        }
        return 0;
    }

    private static int ticks(List<Object> args, int i) {
        return Math.max(0, Math.min(6000, (int) num(args, i)));
    }

    private static String str(List<Object> args, int i) {
        Object o = args.get(i);
        if (o == null) return "";
        if (o instanceof Double d) {
            return d == Math.floor(d) ? String.valueOf(d.longValue()) : String.valueOf(d);
        }
        return String.valueOf(o);
    }

    private static boolean bool(List<Object> args, int i) {
        Object o = args.get(i);
        if (o instanceof Boolean b) return b;
        if (o instanceof Double d) return d != 0;
        return o != null && !String.valueOf(o).isBlank() && !"false".equalsIgnoreCase(String.valueOf(o));
    }

    private static BlockPos pos(List<Object> args, int i) {
        return new BlockPos((int) Math.floor(num(args, i)),
                (int) Math.floor(num(args, i + 1)),
                (int) Math.floor(num(args, i + 2)));
    }

}
