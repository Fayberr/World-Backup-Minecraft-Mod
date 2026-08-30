package net.fayber.worldbackup;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fayber.worldbackup.command.BackupCommands;
import net.fayber.worldbackup.config.ConfigManager;
import net.fayber.worldbackup.service.BackupService;

public class WorldBackupMod implements ModInitializer {
    public static final String MOD_ID = "worldbackup";
    private static int tickCounter = 0;

    @Override
    public void onInitialize() {
        ConfigManager.load();
        BackupCommands.register();

        // Check scheduler every 20 ticks (1 second)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tickCounter >= 20) {
                tickCounter = 0;
                BackupService.checkScheduledBackup(server);
            }
        });
    }
}
