package com.doze.timebound;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    private static Main instance;
    private TimeManager timeManager;
    private TimeBoundListener listener;
    private TimeClockListener clockListener;
    private MasterOfTimeListener masterListener;
    private KeybindManager keybindManager;
    private AdvancementManager advancementManager;
    private RecipeUnlockListener recipeUnlockListener;
    private GlobalTimeItemRegistry globalRegistry;
    private GlobalTimeItemScanner globalScanner;
    private WorldUltimateManager worldUltimateManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        advancementManager = new AdvancementManager(this);
        advancementManager.registerAdvancements();
        timeManager = new TimeManager(this);
        TimeBladeItems.registerRecipes(this);
        MasterOfTimeItems.registerRecipe(this);
        globalRegistry = new GlobalTimeItemRegistry(this);
        globalScanner = new GlobalTimeItemScanner(this, globalRegistry);
        worldUltimateManager = new WorldUltimateManager(this);
        keybindManager = new KeybindManager(this);
        listener = new TimeBoundListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        clockListener = new TimeClockListener(this);
        getServer().getPluginManager().registerEvents(clockListener, this);
        getServer().getPluginManager().registerEvents(new TimeItemProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new TimeItemEntityGuardian(this), this);
        recipeUnlockListener = new RecipeUnlockListener(this);
        getServer().getPluginManager().registerEvents(recipeUnlockListener, this);
        masterListener = new MasterOfTimeListener(this);
        getServer().getPluginManager().registerEvents(masterListener, this);
        getServer().getPluginManager().registerEvents(new KeybindListener(this, keybindManager), this);
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
        TimeFreezeManager.start(this);
        timeManager.start(this);
        globalScanner.requestScan(GlobalTimeItemScanner.Reason.STARTUP);
        long minutes = Math.max(1, getConfig().getLong("globalScanMinutes", 3));
        long ticks = minutes * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(this, () -> globalScanner.requestScan(GlobalTimeItemScanner.Reason.PERIODIC), ticks, ticks);
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

    public MasterOfTimeListener getMasterListener() {
        return masterListener;
    }

    public KeybindManager getKeybindManager() {
        return keybindManager;
    }

    public AdvancementManager getAdvancementManager() {
        return advancementManager;
    }

    public RecipeUnlockListener getRecipeUnlockListener() {
        return recipeUnlockListener;
    }

    public GlobalTimeItemRegistry getGlobalRegistry() {
        return globalRegistry;
    }

    public GlobalTimeItemScanner getGlobalScanner() {
        return globalScanner;
    }

    public WorldUltimateManager getWorldUltimateManager() {
        return worldUltimateManager;
    }

    public NamespacedKey key(String keyName) {
        return new NamespacedKey(this, keyName);
    }
}
