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
        // advancements are automatically loaded from the plugin's bundled data pack resources, so uhhhh kinda useless?
    }

    public void grantWeaponAdvancement(Player player, String weaponType) {
        grant(player, "root", "timebound");
        grant(player, "crafted_" + weaponType, "crafted");
    }

    public void grantMasterAdvancement(Player player) {
        grant(player, "root", "timebound");
        grant(player, "crafted_master", "crafted");
    }

    private void grant(Player player, String id, String criteria) {
        Advancement advancement = cache.computeIfAbsent(id,
            key -> Bukkit.getAdvancement(new NamespacedKey(plugin, key)));

        if (advancement == null) {
            plugin.getLogger().warning(
                "Cannot grant missing advancement timebound:%s to %s"
                    .formatted(id, player.getName())
            );
            return;
        }

        AdvancementProgress progress = player.getAdvancementProgress(advancement);

        if (progress.getRemainingCriteria().contains(criteria)) {
            progress.awardCriteria(criteria);
        }
    }
}
