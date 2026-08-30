# World Backup Mod Specification & Architecture

## Overview
**World Backup** is a lightweight, highly configurable backup mod for Minecraft 26.1.x and 26.2. It runs on both **Singleplayer** (integrated server) and **Dedicated Servers**, providing automated non-blocking world backups, configurable compression, retention policies, and in-game commands.

---

## 1. Core Requirements

1. **Environment & Loader**:
   * Fabric on MC 26.1.x and 26.2.
   * `"environment": "*"` (both singleplayer client and dedicated server supported).
2. **Non-Blocking / Asynchronous Architecture**:
   * **Phase 1 (Sync Game Thread)**: Flushes player data and world chunks to disk via `MinecraftServer.saveEverything(true, true, true)` / `ServerLevel.save()`.
   * **Phase 2 (Async Background Thread)**: Safely snapshots world files into compressed archives (`.zip`) using a dedicated daemon thread (`ScheduledExecutorService` / `ForkJoinPool`), preventing tick lag and game stutter.
3. **High Configurability**:
   * **Interval / Delay**: Automatic periodic backups (e.g. every 30, 60, or 120 minutes). 0 disables auto-backups.
   * **Compression**: Configurable Deflater ZIP compression level (0 = store only / fastest, 1 = fast, 6 = default balanced, 9 = ultra).
   * **Retention & Pruning**: Configurable `maxBackupsToKeep` (e.g. 10). Automatically deletes oldest backups when limit is exceeded.
   * **Pause on Idle**: Pauses automatic scheduled backups when 0 players are connected to dedicated servers.
   * **Custom Destination**: Configurable backup storage path (default: `./backups/<world_name>/`).
   * **Player Notifications**: Configurable chat broadcasts (notify ops, notify all, or silent).
4. **Commands**:
   * `/backup create [custom_name]` — Triggers an immediate manual backup.
   * `/backup list` — Displays recent backups with date, size, and status.
   * `/backup status` — Shows next scheduled backup time and storage usage.
   * `/backup config <key> [value]` — Get or set configuration values live.
5. **GUI & Compatibility**:
   * ModMenu & Cloth Config GUI for Singleplayer.
   * Fallback in-game config command for dedicated servers.

---

## 2. Technical Architecture

### Package Structure (`net.fayber.worldbackup`)

```
net.fayber.worldbackup
├── WorldBackupMod.java               # ModInitializer entrypoint (server & SP lifecycle)
├── config/
│   ├── BackupConfig.java             # Configuration data model
│   └── ConfigManager.java            # JSON persistence (config/worldbackup.json)
├── service/
│   ├── BackupService.java            # Scheduler, save flush coordinator & pruning engine
│   └── ZipArchiver.java              # Asynchronous file stream zipper with compression tuning
├── command/
│   └── BackupCommands.java           # Command registration (/backup ...)
└── compat/
    ├── WorldBackupModMenu.java       # ModMenu API integration
    └── WorldBackupClothScreen.java   # Cloth Config GUI for Singleplayer
```

---

## 3. Data Models

### `BackupConfig.java`
```java
public class BackupConfig {
    public boolean enabled = true;
    public int backupIntervalMinutes = 60;   // 0 = manual only
    public int compressionLevel = 6;         // 0 (none) to 9 (maximum)
    public int maxBackupsToKeep = 10;        // Max archives before pruning oldest
    public boolean pauseIfNoPlayers = true;  // Pause scheduler if server is empty
    public String backupDirectory = "backups"; // Storage root path
    public boolean notifyPlayers = true;     // Broadcast chat messages on start/finish
    public boolean notifyOpsOnly = false;    // Restrict messages to operators
}
```

---

## 4. Build & Compatibility

* **Target MC Versions**: `26.1.x-fabric` and `26.2-fabric`
* **Java Target**: Java 25 (`options.release = 25`)
* **Loom**: `1.17.19` (unobfuscated 26.x Mojang mappings)
* **License**: GPL-3.0-or-later, Copyright 2026 Fayber.
