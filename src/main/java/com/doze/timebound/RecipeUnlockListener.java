package com.doze.timebound;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class RecipeUnlockListener implements Listener {
    private final Set<String> craftedWeapons = new HashSet<>();
    private final JavaPlugin plugin;

    public RecipeUnlockListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getAmount() == 0) return;
        
        String weaponType = TimeBladeItems.getTaggedType(result);
        if (weaponType == null) return;
        
        UUID playerId = player.getUniqueId();
        String recipeKey = playerId + "_" + weaponType;
        
        if (hasCraftedWeapon(player, weaponType)) {
            event.setCancelled(true);
            sendColored(player, NamedTextColor.RED, "You have already crafted this weapon!");
            return;
        }
        
        markWeaponCrafted(player, weaponType);
        craftedWeapons.add(recipeKey);
        
        announceWeaponCraft(player, weaponType);
        grantAdvancement(player, weaponType);
    }

    private boolean hasCraftedWeapon(Player player, String weaponType) {
        UUID playerId = player.getUniqueId();
        String recipeKey = playerId + "_" + weaponType;
        if (craftedWeapons.contains(recipeKey)) {
            return true;
        }
        
        String nbtKey = "crafted_" + weaponType;
        return player.getPersistentDataContainer().has(
            new NamespacedKey(plugin, nbtKey),
            PersistentDataType.BYTE
        );
    }

    private void markWeaponCrafted(Player player, String weaponType) {
        String nbtKey = "crafted_" + weaponType;
        player.getPersistentDataContainer().set(
            new NamespacedKey(plugin, nbtKey),
            PersistentDataType.BYTE,
            (byte) 1
        );
    }

    private void grantAdvancement(Player player, String weaponType) {
        NamespacedKey advKey = new NamespacedKey("timebound", "crafted_" + weaponType);
        Advancement advancement = Bukkit.getAdvancement(advKey);
        if (advancement != null) {
            player.getAdvancementProgress(advancement).awardCriteria("crafted");
        }
    }

    private void announceWeaponCraft(Player player, String weaponType) {
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
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(message);
        }
    }

    private void sendColored(Player player, NamedTextColor color, String message) {
        player.sendMessage(Component.text(message, color));
    }
}
