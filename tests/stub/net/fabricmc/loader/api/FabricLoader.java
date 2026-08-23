package net.fabricmc.loader.api;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Test stub. Must be an INTERFACE, not a class: ModConfig was compiled against the real
 * FabricLoader interface, so its call site is an InterfaceMethodref and a class stub
 * fails at link time with IncompatibleClassChangeError.
 */
public interface FabricLoader {
    static FabricLoader getInstance() {
        return new FabricLoader() {};
    }
    default Path getConfigDir() {
        return Paths.get(System.getProperty("blockpal.test.config", "/tmp/blockpal-cfgtest"));
    }
    default boolean isModLoaded(String id) { return false; }
}
