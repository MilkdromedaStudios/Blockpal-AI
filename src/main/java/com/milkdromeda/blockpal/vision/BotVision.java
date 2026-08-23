package com.milkdromeda.blockpal.vision;

import com.milkdromeda.blockpal.config.ModConfig;
import com.milkdromeda.blockpal.entity.AiAssistantEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>The bot's eyes.</b> Renders what the companion is actually looking at into a
 * small picture, plus a written description of the same scene, so a vision model can
 * <i>see</i> the world the way a player does instead of being handed a coordinate
 * dump it could never have obtained by looking.
 *
 * <p>How it works: a ray is cast from the bot's eye position through every pixel of a
 * small virtual screen (default 80×45, ~90° field of view). Whatever each ray hits is
 * painted with that block's map colour, shaded by which face was hit and how far away
 * it is, exactly like a very low-resolution render. Rays that hit nothing paint sky
 * (or cave darkness underground). Entities in view are then projected onto the same
 * screen and drawn as coloured markers, and a crosshair is stamped in the middle so
 * the model can tell what it would hit if it clicked.
 *
 * <p><b>This is deliberately limited to what the bot could see.</b> Nothing behind it,
 * nothing through walls, nothing beyond the configured range — no x-ray, no map dump.
 * That limitation is the point: the bot plays with a player's information.
 *
 * <p><b>Cost:</b> one capture is {@code width × height} block ray casts on the server
 * thread, so it is rate-limited per bot ({@link #minCaptureIntervalMs()}) and the
 * resolution is clamped. Turn pictures off entirely with {@code visionEnabled} — the
 * text description alone still works (and is what non-vision models get).
 */
public final class BotVision {

    private BotVision() {}

    /**
      * Never render two pictures for the same bot closer together than this. Set by
      * {@link com.milkdromeda.blockpal.agent.Tempo} — a fixed 900 ms floor meant a bot
      * that had just finished a script had to wait most of a second before it could
      * even look at what it had done.
      */
    public static long minCaptureIntervalMs() {
        return com.milkdromeda.blockpal.agent.Tempo.current().visionIntervalMs();
    }

    /** Horizontal field of view, in degrees — roughly a player's default FOV. */
    private static final double FOV_DEGREES = 90.0;

    /** A rendered look: the picture, its size, and the same scene written out. */
    public record Snapshot(int width, int height, byte[] png, String description) {
        public String pngBase64() {
            return png == null || png.length == 0 ? "" : Base64.getEncoder().encodeToString(png);
        }

        /** A {@code data:} URI, which is how OpenAI-compatible vision APIs take an image. */
        public String dataUri() {
            String b64 = pngBase64();
            return b64.isEmpty() ? "" : "data:image/png;base64," + b64;
        }

        public boolean hasImage() {
            return png != null && png.length > 0;
        }
    }

    /**
     * Renders what {@code bot} can see right now. Must be called on the server thread —
     * it reads block states. Returns a description-only snapshot when pictures are
     * disabled, and never throws (a broken render still yields the written scene).
     */
    public static Snapshot capture(AiAssistantEntity bot) {
        ModConfig cfg = ModConfig.get();
        if (!(bot.level() instanceof ServerLevel level)) {
            return new Snapshot(0, 0, new byte[0], "I can't see anything right now.");
        }

        int w = clamp(cfg.visionWidth, 32, 160);
        int h = clamp(cfg.visionHeight, 18, 90);
        double range = clamp(cfg.visionRange, 8, 64);

        Vec3 eye = bot.getEyePosition();
        double yawRad = Math.toRadians(bot.getYRot());
        double pitchRad = Math.toRadians(bot.getXRot());

        // Camera basis. Minecraft's yaw 0 faces +Z (south) and increases clockwise, so
        // "right" is the forward vector turned another 90° of yaw; up is right × forward.
        Vec3 forward = new Vec3(
                -Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(pitchRad),
                Math.cos(yawRad) * Math.cos(pitchRad)).normalize();
        Vec3 right = new Vec3(-Math.cos(yawRad), 0, -Math.sin(yawRad)).normalize();
        Vec3 up = right.cross(forward).normalize();

        double tanH = Math.tan(Math.toRadians(FOV_DEGREES) / 2.0);
        double tanV = tanH * h / (double) w;

        Scene scene = new Scene();
        int[] pixels = cfg.visionEnabled ? new int[w * h] : null;

        for (int py = 0; py < h; py++) {
            double ndcY = 1.0 - 2.0 * (py + 0.5) / h;
            for (int px = 0; px < w; px++) {
                double ndcX = 2.0 * (px + 0.5) / w - 1.0;
                Vec3 dir = forward
                        .add(right.scale(ndcX * tanH))
                        .add(up.scale(ndcY * tanV))
                        .normalize();

                BlockHitResult hit = level.clip(new ClipContext(
                        eye, eye.add(dir.scale(range)),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, bot));

                boolean centre = px == w / 2 && py == h / 2;
                int colour;
                if (hit.getType() == HitResult.Type.MISS) {
                    colour = skyColour(level, dir.y);
                    if (centre) scene.centre = "open air";
                } else {
                    BlockPos pos = hit.getBlockPos();
                    BlockState state = level.getBlockState(pos);
                    double dist = eye.distanceTo(hit.getLocation());
                    colour = shade(baseColour(level, state, pos), hit.getDirection(), dist, range);
                    String name = blockName(state);
                    scene.count(name);
                    if (centre) {
                        scene.centre = name + " " + fmt(dist) + " blocks away";
                        scene.centrePos = pos;
                        scene.centreFace = hit.getDirection();
                    }
                }
                if (pixels != null) pixels[py * w + px] = colour;
            }
        }

        // Entities are drawn on top of the block raster, projected with the same camera.
        List<VisibleEntity> visible = projectEntities(level, bot, eye, forward, right, up,
                tanH, tanV, range, w, h, pixels);

        if (pixels != null) drawCrosshair(pixels, w, h);

        byte[] png = pixels == null ? new byte[0] : Png.encode(pixels, w, h);
        String text = describe(level, bot, scene, visible, range);
        return new Snapshot(w, h, png, text);
    }

    // ── colours ──────────────────────────────────────────────────────────────────

    private static int baseColour(ServerLevel level, BlockState state, BlockPos pos) {
        try {
            int col = state.getMapColor(level, pos).col;
            if (col != 0) return col;
        } catch (Exception ignored) {
            // A modded block with an unusual map colour must never break a look.
        }
        return 0x7F7F7F;
    }

    /** Face shading + distance fade, so the picture reads as a 3-D scene, not a blob. */
    private static int shade(int rgb, Direction face, double dist, double range) {
        double light = switch (face == null ? Direction.UP : face) {
            case UP -> 1.0;
            case DOWN -> 0.55;
            case NORTH, SOUTH -> 0.86;
            default -> 0.72;
        };
        // Fade toward a soft haze with distance, the way fog does in-game.
        double fog = Math.max(0.25, 1.0 - (dist / range) * 0.75);
        double f = light * fog;
        int r = (int) (((rgb >> 16) & 0xFF) * f);
        int g = (int) (((rgb >> 8) & 0xFF) * f);
        int b = (int) ((rgb & 0xFF) * f);
        return (clamp(r, 0, 255) << 16) | (clamp(g, 0, 255) << 8) | clamp(b, 0, 255);
    }

    /** Sky, or cave black when there's no sky above the bot. */
    private static int skyColour(ServerLevel level, double dirY) {
        long t = level.getOverworldClockTime() % 24000L;
        boolean night = t >= 13000 && t < 23000;
        if (dirY < -0.15) return night ? 0x0A0A12 : 0x2B2B33;   // looking down into the void/dark
        if (night) return dirY > 0.4 ? 0x05060F : 0x0C0E1C;
        return dirY > 0.4 ? 0x5A8CD6 : 0x9FC4F0;
    }

    // ── entity overlay ───────────────────────────────────────────────────────────

    /** One entity the bot can see, with where it is on screen and in the world. */
    public record VisibleEntity(String name, String kind, double distance, String bearing) {}

    private static List<VisibleEntity> projectEntities(ServerLevel level, AiAssistantEntity bot,
                                                       Vec3 eye, Vec3 forward, Vec3 right, Vec3 up,
                                                       double tanH, double tanV, double range,
                                                       int w, int h, int[] pixels) {
        List<VisibleEntity> out = new ArrayList<>();
        AABB box = AABB.ofSize(bot.position(), range * 2, Math.min(range, 32) * 2, range * 2);
        List<Entity> entities = level.getEntities(bot, box, e -> e.isAlive());
        entities.sort(Comparator.comparingDouble(e -> e.distanceToSqr(bot)));

        for (Entity e : entities) {
            if (out.size() >= 12) break;
            Vec3 v = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
            double depth = v.dot(forward);
            if (depth < 0.4 || depth > range) continue;

            double sx = v.dot(right) / (depth * tanH);
            double sy = v.dot(up) / (depth * tanV);
            boolean onScreen = sx >= -1.2 && sx <= 1.2 && sy >= -1.2 && sy <= 1.2;
            if (!onScreen) continue;

            String kind = kindOf(e);
            double dist = Math.sqrt(e.distanceToSqr(bot));
            out.add(new VisibleEntity(displayName(e), kind, dist, bearing(sx)));

            if (pixels != null) {
                int cx = (int) ((sx + 1) / 2 * w);
                int cy = (int) ((1 - sy) / 2 * h);
                int size = clamp((int) (Math.max(1.0, e.getBbHeight()) * h / (depth * 2 * tanV) / 2), 1, h / 2);
                fillRect(pixels, w, h, cx, cy, Math.max(1, size / 2), size, markerColour(kind));
            }
        }
        return out;
    }

    private static String kindOf(Entity e) {
        if (e instanceof AiAssistantEntity) return "companion";
        if (e instanceof Player) return "player";
        if (e instanceof Monster) return "hostile";
        if (e instanceof ItemEntity) return "item";
        if (e instanceof LivingEntity) return "animal";
        return "object";
    }

    private static int markerColour(String kind) {
        return switch (kind) {
            case "hostile" -> 0xE03030;
            case "player" -> 0x4090FF;
            case "companion" -> 0x40E060;
            case "item" -> 0xF2D24B;
            case "animal" -> 0xE7B489;
            default -> 0xCCCCCC;
        };
    }

    private static String displayName(Entity e) {
        if (e instanceof ItemEntity item) {
            return item.getItem().getCount() + "× " + item.getItem().getDisplayName().getString();
        }
        if (e instanceof Player p) return p.getName().getString();
        return e.getType().toShortString();
    }

    /** Rough on-screen bearing, so the written scene matches the picture. */
    private static String bearing(double sx) {
        if (sx < -0.45) return "to my left";
        if (sx > 0.45) return "to my right";
        return "straight ahead";
    }

    private static void fillRect(int[] px, int w, int h, int cx, int cy, int halfW, int halfH, int colour) {
        for (int y = cy - halfH; y <= cy + halfH; y++) {
            if (y < 0 || y >= h) continue;
            for (int x = cx - halfW; x <= cx + halfW; x++) {
                if (x < 0 || x >= w) continue;
                px[y * w + x] = colour;
            }
        }
    }

    /** A small white cross dead centre: "this is what you would hit if you clicked". */
    private static void drawCrosshair(int[] px, int w, int h) {
        int cx = w / 2, cy = h / 2;
        for (int d = -2; d <= 2; d++) {
            if (d == 0) continue;
            if (cx + d >= 0 && cx + d < w) px[cy * w + cx + d] = 0xFFFFFF;
            if (cy + d >= 0 && cy + d < h) px[(cy + d) * w + cx] = 0xFFFFFF;
        }
    }

    // ── the written scene ────────────────────────────────────────────────────────

    private static final class Scene {
        final Map<String, Integer> blocks = new HashMap<>();
        String centre = "nothing (too far away)";
        BlockPos centrePos;
        Direction centreFace;

        void count(String name) {
            blocks.merge(name, 1, Integer::sum);
        }
    }

    private static String describe(ServerLevel level, AiAssistantEntity bot, Scene scene,
                                   List<VisibleEntity> visible, double range) {
        BlockPos pos = bot.blockPosition();
        StringBuilder sb = new StringBuilder();
        sb.append("I am at ").append(pos.getX()).append(",").append(pos.getY()).append(",").append(pos.getZ())
          .append(", facing ").append(compass(bot.getYRot()))
          .append(" (yaw ").append((int) bot.getYRot()).append(", pitch ").append((int) bot.getXRot()).append(").\n");
        sb.append("Health ").append((int) bot.getHealth()).append("/").append((int) bot.getMaxHealth());
        long t = level.getOverworldClockTime() % 24000L;
        sb.append(". It is ").append(t >= 13000 && t < 23000 ? "night" : "daytime");
        sb.append(", light here ").append(level.getMaxLocalRawBrightness(pos)).append("/15.\n");

        sb.append("Crosshair is on: ").append(scene.centre);
        if (scene.centrePos != null) {
            sb.append(" at ").append(scene.centrePos.getX()).append(",")
              .append(scene.centrePos.getY()).append(",").append(scene.centrePos.getZ());
            if (scene.centreFace != null) sb.append(" (").append(scene.centreFace.getName()).append(" face)");
        }
        sb.append(".\n");

        List<Map.Entry<String, Integer>> top = new ArrayList<>(scene.blocks.entrySet());
        top.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        if (!top.isEmpty()) {
            sb.append("In view: ");
            for (int i = 0; i < Math.min(8, top.size()); i++) {
                if (i > 0) sb.append(", ");
                sb.append(top.get(i).getKey());
            }
            sb.append(".\n");
        }

        BlockState ground = level.getBlockState(pos.below());
        sb.append("Standing on ").append(blockName(ground)).append(".\n");

        if (visible.isEmpty()) {
            sb.append("No creatures or items in sight.\n");
        } else {
            sb.append("I can see: ");
            for (int i = 0; i < visible.size(); i++) {
                VisibleEntity v = visible.get(i);
                if (i > 0) sb.append("; ");
                sb.append(v.name()).append(" (").append(v.kind()).append(") ")
                  .append(fmt(v.distance())).append(" blocks ").append(v.bearing());
            }
            sb.append(".\n");
        }
        return sb.toString();
    }

    /** The block's registry path, e.g. {@code oak_log} — how a player would name it. */
    public static String blockName(BlockState state) {
        try {
            Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            return id == null ? "unknown" : id.getPath();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** Compass word for a yaw, so the description reads the way a player talks. */
    public static String compass(float yaw) {
        float y = (yaw % 360 + 360) % 360;
        if (y < 22.5 || y >= 337.5) return "south";
        if (y < 67.5) return "south-west";
        if (y < 112.5) return "west";
        if (y < 157.5) return "north-west";
        if (y < 202.5) return "north";
        if (y < 247.5) return "north-east";
        if (y < 292.5) return "east";
        return "south-east";
    }

    private static String fmt(double d) {
        return String.valueOf(Math.round(d * 10) / 10.0);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
