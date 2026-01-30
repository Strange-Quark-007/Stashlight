package dev.strangequark.stashlight;


import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Init {
    private static Path ROOT;


    public static void init() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            throw new IllegalStateException("Stashlight initialized outside client");
        }

        ROOT = FabricLoader.getInstance()
                .getGameDir()
                .resolve("stashlight_cache");

        try {
            Files.createDirectories(ROOT);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create Stashlight cache dir", e);
        }
    }

    public static Path getFileName() {
        Minecraft client = Minecraft.getInstance();
        String fileName;

        if (client.isSingleplayer()) {
            var server = client.getSingleplayerServer();
            if (server == null) {
                throw new IllegalStateException("Singleplayer server missing");
            }
            Path worldFolder = server.getWorldPath(LevelResource.ROOT).normalize();

            fileName = worldFolder.getFileName().toString()
                    .replace(" ", "_")
                    .replace("(", "_")
                    .replace(")", "_");
        } else {
            var info = client.getCurrentServer();
            if (info == null) {
                throw new IllegalStateException("Multiplayer server info missing");
            }
            fileName = "MP_" + info.ip.replace(':', '_').replace('/', '_');
        }

        return ROOT.resolve(fileName + ".dat");
    }
}