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
                    Component.text("A pocket clock that controls time.", NamedTextColor.AQUA),
                    Component.text(""),
                    Component.text("KEYBINDS", NamedTextColor.AQUA),
                    Component.text("Hold in offhand.", NamedTextColor.GRAY),
                    Component.text("F: Time Stop", NamedTextColor.WHITE),
                    Component.text(""),
                    Component.text("ABILITY", NamedTextColor.WHITE),
                    Component.text("Freeze everything in range for 5 seconds.", NamedTextColor.GRAY),
                    Component.text("After: freezing effect for 10 seconds.", NamedTextColor.DARK_GRAY),
                    Component.text("Cooldown: 180 seconds.", NamedTextColor.DARK_GRAY)
            );
            case BRAKE -> List.of(
                    Component.text("A clock that breaks time.", NamedTextColor.DARK_GRAY),
                    Component.text(""),
                    Component.text("KEYBINDS", NamedTextColor.GRAY),
                    Component.text("Hold in offhand.", NamedTextColor.DARK_GRAY),
                    Component.text("F: Time Shatter", NamedTextColor.WHITE),
                    Component.text(""),
                    Component.text("ABILITY", NamedTextColor.WHITE),
                    Component.text("Slow and weaken a target.", NamedTextColor.GRAY),
                    Component.text("Range: 30 blocks (line of sight).", NamedTextColor.DARK_GRAY),
                    Component.text("Cooldown: 60 seconds.", NamedTextColor.DARK_GRAY),
                    Component.text("Applies: Slowness II, Weakness I, Glowing.", NamedTextColor.DARK_GRAY)
            );
            case SKIP -> List.of(
                    Component.text("A stopwatch that speeds up the flow of time.", NamedTextColor.YELLOW),
                    Component.text(""),
                    Component.text("KEYBINDS", NamedTextColor.YELLOW),
                    Component.text("Hold in offhand.", NamedTextColor.DARK_GRAY),
                    Component.text("F: Time Skip", NamedTextColor.WHITE),
                    Component.text(""),
                    Component.text("ABILITY", NamedTextColor.WHITE),
                    Component.text("Directional teleport (8 blocks) + Speed III (8s).", NamedTextColor.GRAY),
                    Component.text("Cooldown: 45 seconds.", NamedTextColor.DARK_GRAY)
            );
            case REVERSE -> List.of(
                    Component.text("A clock that rewinds anything.", NamedTextColor.LIGHT_PURPLE),
                    Component.text(""),
                    Component.text("KEYBINDS", NamedTextColor.LIGHT_PURPLE),
                    Component.text("Hold in offhand.", NamedTextColor.DARK_GRAY),
                    Component.text("F: Roundabout", NamedTextColor.WHITE),
                    Component.text(""),
                    Component.text("ABILITY", NamedTextColor.WHITE),
                    Component.text("Heal 2.5 hearts and restore saturation.", NamedTextColor.GRAY),
                    Component.text("Cooldown: 45 seconds.", NamedTextColor.DARK_GRAY)
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
