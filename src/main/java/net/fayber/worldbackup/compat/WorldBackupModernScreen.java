package net.fayber.worldbackup.compat;

import net.fayber.modernconfig.api.ModernConfigScreen;
import net.fayber.worldbackup.config.BackupConfig;
import net.fayber.worldbackup.config.ConfigManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Modern Config version of the settings screen. Only loaded when Modern Config is installed. */
public final class WorldBackupModernScreen {
    private WorldBackupModernScreen() {}

    public static Screen create(Screen parent) {
        BackupConfig c = ConfigManager.get();
        return ModernConfigScreen.builder(Component.literal("World Backup Settings"), parent, ConfigManager::save)

                .category("Schedule")
                .bool("Enable Backups", () -> c.enabled, v -> c.enabled = v)
                .tooltip("Turns automatic backups on or off. Manual /backup create always works.")
                .intSlider("Backup Interval (Minutes)", () -> c.backupIntervalMinutes, v -> c.backupIntervalMinutes = v, 0, 720, 5)
                .tooltip("Minutes between automatic backups. 0 means manual backups only.")
                .bool("Pause When Empty", () -> c.pauseIfNoPlayers, v -> c.pauseIfNoPlayers = v)
                .tooltip("Skip automatic backups while no players are online.")

                .category("Storage")
                .intSlider("Compression Level", () -> c.compressionLevel, v -> c.compressionLevel = v, 0, 9, 1)
                .tooltip("0 stores files uncompressed and is fastest. 9 is smallest and slowest.")
                .intSlider("Max Backups to Keep", () -> c.maxBackupsToKeep, v -> c.maxBackupsToKeep = v, 0, 100, 1)
                .tooltip("Oldest backups are deleted past this number. 0 keeps everything.")
                .text("Backup Folder", () -> c.backupDirectory, v -> c.backupDirectory = v, 256)
                .tooltip("Relative paths are inside the game or server folder. Each world gets its own subfolder.")

                .category("Messages")
                .bool("Chat Notifications", () -> c.notifyPlayers, v -> c.notifyPlayers = v)
                .tooltip("Send a chat message when a backup starts and finishes.")
                .bool("Ops Only", () -> c.notifyOpsOnly, v -> c.notifyOpsOnly = v)
                .tooltip("Send those messages only to operators and the singleplayer host.")

                .build();
    }
}
