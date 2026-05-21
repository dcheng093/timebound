package com.doze.timebound;

import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

@SuppressWarnings("deprecation")
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
            int customModelData = switch (bladeType) {
                case FREEZE -> 1;
                case SKIP -> 2;
                case REVERSE -> 3;
                case BRAKE -> 4;
            };
            meta.setCustomModelData(customModelData);

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
        registerFreezeBladeRecipe(plugin);
        registerBrakeBladeRecipe(plugin);
        registerSkipBladeRecipe(plugin);
        registerReverseBladeRecipe(plugin);
    }

    public static NamespacedKey recipeKey(Main plugin, ClockType type) {
        return switch (type) {
            case FREEZE -> new NamespacedKey(plugin, "freeze_time_blade_recipe");
            case BRAKE -> new NamespacedKey(plugin, "time_brake_blade_recipe");
            case SKIP -> new NamespacedKey(plugin, "time_skip_blade_recipe");
            case REVERSE -> new NamespacedKey(plugin, "time_reverse_blade_recipe");
        };
    }

    private static void registerFreezeBladeRecipe(Main plugin) {
        NamespacedKey key = recipeKey(plugin, ClockType.FREEZE);
        Bukkit.removeRecipe(key);
        ShapedRecipe recipe = new ShapedRecipe(key, createBlade("freeze"));
        recipe.shape(
            "BNB",
            "NCN",
            "BNB"
        );
        recipe.setIngredient('B', Material.BLUE_ICE);
        recipe.setIngredient('N', Material.SNOWBALL);
        recipe.setIngredient('C', Material.CLOCK);
        recipe.setGroup("time_weapons");
        Bukkit.addRecipe(recipe);
        plugin.getLogger().info("Registered Freeze Time Blade recipe");
    }

    private static void registerBrakeBladeRecipe(Main plugin) {
        NamespacedKey key = recipeKey(plugin, ClockType.BRAKE);
        Bukkit.removeRecipe(key);
        ShapedRecipe recipe = new ShapedRecipe(key, createBlade("brake"));
        recipe.shape(
            "IRI",
            "RCR",
            "ARA"
        );
        recipe.setIngredient('I', Material.IRON_BLOCK);
        recipe.setIngredient('R', Material.REDSTONE_BLOCK);
        recipe.setIngredient('A', Material.ANVIL);
        recipe.setIngredient('C', Material.CLOCK);
        recipe.setGroup("time_weapons");
        
        Bukkit.addRecipe(recipe);
        plugin.getLogger().info("Registered Time Brake Mace recipe");
    }

    private static void registerSkipBladeRecipe(Main plugin) {
        NamespacedKey key = recipeKey(plugin, ClockType.SKIP);
        Bukkit.removeRecipe(key);
        ShapedRecipe recipe = new ShapedRecipe(key, createBlade("skip"));
        recipe.shape(
            "GFG",
            "FCF",
            "GFG"
        );
        recipe.setIngredient('G', Material.GOLD_BLOCK);
        recipe.setIngredient('F', Material.FEATHER);
        recipe.setIngredient('C', Material.CLOCK);
        recipe.setGroup("time_weapons");
        Bukkit.addRecipe(recipe);
        plugin.getLogger().info("Registered Time Skip Blade recipe");
    }

    private static void registerReverseBladeRecipe(Main plugin) {
        NamespacedKey key = recipeKey(plugin, ClockType.REVERSE);
        Bukkit.removeRecipe(key);
        ShapedRecipe recipe = new ShapedRecipe(key, createBlade("reverse"));
        recipe.shape(
            "AEA",
            "ECE",
            "AEA"
        );
        recipe.setIngredient('A', Material.AMETHYST_BLOCK);
        recipe.setIngredient('E', Material.END_ROD);
        recipe.setIngredient('C', Material.CLOCK);
        recipe.setGroup("time_weapons");
        
        Bukkit.addRecipe(recipe);
        plugin.getLogger().info("Registered Time Reverse Blade recipe");
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
                ChatColor.AQUA + "" + ChatColor.BOLD + "Lunar Dial Star",
                ChatColor.AQUA + "" + ChatColor.BOLD + "Lunar Dial",
                Material.NETHERITE_SWORD,
                "freeze_time_blade",
                List.of(
                        "",
                        ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "TEMPORAL ARTIFACT",
                        ChatColor.GRAY + "A weapon that freezes fate itself.",
                        "",
                        ChatColor.AQUA + "" + ChatColor.BOLD + "KEYBINDS",
                        ChatColor.GRAY + "Hold in main hand. Use F.",
                        ChatColor.WHITE + "F: Time Lock",
                        ChatColor.WHITE + "Sneak + F: Temporal Domain",
                        "",
                        ChatColor.AQUA + "" + ChatColor.BOLD + "SKILL  " + ChatColor.DARK_GRAY + "(60s CD)",
                        ChatColor.GRAY + "Projectile freeze.",
                        ChatColor.WHITE + "Stops target movement for 5s.",
                        ChatColor.WHITE + "Hits are buffered and released after.",
                        ChatColor.DARK_GRAY + "Buffered hits: max 5 damage per hit, max 5 hearts total.",
                        "",
                        ChatColor.BLUE + "" + ChatColor.BOLD + "ULTIMATE  " + ChatColor.DARK_GRAY + "(5 kills, 10s)",
                        ChatColor.GRAY + "Sneak + F.",
                        ChatColor.WHITE + "Freeze the entire world except you.",
                        ChatColor.WHITE + "Hits become unavoidable.",
                        "",
                        ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "PASSIVE",
                        ChatColor.WHITE + "Critical hits: 5% chance to slow + freeze."
                )
        ),
        BRAKE(
                "brake",
                ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Chrono Lock Star",
                ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Chrono Lock",
                Material.MACE,
                "time_brake_blade",
                List.of(
                        "",
                        ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "TEMPORAL ARTIFACT",
                        ChatColor.GRAY + "A weapon that weakens time's defenders.",
                        "",
                        ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "KEYBINDS",
                        ChatColor.GRAY + "Hold in main hand. Use F.",
                        ChatColor.WHITE + "F: Neutralize",
                        ChatColor.WHITE + "Sneak + F: Temporal Deceleration",
                        "",
                        ChatColor.GRAY + "" + ChatColor.BOLD + "SKILL  " + ChatColor.DARK_GRAY + "(60s CD)",
                        ChatColor.GRAY + "Weaken a target and disable shields.",
                        ChatColor.WHITE + "Weakness + Slowness (5s).",
                        ChatColor.WHITE + "Shield stun/disable.",
                        "",
                        ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "ULTIMATE  " + ChatColor.DARK_GRAY + "(5 kills, 10s)",
                        ChatColor.GRAY + "Sneak + F.",
                        ChatColor.WHITE + "Everything becomes slowed and weakened.",
                        ChatColor.WHITE + "Hits become unavoidable.",
                        "",
                        ChatColor.BLACK + "" + ChatColor.BOLD + "PASSIVE",
                        ChatColor.WHITE + "15% chance on hit: weaken enemy."
                )
        ),
        SKIP(
                "skip",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Flashstep Star",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Flashstep",
                Material.NETHERITE_SWORD,
                "time_skip_blade",
                List.of(
                        "",
                        ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "TEMPORAL ARTIFACT",
                        ChatColor.GRAY + "A blade that outruns the present.",
                        "",
                        ChatColor.YELLOW + "" + ChatColor.BOLD + "KEYBINDS",
                        ChatColor.GRAY + "Hold in main hand. Use F.",
                        ChatColor.WHITE + "F: Transmission",
                        ChatColor.WHITE + "Sneak + F: Time Acceleration",
                        "",
                        ChatColor.YELLOW + "" + ChatColor.BOLD + "SKILL  " + ChatColor.DARK_GRAY + "(3s CD, 3 charges)",
                        ChatColor.GRAY + "Directional teleport (14 blocks).",
                        ChatColor.DARK_GRAY + "Regen: 1 charge / 10s (full in 10s).",
                        "",
                        ChatColor.GOLD + "" + ChatColor.BOLD + "ULTIMATE  " + ChatColor.DARK_GRAY + "(5 kills, 10s)",
                        ChatColor.GRAY + "Sneak + F.",
                        ChatColor.WHITE + "You become faster than everything.",
                        ChatColor.WHITE + "Others appear slowed.",
                        "",
                        ChatColor.RED + "" + ChatColor.BOLD + "PASSIVE",
                        ChatColor.WHITE + "Critical hits grant Velocity Stacks (max 10).",
                        ChatColor.DARK_GRAY + "Taking damage removes 1 stack."
                )
        ),
        REVERSE(
                "reverse",
                ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Requiem Star",
                ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Requiem",
                Material.NETHERITE_SWORD,
                "time_reverse_blade",
                List.of(
                        "",
                        ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "TEMPORAL ARTIFACT",
                        ChatColor.GRAY + "A blade that remembers what you lost.",
                        "",
                        ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "KEYBINDS",
                        ChatColor.GRAY + "Hold in main hand. Use F.",
                        ChatColor.WHITE + "F: Shock Absorb",
                        ChatColor.WHITE + "Sneak + F: Bites The Dust",
                        "",
                        ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "SKILL  " + ChatColor.DARK_GRAY + "(30s CD)",
                        ChatColor.GRAY + "Absorb damage, then release a shockwave.",
                        "",
                        ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "ULTIMATE  " + ChatColor.DARK_GRAY + "(5 kills)",
                        ChatColor.GRAY + "Sneak + F.",
                        ChatColor.WHITE + "Rewind everything within 50 blocks by 5 seconds.",
                        ChatColor.DARK_GRAY + "Can revive very recent deaths.",
                        "",
                        ChatColor.DARK_RED + "" + ChatColor.BOLD + "PASSIVE",
                        ChatColor.WHITE + "Critical hits: 3-5% chance to rewind your health 5 seconds."
                )
        );
        private final String commandName;
        private final String starName;
        private final String bladeName;
        private final Material bladeMaterial;
        private final List<String> lore;
        BladeType(String commandName, String starName, String bladeName, Material bladeMaterial, String recipeKey, List<String> lore) {
            this.commandName = commandName;
            this.starName = starName;
            this.bladeName = bladeName;
            this.bladeMaterial = bladeMaterial;
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
