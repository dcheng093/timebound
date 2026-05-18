package com.doze.timebound;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

public final class GlobalTimeItemRegistry {
    public enum Kind {
        WEAPON,
        CLOCK,
        MASTER
    }

    public record Record(UUID uid, Kind kind, String weaponType, ClockType clockType) {
        public Record {
            Objects.requireNonNull(uid, "uid");
            Objects.requireNonNull(kind, "kind");
        }
    }

    private final Main plugin;

    // Snapshot of the last completed scan.
    private final AtomicReference<Map<UUID, Record>> byUid = new AtomicReference<>(Map.of());

    // Quick derived counts (computed at publish time).
    private final AtomicReference<Map<String, Integer>> weaponCounts = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<ClockType, Integer>> clockCounts = new AtomicReference<>(Map.of());
    private final AtomicReference<Integer> masterCount = new AtomicReference<>(0);

    public GlobalTimeItemRegistry(Main plugin) {
        this.plugin = plugin;
    }

    public void publish(Set<Record> records) {
        Map<UUID, Record> uidMap = new ConcurrentHashMap<>();
        Map<String, Integer> weapons = new ConcurrentHashMap<>();
        Map<ClockType, Integer> clocks = new ConcurrentHashMap<>();
        int masters = 0;

        for (Record r : records) {
            uidMap.put(r.uid(), r);
            if (r.kind() == Kind.MASTER) {
                masters++;
                continue;
            }
            if (r.kind() == Kind.WEAPON && r.weaponType() != null) {
                weapons.merge(r.weaponType(), 1, Integer::sum);
            }
            if (r.kind() == Kind.CLOCK && r.clockType() != null) {
                clocks.merge(r.clockType(), 1, Integer::sum);
            }
        }

        byUid.set(Map.copyOf(uidMap));
        weaponCounts.set(Map.copyOf(weapons));
        clockCounts.set(Map.copyOf(clocks));
        masterCount.set(masters);
    }

    public Optional<Record> identify(ItemStack stack) {
        if (TimeBoundItems.isEmpty(stack)) return Optional.empty();

        ItemStack ensured = stack.clone();
        if (TimeBoundItems.isTimeItem(plugin, ensured)) {
            TimeItemUid.ensure(plugin, ensured);
        }
        Optional<UUID> uid = TimeItemUid.get(plugin, ensured);
        if (uid.isEmpty()) return Optional.empty();

        String weaponType = TimeBladeItems.getTaggedType(ensured);
        if (weaponType != null) {
            return Optional.of(new Record(uid.get(), Kind.WEAPON, weaponType, null));
        }

        ClockType clockType = TimeClockItems.getClockType(plugin, ensured);
        if (clockType != null) {
            return Optional.of(new Record(uid.get(), Kind.CLOCK, null, clockType));
        }

        if (TimeBoundItems.isMasterOfTime(plugin, ensured)) {
            return Optional.of(new Record(uid.get(), Kind.MASTER, null, null));
        }

        return Optional.empty();
    }

    public boolean anyWeaponExists(String type) {
        return weaponCounts.get().getOrDefault(type, 0) > 0;
    }

    public boolean anyClockExists(ClockType type) {
        return clockCounts.get().getOrDefault(type, 0) > 0;
    }

    public boolean anyMasterExists() {
        return masterCount.get() > 0;
    }

    public Map<String, Integer> weaponCounts() {
        return weaponCounts.get();
    }

    public Map<ClockType, Integer> clockCounts() {
        return clockCounts.get();
    }

    public void logDuplicateViolation(String message) {
        plugin.getLogger().warning("[TestMode] " + message);
        for (var p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("timebound.admin")) {
                p.sendMessage(net.kyori.adventure.text.Component.text("[TimeBound TestMode] " + message,
                        net.kyori.adventure.text.format.NamedTextColor.RED));
            }
        }
    }
}

