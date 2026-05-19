package com.doze.timebound;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

public class AdvancementManager {

    private final Main plugin;

    public AdvancementManager(Main plugin) {
        this.plugin = plugin;
    }

    public void registerAdvancements() {
        // Advancements are loaded from the data folder during plugin startup
        // This method is retained for compatibility but advancement loading is handled by the plugin.yml registration
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
        Advancement advancement = Bukkit.getAdvancement(new NamespacedKey(plugin, id));
        if (advancement == null) {
            plugin.getLogger().warning("Cannot grant missing advancement timebound:%s to %s".formatted(id, player.getName()));
            return;
        }

        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        if (!progress.isDone() && progress.getRemainingCriteria().contains(criteria)) {
            progress.awardCriteria(criteria);
        }
    }
}
