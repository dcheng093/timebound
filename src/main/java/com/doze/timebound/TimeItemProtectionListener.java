package com.doze.timebound;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class TimeItemProtectionListener implements Listener {
    private final Main plugin;
    private final Set<UUID> announcedRescues = new HashSet<>();

    public TimeItemProtectionListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> removeTimeItemsFromEnderChest(plugin, event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item item)) return;
        if (!TimeBoundItems.isTimeItem(plugin, item.getItemStack())) return;

        // Time items are indestructible.
        event.setCancelled(true);

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (isFireOrLava(cause)) {
            rescueItem(item, "was saved from lava/fire");
            return;
        }

        if (cause == EntityDamageEvent.DamageCause.VOID) {
            rescueItem(item, "was pulled back from the void");
            return;
        }

        if (cause == EntityDamageEvent.DamageCause.CONTACT) {
            rescueItem(item, "was saved from a cactus");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent event) {
        Item item = event.getEntity();
        if (!TimeBoundItems.isTimeItem(plugin, item.getItemStack())) return;
        event.setCancelled(true);
        try {
            item.setUnlimitedLifetime(true);
        } catch (Throwable ignored) {
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        if (!(event.getEntity() instanceof Item item)) return;
        if (!TimeBoundItems.isTimeItem(plugin, item.getItemStack())) return;
        // Covers clears, despawns, plugin removals, etc. We treat it as a trigger to refresh the global registry.
        Bukkit.getScheduler().runTask(plugin, () -> plugin.getGlobalScanner().requestScan(GlobalTimeItemScanner.Reason.DESTRUCTION));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory().getType() != InventoryType.ENDER_CHEST) return;

        boolean topSlot = event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize();
        boolean movingTimeItemIntoEnderChest = false;

        if (topSlot && TimeBoundItems.isTimeItem(plugin, event.getCursor())) {
            movingTimeItemIntoEnderChest = true;
        }

        if (topSlot && event.getClick().isKeyboardClick()) {
            ItemStack hotbarItem = event.getHotbarButton() >= 0
                    ? player.getInventory().getItem(event.getHotbarButton())
                    : player.getInventory().getItemInOffHand();
            movingTimeItemIntoEnderChest = movingTimeItemIntoEnderChest || TimeBoundItems.isTimeItem(plugin, hotbarItem);
        }

        if (event.isShiftClick() && !topSlot && TimeBoundItems.isTimeItem(plugin, event.getCurrentItem())) {
            movingTimeItemIntoEnderChest = true;
        }

        if (movingTimeItemIntoEnderChest) {
            event.setCancelled(true);
            denyEnderChest(player);
            Bukkit.getScheduler().runTask(plugin, () -> removeTimeItemsFromEnderChest(plugin, player));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory().getType() != InventoryType.ENDER_CHEST) return;
        if (!TimeBoundItems.isTimeItem(plugin, event.getOldCursor())) return;

        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < topSize) {
                event.setCancelled(true);
                denyEnderChest(player);
                Bukkit.getScheduler().runTask(plugin, () -> removeTimeItemsFromEnderChest(plugin, player));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player
                && event.getView().getTopInventory().getType() == InventoryType.ENDER_CHEST) {
            removeTimeItemsFromEnderChest(plugin, player);
        }
    }

    public static void removeTimeItemsFromEnderChest(Main plugin, Player player) {
        Inventory enderChest = player.getEnderChest();
        for (int slot = 0; slot < enderChest.getSize(); slot++) {
            ItemStack item = enderChest.getItem(slot);
            if (!TimeBoundItems.isTimeItem(plugin, item)) continue;

            enderChest.setItem(slot, null);
            giveOrDrop(plugin, player, item);
            player.sendMessage(Component.text(TimeBoundItems.displayName(plugin, item) + " cannot be stored in an ender chest.", NamedTextColor.RED));
        }
        player.updateInventory();
    }



    private boolean isFireOrLava(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR
                || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION;
    }

    private void rescueItem(Item item, String reason) {
        // Keep messaging rate-limited per entity.
        if (!announcedRescues.add(item.getUniqueId())) return;

        // Push upwards slightly and extinguish.
        try {
            item.setFireTicks(0);
        } catch (Throwable ignored) {
        }
        item.setVelocity(new Vector(0, Math.max(0.12, item.getVelocity().getY()), 0));

        // If in void-ish, teleport to world spawn.
        Location loc = item.getLocation();
        if (loc.getY() < -64) {
            item.teleport(item.getWorld().getSpawnLocation().clone().add(0, 1.0, 0));
        }

        String name = TimeBoundItems.displayName(plugin, item.getItemStack());
        Component message = Component.text(name, NamedTextColor.GOLD)
                .append(Component.text(" " + reason + ".", NamedTextColor.AQUA));

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.35f, 1.6f);
        }
        plugin.getLogger().info("%s %s.".formatted(name, reason));
    }

    private static void giveOrDrop(Main plugin, Player player, ItemStack item) {
        if (TimeBoundItems.isEmpty(item)) return;

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            Item dropped = player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            if (TimeBoundItems.isTimeItem(plugin, dropped.getItemStack())) {
                dropped.setFireTicks(0);
                dropped.setUnlimitedLifetime(true);
            }
        }
    }

    private void denyEnderChest(Player player) {
        player.sendMessage(Component.text("Time weapons and Time Clocks cannot be stored in an ender chest.", NamedTextColor.RED));
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 0.7f, 0.8f);
    }
}
