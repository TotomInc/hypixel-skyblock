package dev.totominc.skyblock.client.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import dev.totominc.skyblock.SkyblockMod;

public final class SkyblockConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("hypixelskyblock.json");

	private static SkyblockConfig instance = new SkyblockConfig();

	public boolean enderNodeHelper = true;
	public boolean debugForceTheEnd = false;
	public float[] frameColor = {0.086f, 0.612f, 0.612f};
	public float fillAlpha = 0.28f;
	public float frameAlpha = 0.95f;
	public float frameThickness = 0.04f;

	public static SkyblockConfig get() {
		return instance;
	}

	public boolean toggleEnderNodeHelper() {
		enderNodeHelper = !enderNodeHelper;
		return enderNodeHelper;
	}

	public static void load() {
		if (!Files.isRegularFile(PATH)) {
			save();
			return;
		}

		try (var reader = Files.newBufferedReader(PATH)) {
			SkyblockConfig loaded = GSON.fromJson(reader, SkyblockConfig.class);

			if (loaded != null) {
				instance = loaded;
			}
		} catch (IOException exception) {
			SkyblockMod.LOGGER.warn("Failed to read config, using defaults", exception);
		}
	}

	public static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(instance));
		} catch (IOException exception) {
			SkyblockMod.LOGGER.warn("Failed to write config", exception);
		}
	}
}
