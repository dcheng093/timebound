package com.doze.timebound;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TrialSpawnerListener implements Listener {
    private final Main plugin;
    private final Map<UUID, TrialSessionData> activeSessions = new HashMap<>();
    
    public TrialSpawnerListener(Main plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerInteractBlock(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Block block = event.getClickedBlock();
        
        if (block.getType().toString().contains("TRIAL_SPAWNER")) {
            Player player = event.getPlayer();
            UUID playerId = player.getUniqueId();
            
            TimeStructureManager manager = new TimeStructureManager(plugin);
            ArmorStand brakeLoc = findBrakeClockMarker();
            
            if (brakeLoc != null && player.getLocation().distance(brakeLoc.getLocation()) < 50) {
                if (!activeSessions.containsKey(playerId)) {
                    activeSessions.put(playerId, new TrialSessionData(player, block.getLocation()));
                    Bukkit.broadcastMessage(ChatColor.BOLD + "" + ChatColor.DARK_PURPLE + player.getName() + " has started the Brake Room Trial!");
                    plugin.getLogger().info(player.getName() + " started Brake Room Trial at " + block.getLocation());
                }
            }
        }
    }
    
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        String entityType = event.getEntityType().toString();
        if (entityType.contains("OMEN") || entityType.contains("TRIAL")) {
            Location deathLoc = event.getEntity().getLocation();
            for (TrialSessionData session : new HashMap<>(activeSessions).values()) {
                if (session.block.getWorld().equals(deathLoc.getWorld()) 
                    && session.block.getLocation().distance(deathLoc) < 50) {
                    session.mobs_defeated++;
                    if (session.mobs_defeated >= 3) {
                        completeTrialSession(session);
                    }
                }
            }
        }
    }
    
    private void completeTrialSession(TrialSessionData session) {
        Bukkit.broadcastMessage(ChatColor.BOLD + "" + ChatColor.DARK_PURPLE + session.player.getName() + " has completed the Brake Room Trial!");
        plugin.getLogger().info(session.player.getName() + " completed Brake Room Trial");
        
        Location spawnLoc = session.block.getLocation().add(0, 2, 0);
        if (plugin.getClockListener() != null) {
            plugin.getClockListener().spawnClickableClock(spawnLoc, ClockType.BRAKE);
        }
        
        activeSessions.remove(session.player.getUniqueId());
    }
    
    private ArmorStand findBrakeClockMarker() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.ArmorStand stand : world.getEntitiesByClass(org.bukkit.entity.ArmorStand.class)) {
                org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "timebound_structure_marker");
                if (stand.getPersistentDataContainer().has(key, org.bukkit.persistence.PersistentDataType.STRING)) {
                    String markerType = stand.getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.STRING);
                    if ("brake".equals(markerType)) {
                        return stand;
                    }
                }
            }
        }
        return null;
    }
    
    private static class TrialSessionData {
        Player player;
        Location block;
        int mobs_defeated = 0;
        
        TrialSessionData(Player player, Location block) {
            this.player = player;
            this.block = block;
        }
    }
}
