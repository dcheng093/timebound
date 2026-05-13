package com.doze.timebound;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class RecipeUnlockListener implements Listener {
    private final Main plugin;
    private final NamespacedKey weaponClaimKey;
    private final NamespacedKey clockClaimKey;
    private final Set<String> announcedWeapons = new HashSet<>();

    public RecipeUnlockListener(Main plugin) {
        this.plugin = plugin;
        this.weaponClaimKey = new NamespacedKey(plugin, "claimed_weapon");
        this.clockClaimKey = new NamespacedKey(plugin, "claimed_clock");
        loadState();
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupLoadedDuplicates, 40L, 200L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            unlockRecipesFromInventory(event.getPlayer(), true);
            cleanupLoadedDuplicates();
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        StarTimerManager.stop(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> unlockRecipesFromInventory(event.getPlayer(), true));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        String type = TimeBladeItems.getTaggedType(inventory.getResult());
        if (type != null && isWeaponClaimed(type)) {
            inventory.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        String type = TimeBladeItems.getTaggedType(event.getResult());
        if (type != null && isWeaponClaimed(type)) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack result = event.getRecipe().getResult();
        String weaponType = TimeBladeItems.getTaggedType(result);
        if (weaponType == null) return;

        if (isWeaponClaimed(weaponType)) {
            event.setCancelled(true);
            event.getInventory().setResult(null);
            denyCraft(player, weaponType);
            return;
        }

        if (!craftContainsClock(event.getInventory(), clockTypeForWeapon(weaponType))) {
            event.setCancelled(true);
            event.getInventory().setResult(null);
            denyCraft(player, weaponType);
            return;
        }

        claimWeapon(weaponType);
        announceWeaponCraft(player, weaponType);
        plugin.getAdvancementManager().grantWeaponAdvancement(player, weaponType);
        Bukkit.getScheduler().runTask(plugin, () -> {
            unlockRecipesFromInventory(player, false);
            cleanupLoadedDuplicates();
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            unlockRecipesFromInventory(player, false);
            cleanupLoadedDuplicates();
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Bukkit.getScheduler().runTask(plugin, () -> unlockRecipesFromInventory(player, false));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            unlockRecipesFromInventory(player, false);
            cleanupLoadedDuplicates();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Bukkit.getScheduler().runTask(plugin, this::cleanupLoadedDuplicates);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack stack = event.getItem().getItemStack();
        String weaponType = TimeBladeItems.getTaggedType(stack);
        ClockType clockType = TimeClockItems.getClockType(plugin, stack);
        if ((weaponType != null && isWeaponClaimed(weaponType) && hasWeapon(player, weaponType))
                || (clockType != null && isClockClaimed(clockType) && hasClock(player, clockType))) {
            event.setCancelled(true);
            event.getItem().remove();
            sendColored(player, NamedTextColor.RED, "Duplicate TimeBound items are not allowed.");
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            unlockRecipesFromInventory(player, true);
            cleanupLoadedDuplicates();
        });
    }

    public void unlockRecipesFromInventory(Player player, boolean toast) {
        for (ItemStack item : player.getInventory().getContents()) {
            ClockType type = TimeClockItems.getClockType(plugin, item);
            if (type != null) {
                claimClock(type);
                NamespacedKey recipeKey = TimeBladeItems.recipeKey(plugin, type);
                if (!player.hasDiscoveredRecipe(recipeKey)) {
                    player.discoverRecipe(recipeKey);
                    if (toast) {
                        player.getWorld().playSound(player.getLocation(), Sound.UI_TOAST_IN, 0.7f, 1.0f);
                    }
                }
            }
        }
        player.updateInventory();
    }

    public boolean canClaimClock(Player player, ClockType type) {
        return !isClockClaimed(type) && !hasClock(player, type);
    }

    public void recordClockClaim(Player player, ClockType type) {
        claimClock(type);
        unlockRecipesFromInventory(player, true);
        cleanupLoadedDuplicates();
    }

    private boolean craftContainsClock(CraftingInventory inventory, ClockType type) {
        if (type == null) return false;
        for (ItemStack item : inventory.getMatrix()) {
            if (TimeClockItems.getClockType(plugin, item) == type) {
                return true;
            }
        }
        return false;
    }

    private ClockType clockTypeForWeapon(String weaponType) {
        return ClockType.fromKey(weaponType);
    }

    private boolean hasWeapon(Player player, String type) {
        return inventoryHasWeapon(player.getInventory(), type);
    }

    private boolean inventoryHasWeapon(Inventory inventory, String type) {
        for (ItemStack item : inventory.getContents()) {
            if (type.equals(TimeBladeItems.getTaggedType(item))) return true;
        }
        return false;
    }

    private boolean hasClock(Player player, ClockType type) {
        return inventoryHasClock(player.getInventory(), type);
    }

    private boolean inventoryHasClock(Inventory inventory, ClockType type) {
        for (ItemStack item : inventory.getContents()) {
            if (TimeClockItems.getClockType(plugin, item) == type) return true;
        }
        return false;
    }

    private void cleanupLoadedDuplicates() {
        Map<ClockType, Boolean> seenClocks = new EnumMap<>(ClockType.class);
        Set<String> seenWeapons = new HashSet<>();
        for (String type : claimedWeapons()) {
            seenWeapons.add(type + ":claimed");
        }
        for (ClockType type : claimedClocks()) {
            seenClocks.put(type, false);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            TimeItemProtectionListener.removeTimeItemsFromEnderChest(plugin, player);
            cleanupInventory(player.getInventory(), seenWeapons, seenClocks);
            unlockRecipesFromInventory(player, false);
        }

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item item && shouldRemoveEntityItem(item.getItemStack(), seenWeapons, seenClocks)) {
                    item.remove();
                }
            }

            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof InventoryHolder holder) {
                        cleanupInventory(holder.getInventory(), seenWeapons, seenClocks);
                    }
                }
            }
        }
        saveState();
    }

    private void cleanupInventory(Inventory inventory) {
        cleanupInventory(inventory, new HashSet<>(), new EnumMap<>(ClockType.class));
    }

    private void cleanupInventory(Inventory inventory, Set<String> seenWeapons, Map<ClockType, Boolean> seenClocks) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;

            String weaponType = TimeBladeItems.getTaggedType(item);
            if (weaponType != null) {
                if (seenWeapons.contains(weaponType)) {
                    inventory.setItem(slot, null);
                } else {
                    seenWeapons.add(weaponType);
                    claimWeapon(weaponType);
                }
                continue;
            }

            ClockType clockType = TimeClockItems.getClockType(plugin, item);
            if (clockType != null) {
                if (Boolean.TRUE.equals(seenClocks.get(clockType))) {
                    inventory.setItem(slot, null);
                } else {
                    seenClocks.put(clockType, true);
                    claimClock(clockType);
                }
            }
        }
    }

    private boolean shouldRemoveEntityItem(ItemStack stack, Set<String> seenWeapons, Map<ClockType, Boolean> seenClocks) {
        String weaponType = TimeBladeItems.getTaggedType(stack);
        if (weaponType != null) {
            if (seenWeapons.contains(weaponType)) return true;
            seenWeapons.add(weaponType);
            claimWeapon(weaponType);
            return false;
        }

        ClockType clockType = TimeClockItems.getClockType(plugin, stack);
        if (clockType != null) {
            if (Boolean.TRUE.equals(seenClocks.get(clockType))) return true;
            seenClocks.put(clockType, true);
            claimClock(clockType);
            return false;
        }
        return false;
    }

    private Set<String> claimedWeapons() {
        return plugin.getConfig().getConfigurationSection("claimed.weapons") == null
                ? Set.of()
                : plugin.getConfig().getConfigurationSection("claimed.weapons").getKeys(false);
    }

    private Set<ClockType> claimedClocks() {
        Set<ClockType> result = EnumSet.noneOf(ClockType.class);
        if (plugin.getConfig().getConfigurationSection("claimed.clocks") == null) return result;
        for (String key : plugin.getConfig().getConfigurationSection("claimed.clocks").getKeys(false)) {
            ClockType type = ClockType.fromKey(key);
            if (type != null) result.add(type);
        }
        return result;
    }

    private boolean isWeaponClaimed(String type) {
        return plugin.getConfig().getBoolean("claimed.weapons." + type, false);
    }

    private void claimWeapon(String type) {
        plugin.getConfig().set("claimed.weapons." + type, true);
    }

    private boolean isClockClaimed(ClockType type) {
        return plugin.getConfig().getBoolean("claimed.clocks." + type.key(), false);
    }

    private void claimClock(ClockType type) {
        plugin.getConfig().set("claimed.clocks." + type.key(), true);
    }

    private void loadState() {
        plugin.saveDefaultConfig();
    }

    private void saveState() {
        plugin.saveConfig();
    }

    private void denyCraft(Player player, String weaponType) {
        sendColored(player, NamedTextColor.RED, weaponDisplayName(weaponType) + " already exists.");
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.7f);
    }

    private void announceWeaponCraft(Player player, String weaponType) {
        if (announcedWeapons.contains(weaponType)) return;
        Component weaponName = switch (weaponType) {
            case "freeze" -> Component.text("Freeze Time Blade", NamedTextColor.AQUA, TextDecoration.BOLD);
            case "brake" -> Component.text("Time Brake Mace", NamedTextColor.DARK_GRAY, TextDecoration.BOLD);
            case "skip" -> Component.text("Time Skip Blade", NamedTextColor.YELLOW, TextDecoration.BOLD);
            case "reverse" -> Component.text("Time Reverse Blade", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD);
            default -> Component.text("Time Weapon", NamedTextColor.GOLD, TextDecoration.BOLD);
        };

        Component message = Component.text(player.getName(), NamedTextColor.WHITE)
                .append(Component.text(" has crafted a ", NamedTextColor.YELLOW))
                .append(weaponName)
                .append(Component.text("!", NamedTextColor.YELLOW));

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(message);
        }
        announcedWeapons.add(weaponType);
    }

    private String weaponDisplayName(String type) {
        return switch (type) {
            case "freeze" -> "Freeze Time Blade";
            case "brake" -> "Time Brake Mace";
            case "skip" -> "Time Skip Blade";
            case "reverse" -> "Time Reverse Blade";
            default -> "This TimeBound weapon";
        };
    }

    private void sendColored(Player player, NamedTextColor color, String message) {
        player.sendMessage(Component.text(message, color));
    }
}
