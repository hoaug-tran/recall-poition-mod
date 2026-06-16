package com.hoaug.recall;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class RecallConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "recall_potion.json");

    public int setHomeCooldownSeconds = 11160; // 3 hours 6 minutes
    public double tpaCost = 20000.0;
    public int htpTimeoutSeconds = 30; // Request expiry
    public int htpCooldownSeconds = 30; // Command cooldown

    public static RecallConfig INSTANCE = new RecallConfig();

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, RecallConfig.class);
            } catch (IOException e) {
                RecallMod.LOGGER.error("Failed to load config", e);
            }
        } else {
            save();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            RecallMod.LOGGER.error("Failed to save config", e);
        }
    }
}
