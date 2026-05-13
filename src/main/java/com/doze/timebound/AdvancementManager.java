package com.doze.timebound;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class AdvancementManager {
    private final JavaPlugin plugin;

    public AdvancementManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadAdvancements();
    }

    private void loadAdvancements() {
        Path advancementsDir = Paths.get(Bukkit.getWorlds().get(0).getWorldFolder().getAbsolutePath())
                .getParent()
                .resolve("advancements_custom");
        
        try {
            Files.createDirectories(advancementsDir);
        } catch (IOException e) {
            plugin.getLogger().warning(() -> String.format("Advancement not found: %s", e.getMessage()));
        }
    }

    public void grantWeaponAdvancement(Player player, String weaponType) {
        NamespacedKey advKey = new NamespacedKey("timebound", "crafted_" + weaponType);
        Advancement advancement = Bukkit.getAdvancement(advKey);
        
        if (advancement != null) {
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            if (!progress.isDone()) {
                progress.awardCriteria("crafted");
            }
        }
    }
}
