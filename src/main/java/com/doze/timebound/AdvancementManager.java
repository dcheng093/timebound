package com.doze.timebound;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

public class AdvancementManager {
    private static final List<String> ADVANCEMENTS = List.of(
            "root",
            "crafted_freeze",
            "crafted_brake",
            "crafted_skip",
            "crafted_reverse"
    );

    private final Main plugin;

    public AdvancementManager(Main plugin) {
        this.plugin = plugin;
    }

    public void registerAdvancements() {
        for (String id : ADVANCEMENTS) {
            NamespacedKey key = new NamespacedKey(plugin, id);
            try {
                Bukkit.getUnsafe().removeAdvancement(key);
            } catch (IllegalArgumentException ignored) {
            }
        }

        for (String id : ADVANCEMENTS) {
            NamespacedKey key = new NamespacedKey(plugin, id);
            String path = "data/timebound/advancements/" + id + ".json";
            try (InputStream stream = plugin.getResource(path)) {
                if (stream == null) {
                    plugin.getLogger().warning("Missing advancement resource: " + path);
                    continue;
                }
                String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                Bukkit.getUnsafe().loadAdvancement(key, json);
            } catch (IOException | IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to register advancement " + key, e);
            }
        }
    }

    public void grantWeaponAdvancement(Player player, String weaponType) {
        grant(player, "root", "timebound");
        grant(player, "crafted_" + weaponType, "crafted");
    }

    private void grant(Player player, String id, String criteria) {
        Advancement advancement = Bukkit.getAdvancement(new NamespacedKey(plugin, id));
        if (advancement == null) {
            plugin.getLogger().warning("Cannot grant missing advancement timebound:" + id + " to " + player.getName());
            return;
        }

        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        if (!progress.isDone() && progress.getRemainingCriteria().contains(criteria)) {
            progress.awardCriteria(criteria);
        }
    }
}
