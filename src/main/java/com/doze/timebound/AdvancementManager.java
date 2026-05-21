package com.doze.timebound;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

public class AdvancementManager {

    private final Main plugin;
    private final Map<String, Advancement> cache = new HashMap<>();

    public AdvancementManager(Main plugin) {
        this.plugin = plugin;
    }

    public void registerAdvancements() {
        // advancements are automatically loaded from the plugin's bundled data pack resources
        Advancement root = Bukkit.getAdvancement(new NamespacedKey(plugin, "root"));
        if (root != null) {
            cache.put("root", root);
            plugin.getLogger().info("Advancement 'root' registered successfully");
        } else {
            plugin.getLogger().warning("Failed to register advancement 'root' - advancement not found by Bukkit");
        }
    }

    public void grantWeaponAdvancement(Player player, String weaponType) {
        grant(player, "root", "timebound");
        grant(player, "crafted_" + weaponType, "crafted");
        plugin.getLogger().info(String.format("Granted weapon advancement for %s to %s", weaponType, player.getName()));
    }

    public void grantMasterAdvancement(Player player) {
        grant(player, "root", "timebound");
        grant(player, "crafted_master", "crafted");
        plugin.getLogger().info(String.format("Granted master advancement to %s", player.getName()));
    }

    private void grant(Player player, String id, String criteria) {
        Advancement advancement = cache.computeIfAbsent(id,
            key -> Bukkit.getAdvancement(new NamespacedKey(plugin, key)));

        if (advancement == null) {
            plugin.getLogger().warning(
                "Cannot grant missing advancement timebound:%s to %s - advancement not loaded by Bukkit. Check that the JSON file exists in src/main/resources/data/timebound/advancements/"
                    .formatted(id, player.getName())
            );
            return;
        }

        try {
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            
            if (progress.getRemainingCriteria().contains(criteria)) {
                progress.awardCriteria(criteria);
                plugin.getLogger().info(String.format("Awarded criterion '%s' for advancement '%s' to %s", criteria, id, player.getName()));
            } else if (progress.isDone()) {
                // advancement already complete, that's fine
                plugin.getLogger().info(String.format("Advancement '%s' already complete for %s", id, player.getName()));
            } else {
                plugin.getLogger().warning(String.format("Criterion '%s' not found in remaining criteria for advancement '%s'. Remaining: %s", criteria, id, progress.getRemainingCriteria()));
            }
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "unknown error";
            plugin.getLogger().warning(String.format("Error granting advancement '%s' to %s: %s", id, player.getName(), msg));
        }
    }
}
