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
        registerClockRecipes();
        chunkyMonitor = new ChunkyMonitor();

        // EVENTS
        listener = new TimeBoundListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        clockListener = new TimeClockListener(this);
        getServer().getPluginManager().registerEvents(clockListener, this);
        getServer().getPluginManager().registerEvents(chunkyMonitor, this);
        getServer().getPluginManager().registerEvents(new TrialSpawnerListener(this), this);

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
    // CLOCK RECIPE REGISTRATION
    // ==========================================
    private void registerClockRecipes() {
        for (ClockType type : ClockType.values()) {
            NamespacedKey key = new NamespacedKey(this, "clock_recipe_" + type.key());
            Bukkit.removeRecipe(key);
            
            org.bukkit.inventory.ShapelessRecipe recipe = new org.bukkit.inventory.ShapelessRecipe(key, TimeClockItems.createClock(this, type));
            
            switch (type) {
                case FREEZE -> {
                    recipe.addIngredient(org.bukkit.Material.SNOWBALL);
                    recipe.addIngredient(org.bukkit.Material.POWDER_SNOW_BUCKET);
                    recipe.addIngredient(org.bukkit.Material.ICE);
                    recipe.addIngredient(org.bukkit.Material.BLUE_DYE);
                }
                case BRAKE -> {
                    recipe.addIngredient(org.bukkit.Material.GRAY_DYE);
                    recipe.addIngredient(org.bukkit.Material.ANVIL);
                    recipe.addIngredient(org.bukkit.Material.IRON_BLOCK);
                    recipe.addIngredient(org.bukkit.Material.REDSTONE_BLOCK);
                }
                case SKIP -> {
                    recipe.addIngredient(org.bukkit.Material.SUGAR);
                    recipe.addIngredient(org.bukkit.Material.FEATHER);
                    recipe.addIngredient(org.bukkit.Material.YELLOW_DYE);
                    recipe.addIngredient(org.bukkit.Material.NETHER_WART);
                }
                case REVERSE -> {
                    recipe.addIngredient(org.bukkit.Material.AMETHYST_SHARD);
                    recipe.addIngredient(org.bukkit.Material.PURPLE_DYE);
                    recipe.addIngredient(org.bukkit.Material.ENDER_PEARL);
                    recipe.addIngredient(org.bukkit.Material.DRAGON_HEAD);
                }
            }
            
            Bukkit.addRecipe(recipe);
            getLogger().info("Registered recipe for " + type.displayName());
        }
    }

    // ==========================================

    // SHORTCUT METHOD FOR TIMESTRUCTUREMANAGER
    // ==========================================
    public NamespacedKey key(String keyName) {
        return new NamespacedKey(this, keyName);
    }
}