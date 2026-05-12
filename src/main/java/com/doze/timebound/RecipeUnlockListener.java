package com.doze.timebound;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class RecipeUnlockListener implements Listener {
    private final Set<UUID> notifiedPlayers = new HashSet<>();

    public RecipeUnlockListener(JavaPlugin plugin) {
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getAmount() == 0) return;
        
        String weaponType = TimeBladeItems.getTaggedType(result);
        if (weaponType == null) return;
        
        UUID playerId = player.getUniqueId();
        
        // Check if player hasn't already been notified about this recipe
        if (!notifiedPlayers.contains(playerId)) {
            notifiedPlayers.add(playerId);
            showRecipeUnlockMessage(player, weaponType);
        }
    }

    private void showRecipeUnlockMessage(Player player, String weaponType) {
        Component message = switch (weaponType) {
            case "freeze" -> Component.text("Recipe Unlocked: ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("Freeze Time Blade", NamedTextColor.AQUA, TextDecoration.BOLD));
            case "brake" -> Component.text("Recipe Unlocked: ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("Time Brake Mace", NamedTextColor.DARK_GRAY, TextDecoration.BOLD));
            case "skip" -> Component.text("Recipe Unlocked: ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("Time Skip Blade", NamedTextColor.YELLOW, TextDecoration.BOLD));
            case "reverse" -> Component.text("Recipe Unlocked: ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("Time Reverse Blade", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
            default -> Component.text("Recipe Unlocked!", NamedTextColor.GOLD, TextDecoration.BOLD);
        };
        
        // Show as action bar and send message
        player.sendActionBar(message);
        player.sendMessage(Component.text(""));
        player.sendMessage(message);
        player.sendMessage(Component.text(""));
    }
}
