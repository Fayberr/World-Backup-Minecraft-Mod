package net.fayber.worldbackup.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fayber.worldbackup.service.BackupService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

public class BackupCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("backup")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("create")
                        .executes(ctx -> {
                            BackupService.triggerBackup(ctx.getSource().getServer(), "manual");
                            return 1;
                        })
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    String label = StringArgumentType.getString(ctx, "name");
                                    BackupService.triggerBackup(ctx.getSource().getServer(), label);
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("status")
                        .executes(ctx -> {
                            boolean inProgress = BackupService.isBackupInProgress();
                            ctx.getSource().sendSuccess(() -> Component.literal("[WorldBackup] Status: " + (inProgress ? "Backup in progress" : "Idle")), false);
                            return 1;
                        })
                )
        );
    }
}
