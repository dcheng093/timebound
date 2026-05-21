package com.doze.timebound;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class TimeItemEntityGuardian implements Listener {
    private static final double VOID_Y = -64.0;
    public static final String OWNER_KEY = "timebound_owner";
    private final Main plugin;
    private final Set<UUID> tracked = ConcurrentHashMap.newKeySet();
    private final Set<UUID> announcedVoid = ConcurrentHashMap.newKeySet();
    public TimeItemEntityGuardian(Main plugin) {
        this.plugin = plugin;
        new BukkitRunnable() {
            @Override
            public void run() {
                tickVoidCleanup();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Item item = event.getItemDrop();
        protect(item);
        try {
            item.getPersistentDataContainer().set(plugin.key(OWNER_KEY), PersistentDataType.STRING, event.getPlayer().getUniqueId().toString());
        } catch (Throwable ignored) {
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemSpawn(ItemSpawnEvent event) {
        protect(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        for (Entity e : chunk.getEntities()) {
            if (e instanceof Item item) {
                protect(item);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        if (event.getEntity() instanceof Item item) {
            tracked.remove(item.getUniqueId());
            announcedVoid.remove(item.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent event) {
        Item item = event.getEntity();
        if (!TimeBoundItems.isTimeItem(plugin, item.getItemStack())) return;
        event.setCancelled(true);
        protect(item);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMerge(ItemMergeEvent event) {
        Item a = event.getEntity();
        Item b = event.getTarget();
        if (TimeBoundItems.isTimeItem(plugin, a.getItemStack()) || TimeBoundItems.isTimeItem(plugin, b.getItemStack())) {
            event.setCancelled(true);
        }
    }

    private void protect(Item item) {
        ItemStack stack = item.getItemStack();
        if (!TimeBoundItems.isTimeItem(plugin, stack)) return;
        if (!TimeItemUid.has(plugin, stack)) {
            ItemStack copy = stack.clone();
            TimeItemUid.ensure(plugin, copy);
            item.setItemStack(copy);
        }
        try {
            item.setUnlimitedLifetime(true);
        } catch (Throwable ignored) {
        }
        try {
            item.setInvulnerable(true);
        } catch (Throwable ignored) {
        }
        tracked.add(item.getUniqueId());
    }

    private void tickVoidCleanup() {
        if (tracked.isEmpty()) return;
        Object[] ids = tracked.toArray();
        for (Object o : ids) {
            UUID id = (UUID) o;
            Entity e = Bukkit.getEntity(id);
            if (!(e instanceof Item item) || !item.isValid()) {
                tracked.remove(id);
                announcedVoid.remove(id);
                continue;
            }
            if (item.getLocation().getY() > VOID_Y) continue;
            if (announcedVoid.add(id)) {
                String name = TimeBoundItems.displayName(plugin, item.getItemStack());
                Component msg = Component.text(name, NamedTextColor.GOLD)
                        .append(Component.text(" slipped into the void, but time refuses to let it be lost.", NamedTextColor.AQUA));
                Bukkit.broadcast(msg);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.5f, 1.4f);
                }
                plugin.getLogger().info(String.format("%s void rescue triggered.", name));
            }
            rescueFromVoid(item);
        }
    }

    private void rescueFromVoid(Item item) {
        UUID owner = null;
        try {
            String raw = item.getPersistentDataContainer().get(plugin.key(OWNER_KEY), PersistentDataType.STRING);
            if (raw != null) owner = UUID.fromString(raw);
        } catch (Throwable ignored) {
        }
        if (owner != null) {
            Player p = Bukkit.getPlayer(owner);
            if (p != null && p.isOnline()) {
                item.teleport(p.getLocation().add(0, 1.0, 0));
                item.setVelocity(new Vector(0, 0.15, 0));
                protect(item);
                return;
            }
        }
        var w = item.getWorld();
        var spawn = w.getSpawnLocation().clone().add(0, 1.0, 0);
        item.teleport(spawn);
        item.setVelocity(new Vector(0, 0.1, 0));
        protect(item);
    }
}
