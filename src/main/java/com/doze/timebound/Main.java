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

    @Override
    public void onEnable() {
        instance = this;
        // saveDefaultConfig(); <-- Delete this line!
        timeManager = new TimeManager(this);
        TimeBladeItems.registerRecipes(this);
        chunkyMonitor = new ChunkyMonitor();

        // EVENTS
        listener = new TimeBoundListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        clockListener = new TimeClockListener(this);
        getServer().getPluginManager().registerEvents(clockListener, this);
        getServer().getPluginManager().registerEvents(chunkyMonitor, this);

        // ... rest of the code remains the same ...

        // COMMANDS
        if (getCommand("timebound") != null) {
            TimeBoundCommand command = new TimeBoundCommand(clockListener);
            getCommand("timebound").setExecutor(command);
            getCommand("timebound").setTabCompleter(command);
            getLogger().info("Registered /timebound command executor and tab completer.");
        } else {
            getLogger().warning("timebound command missing in plugin.yml");
        }

        // TRUST COMMANDS
        if (getCommand("trust") != null) {
            TrustManager trustManager = new TrustManager();
            getCommand("trust").setExecutor(trustManager);
            getCommand("trust").setTabCompleter(trustManager);
            if (getCommand("untrust") != null) {
                getCommand("untrust").setExecutor(trustManager);
                getCommand("untrust").setTabCompleter(trustManager);
            }
        }

        // FREEZE LOCK TRACKER
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (World w : Bukkit.getWorlds()) {
                for (Entity e : w.getEntities()) {
                    if (TimeFreezeManager.isFrozen(e)) {
                        TimeFreezeManager.lockPosition(e);
                    }
                }
            }
        }, 1L, 1L);

        // ENTITY TRACKER
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

    // ==========================================
    // SHORTCUT METHOD FOR TIMESTRUCTUREMANAGER
    // ==========================================
    public NamespacedKey key(String keyName) {
        return new NamespacedKey(this, keyName);
    }
}