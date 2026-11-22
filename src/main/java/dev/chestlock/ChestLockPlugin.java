package dev.chestlock;

import dev.chestlock.data.LockManager;
import dev.chestlock.gui.LockpickGame;
import dev.chestlock.gui.TrustGui;
import dev.chestlock.item.LockItems;
import dev.chestlock.listener.AccessListener;
import dev.chestlock.listener.LockListener;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Clase principal: solo cablea los componentes. Nada de lógica aquí.
 */
public final class ChestLockPlugin extends JavaPlugin {

    private LockManager lockManager;
    private LockItems lockItems;
    private TrustGui trustGui;
    private LockpickGame lockpickGame;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();

        lockManager = new LockManager(this);
        lockManager.load();

        lockItems = new LockItems(this);
        lockItems.registerRecipes();

        trustGui = new TrustGui(this);
        lockpickGame = new LockpickGame(this);

        Bukkit.getPluginManager().registerEvents(new LockListener(this), this);
        Bukkit.getPluginManager().registerEvents(new AccessListener(this), this);
        Bukkit.getPluginManager().registerEvents(trustGui, this);
        Bukkit.getPluginManager().registerEvents(lockpickGame, this);

        PluginCommand cmd = getCommand("chestlock");
        if (cmd != null) {
            ChestLockCommand handler = new ChestLockCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        // Autoguardado cada 5 minutos (solo escribe si hubo cambios; la E/S va en async).
        long interval = 20L * 60 * 5;
        Bukkit.getScheduler().runTaskTimer(this, lockManager::saveIfDirty, interval, interval);
    }

    @Override
    public void onDisable() {
        if (lockpickGame != null) lockpickGame.shutdown();
        if (lockManager != null) lockManager.saveNow();
    }

    public LockManager getLockManager() { return lockManager; }
    public LockItems getLockItems() { return lockItems; }
    public TrustGui getTrustGui() { return trustGui; }
    public LockpickGame getLockpickGame() { return lockpickGame; }
}
