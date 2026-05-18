package com.doze.timebound;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class TimeItemUid {
    public static final String UID_KEY = "timebound_uid";

    private TimeItemUid() {
    }

    public static NamespacedKey key(Main plugin) {
        return new NamespacedKey(plugin, UID_KEY);
    }

    public static Optional<UUID> get(Main plugin, ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return Optional.empty();
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        String raw = pdc.get(key(plugin), PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static boolean has(Main plugin, ItemStack stack) {
        return get(plugin, stack).isPresent();
    }

    public static ItemStack ensure(Main plugin, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return stack;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey key = key(plugin);
        String existing = pdc.get(key, PersistentDataType.STRING);
        if (existing != null && !existing.isBlank()) return stack;

        pdc.set(key, PersistentDataType.STRING, UUID.randomUUID().toString());
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Forces a new UID. Used for creative-mode inventory operations where the client can duplicate NBT.
     */
    public static ItemStack regenerate(Main plugin, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return stack;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, UUID.randomUUID().toString());
        stack.setItemMeta(meta);
        return stack;
    }
}
