package com.doze.timebound;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Best-effort global scanner.
 *
 * Important: Inventory/entity access must happen on the main thread. We snapshot the
 * ItemStacks synchronously, then process + publish async.
 */
public final class GlobalTimeItemScanner {
    public enum Reason {
        STARTUP,
        PERIODIC,
        LOGIN,
        LOGOUT,
        CRAFT,
        DESTRUCTION,
        ADMIN
    }

    private final Main plugin;
    private final GlobalTimeItemRegistry registry;
    private final AtomicBoolean scanRunning = new AtomicBoolean(false);

    public GlobalTimeItemScanner(Main plugin, GlobalTimeItemRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public boolean isScanRunning() {
        return scanRunning.get();
    }

    public void requestScan(Reason reason) {
        // Coalesce scans: if one is running, let it finish; next periodic/event will refresh soon anyway.
        if (!scanRunning.compareAndSet(false, true)) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            ConcurrentLinkedQueue<ItemStack> snapshot = new ConcurrentLinkedQueue<>();

            for (Player player : Bukkit.getOnlinePlayers()) {
                addInventory(snapshot, player.getInventory(), true);
                // Ender chest is blocked, but still part of "global existence" for enforcement.
                addInventory(snapshot, player.getEnderChest(), true);
            }

            for (World world : Bukkit.getWorlds()) {
                for (Entity e : world.getEntities()) {
                    if (e instanceof Item item) {
                        ItemStack live = item.getItemStack();
                        if (TimeBoundItems.isTimeItem(plugin, live) && !TimeItemUid.has(plugin, live)) {
                            ItemStack copy = live.clone();
                            TimeItemUid.ensure(plugin, copy);
                            item.setItemStack(copy);
                            live = copy;
                        }
                        snapshot.add(live.clone());
                    }
                }
                for (Chunk chunk : world.getLoadedChunks()) {
                    for (BlockState state : chunk.getTileEntities()) {
                        if (state instanceof InventoryHolder holder) {
                            addInventory(snapshot, holder.getInventory(), false);
                        }
                    }
                }
            }

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    Set<GlobalTimeItemRegistry.Record> records = new HashSet<>();
                    for (ItemStack stack : snapshot) {
                        if (!TimeBoundItems.isTimeItem(plugin, stack) && !TimeBoundItems.isMasterOfTime(plugin, stack)) {
                            continue;
                        }
                        // Snapshot copies: ensure a UID exists so the registry can track it even if the live item is legacy.
                        TimeItemUid.ensure(plugin, stack);
                        registry.identify(stack).ifPresent(records::add);
                    }

                    // Best-effort offline scan from playerdata files (reflection-only; may no-op on API-only runtimes).
                    records.addAll(OfflinePlayerdataScanner.scan(plugin, registry));

                    registry.publish(records);
                } catch (Throwable t) {
                    plugin.getLogger().warning("Global scan failed: %s".formatted(t.getMessage()));
                } finally {
                    scanRunning.set(false);
                }
            });
        });
    }

    private void addInventory(ConcurrentLinkedQueue<ItemStack> snapshot, Inventory inv, boolean updatePlayerInventory) {
        if (inv == null) return;
        boolean changed = false;
        ItemStack[] contents = inv.getContents();
        if (contents == null) return;

        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType().isAir()) continue;
            if (TimeBoundItems.isTimeItem(plugin, stack) && !TimeItemUid.has(plugin, stack)) {
                ItemStack copy = stack.clone();
                TimeItemUid.ensure(plugin, copy);
                contents[i] = copy;
                stack = copy;
                changed = true;
            }
            snapshot.add(stack.clone());
        }
        if (changed) {
            inv.setContents(contents);
            if (updatePlayerInventory && inv.getHolder() instanceof Player p) {
                p.updateInventory();
            }
        }
    }

    /**
     * Offline scan: implemented separately to keep the main scanner API-only clean.
     */
    private static final class OfflinePlayerdataScanner {
        private OfflinePlayerdataScanner() {}

        static Set<GlobalTimeItemRegistry.Record> scan(Main plugin, GlobalTimeItemRegistry registry) {
            try {
                Set<GlobalTimeItemRegistry.Record> out = new HashSet<>();

                for (World world : Bukkit.getWorlds()) {
                    File folder = world.getWorldFolder();
                    File playerdata = new File(folder, "playerdata");
                    if (!playerdata.isDirectory()) continue;

                    File[] datFiles = playerdata.listFiles((dir, name) -> name.endsWith(".dat"));
                    if (datFiles == null) continue;

                    for (File f : datFiles) {
                        out.addAll(PlayerDatNbtScanner.scanFile(plugin, f));
                    }
                }

                return out;
            } catch (Throwable ignored) {
                return Set.of();
            }
        }
    }

    /**
     * Minimal NBT parser for offline scanning. We only need enough to walk player inventories and
     * detect items containing our PDC keys.
     */
    @SuppressWarnings("unchecked")
    private static final class PlayerDatNbtScanner {
        private static final byte TAG_END = 0;
        private static final byte TAG_BYTE = 1;
        private static final byte TAG_SHORT = 2;
        private static final byte TAG_INT = 3;
        private static final byte TAG_LONG = 4;
        private static final byte TAG_FLOAT = 5;
        private static final byte TAG_DOUBLE = 6;
        private static final byte TAG_BYTE_ARRAY = 7;
        private static final byte TAG_STRING = 8;
        private static final byte TAG_LIST = 9;
        private static final byte TAG_COMPOUND = 10;
        private static final byte TAG_INT_ARRAY = 11;
        private static final byte TAG_LONG_ARRAY = 12;

        private PlayerDatNbtScanner() {}

        static Set<GlobalTimeItemRegistry.Record> scanFile(Main plugin, File datFile) {
            try (var fis = new java.io.FileInputStream(datFile);
                 var gis = new java.util.zip.GZIPInputStream(fis);
                 var in = new java.io.DataInputStream(gis)) {

                byte rootType = in.readByte();
                if (rootType != TAG_COMPOUND) return Set.of();
                readUtf(in); // root name
                Map<String, Object> root = readCompound(in);

                Set<GlobalTimeItemRegistry.Record> out = new HashSet<>();
                scanItemList(plugin, root, "Inventory", out);
                scanItemList(plugin, root, "EnderItems", out);
                return out;
            } catch (Throwable ignored) {
                return Set.of();
            }
        }

        private static void scanItemList(Main plugin, Map<String, Object> root, String key, Set<GlobalTimeItemRegistry.Record> out) {
            Object v = root.get(key);
            if (!(v instanceof NbtList list)) return;
            if (list.elementType != TAG_COMPOUND) return;

            for (Object o : list.elements) {
                if (!(o instanceof Map<?, ?> item)) continue;
                extractTimeRecordsFromItem(plugin, (Map<String, Object>) item, out);
            }
        }

        private static void extractTimeRecordsFromItem(Main plugin, Map<String, Object> item, Set<GlobalTimeItemRegistry.Record> out) {
            // On Spigot/Paper, PDC is stored under "tag" -> "PublicBukkitValues" as string keys.
            Object tag = item.get("tag");
            if (!(tag instanceof Map<?, ?> tagComp)) return;
            Object pbv = tagComp.get("PublicBukkitValues");
            if (!(pbv instanceof Map<?, ?> pbvComp)) return;

            Map<String, Object> pbvMap = (Map<String, Object>) pbvComp;

            String uidRaw = getString(pbvMap, plugin.getName().toLowerCase(java.util.Locale.ROOT) + ":" + TimeItemUid.UID_KEY);
            if (uidRaw == null) {
                // Also accept explicit namespace "timebound" for safety if plugin name changes.
                uidRaw = getString(pbvMap, "timebound:" + TimeItemUid.UID_KEY);
            }
            if (uidRaw == null) return;

            java.util.UUID uid;
            try {
                uid = java.util.UUID.fromString(uidRaw);
            } catch (IllegalArgumentException ignored) {
                return;
            }

            String weaponType = getString(pbvMap, "timebound:" + TimeBladeItems.TIME_WEAPON_KEY);
            if (weaponType == null) {
                weaponType = getString(pbvMap, plugin.getName().toLowerCase(java.util.Locale.ROOT) + ":" + TimeBladeItems.TIME_WEAPON_KEY);
            }
            if (weaponType != null) {
                out.add(new GlobalTimeItemRegistry.Record(uid, GlobalTimeItemRegistry.Kind.WEAPON, weaponType, null));
                return;
            }

            String clockTypeRaw = getString(pbvMap, "timebound:" + TimeClockItems.CLOCK_KEY);
            if (clockTypeRaw == null) {
                clockTypeRaw = getString(pbvMap, plugin.getName().toLowerCase(java.util.Locale.ROOT) + ":" + TimeClockItems.CLOCK_KEY);
            }
            if (clockTypeRaw != null) {
                ClockType ct = ClockType.fromKey(clockTypeRaw);
                if (ct != null) {
                    out.add(new GlobalTimeItemRegistry.Record(uid, GlobalTimeItemRegistry.Kind.CLOCK, null, ct));
                    return;
                }
            }

            String master = getString(pbvMap, "timebound:" + TimeBoundItems.MASTER_KEY);
            if (master != null) {
                out.add(new GlobalTimeItemRegistry.Record(uid, GlobalTimeItemRegistry.Kind.MASTER, null, null));
            }
        }

        private static String getString(Map<String, Object> c, String key) {
            Object v = c.get(key);
            return v instanceof String s ? s : null;
        }

        private static Map<String, Object> readCompound(java.io.DataInputStream in) throws java.io.IOException {
            var map = new java.util.HashMap<String, Object>();
            while (true) {
                byte type = in.readByte();
                if (type == TAG_END) break;
                String name = readUtf(in);
                map.put(name, readPayload(in, type));
            }
            return map;
        }

        private static Object readPayload(java.io.DataInputStream in, byte type) throws java.io.IOException {
            return switch (type) {
                case TAG_BYTE -> in.readByte();
                case TAG_SHORT -> in.readShort();
                case TAG_INT -> in.readInt();
                case TAG_LONG -> in.readLong();
                case TAG_FLOAT -> in.readFloat();
                case TAG_DOUBLE -> in.readDouble();
                case TAG_BYTE_ARRAY -> {
                    int len = in.readInt();
                    byte[] b = new byte[len];
                    in.readFully(b);
                    yield b;
                }
                case TAG_STRING -> readUtf(in);
                case TAG_LIST -> {
                    byte elemType = in.readByte();
                    int len = in.readInt();
                    var els = new java.util.ArrayList<Object>(Math.max(0, len));
                    for (int i = 0; i < len; i++) {
                        els.add(readPayload(in, elemType));
                    }
                    yield new NbtList(elemType, els);
                }
                case TAG_COMPOUND -> readCompound(in);
                case TAG_INT_ARRAY -> {
                    int len = in.readInt();
                    int[] arr = new int[len];
                    for (int i = 0; i < len; i++) arr[i] = in.readInt();
                    yield arr;
                }
                case TAG_LONG_ARRAY -> {
                    int len = in.readInt();
                    long[] arr = new long[len];
                    for (int i = 0; i < len; i++) arr[i] = in.readLong();
                    yield arr;
                }
                default -> null;
            };
        }

        private static String readUtf(java.io.DataInputStream in) throws java.io.IOException {
            return in.readUTF();
        }

        private static final class NbtList {
            final byte elementType;
            final java.util.List<Object> elements;

            NbtList(byte elementType, java.util.List<Object> elements) {
                this.elementType = elementType;
                this.elements = elements;
            }
        }
    }
}
