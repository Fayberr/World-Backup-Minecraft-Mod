package net.fayber.worldbackup.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * Picks the settings screen: Modern Config if installed, else Cloth Config, else none.
 * Each screen class is only touched after its isModLoaded check, so the optional mods' classes
 * are never loaded when they are missing.
 */
public final class ConfigScreenRouter {
    private ConfigScreenRouter() {}

    @Nullable
    public static Screen create(Screen parent) {
        FabricLoader loader = FabricLoader.getInstance();
        if (loader.isModLoaded("modernconfig")) {
            return WorldBackupModernScreen.create(parent);
        }
        if (loader.isModLoaded("cloth-config")) {
            return WorldBackupClothScreen.create(parent);
        }
        return null;
    }
}
