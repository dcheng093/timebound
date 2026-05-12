package com.doze.timebound;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

@SuppressWarnings("null")
public class TrialSpawnerListener implements Listener {
    private final Main plugin;
    private final Map<UUID, TrialSessionData> activeSessions = new HashMap<>();
    
    public TrialSpawnerListener(Main plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerInteractBlock(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        org.bukkit.block.Block block = event.getClickedBlock();
        
        if (block.getType().toString().contains("TRIAL_SPAWNER")) {
            Player player = event.getPlayer();
            UUID playerId = player.getUniqueId();
            
            try {
                org.bukkit.entity.ArmorStand brakeLoc = findBrakeClockMarker();
                
                if (brakeLoc != null && brakeLoc.getLocation() != null && 
                    player.getLocation().distance(brakeLoc.getLocation()) < 50) {
                    if (!activeSessions.containsKey(playerId)) {
                        activeSessions.put(playerId, new TrialSessionData(player, block.getLocation()));
                        Component message = Component.text(player.getName(), NamedTextColor.DARK_PURPLE, TextDecoration.BOLD)
                            .append(Component.text(" has started the Brake Room Trial!", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.sendMessage(message);
                        }
                        plugin.getLogger().info(() -> player.getName() + " started Brake Room Trial at " + block.getLocation());
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error checking Brake Clock marker for player " + player.getName(), e);
            }
        }
    }
    
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        try {
            String entityType = event.getEntityType().toString();
            if (entityType.contains("OMEN") || entityType.contains("TRIAL")) {
                Location deathLoc = event.getEntity().getLocation();
                if (deathLoc == null || deathLoc.getWorld() == null) return;
                
                for (TrialSessionData session : new HashMap<>(activeSessions).values()) {
                    if (session.block != null && session.block.getWorld() != null &&
                        session.block.getWorld().equals(deathLoc.getWorld()) && 
                        session.block.distance(deathLoc) < 50) {
                        session.mobs_defeated++;
                        if (session.mobs_defeated >= 3) {
                            completeTrialSession(session);
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error processing entity death in trial", e);
        }
    }
    
    private void completeTrialSession(TrialSessionData session) {
        try {
            Component message = Component.text(session.player.getName(), NamedTextColor.DARK_PURPLE, TextDecoration.BOLD)
                .append(Component.text(" has completed the Brake Room Trial!", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(message);
            }
            plugin.getLogger().info(() -> session.player.getName() + " completed Brake Room Trial");
            
            Location spawnLoc = session.block.add(0, 2, 0);
            if (plugin.getClockListener() != null) {
                plugin.getClockListener().spawnClickableClock(spawnLoc, ClockType.BRAKE);
            }
            
            activeSessions.remove(session.player.getUniqueId());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error completing trial session", e);
        }
    }
    
    private org.bukkit.entity.ArmorStand findBrakeClockMarker() {
        try {
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                if (world == null) continue;
                try {
                    for (org.bukkit.entity.ArmorStand stand : world.getEntitiesByClass(org.bukkit.entity.ArmorStand.class)) {
                        if (stand == null) continue;
                        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "timebound_structure_marker");
                        if (stand.getPersistentDataContainer().has(key, org.bukkit.persistence.PersistentDataType.STRING)) {
                            String markerType = stand.getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.STRING);
                            if ("brake".equals(markerType)) {
                                return stand;
                            }
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.FINE, "Error searching world " + world.getName() + " for brake clock marker", e);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error finding brake clock marker", e);
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
