package net.fayber.worldbackup.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fayber.worldbackup.config.BackupConfig;
import net.fayber.worldbackup.config.ConfigManager;
import net.fayber.worldbackup.service.BackupService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

public class BackupCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
    }

    private static boolean allowed(CommandSourceStack source) {
        if (source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            return true;
        }
        // The singleplayer host can use it with cheats off. LAN guests and other players cannot.
        ServerPlayer player = source.getPlayer();
        return player != null && source.getServer().isSingleplayerOwner(player.nameAndId());
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("backup")
                .requires(BackupCommands::allowed)
                .then(Commands.literal("create")
                        .executes(ctx -> create(ctx.getSource(), "manual"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> create(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource()))));
    }

    private static int create(CommandSourceStack source, String label) {
        BackupService service = BackupService.current();
        if (service == null) {
            source.sendFailure(Component.literal("[World Backup] The server is not ready yet."));
            return 0;
        }
        if (label.length() > 40) {
            label = label.substring(0, 40);
        }
        if (!service.triggerBackup(label)) {
            source.sendFailure(Component.literal("[World Backup] A backup is already running."));
            return 0;
        }
        if (!ConfigManager.get().notifyPlayers) {
            source.sendSuccess(() -> Component.literal("[World Backup] Backup started."), false);
        }
        return 1;
    }

    private static int status(CommandSourceStack source) {
        BackupService service = BackupService.current();
        if (service == null) {
            source.sendFailure(Component.literal("[World Backup] The server is not ready yet."));
            return 0;
        }
        BackupConfig config = ConfigManager.get();
        StringBuilder sb = new StringBuilder("[World Backup]");

        if (service.isRunning()) {
            sb.append("\nRunning: ").append(service.runningLabel())
                    .append(" (").append(BackupService.formatDuration(service.runningForMillis())).append(")");
        } else {
            sb.append("\nIdle");
        }

        long next = service.millisUntilNext();
        if (next < 0) {
            sb.append("\nSchedule: manual only");
        } else if (service.pausedForEmptyServer()) {
            sb.append("\nSchedule: every ").append(config.backupIntervalMinutes).append(" min, paused while the server is empty");
        } else {
            sb.append("\nSchedule: every ").append(config.backupIntervalMinutes).append(" min, next in ")
                    .append(BackupService.formatDuration(next));
        }

        BackupService.Stored stored = service.stored();
        if (stored.newest() == null) {
            sb.append("\nLast backup: none yet");
        } else {
            sb.append("\nLast backup: ").append(stored.newest().getFileName()).append(", ")
                    .append(BackupService.formatAgo(stored.newestModified()));
            BackupService.LastRun run = service.lastRun();
            if (run != null && run.fileName().equals(stored.newest().getFileName().toString())) {
                sb.append(", took ").append(BackupService.formatDuration(run.durationMillis()));
            }
        }
        sb.append("\nStored: ").append(stored.count()).append(config.maxBackupsToKeep > 0 ? " of " + config.maxBackupsToKeep : "")
                .append(" backups, ").append(BackupService.formatSize(stored.totalBytes()));
        sb.append("\nFolder: ").append(service.backupFolder());

        String text = sb.toString();
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
