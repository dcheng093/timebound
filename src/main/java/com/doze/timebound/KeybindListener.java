package com.doze.timebound;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/**
 * Unified keybind listener using only F and Shift+F.
 * 
 * F = Swap hands event (redirected to ability)
 * Shift+F = Swap hands while sneaking (redirected to ultimate)
 * 
 * Holds key to charge, releases to cancel or activate when fully charged.
 */
public class KeybindListener implements Listener {
    private final Main plugin;
    private final KeybindManager keybindManager;
    
    // Track which keys are currently held
    private final Map<UUID, Boolean> fKeyHeld = new HashMap<>();
    private final Map<UUID, Integer> chargeTaskIds = new HashMap<>();
    
    public KeybindListener(Main plugin, KeybindManager keybindManager) {
        this.plugin = plugin;
        this.keybindManager = keybindManager;
    }
    
    /**
     * Intercept swap hands event (F key).
     * - If sneaking: Ultimate
     * - If not sneaking: Regular ability
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        
        // Cancel the actual swap
        e.setCancelled(true);
        
        boolean isUltimate = p.isSneaking();
        keybindManager.onKeyPress(p, isUltimate);
        
        // Track key hold
        markKeyPressed(p);
        
        // Schedule key release check (F key auto-releases quickly, so we check after a short delay)
        scheduleKeyReleaseCheck(p);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        fKeyHeld.remove(id);
        
        // Cancel any pending release checks
        Integer taskId = chargeTaskIds.remove(id);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        
        keybindManager.cleanup(e.getPlayer());
    }
    
    private void markKeyPressed(Player player) {
        UUID id = player.getUniqueId();
        fKeyHeld.put(id, true);
    }
    
    private void scheduleKeyReleaseCheck(Player player) {
        UUID id = player.getUniqueId();
        
        // Cancel previous task
        Integer oldTaskId = chargeTaskIds.remove(id);
        if (oldTaskId != null) {
            Bukkit.getScheduler().cancelTask(oldTaskId);
        }
        
        // Schedule check - F key typically releases after a few ticks
        int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            if (fKeyHeld.getOrDefault(id, false)) {
                // Key was held beyond the initial press
                // Keep the charge going, it will auto-complete when fully charged
            } else {
                // Key was released
                if (player.isOnline()) {
                    keybindManager.onKeyRelease(player);
                }
            }
            chargeTaskIds.remove(id);
        }, 50L); // Check after 2.5 seconds (50 ticks)
        
        chargeTaskIds.put(id, taskId);
    }
}
