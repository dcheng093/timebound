package com.doze.timebound;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class TimeItemProtectionListener implements Listener {
    private final Main plugin;
    private final Set<UUID> announcedDestroyedItems = new HashSet<>();

    public TimeItemProtectionListener(Main plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::protectLoadedItemEntities, 20L, 100L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> removeTimeItemsFromEnderChest(plugin, event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        protectItemEntity(event.getItemDrop());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        protectItemEntity(event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item item)) return;
        if (!TimeBoundItems.isTimeItem(plugin, item.getItemStack())) return;

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (isFireOrLava(cause)) {
            event.setCancelled(true);
            item.setFireTicks(0);
            protectItemEntity(item);
            return;
        }

        if (cause == EntityDamageEvent.DamageCause.VOID || cause == EntityDamageEvent.DamageCause.CONTACT) {
            announceDestroyed(item, cause == EntityDamageEvent.DamageCause.VOID ? "fell into the void" : "was destroyed by a cactus");
        }
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

    private void protectLoadedItemEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                protectItemEntity(item);
            }
        }
    }

    private void protectItemEntity(Item item) {
        if (!TimeBoundItems.isTimeItem(plugin, item.getItemStack())) return;
        item.setFireTicks(0);
        item.setUnlimitedLifetime(true);
    }

    private boolean isFireOrLava(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR;
    }

    private void announceDestroyed(Item item, String reason) {
        if (!announcedDestroyedItems.add(item.getUniqueId())) return;

        String name = TimeBoundItems.displayName(plugin, item.getItemStack());
        Component message = Component.text(name, NamedTextColor.GOLD)
                .append(Component.text(" " + reason + ".", NamedTextColor.RED));

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 1.8f);
        }
        plugin.getLogger().warning(name + " " + reason + ".");
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
