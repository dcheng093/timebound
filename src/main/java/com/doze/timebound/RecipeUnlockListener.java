package com.doze.timebound;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Duplicate restriction and recipe unlocks driven by the global UID registry + scans.
 *
 * Strict mode:
 * - Blocks crafting duplicates (weapons/clocks/master) if any exist globally.
 *
 * Test mode:
 * - Allows crafting but logs violations.
 */
public final class RecipeUnlockListener implements Listener {
    private final Main plugin;

    public RecipeUnlockListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            ensureUids(event.getPlayer());
            unlockRecipesFromInventory(event.getPlayer(), true);
            plugin.getGlobalScanner().requestScan(GlobalTimeItemScanner.Reason.LOGIN);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        StarTimerManager.stop(event.getPlayer());
        plugin.getGlobalScanner().requestScan(GlobalTimeItemScanner.Reason.LOGOUT);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> unlockRecipesFromInventory(event.getPlayer(), true));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        ItemStack result = inv.getResult();
        if (result == null) return;

        boolean testMode = plugin.getConfig().getBoolean("testMode", false);

        String weaponType = TimeBladeItems.getTaggedType(result);
        if (weaponType != null) {
            if (!testMode && plugin.getGlobalRegistry().anyWeaponExists(weaponType)) {
                inv.setResult(null);
                return;
            }
            if (!craftContainsClock(inv, ClockType.fromKey(weaponType))) {
                inv.setResult(null);
                return;
            }
            return;
        }

        if (TimeBoundItems.isMasterOfTime(plugin, result)) {
            if (!isValidMasterRecipeMatrix(inv.getMatrix())) {
                inv.setResult(null);
                return;
            }
            if (!testMode && plugin.getGlobalRegistry().anyMasterExists()) {
                inv.setResult(null);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        ItemStack result = event.getResult();
        String type = TimeBladeItems.getTaggedType(result);
        if (type == null) return;

        boolean testMode = plugin.getConfig().getBoolean("testMode", false);
        if (!testMode && plugin.getGlobalRegistry().anyWeaponExists(type)) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        boolean testMode = plugin.getConfig().getBoolean("testMode", false);
        ItemStack result = event.getCurrentItem();
        if (result == null) return;

        // Eternity craft (Master of Time legacy key)
        if (TimeBoundItems.isMasterOfTime(plugin, result)) {
            if (!isValidMasterRecipeMatrix(event.getInventory().getMatrix())) {
                event.setCancelled(true);
                event.getInventory().setResult(null);
                return;
            }

            if (plugin.getGlobalRegistry().anyMasterExists()) {
                if (!testMode) {
                    event.setCancelled(true);
                    event.getInventory().setResult(null);
                    player.sendMessage(Component.text("Eternity already exists.", NamedTextColor.RED));
                    return;
                }
                plugin.getGlobalRegistry().logDuplicateViolation(player.getName() + " crafted duplicate Eternity.");
            }

            // Replace static recipe result with a fresh UID instance.
            event.setCurrentItem(MasterOfTimeItems.createCrafted(plugin));
            MasterOfTimeItems.announceCraft(plugin, player);
            plugin.getAdvancementManager().grantMasterAdvancement(player);

            // Special trigger: crafting the legendary resets availability of the 4 base blades.
            // The registry is recomputed from scans; we force an immediate scan to reflect consumption.
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getGlobalScanner().requestScan(GlobalTimeItemScanner.Reason.CRAFT));
            return;
        }

        // Timeblade craft
        String weaponType = TimeBladeItems.getTaggedType(result);
        if (weaponType != null) {
            if (plugin.getGlobalRegistry().anyWeaponExists(weaponType)) {
                if (!testMode) {
                    event.setCancelled(true);
                    event.getInventory().setResult(null);
                    denyCraft(player, weaponType);
                    return;
                }
                plugin.getGlobalRegistry().logDuplicateViolation(player.getName() + " crafted duplicate weapon: " + weaponType);
            }
            if (!craftContainsClock(event.getInventory(), ClockType.fromKey(weaponType))) {
                event.setCancelled(true);
                event.getInventory().setResult(null);
                denyCraft(player, weaponType);
                return;
            }

            ItemStack crafted = result.clone();
            TimeItemUid.ensure(plugin, crafted);
            event.setCurrentItem(crafted);

            announceWeaponCraft(player, weaponType);
            plugin.getAdvancementManager().grantWeaponAdvancement(player, weaponType);
            Bukkit.getScheduler().runTask(plugin, () -> {
                unlockRecipesFromInventory(player, false);
                plugin.getGlobalScanner().requestScan(GlobalTimeItemScanner.Reason.CRAFT);
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> unlockRecipesFromInventory(player, false));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> unlockRecipesFromInventory(player, false));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                unlockRecipesFromInventory(player, false);
                plugin.getGlobalScanner().requestScan(GlobalTimeItemScanner.Reason.PERIODIC);
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Never delete/deny pickups. The global registry is scan-based and can be stale; denying pickup causes
        // false positives (exactly the bug you're seeing). Duplicate prevention is enforced at creation time
        // (craft/give/spawn), not on transfer of an existing world item entity.
        ItemStack stack = event.getItem().getItemStack();
        if (TimeBoundItems.isTimeItem(plugin, stack) || TimeBoundItems.isMasterOfTime(plugin, stack)) {
            ItemStack copy = stack.clone();
            TimeItemUid.ensure(plugin, copy);
            event.getItem().setItemStack(copy);
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            unlockRecipesFromInventory(player, true);
            plugin.getGlobalScanner().requestScan(GlobalTimeItemScanner.Reason.PERIODIC);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPickupMonitor(EntityPickupItemEvent event) {
        // If some other plugin cancels the pickup, make sure we don't end up with a stale global registry
        // that blocks crafting/pickups due to "ghost" entries.
        if (!(event.getEntity() instanceof Player)) return;
        if (!event.isCancelled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> plugin.getGlobalScanner().requestScan(GlobalTimeItemScanner.Reason.PERIODIC));
    }

    public void unlockRecipesFromInventory(Player player, boolean toast) {
        ensureUids(player);
        // Blade recipes unlock when the player holds the matching clock.
        for (ItemStack item : player.getInventory().getContents()) {
            ClockType type = TimeClockItems.getClockType(plugin, item);
            if (type == null) continue;

            NamespacedKey recipeKey = TimeBladeItems.recipeKey(plugin, type);
            if (!player.hasDiscoveredRecipe(recipeKey)) {
                player.discoverRecipe(recipeKey);
                if (toast) {
                    player.getWorld().playSound(player.getLocation(), Sound.UI_TOAST_IN, 0.7f, 1.0f);
                }
            }
        }

        // Master recipe unlock when the player has all four blades at least once.
        EnumSet<ClockType> blades = EnumSet.noneOf(ClockType.class);
        for (ItemStack item : player.getInventory().getContents()) {
            String weaponType = TimeBladeItems.getTaggedType(item);
            ClockType ct = ClockType.fromKey(weaponType);
            if (ct != null) blades.add(ct);
        }
        if (blades.containsAll(EnumSet.allOf(ClockType.class))) {
            NamespacedKey key = MasterOfTimeItems.recipeKey(plugin);
            if (!player.hasDiscoveredRecipe(key)) {
                player.discoverRecipe(key);
                if (toast) player.getWorld().playSound(player.getLocation(), Sound.UI_TOAST_IN, 0.7f, 1.0f);
            }
        }

        player.updateInventory();
    }

    private void ensureUids(Player player) {
        boolean changed = false;
        var inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        if (contents != null) {
            for (int i = 0; i < contents.length; i++) {
                ItemStack it = contents[i];
                if (!TimeBoundItems.isTimeItem(plugin, it)) continue;
                if (TimeItemUid.has(plugin, it)) continue;
                ItemStack copy = it.clone();
                TimeItemUid.ensure(plugin, copy);
                contents[i] = copy;
                changed = true;
            }
            if (changed) inv.setContents(contents);
        }
        ItemStack off = inv.getItemInOffHand();
        if (TimeBoundItems.isTimeItem(plugin, off) && !TimeItemUid.has(plugin, off)) {
            ItemStack copy = off.clone();
            TimeItemUid.ensure(plugin, copy);
            inv.setItemInOffHand(copy);
            changed = true;
        }
        ItemStack main = inv.getItemInMainHand();
        if (TimeBoundItems.isTimeItem(plugin, main) && !TimeItemUid.has(plugin, main)) {
            ItemStack copy = main.clone();
            TimeItemUid.ensure(plugin, copy);
            inv.setItemInMainHand(copy);
            changed = true;
        }
        if (changed) player.updateInventory();
    }

    public boolean canClaimClock(Player player, ClockType type) {
        // Bugfix requirement: clock obtain restriction is per-player per-type (not global).
        return !playerHasClock(player, type);
    }

    public void recordClockClaim(Player player, ClockType type) {
        // Registry is driven by scans; claiming a clock changes inventories, so rescan soon.
        unlockRecipesFromInventory(player, true);
        plugin.getGlobalScanner().requestScan(GlobalTimeItemScanner.Reason.PERIODIC);
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

    private boolean isValidMasterRecipeMatrix(ItemStack[] matrix) {
        if (matrix == null || matrix.length < 9) return false;

        // Expected shape:
        // 0 1 2
        // 3 4 5
        // 6 7 8
        // Blades at 1,3,5,7 and Nether Star at 4.
        if (matrix[4] == null || matrix[4].getType() != org.bukkit.Material.NETHER_STAR) return false;

        int[] bladeSlots = {1, 3, 5, 7};
        Set<String> seen = new HashSet<>();
        for (int slot : bladeSlots) {
            ItemStack s = matrix[slot];
            String wt = TimeBladeItems.getTaggedType(s);
            if (wt == null) return false;
            if (!EnumSet.allOf(ClockType.class).contains(ClockType.fromKey(wt))) return false;
            if (!TimeItemUid.has(plugin, s)) return false;
            seen.add(wt);
        }

        // Ensure uniqueness + must include all 4.
        return seen.size() == 4
                && seen.contains("freeze")
                && seen.contains("brake")
                && seen.contains("skip")
                && seen.contains("reverse");
    }

    private boolean playerHasWeapon(Player player, String type) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (type.equals(TimeBladeItems.getTaggedType(item))) return true;
        }
        return false;
    }

    private boolean playerHasClock(Player player, ClockType type) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (TimeClockItems.getClockType(plugin, item) == type) return true;
        }
        return false;
    }

    private void denyCraft(Player player, String weaponType) {
        String name = weaponDisplayName(weaponType);
        Component msg = Component.text(player.getName(), NamedTextColor.WHITE)
                .append(Component.text(" attempted to craft a duplicate ", NamedTextColor.YELLOW))
                .append(Component.text(name, NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" (blocked).", NamedTextColor.RED));
        Bukkit.broadcast(msg);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.7f);
        plugin.getLogger().warning(player.getName() + " attempted to craft duplicate " + weaponType);
    }

    private void announceWeaponCraft(Player player, String weaponType) {
        Component weaponName = switch (weaponType) {
            case "freeze" -> Component.text("Lunar Dial", NamedTextColor.AQUA, TextDecoration.BOLD);
            case "brake" -> Component.text("Chrono Lock", NamedTextColor.DARK_GRAY, TextDecoration.BOLD);
            case "skip" -> Component.text("Flashstep", NamedTextColor.YELLOW, TextDecoration.BOLD);
            case "reverse" -> Component.text("Requiem", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD);
            default -> Component.text("Time Weapon", NamedTextColor.GOLD, TextDecoration.BOLD);
        };

        Component message = Component.text(player.getName(), NamedTextColor.WHITE)
                .append(Component.text(" has crafted a ", NamedTextColor.YELLOW))
                .append(weaponName)
                .append(Component.text("!", NamedTextColor.YELLOW));

        Bukkit.broadcast(message);
    }

    private String weaponDisplayName(String type) {
        return switch (type) {
            case "freeze" -> "Lunar Dial";
            case "brake" -> "Chrono Lock";
            case "skip" -> "Flashstep";
            case "reverse" -> "Requiem";
            default -> "This TimeBound weapon";
        };
    }
}
