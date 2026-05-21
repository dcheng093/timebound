package com.doze.timebound;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class MasterOfTimeItems {
    private MasterOfTimeItems() {
    }

    public static NamespacedKey recipeKey(Main plugin) {
        return new NamespacedKey(plugin, "master_of_time_recipe");
    }

    public static ItemStack createPrototype(Main plugin) {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        Component name = Component.text()
                .append(Component.text("Eternity", TextColor.color(0xFFD66E), TextDecoration.BOLD))
                .build();
        meta.displayName(name);
        meta.lore(List.of(
                Component.text(""),
                Component.text("TEMPORAL DEFIANCE", TextColor.color(0xFFB000), TextDecoration.BOLD),
                Component.text("The one that stands above all.", NamedTextColor.GRAY),
                Component.text("Master of all time itself.", NamedTextColor.DARK_GRAY),
                Component.text(""),
                Component.text("ABILITIES", TextColor.color(0x8FE3FF), TextDecoration.BOLD),
                Component.text("F: Flash", NamedTextColor.WHITE).append(Component.text("  (15s CD)", NamedTextColor.DARK_GRAY)),
                Component.text("  3x speed boost for 3 seconds (instant).", NamedTextColor.GRAY),
                Component.text("F (Charge): Temporal Disturbance", NamedTextColor.WHITE).append(Component.text("  (120s CD)", NamedTextColor.DARK_GRAY)),
                Component.text("  100 blocks: Slowness II, Glowing, Weakness I.", NamedTextColor.GRAY),
                Component.text(""),
                Component.text("ULTIMATE", TextColor.color(0xFFD66E), TextDecoration.BOLD),
                Component.text("Shift+F (Charge): Hourglass's Sanctuary", NamedTextColor.WHITE).append(Component.text("  (7 Kills)", NamedTextColor.DARK_GRAY)),
                Component.text("  Global timestop for 20s. Everyone: Glowing + Weakness I.", NamedTextColor.GRAY),
                Component.text(""),
                Component.text("PASSIVE (WHILE HELD)", TextColor.color(0xFFD66E), TextDecoration.BOLD),
                Component.text("Speed II, Strength I, Health Boost (20 hearts total).", NamedTextColor.GRAY),
                Component.text(""),
                Component.text("This item is authenticated via TimeBound UID.", NamedTextColor.DARK_GRAY)
        ));
        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        meta.addEnchant(Enchantment.LOOTING, 3, true);
        meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
        meta.addEnchant(Enchantment.SWEEPING_EDGE, 3, true);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, TimeBoundItems.MASTER_KEY), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createCrafted(Main plugin) {
        ItemStack item = createPrototype(plugin);
        TimeItemUid.ensure(plugin, item);
        return item;
    }

    public static void registerRecipe(Main plugin) {
        NamespacedKey key = recipeKey(plugin);
        Bukkit.removeRecipe(key);

        ShapedRecipe recipe = new ShapedRecipe(key, createPrototype(plugin));
        recipe.shape(
                " B ",
                "BNB",
                " B "
        );
        recipe.setIngredient('N', Material.NETHER_STAR);
        recipe.setIngredient('B', new RecipeChoice.MaterialChoice(List.of(Material.NETHERITE_SWORD, Material.MACE)));
        recipe.setGroup("time_weapons");
        Bukkit.addRecipe(recipe);
        plugin.getLogger().info("Registered Master of Time recipe");
    }

    public static void announceCraft(Main plugin, org.bukkit.entity.Player player) {
        Component msg = Component.text(player.getName(), NamedTextColor.WHITE)
                .append(Component.text(" has forged ", NamedTextColor.YELLOW))
                .append(Component.text("Eternity", TextColor.color(0xFFD66E), TextDecoration.BOLD))
                .append(Component.text("!", NamedTextColor.YELLOW));
        for (var p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(msg);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        }
        plugin.getLogger().info("%s crafted Eternity.".formatted(player.getName()));
    }
}
