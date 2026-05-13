package com.doze.timebound;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    private static Main instance;
    private TimeManager timeManager;
    private TimeBoundListener listener;
    private TimeClockListener clockListener;
    private ChunkyMonitor chunkyMonitor;
    private AdvancementManager advancementManager;
    private RecipeUnlockListener recipeUnlockListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        advancementManager = new AdvancementManager(this);
        advancementManager.registerAdvancements();
        timeManager = new TimeManager(this);
        TimeBladeItems.registerRecipes(this);
        chunkyMonitor = new ChunkyMonitor();

        listener = new TimeBoundListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        clockListener = new TimeClockListener(this);
        getServer().getPluginManager().registerEvents(clockListener, this);
        getServer().getPluginManager().registerEvents(chunkyMonitor, this);
        getServer().getPluginManager().registerEvents(new TimeItemProtectionListener(this), this);
        recipeUnlockListener = new RecipeUnlockListener(this);
        getServer().getPluginManager().registerEvents(recipeUnlockListener, this);

        var timeboundCommand = getCommand("timebound");
        if (timeboundCommand != null) {
            TimeBoundCommand command = new TimeBoundCommand(clockListener);
            timeboundCommand.setExecutor(command);
            timeboundCommand.setTabCompleter(command);
            getLogger().info("Registered /timebound command executor and tab completer.");
        } else {
            getLogger().warning("timebound command missing in plugin.yml");
        }

        var trustCommand = getCommand("trust");
        if (trustCommand != null) {
            TrustManager trustManager = new TrustManager();
            trustCommand.setExecutor(trustManager);
            trustCommand.setTabCompleter(trustManager);

            var untrustCommand = getCommand("untrust");
            if (untrustCommand != null) {
                untrustCommand.setExecutor(trustManager);
                untrustCommand.setTabCompleter(trustManager);
            }
        }
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (World w : Bukkit.getWorlds()) {
                for (Entity e : w.getEntities()) {
                    if (TimeFreezeManager.isFrozen(e)) {
                        TimeFreezeManager.lockPosition(e);
                    }
                }
            }
        }, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (World w : Bukkit.getWorlds()) {
                for (Entity e : w.getEntities()) {
                    timeManager.trackEntity(e);
                }
            }
        }, 1L, 1L);
        getLogger().info("TimeBound Enabled");
    }

    public static Main getInstance() {
        return instance;
    }

    public TimeManager getTimeManager() {
        return timeManager;
    }

    public TimeBoundListener getListener() {
        return listener;
    }

    public TimeClockListener getClockListener() {
        return clockListener;
    }

    public ChunkyMonitor getChunkyMonitor() {
        return chunkyMonitor;
    }

    public AdvancementManager getAdvancementManager() {
        return advancementManager;
    }

    public RecipeUnlockListener getRecipeUnlockListener() {
        return recipeUnlockListener;
    }

    public NamespacedKey key(String keyName) {
        return new NamespacedKey(this, keyName);
    }
}
