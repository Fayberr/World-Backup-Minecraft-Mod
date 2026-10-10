package net.fayber.worldbackup.compat;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fayber.worldbackup.config.BackupConfig;
import net.fayber.worldbackup.config.ConfigManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class WorldBackupClothScreen {
    private WorldBackupClothScreen() {}

    public static Screen create(Screen parent) {
        BackupConfig config = ConfigManager.get();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("World Backup Settings"))
                .setSavingRunnable(ConfigManager::save);

        ConfigEntryBuilder eb = builder.entryBuilder();

        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));
        general.addEntry(eb.startBooleanToggle(Component.literal("Enable Backups"), config.enabled)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Global enable/disable toggle."))
                .setSaveConsumer(val -> config.enabled = val)
                .build());

        general.addEntry(eb.startIntSlider(Component.literal("Backup Interval (Minutes)"), config.backupIntervalMinutes, 0, 720)
                .setDefaultValue(60)
                .setTooltip(Component.literal("Automatic backup frequency in minutes (0 = manual backups only)."))
                .setSaveConsumer(val -> config.backupIntervalMinutes = val)
                .build());

        general.addEntry(eb.startIntSlider(Component.literal("Compression Level"), config.compressionLevel, 0, 9)
                .setDefaultValue(6)
                .setTooltip(Component.literal("ZIP compression level: 0 (Fastest/no compression) to 9 (Maximum compression)."))
                .setSaveConsumer(val -> config.compressionLevel = val)
                .build());

        general.addEntry(eb.startIntSlider(Component.literal("Max Backups to Keep"), config.maxBackupsToKeep, 1, 100)
                .setDefaultValue(10)
                .setTooltip(Component.literal("Automatically deletes oldest backups when this limit is exceeded."))
                .setSaveConsumer(val -> config.maxBackupsToKeep = val)
                .build());

        general.addEntry(eb.startBooleanToggle(Component.literal("Pause When Empty"), config.pauseIfNoPlayers)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Pause automated backups on dedicated servers when 0 players are online."))
                .setSaveConsumer(val -> config.pauseIfNoPlayers = val)
                .build());

        general.addEntry(eb.startBooleanToggle(Component.literal("Chat Notifications"), config.notifyPlayers)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Broadcast chat messages when a backup begins and completes."))
                .setSaveConsumer(val -> config.notifyPlayers = val)
                .build());

        general.addEntry(eb.startBooleanToggle(Component.literal("Chat Notifications: Ops Only"), config.notifyOpsOnly)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Send backup messages only to operators and the singleplayer host."))
                .setSaveConsumer(val -> config.notifyOpsOnly = val)
                .build());

        return builder.build();
    }
}
