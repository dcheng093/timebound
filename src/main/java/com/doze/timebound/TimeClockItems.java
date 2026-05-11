package com.doze.timebound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class TimeClockItems {
    public static final String CLOCK_KEY = "time_clock";

    private TimeClockItems() {
    }

    public static ItemStack createClock(Main plugin, ClockType type) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text(type.displayName(), type.color()));
        meta.lore(List.of(
                Component.text("Reusable Time Clock", NamedTextColor.GRAY),
                Component.text(""),
                Component.text("Right-click to activate ability.", NamedTextColor.WHITE),
                Component.text("Cooldown: 60 seconds", NamedTextColor.DARK_GRAY)
        ));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(clockKey(plugin), PersistentDataType.STRING, type.key());
        item.setItemMeta(meta);
        return item;
    }

    public static ClockType getClockType(Main plugin, ItemStack item) {
        if (item == null || item.getType() != Material.CLOCK || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return ClockType.fromKey(pdc.get(clockKey(plugin), PersistentDataType.STRING));
    }

    public static NamespacedKey clockKey(Main plugin) {
        return new NamespacedKey(plugin, CLOCK_KEY);
    }
}
