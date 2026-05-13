package com.doze.timebound;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class TimeBoundItems {
    private TimeBoundItems() {
    }

    public static boolean isTimeItem(Main plugin, ItemStack item) {
        return getWeaponType(item) != null || getClockType(plugin, item) != null;
    }

    public static String getWeaponType(ItemStack item) {
        return TimeBladeItems.getTaggedType(item);
    }

    public static ClockType getClockType(Main plugin, ItemStack item) {
        return TimeClockItems.getClockType(plugin, item);
    }

    public static String displayName(Main plugin, ItemStack item) {
        String weaponType = getWeaponType(item);
        if (weaponType != null) {
            return switch (weaponType) {
                case "freeze" -> "Freeze Time Blade";
                case "brake" -> "Time Brake Mace";
                case "skip" -> "Time Skip Blade";
                case "reverse" -> "Time Reverse Blade";
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
}
