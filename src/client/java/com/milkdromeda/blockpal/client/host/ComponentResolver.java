package com.milkdromeda.blockpal.client.host;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Resolves the download URLs for everything a Bedrock-capable Java server needs,
 * straight from the <b>official</b> metadata endpoints (so the host always gets
 * the latest builds, matching "use the latest Geyser"):
 *
 * <ul>
 *   <li>Minecraft dedicated server jar — Mojang piston manifest (with SHA-1).</li>
 *   <li>Fabric server launcher — FabricMC meta.</li>
 *   <li>Fabric API — Modrinth (required by Geyser-Fabric / Floodgate-Fabric).</li>
 *   <li>Geyser-Fabric &amp; Floodgate-Fabric — GeyserMC download API (latest build).</li>
 * </ul>
 *
 * All URLs are centralised here so they're easy to audit and update.
 */
final class ComponentResolver {

    private ComponentResolver() {}

    /** The Minecraft version this Blockpal build targets (keep in sync with gradle.properties). */
    static final String MC_VERSION = "26.2";

    record Artifact(String url, String sha1) {}

    /** The vanilla dedicated-server jar for {@link #MC_VERSION}, with its SHA-1. */
    static Artifact mojangServer() throws Exception {
        JsonObject manifest = JsonParser.parseString(Http.getString(
                "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")).getAsJsonObject();
        String pkgUrl = null;
        for (JsonElement el : manifest.getAsJsonArray("versions")) {
            JsonObject v = el.getAsJsonObject();
            if (MC_VERSION.equals(v.get("id").getAsString())) {
                pkgUrl = v.get("url").getAsString();
                break;
            }
        }
        if (pkgUrl == null) throw new IllegalStateException("Minecraft " + MC_VERSION + " not found in Mojang manifest");
        JsonObject pkg = JsonParser.parseString(Http.getString(pkgUrl)).getAsJsonObject();
        JsonObject server = pkg.getAsJsonObject("downloads").getAsJsonObject("server");
        return new Artifact(server.get("url").getAsString(), server.get("sha1").getAsString());
    }

    /** The runnable Fabric server-launcher jar for {@link #MC_VERSION}. */
    static String fabricServerLauncherUrl() throws Exception {
        return "https://meta.fabricmc.net/v2/versions/loader/" + MC_VERSION + "/"
                + latestFabricLoader() + "/" + latestFabricInstaller() + "/server/jar";
    }

    private static String latestFabricLoader() throws Exception {
        JsonArray arr = JsonParser.parseString(Http.getString(
                "https://meta.fabricmc.net/v2/versions/loader/" + MC_VERSION)).getAsJsonArray();
        for (JsonElement el : arr) {
            JsonObject loader = el.getAsJsonObject().getAsJsonObject("loader");
            if (loader.get("stable").getAsBoolean()) return loader.get("version").getAsString();
        }
        if (arr.size() > 0) return arr.get(0).getAsJsonObject().getAsJsonObject("loader").get("version").getAsString();
        throw new IllegalStateException("No Fabric loader available for " + MC_VERSION);
    }

    private static String latestFabricInstaller() throws Exception {
        JsonArray arr = JsonParser.parseString(Http.getString(
                "https://meta.fabricmc.net/v2/versions/installer")).getAsJsonArray();
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            if (o.get("stable").getAsBoolean()) return o.get("version").getAsString();
        }
        if (arr.size() > 0) return arr.get(0).getAsJsonObject().get("version").getAsString();
        throw new IllegalStateException("No Fabric installer version available");
    }

    /** The Fabric API jar matching {@link #MC_VERSION}, from Modrinth. */
    static String fabricApiUrl() throws Exception {
        String body = Http.getString("https://api.modrinth.com/v2/project/fabric-api/version"
                + "?game_versions=%5B%22" + MC_VERSION + "%22%5D&loaders=%5B%22fabric%22%5D");
        JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
        if (arr.isEmpty()) throw new IllegalStateException("No Fabric API build for " + MC_VERSION);
        JsonArray files = arr.get(0).getAsJsonObject().getAsJsonArray("files");
        for (JsonElement el : files) {
            JsonObject f = el.getAsJsonObject();
            if (f.has("primary") && f.get("primary").getAsBoolean()) return f.get("url").getAsString();
        }
        return files.get(0).getAsJsonObject().get("url").getAsString();
    }

    /** Latest Geyser build for the Fabric platform (the URL 302-redirects to the jar). */
    static String geyserFabricUrl() {
        return "https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/fabric";
    }

    /** Latest Floodgate build for the Fabric platform (lets Bedrock players join without a Java account). */
    static String floodgateFabricUrl() {
        return "https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/fabric";
    }
}
