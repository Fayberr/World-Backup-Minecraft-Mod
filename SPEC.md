# World Backup Mod Specification & Architecture

## Overview
**World Backup** is a lightweight, highly configurable backup mod for Minecraft 26.1.x, 26.2 and 26.3. It runs on both **Singleplayer** (integrated server) and **Dedicated Servers**, providing automated non-blocking world backups, configurable compression, retention policies, and in-game commands.

---

## 1. Core Requirements

1. **Environment & Loader**:
   * Fabric on MC 26.1.x and 26.2.
   * `"environment": "*"` (both singleplayer client and dedicated server supported).
2. **Consistent, low-impact backups**:
   * Step 1 (server thread): flush pending chunk writes with `saveEverything(true, true, false)` and copy the world folder to a `.snapshot-*` folder next to the backups. The server thread is blocked during the copy, so no region file can change mid-copy. `noSave` is not used: it only stops the periodic autosave, not writes from unloading chunks.
   * Step 2 (worker thread): zip the snapshot to `<name>.zip.tmp`, rename to `.zip` when complete, delete the snapshot. Entries sit under a `<world>/` root folder.
   * On server stop the zip is cancelled, the temp files are removed, and no partial `.zip` is left.
   * The tick stall is the time to copy the world once. Large worlds (several GB) stall for seconds.
3. **High Configurability**:
   * **Interval / Delay**: Automatic periodic backups (e.g. every 30, 60, or 120 minutes). 0 disables auto-backups.
   * **Compression**: Configurable Deflater ZIP compression level (0 = store only / fastest, 1 = fast, 6 = default balanced, 9 = ultra).
   * **Retention & Pruning**: Configurable `maxBackupsToKeep` (e.g. 10). Automatically deletes oldest backups when limit is exceeded.
   * **Pause on Idle**: Pauses automatic scheduled backups when 0 players are connected to dedicated servers.
   * **Custom Destination**: `backupDirectory` (default `backups`), relative paths resolve against the game or server folder, backups go to `<dir>/<world folder name>/`.
   * **Player Notifications**: Configurable chat broadcasts (notify ops, notify all, or silent).
4. **Commands** (operators, and the singleplayer host even with cheats off; LAN guests cannot use them):
   * `/backup create [name]`: immediate backup.
   * `/backup status`: running state, schedule and time to next backup, last backup (age, duration), stored count and size, folder.
   * `/backup list` and `/backup config` are not implemented.
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
    public int maxBackupsToKeep = 10;        // 0 = keep everything
    public boolean pauseIfNoPlayers = true;  // Pause scheduler if server is empty
    public String backupDirectory = "backups"; // Storage root path
    public boolean notifyPlayers = true;     // Broadcast chat messages on start/finish
    public boolean notifyOpsOnly = false;    // Restrict messages to operators
}
```

---

## 4. Build & Compatibility

* **Target MC Versions**: `26.1.x-fabric`, `26.2-fabric` and `26.3-fabric` (only 26.3 has the rewrite)
* **Java Target**: Java 25 (`options.release = 25`)
* **Loom**: `1.17.19` (unobfuscated 26.x Mojang mappings)
* **License**: GPL-3.0-or-later, Copyright 2026 Fayber.
