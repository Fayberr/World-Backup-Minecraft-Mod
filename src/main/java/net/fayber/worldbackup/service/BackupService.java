package net.fayber.worldbackup.service;

import net.fabricmc.loader.api.FabricLoader;
import net.fayber.worldbackup.WorldBackupMod;
import net.fayber.worldbackup.config.BackupConfig;
import net.fayber.worldbackup.config.ConfigManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One instance per running server (integrated or dedicated). A backup has two steps: flush the
 * world and copy it to a snapshot folder on the server thread, so nothing can write to the
 * region files mid-copy, then zip the snapshot on a worker thread.
 */
public class BackupService {
    private static BackupService current;

    public static BackupService current() {
        return current;
    }

    public static void start(MinecraftServer server) {
        current = new BackupService(server);
    }

    public static void stop() {
        if (current != null) {
            current.shutdown();
            current = null;
        }
    }

    public record LastRun(String fileName, long sizeBytes, long durationMillis, long finishedAtMillis) {}

    private final MinecraftServer server;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "WorldBackup-Worker");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean cancelled = false;
    private volatile long lastStartMillis = System.currentTimeMillis();
    private volatile long runningSinceMillis = 0;
    private volatile String runningLabel = "";
    private volatile LastRun lastRun;
    private int tickCounter = 0;

    private BackupService(MinecraftServer server) {
        this.server = server;
    }

    public boolean isRunning() {
        return running.get();
    }

    public String runningLabel() {
        return runningLabel;
    }

    public long runningForMillis() {
        return running.get() ? System.currentTimeMillis() - runningSinceMillis : 0;
    }

    public LastRun lastRun() {
        return lastRun;
    }

    /** Milliseconds until the next scheduled backup, or -1 when scheduling is off. */
    public long millisUntilNext() {
        BackupConfig config = ConfigManager.get();
        if (!config.enabled || config.backupIntervalMinutes <= 0) {
            return -1;
        }
        return Math.max(0, lastStartMillis + config.backupIntervalMinutes * 60_000L - System.currentTimeMillis());
    }

    public boolean pausedForEmptyServer() {
        return ConfigManager.get().pauseIfNoPlayers && server.getPlayerList().getPlayerCount() == 0;
    }

    public Path backupFolder() {
        return resolveBackupRoot().resolve(sanitize(worldFolderName()));
    }

    public void tick() {
        if (++tickCounter < 20) {
            return;
        }
        tickCounter = 0;
        if (running.get() || millisUntilNext() != 0 || pausedForEmptyServer()) {
            return;
        }
        triggerBackup("auto");
    }

    /** Must be called on the server thread. Returns false if a backup was already running. */
    public boolean triggerBackup(String label) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        BackupConfig config = ConfigManager.get();
        long start = System.currentTimeMillis();
        lastStartMillis = start;
        runningSinceMillis = start;
        runningLabel = label;
        cancelled = false;

        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        String worldName = worldFolderName();
        Path folder = backupFolder();
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String zipName = sanitize(worldName) + "_" + stamp + (label.equals("auto") ? "" : "_" + label) + ".zip";
        Path zip = folder.resolve(zipName);
        Path snapshot = folder.resolve(".snapshot-" + stamp);

        notify("Starting world backup (" + label + ")...");

        try {
            Files.createDirectories(folder);
            deleteLeftovers(folder);
            // Flush pending chunk writes first. The server thread stays blocked until the copy
            // is done, so no region file can change underneath it.
            server.saveEverything(true, true, false);
            copyWorld(world, snapshot, folder);
        } catch (Exception e) {
            WorldBackupMod.LOGGER.error("Backup snapshot failed", e);
            deleteTree(snapshot);
            running.set(false);
            notify("Backup failed while copying the world: " + e.getMessage());
            return true;
        }
        long snapshotMillis = System.currentTimeMillis() - start;

        int level = config.compressionLevel;
        int keep = config.maxBackupsToKeep;
        worker.submit(() -> {
            try {
                ZipArchiver.createZip(snapshot, zip, worldName, level, () -> cancelled);
                long size = Files.size(zip);
                long elapsed = System.currentTimeMillis() - start;
                lastRun = new LastRun(zipName, size, elapsed, System.currentTimeMillis());
                WorldBackupMod.LOGGER.info("Backup {} finished in {} ms ({} ms on the server thread)", zipName, elapsed, snapshotMillis);
                notify("Backup finished in " + (elapsed / 1000) + "s, " + formatSize(size) + ".");
                prune(folder, keep);
            } catch (ZipArchiver.Cancelled e) {
                WorldBackupMod.LOGGER.info("Backup cancelled because the server is stopping");
            } catch (Exception e) {
                WorldBackupMod.LOGGER.error("Backup failed", e);
                notify("Backup failed: " + e.getMessage());
            } finally {
                deleteTree(snapshot);
                running.set(false);
            }
        });
        return true;
    }

    /** Called when the server is stopping: abort the zip and wait for it to clean up. */
    private void shutdown() {
        cancelled = true;
        worker.shutdown();
        try {
            if (!worker.awaitTermination(15, TimeUnit.SECONDS)) {
                WorldBackupMod.LOGGER.warn("Backup worker did not stop within 15 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record Stored(int count, long totalBytes, Path newest, long newestModified) {}

    public Stored stored() {
        List<Path> zips = listZips(backupFolder());
        long total = 0;
        Path newest = null;
        long newestTime = 0;
        for (Path p : zips) {
            try {
                total += Files.size(p);
                long t = Files.getLastModifiedTime(p).toMillis();
                if (newest == null || t > newestTime) {
                    newest = p;
                    newestTime = t;
                }
            } catch (IOException ignored) {
                // file vanished between the listing and the stat
            }
        }
        return new Stored(zips.size(), total, newest, newestTime);
    }

    public boolean canManage(ServerPlayer player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                || server.isSingleplayerOwner(player.nameAndId());
    }

    private void notify(String message) {
        String text = "[World Backup] " + message;
        WorldBackupMod.LOGGER.info(message);
        BackupConfig config = ConfigManager.get();
        if (!config.notifyPlayers) {
            return;
        }
        server.execute(() -> {
            Component component = Component.literal(text);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!config.notifyOpsOnly || canManage(player)) {
                    player.sendSystemMessage(component);
                }
            }
        });
    }

    private String worldFolderName() {
        Path name = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
        return name == null ? "world" : name.toString();
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private Path resolveBackupRoot() {
        Path p = Path.of(ConfigManager.get().backupDirectory);
        if (!p.isAbsolute()) {
            p = FabricLoader.getInstance().getGameDir().resolve(p);
        }
        return p.toAbsolutePath().normalize();
    }

    private static void copyWorld(Path world, Path snapshot, Path skipDir) throws IOException {
        Path skip = skipDir.toAbsolutePath().normalize();
        Files.walkFileTree(world, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                // The backup folder can sit inside the world folder. Do not archive the archives.
                if (dir.toAbsolutePath().normalize().startsWith(skip)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(snapshot.resolve(world.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString().equals("session.lock")) {
                    return FileVisitResult.CONTINUE;
                }
                Files.copy(file, snapshot.resolve(world.relativize(file).toString()),
                        StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                if (exc instanceof NoSuchFileException) {
                    return FileVisitResult.CONTINUE;
                }
                throw exc;
            }
        });
    }

    private static void deleteLeftovers(Path folder) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                if (name.startsWith(".snapshot-")) {
                    deleteTree(p);
                } else if (name.endsWith(".zip.tmp")) {
                    Files.deleteIfExists(p);
                }
            }
        } catch (IOException e) {
            WorldBackupMod.LOGGER.warn("Could not clean old temporary files in {}", folder, e);
        }
    }

    private static void deleteTree(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.deleteIfExists(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            WorldBackupMod.LOGGER.warn("Could not delete {}", dir, e);
        }
    }

    private static List<Path> listZips(Path folder) {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            return out;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*.zip")) {
            for (Path p : stream) {
                out.add(p);
            }
        } catch (IOException e) {
            WorldBackupMod.LOGGER.warn("Could not list {}", folder, e);
        }
        return out;
    }

    private static void prune(Path folder, int keep) {
        if (keep <= 0) {
            return;
        }
        List<Path> zips = listZips(folder);
        if (zips.size() <= keep) {
            return;
        }
        zips.sort(Comparator.comparingLong(p -> {
            try {
                return Files.getLastModifiedTime(p).toMillis();
            } catch (IOException e) {
                return 0L;
            }
        }));
        for (int i = 0; i < zips.size() - keep; i++) {
            try {
                Files.deleteIfExists(zips.get(i));
                WorldBackupMod.LOGGER.info("Deleted old backup {}", zips.get(i).getFileName());
            } catch (IOException e) {
                WorldBackupMod.LOGGER.warn("Could not delete {}", zips.get(i), e);
            }
        }
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024 * 1024) {
            return Math.max(1, bytes / 1024) + " KB";
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    public static String formatDuration(long millis) {
        long s = millis / 1000;
        if (s < 60) {
            return s + "s";
        }
        long m = s / 60;
        if (m < 60) {
            return m + "m " + (s % 60) + "s";
        }
        return (m / 60) + "h " + (m % 60) + "m";
    }

    public static String formatAgo(long timestampMillis) {
        return formatDuration(Math.max(0, System.currentTimeMillis() - timestampMillis)) + " ago";
    }

}
