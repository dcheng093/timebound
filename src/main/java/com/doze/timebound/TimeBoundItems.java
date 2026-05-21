package com.doze.timebound;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class TimeBoundItems {
    public static final String MASTER_KEY = "master_of_time";
    private TimeBoundItems() {
    }

    public static boolean isTimeItem(Main plugin, ItemStack item) {
        return getWeaponType(item) != null || getClockType(plugin, item) != null || isMasterOfTime(plugin, item);
    }

    public static String getWeaponType(ItemStack item) {
        return TimeBladeItems.getTaggedType(item);
    }

    public static ClockType getClockType(Main plugin, ItemStack item) {
        return TimeClockItems.getClockType(plugin, item);
    }

    public static String displayName(Main plugin, ItemStack item) {
        if (isMasterOfTime(plugin, item)) {
            return "Eternity";
        }
        String weaponType = getWeaponType(item);
        if (weaponType != null) {
            return switch (weaponType) {
                case "freeze" -> "Lunar Dial";
                case "brake" -> "Chrono Lock";
                case "skip" -> "Flashstep";
                case "reverse" -> "Requiem";
                default -> "Time Weapon";
            };
        }
        ClockType clockType = getClockType(plugin, item);
        if (clockType != null) {
            return clockType.displayName();
        }
        return "TimeBound item";
    }

    public static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    public static boolean isMasterOfTime(Main plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin, MASTER_KEY), PersistentDataType.BYTE);
    }
}
