package net.fayber.worldbackup;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fayber.worldbackup.command.BackupCommands;
import net.fayber.worldbackup.config.ConfigManager;
import net.fayber.worldbackup.service.BackupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorldBackupMod implements ModInitializer {
    public static final String MOD_ID = "worldbackup";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ConfigManager.load();
        BackupCommands.register();

        ServerLifecycleEvents.SERVER_STARTED.register(BackupService::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> BackupService.stop());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            BackupService service = BackupService.current();
            if (service != null) {
                service.tick();
            }
        });
    }
}
