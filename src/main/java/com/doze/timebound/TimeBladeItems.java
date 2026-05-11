package com.doze.timebound;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;

public final class TimeBladeItems {

    public static final String TIME_WEAPON_KEY = "time_weapon";

    private TimeBladeItems() {
    }

    public static ItemStack createStar(String type) {
        BladeType bladeType = BladeType.from(type);
        if (bladeType == null) return null;

        return namedItem(Material.NETHER_STAR, bladeType.starName);
    }

    public static ItemStack createBlade(String type) {
        BladeType bladeType = BladeType.from(type);
        if (bladeType == null) return null;

        ItemStack item = namedItem(bladeType.bladeMaterial, bladeType.bladeName);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (bladeType.bladeMaterial == Material.MACE) {
                meta.addEnchant(Enchantment.DENSITY, 3, true);
                meta.addEnchant(Enchantment.WIND_BURST, 1, true);
                meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
            } else {
                meta.addEnchant(Enchantment.SHARPNESS, 5, true);
                meta.addEnchant(Enchantment.LOOTING, 3, true);
                meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
                meta.addEnchant(Enchantment.SWEEPING_EDGE, 3, true);
            }
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            meta.setLore(bladeType.lore);
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(Main.getInstance(), TIME_WEAPON_KEY),
                    PersistentDataType.STRING,
                    bladeType.commandName
            );

            // CUSTOM MODEL DATA FOR YOUR RESOURCE PACK
            if (bladeType == BladeType.FREEZE) {
                meta.setCustomModelData(1);
            } else if (bladeType == BladeType.SKIP) {
                meta.setCustomModelData(2);
            } else if (bladeType == BladeType.REVERSE) {
                meta.setCustomModelData(3);
            } else if (bladeType == BladeType.BRAKE) {
                meta.setCustomModelData(4);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    public static boolean isBlade(ItemStack item, String name) {
        if (item == null) return false;
        return hasNameAndType(item, item.getType(), name);
    }

    public static String getTaggedType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(Main.getInstance(), TIME_WEAPON_KEY),
                PersistentDataType.STRING
        );
    }

    public static void registerRecipes(Main plugin) {
        for (BladeType type : BladeType.values()) {
            NamespacedKey key = new NamespacedKey(plugin, type.recipeKey);
            Bukkit.removeRecipe(key);

            ShapelessRecipe recipe = new ShapelessRecipe(key, createBlade(type.commandName));
            recipe.addIngredient(type.bladeMaterial);
            recipe.addIngredient(new org.bukkit.inventory.RecipeChoice.ExactChoice(createStar(type.commandName)));
            Bukkit.addRecipe(recipe);
        }
    }

    private static ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static boolean hasNameAndType(ItemStack item, Material material, String name) {
        if (item == null || item.getType() != material) return false;
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return false;
        String itemName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        return itemName != null && itemName.equalsIgnoreCase(name);
    }

    public enum BladeType {
        FREEZE(
                "freeze",
                ChatColor.AQUA + "" + ChatColor.BOLD + "Freeze Star",
                ChatColor.AQUA + "" + ChatColor.BOLD + "Freeze Time Blade",
                Material.NETHERITE_SWORD,
                "freeze_time_blade",
                List.of(
                        "",
                        ChatColor.AQUA + "" + ChatColor.BOLD + "ABILITY",
                        ChatColor.GRAY + "Press F while aiming at an entity.",
                        ChatColor.WHITE + "Freezes the target and inflicts powdered snow.",
                        ChatColor.DARK_GRAY + "Ability Cooldown: 60 seconds.",
                        "",
                        ChatColor.BLUE + "" + ChatColor.BOLD + "ULT",
                        ChatColor.GRAY + "Sneak + F.",
                        ChatColor.WHITE + "Freezes everyone on the server except you.",
                        ChatColor.DARK_GRAY + "Ultimate Cooldown: Refill full charge after use.",
                        ChatColor.DARK_GRAY + "Ult Charge: 5 player kills with this blade.",
                        "",
                        ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "PASSIVE",
                        ChatColor.WHITE + "5% chance to apply powdered snow damage.",
                        ChatColor.WHITE + "Deals more damage the longer the target is frozen."
                )
        ),
        BRAKE(
                "brake",
                ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Time Brake Star",
                ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Time Brake Mace",
                Material.MACE,
                "time_brake_blade",
                List.of(
                        "",
                        ChatColor.GRAY + "" + ChatColor.BOLD + "ABILITY",
                        ChatColor.GRAY + "Press F while aiming at an entity.",
                        ChatColor.WHITE + "Applies brake pressure without a blindness screen.",
                        ChatColor.WHITE + "Players cannot sprint and lose shield use for 10s.",
                        ChatColor.DARK_GRAY + "Ability Cooldown: 60 seconds.",
                        "",
                        ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "ULT",
                        ChatColor.GRAY + "Sneak + F.",
                        ChatColor.WHITE + "Slows everyone on the server except you.",
                        ChatColor.DARK_GRAY + "Ultimate Cooldown: Refill full charge after use.",
                        ChatColor.DARK_GRAY + "Ult Charge: 5 player kills with this mace.",
                        "",
                        ChatColor.BLACK + "" + ChatColor.BOLD + "PASSIVE",
                        ChatColor.WHITE + "5% chance to inflict slowness on hit."
                )
        ),
        SKIP(
                "skip",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Time Skip Star",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Time Skip Blade",
                Material.NETHERITE_SWORD,
                "time_skip_blade",
                List.of(
                        "",
                        ChatColor.YELLOW + "" + ChatColor.BOLD + "ABILITY",
                        ChatColor.GRAY + "Press F.",
                        ChatColor.WHITE + "Instant 14 block teleport.",
                        ChatColor.DARK_GRAY + "Ability Cooldown: 3 seconds.",
                        ChatColor.DARK_GRAY + "Ability Charges: 3 (Restores 1 every 10s)",
                        "",
                        ChatColor.GOLD + "" + ChatColor.BOLD + "ULT",
                        ChatColor.GRAY + "Sneak + F.",
                        ChatColor.WHITE + "Slows everyone while giving you Speed IV.",
                        ChatColor.DARK_GRAY + "Ultimate Cooldown: Refill full charge after use.",
                        ChatColor.DARK_GRAY + "Ult Charge: 5 player kills with this blade.",
                        "",
                        ChatColor.RED + "" + ChatColor.BOLD + "PASSIVE",
                        ChatColor.WHITE + "Hits build speed and damage stacks.",
                        ChatColor.WHITE + "Capped at Speed 5 and Strength 2.",
                        ChatColor.WHITE + "Taking damage lowers stacks one at a time."
                )
        ),
        REVERSE(
                "reverse",
                ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Reverse Star",
                ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Time Reverse Blade",
                Material.NETHERITE_SWORD,
                "time_reverse_blade",
                List.of(
                        "",
                        ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "ABILITY",
                        ChatColor.GRAY + "Press F.",
                        ChatColor.WHITE + "Absorb damage, then release a shockwave.",
                        ChatColor.DARK_GRAY + "Ability Cooldown: 30 seconds.",
                        "",
                        ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "ULT",
                        ChatColor.GRAY + "Sneak + F.",
                        ChatColor.WHITE + "Rewinds nearby blocks and entities 5 seconds.",
                        ChatColor.WHITE + "Can revive players who just died.",
                        ChatColor.DARK_GRAY + "Ultimate Cooldown: Refill full charge after use.",
                        ChatColor.DARK_GRAY + "Ult Charge: 5 player kills with this blade.",
                        "",
                        ChatColor.DARK_RED + "" + ChatColor.BOLD + "PASSIVE",
                        ChatColor.WHITE + "5% chance to restore your health to 5s ago",
                        ChatColor.WHITE + "and reverse the target's movement."
                )
        );

        private final String commandName;
        private final String starName;
        private final String bladeName;
        private final Material bladeMaterial;
        private final String recipeKey;
        private final List<String> lore;

        BladeType(String commandName, String starName, String bladeName, Material bladeMaterial, String recipeKey, List<String> lore) {
            this.commandName = commandName;
            this.starName = starName;
            this.bladeName = bladeName;
            this.bladeMaterial = bladeMaterial;
            this.recipeKey = recipeKey;
            this.lore = lore;
        }

        public static BladeType from(String type) {
            if (type == null) return null;

            String normalized = type.toLowerCase(Locale.ROOT);
            for (BladeType bladeType : values()) {
                if (bladeType.commandName.equals(normalized)) {
                    return bladeType;
                }
            }

            return null;
        }
    }
}