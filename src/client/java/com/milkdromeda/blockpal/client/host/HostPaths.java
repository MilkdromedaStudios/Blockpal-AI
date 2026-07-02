package com.milkdromeda.blockpal.client.host;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Everything the in-mod server host writes lives under one managed folder,
 * {@code <game dir>/blockpal-host/}, so it's easy to find, back up, or delete.
 */
final class HostPaths {

    private HostPaths() {}

    static final Path ROOT = FabricLoader.getInstance().getGameDir().resolve("blockpal-host");

    static Path serverDir()      { return ROOT.resolve("server"); }
    static Path modsDir()        { return serverDir().resolve("mods"); }
    static Path serverJar()      { return serverDir().resolve("server.jar"); }
    static Path fabricLauncher() { return serverDir().resolve("fabric-server-launch.jar"); }

    static Path tunnelDir()      { return ROOT.resolve("tunnel"); }
    static Path playitBinary() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return tunnelDir().resolve(windows ? "playit.exe" : "playit");
    }
}
