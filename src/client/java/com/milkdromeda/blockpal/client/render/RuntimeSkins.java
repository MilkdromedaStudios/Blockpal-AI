package com.milkdromeda.blockpal.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Loads custom skins that the player drops into
 * {@code config/blockpal/skins/}. Any {@code <name>.png} placed there can be
 * applied with {@code /ai skin <name>}; the PNG is loaded and registered as a
 * dynamic texture the first time the renderer asks for it.
 *
 * <p>This is entirely client-side: the skin id is just a string synced from the
 * server, and each client resolves it against its own skins folder.
 */
public final class RuntimeSkins {
    private RuntimeSkins() {}

    /** Folder players drop skin PNGs into. Lives next to {@code config.json}. */
    public static final Path SKIN_DIR = FabricLoader.getInstance()
            .getConfigDir().resolve("blockpal").resolve("skins");

    // sanitized base name -> file on disk (cheap to refresh, no GPU work)
    private static final Map<String, Path> files = new HashMap<>();
    // sanitized base name -> registered texture id (loaded lazily, render thread)
    private static final Map<String, Identifier> registered = new HashMap<>();

    /** Create the folder (with a README) and do a first scan. No GPU work. */
    public static void init() {
        ensureFolder();
        refresh();
    }

    /** (Re)scan the skins folder for {@code *.png} files. */
    public static synchronized void refresh() {
        files.clear();
        if (!Files.isDirectory(SKIN_DIR)) return;
        try (Stream<Path> s = Files.list(SKIN_DIR)) {
            s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .forEach(p -> {
                        String key = sanitize(stripPng(p.getFileName().toString()));
                        if (!key.isEmpty()) files.put(key, p);
                    });
        } catch (IOException ignored) {
            // Folder unreadable — leave the list empty; renderer falls back to Steve.
        }
    }

    /** Drop cached textures and re-scan so edited files reload from disk. */
    public static synchronized void reload() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            for (Identifier id : registered.values()) mc.getTextureManager().release(id);
        }
        registered.clear();
        refresh();
    }

    /** Available custom skin names, sorted, for listing in chat. */
    public static synchronized Set<String> names() {
        return new TreeSet<>(files.keySet());
    }

    /**
     * Resolve a user-supplied skin name to a registered texture id, loading and
     * registering the PNG lazily. Returns {@code null} if there's no such file.
     * Must run on the render thread — it touches the texture manager.
     */
    public static synchronized Identifier textureFor(String name) {
        if (name == null) return null;
        String key = sanitize(stripPng(name));
        Identifier existing = registered.get(key);
        if (existing != null) return existing;
        Path file = files.get(key);
        if (file == null) return null;
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage img = NativeImage.read(in);
            Identifier id = Identifier.fromNamespaceAndPath("blockpal", "dynamic_skins/" + key);
            Minecraft.getInstance().getTextureManager()
                    .register(id, new DynamicTexture(() -> "blockpal/skin/" + key, img));
            registered.put(key, id);
            return id;
        } catch (IOException e) {
            // Corrupt/unsupported PNG — forget it so we don't retry every frame.
            files.remove(key);
            return null;
        }
    }

    private static void ensureFolder() {
        try {
            Files.createDirectories(SKIN_DIR);
            Path readme = SKIN_DIR.resolve("README.txt");
            if (!Files.exists(readme)) Files.writeString(readme, README);
        } catch (IOException ignored) {
            // Non-fatal: without the folder there are simply no custom skins.
        }
    }

    private static String stripPng(String s) {
        return s.toLowerCase(Locale.ROOT).endsWith(".png") ? s.substring(0, s.length() - 4) : s;
    }

    /** Keep only characters valid in a texture path; spaces become underscores. */
    private static String sanitize(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.') b.append(c);
            else if (c == ' ') b.append('_');
        }
        return b.toString();
    }

    private static final String README =
            "Custom Blockpal skins\n"
          + "=========================\n\n"
          + "Drop a 64x64 Minecraft player skin (PNG, slim/classic both fine) into\n"
          + "this folder, then point the bot at it in game:\n\n"
          + "    /ai skin <filename>      (without the .png, e.g. /ai skin pirate)\n\n"
          + "Useful client commands:\n"
          + "    /aiskins list            list the skins found in this folder\n"
          + "    /aiskins reload          re-scan after adding or editing a file\n\n"
          + "Notes:\n"
          + "  * Names are case-insensitive; spaces become underscores.\n"
          + "  * Edited a file? Run /aiskins reload to see the change without a\n"
          + "    full game restart.\n"
          + "  * Built-in skins (no file needed): default, robot, void, slate,\n"
          + "    ember, forest, amethyst.\n";
}
