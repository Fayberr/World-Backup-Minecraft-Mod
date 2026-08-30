package net.fayber.worldbackup.service;

import net.fayber.worldbackup.config.BackupConfig;
import net.fayber.worldbackup.config.ConfigManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class BackupService {
    private static final ExecutorService BACKUP_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "WorldBackup-Worker");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicBoolean BACKUP_IN_PROGRESS = new AtomicBoolean(false);
    private static long lastBackupTime = System.currentTimeMillis();

    public static boolean isBackupInProgress() {
        return BACKUP_IN_PROGRESS.get();
    }

    public static void checkScheduledBackup(MinecraftServer server) {
        BackupConfig config = ConfigManager.get();
        if (!config.enabled || config.backupIntervalMinutes <= 0) {
            return;
        }

        if (config.pauseIfNoPlayers && server.getPlayerList().getPlayerCount() == 0) {
            return;
        }

        long intervalMillis = config.backupIntervalMinutes * 60L * 1000L;
        if (System.currentTimeMillis() - lastBackupTime >= intervalMillis) {
            triggerBackup(server, "auto");
        }
    }

    public static void triggerBackup(MinecraftServer server, String label) {
        if (!BACKUP_IN_PROGRESS.compareAndSet(false, true)) {
            broadcastMessage(server, "[WorldBackup] A backup is already in progress!");
            return;
        }

        lastBackupTime = System.currentTimeMillis();
        BackupConfig config = ConfigManager.get();

        if (config.notifyPlayers) {
            broadcastMessage(server, "[WorldBackup] Starting world backup (" + label + ")...");
        }

        // 1. Synchronously flush world data to disk on game thread
        try {
            server.saveEverything(true, true, true);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Path worldPath = server.getWorldPath(LevelResource.ROOT);
        String worldName = worldPath.getFileName().toString();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String zipName = worldName + "_" + timestamp + (label.equals("auto") ? "" : "_" + label) + ".zip";

        File backupDir = new File(config.backupDirectory, worldName);
        File targetZip = new File(backupDir, zipName);

        // 2. Perform compression asynchronously on worker thread
        BACKUP_EXECUTOR.submit(() -> {
            long start = System.currentTimeMillis();
            try {
                ZipArchiver.createZipArchive(worldPath, targetZip, config.compressionLevel);
                long elapsed = System.currentTimeMillis() - start;
                long sizeMb = targetZip.length() / (1024 * 1024);

                if (config.notifyPlayers) {
                    broadcastMessage(server, "[WorldBackup] Backup finished in " + (elapsed / 1000) + "s (Size: " + sizeMb + " MB)");
                }

                pruneOldBackups(backupDir, config.maxBackupsToKeep);
            } catch (Exception e) {
                e.printStackTrace();
                broadcastMessage(server, "[WorldBackup] Backup failed: " + e.getMessage());
            } finally {
                BACKUP_IN_PROGRESS.set(false);
            }
        });
    }

    private static void pruneOldBackups(File backupDir, int maxToKeep) {
        if (maxToKeep <= 0 || !backupDir.exists()) return;

        File[] backups = backupDir.listFiles((dir, name) -> name.endsWith(".zip"));
        if (backups != null && backups.length > maxToKeep) {
            Arrays.sort(backups, Comparator.comparingLong(File::lastModified));
            int toDelete = backups.length - maxToKeep;
            for (int i = 0; i < toDelete; i++) {
                backups[i].delete();
            }
        }
    }

    private static void broadcastMessage(MinecraftServer server, String message) {
        server.execute(() -> {
            server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
        });
    }
}
