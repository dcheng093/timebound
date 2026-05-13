package com.doze.timebound;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class TimeClockItems {
    public static final String CLOCK_KEY = "time_clock";

    private TimeClockItems() {
    }

    public static ItemStack createClock(Main plugin, ClockType type) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text(type.displayName(), type.color()));
        
        List<Component> lore = switch (type) {
            case FREEZE -> List.of(
                    Component.text("Ultimate Time-Freezing Device", NamedTextColor.AQUA),
                    Component.text(""),
                    Component.text("ABILITY: Right-click", NamedTextColor.WHITE),
                    Component.text("Freezes target in powdered snow", NamedTextColor.GRAY),
                    Component.text("Cooldown: 60 seconds", NamedTextColor.DARK_GRAY),
                    Component.text(""),
                    Component.text("PASSIVE: Critical Hits", NamedTextColor.AQUA),
                    Component.text("Applies powdered snow damage on critical hits", NamedTextColor.GRAY),
                    Component.text("Freeze Blade damage cap: 10 hearts", NamedTextColor.DARK_GRAY)
            );
            case BRAKE -> List.of(
                    Component.text("Temporal Brake Mechanism", NamedTextColor.DARK_GRAY),
                    Component.text(""),
                    Component.text("ABILITY: Right-click", NamedTextColor.WHITE),
                    Component.text("Applies Weakness and Glowing effect", NamedTextColor.GRAY),
                    Component.text("Disables shields and sprint", NamedTextColor.DARK_GRAY),
                    Component.text("Cooldown: 60 seconds", NamedTextColor.DARK_GRAY),
                    Component.text(""),
                    Component.text("PASSIVE: Weakening Strikes", NamedTextColor.DARK_GRAY),
                    Component.text("15% chance to inflict Weakness", NamedTextColor.GRAY)
            );
            case SKIP -> List.of(
                    Component.text("Temporal Skip Protocol", NamedTextColor.YELLOW),
                    Component.text(""),
                    Component.text("ABILITY: Right-click to Dash", NamedTextColor.WHITE),
                    Component.text("Teleports up to 14 blocks forward", NamedTextColor.GRAY),
                    Component.text("3 charges (Restores 1 every 10s)", NamedTextColor.DARK_GRAY),
                    Component.text(""),
                    Component.text("PASSIVE: Speed Stacking", NamedTextColor.YELLOW),
                    Component.text("Critical hits build speed and damage", NamedTextColor.GRAY),
                    Component.text("Max Speed 5 and Strength 2", NamedTextColor.DARK_GRAY)
            );
            case REVERSE -> List.of(
                    Component.text("Temporal Rewind Engine", NamedTextColor.LIGHT_PURPLE),
                    Component.text(""),
                    Component.text("ABILITY: Right-click to Absorb", NamedTextColor.WHITE),
                    Component.text("Absorbs incoming damage for 5s", NamedTextColor.GRAY),
                    Component.text("Releases shockwave after absorption", NamedTextColor.DARK_GRAY),
                    Component.text("Cooldown: 30 seconds", NamedTextColor.DARK_GRAY),
                    Component.text(""),
                    Component.text("PASSIVE: Rewind Health", NamedTextColor.LIGHT_PURPLE),
                    Component.text("5% chance to revert health to 5s ago", NamedTextColor.GRAY)
            );
        };
        
        meta.lore(lore);
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
