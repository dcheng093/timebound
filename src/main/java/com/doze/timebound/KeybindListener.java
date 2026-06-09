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

public class KeybindListener implements Listener {
    private final Main plugin;
    private final KeybindManager keybindManager;
    private final Map<UUID, Long> masterKeyPressTime = new HashMap<>();
    private final Map<UUID, Boolean> masterKeyHeld = new HashMap<>();
    private final Map<UUID, Integer> masterChargeTaskIds = new HashMap<>();
    
    private static final long MASTER_CHARGE_DELAY_TICKS = 6; // ~300ms to trigger charge mode
    
    public KeybindListener(Main plugin, KeybindManager keybindManager) {
        this.plugin = plugin;
        this.keybindManager = keybindManager;
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        // Only intercept the swap-hands key when the player is actually holding a
        // TimeBound weapon/clock that uses it. Otherwise let the vanilla off-hand
        // swap happen normally so players can still off-hand shields, totems, etc.
        if (!keybindManager.hasKeybindWeapon(p)) {
            return;
        }
        // cancel the actual swap
        e.setCancelled(true);
        boolean isUltimate = p.isSneaking();
        // special handling for master of time ability (F without sneak)
        if (TimeBoundItems.isMasterOfTime(plugin, p.getInventory().getItemInMainHand()) && !isUltimate) {
            handleMasterOfTimeAbility(p);
        } else {
            keybindManager.onKeyPress(p, isUltimate);
        }
    }
    
    private void handleMasterOfTimeAbility(Player p) {
        UUID id = p.getUniqueId();
        Integer oldTaskId = masterChargeTaskIds.remove(id);
        if (oldTaskId != null) {
            Bukkit.getScheduler().cancelTask(oldTaskId);
            plugin.getMasterListener().activateMasterCharge(p);
            return;
        }
        plugin.getMasterListener().activateMasterAbility(p);
        int taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            masterChargeTaskIds.remove(id);
        }, MASTER_CHARGE_DELAY_TICKS);
        
        masterChargeTaskIds.put(id, taskId);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        masterKeyPressTime.remove(id);
        masterKeyHeld.remove(id);
        Integer taskId = masterChargeTaskIds.remove(id);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        keybindManager.cleanup(e.getPlayer());
    }
}
