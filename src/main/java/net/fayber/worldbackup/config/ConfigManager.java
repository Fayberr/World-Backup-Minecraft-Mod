package net.fayber.worldbackup.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.fayber.worldbackup.WorldBackupMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("worldbackup.json");
    private static BackupConfig config = new BackupConfig();

    public static BackupConfig get() {
        return config;
    }

    public static void load() {
        if (Files.exists(CONFIG_FILE)) {
            try {
                BackupConfig loaded = GSON.fromJson(Files.readString(CONFIG_FILE), BackupConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (Exception e) {
                WorldBackupMod.LOGGER.warn("Could not read {}, using defaults", CONFIG_FILE, e);
            }
        }
        sanitize(config);
        save();
    }

    public static void save() {
        sanitize(config);
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            Files.writeString(CONFIG_FILE, GSON.toJson(config));
        } catch (IOException e) {
            WorldBackupMod.LOGGER.error("Could not write {}", CONFIG_FILE, e);
        }
    }

    private static void sanitize(BackupConfig c) {
        c.backupIntervalMinutes = Math.max(0, c.backupIntervalMinutes);
        c.compressionLevel = Math.max(0, Math.min(9, c.compressionLevel));
        c.maxBackupsToKeep = Math.max(0, c.maxBackupsToKeep);
        if (c.backupDirectory == null || c.backupDirectory.isBlank()) {
            c.backupDirectory = "backups";
        }
    }
}
