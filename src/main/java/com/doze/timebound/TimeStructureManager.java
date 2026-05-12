package com.doze.timebound;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.Structure;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BiomeSearchResult;
import org.bukkit.util.StructureSearchResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class TimeStructureManager {

    private static final String MARKER_KEY = "timebound_structure_marker";
    private static final int SEARCH_RADIUS = 15000;
    private static final int MAX_PLACEMENT_ATTEMPTS = 8;
    private static final long CHUNKY_WAIT_MS = 120000L;
    private static final long CHUNKY_CHECK_INTERVAL_MS = 3000L;

    private final Main plugin;
    private final File schematicsFolder;

    public TimeStructureManager(Main plugin) {
        this.plugin = plugin;
        this.schematicsFolder = new File(plugin.getDataFolder(), "schematics");
        if (!schematicsFolder.exists()) {
            schematicsFolder.mkdirs();
        }
    }

    public void generateAll(Player admin) {
        generate(admin, Arrays.asList(ClockType.FREEZE, ClockType.BRAKE, ClockType.REVERSE, ClockType.SKIP));
    }

    public void generateSingle(Player admin, ClockType type) {
        generate(admin, Collections.singletonList(type));
    }

    public void locateStructure(Player player, ClockType type) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            ArmorStand marker = findStructureMarker(type);
            if (marker == null) {
                String message = "No " + type.displayName() + " structure has been generated yet.";
                player.sendMessage(ChatColor.RED + message);
                plugin.getLogger().info(message);
                return;
            }

            Location location = marker.getLocation();
            String worldName = location.getWorld().getName();
            String result = String.format("%s structure found in world '%s' at %d, %d, %d.", type.displayName(), worldName, location.getBlockX(), location.getBlockY(), location.getBlockZ());
            plugin.getLogger().info(result);
            player.sendMessage(ChatColor.GREEN + result);
        });
    }

    private void generate(Player admin, List<ClockType> types) {
        progress(admin, "Preparing search for TimeBound structures...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!waitForChunkyIfNeeded(admin)) {
                error(admin, "Chunky generation is still active. Wait a moment and try again.");
                return;
            }

            Location origin = admin.getLocation();
            World world = origin.getWorld();
            Map<ClockType, Location> candidates = new HashMap<>();

            for (ClockType type : types) {
                candidates.put(type, findCandidateLocation(world, origin, type));
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                for (ClockType type : types) {
                    Location candidate = candidates.get(type);
                    if (candidate == null) {
                        error(admin, "No suitable location found for " + type.displayName() + ". Generation skipped.");
                        continue;
                    }
                    pasteStructure(admin, type, candidate);
                }
                progress(admin, "All requested generation tasks completed.");
            });
        });
    }

    private boolean waitForChunkyIfNeeded(Player admin) {
        ChunkyMonitor monitor = plugin.getChunkyMonitor();
        if (monitor == null || !monitor.isChunkyActive()) {
            return true;
        }

        progress(admin, "Detected /chunky start. Waiting for Chunky generation to finish before searching...");
        long start = System.currentTimeMillis();

        while (System.currentTimeMillis() - start < CHUNKY_WAIT_MS) {
            if (!monitor.isChunkyActive()) {
                progress(admin, "Chunky generation appears to have settled. Continuing structure search.");
                return true;
            }
            try {
                Thread.sleep(CHUNKY_CHECK_INTERVAL_MS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return !monitor.isChunkyActive();
    }

    private Location findCandidateLocation(World world, Location origin, ClockType type) {
        switch (type) {
            case BRAKE -> {
                progress(origin, "Searching for the nearest naturally generated Trial Chamber...");
                StructureSearchResult structureSearch = world.locateNearestStructure(origin, Structure.TRIAL_CHAMBERS, SEARCH_RADIUS, true);
                if (structureSearch == null) {
                    progress(origin, "No Trial Chamber found within " + SEARCH_RADIUS + " blocks.");
                    return null;
                }
                Location trialLocation = structureSearch.getLocation();
                progress(origin, "Trial Chamber found at " + formatLoc(trialLocation) + ".");
                return trialLocation;
            }
            case FREEZE -> {
                progress(origin, "Searching for a Snowy/Ice biome...");
                Location freezeLocation = searchNearestBiome(origin, Biome.SNOWY_PLAINS, Biome.ICE_SPIKES, Biome.FROZEN_PEAKS, Biome.SNOWY_TAIGA);
                if (freezeLocation == null) {
                    progress(origin, "No Snowy/Ice biome found within " + SEARCH_RADIUS + " blocks.");
                    return null;
                }
                progress(origin, "Snowy/Ice biome found at " + formatLoc(freezeLocation) + ".");
                return freezeLocation;
            }
            case SKIP -> {
                progress(origin, "Searching for a Desert biome...");
                Location skipLocation = searchNearestBiome(origin, Biome.DESERT, Biome.DESERT_HILLS, Biome.DESERT_LAKES);
                if (skipLocation == null) {
                    progress(origin, "No Desert biome found within " + SEARCH_RADIUS + " blocks.");
                    return null;
                }
                progress(origin, "Desert biome found at " + formatLoc(skipLocation) + ".");
                return skipLocation;
            }
            case REVERSE -> {
                progress(origin, "Searching for a Pale Garden biome...");
                Location reverseLocation = searchPaleGardenBiome(origin);
                if (reverseLocation == null) {
                    progress(origin, "No Pale Garden biome found within " + SEARCH_RADIUS + " blocks. Trying fallback...");
                    reverseLocation = searchNearestBiome(origin, Biome.DARK_FOREST, Biome.FOREST, Biome.TAIGA);
                }
                if (reverseLocation == null) {
                    progress(origin, "No suitable biome found for Reverse Clock within " + SEARCH_RADIUS + " blocks.");
                    return null;
                }
                progress(origin, "Reverse biome found at " + formatLoc(reverseLocation) + ".");
                return reverseLocation;
            }
            default -> {
                return null;
            }
        }
    }

    private Location searchNearestBiome(Location origin, Biome... biomes) {
        BiomeSearchResult result = origin.getWorld().locateNearestBiome(origin, SEARCH_RADIUS, biomes);
        return result == null ? null : result.getLocation();
    }

    private Location searchPaleGardenBiome(Location origin) {
        Biome paleGarden = safeBiome("PALE_GARDEN");
        if (paleGarden != null) {
            BiomeSearchResult result = origin.getWorld().locateNearestBiome(origin, SEARCH_RADIUS, paleGarden);
            if (result != null) return result.getLocation();
        }
        return null;
    }

    private Biome safeBiome(String name) {
        try {
            return Biome.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void pasteStructure(Player admin, ClockType type, Location searchLocation) {
        progress(admin, "Building " + type.displayName() + "...");

        String fileName = type.name().toLowerCase() + "clock.schem";
        File schematicFile = new File(schematicsFolder, fileName);
        if (!schematicFile.exists()) {
            error(admin, "Missing schematic file: " + fileName);
            return;
        }

        Clipboard clipboard = loadClipboard(schematicFile);
        if (clipboard == null) {
            error(admin, "Failed to read schematic file: " + fileName);
            return;
        }

        Location pasteOrigin = calculatePasteOrigin(type, searchLocation, clipboard);
        Location finalOrigin = findValidPasteOrigin(type, pasteOrigin, clipboard);
        if (finalOrigin == null) {
            error(admin, "Could not find a clean placement for " + type.displayName() + ". Generation aborted.");
            return;
        }

        if (!pasteClipboard(clipboard, finalOrigin)) {
            error(admin, "Failed to paste " + type.displayName() + " schematic at " + formatLoc(finalOrigin) + ".");
            return;
        }

        createStructureMarker(type, finalOrigin);
        if (type == ClockType.BRAKE) {
            placeBrakeQuartzMarker(finalOrigin, clipboard);
        }

        Location hologram = findHologramTarget(finalOrigin, type);
        plugin.getClockListener().spawnClickableClock(hologram, type);

        progress(admin, type.displayName() + " generated at " + formatLoc(finalOrigin) + ".");
    }

    private Clipboard loadClipboard(File file) {
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) return null;
        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            return reader.read();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to read schematic " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }

    private Location calculatePasteOrigin(ClockType type, Location searchLocation, Clipboard clipboard) {
        Location origin = searchLocation.clone();
        if (type == ClockType.BRAKE) {
            return origin.add(0, -2, 0);
        }
        if (type == ClockType.FREEZE || type == ClockType.SKIP) {
            return getHighest(origin);
        }
        if (type == ClockType.REVERSE) {
            origin = getHighest(origin);
            origin.add(0, ThreadLocalRandom.current().nextInt(70, 91), 0);
            return origin;
        }
        return origin;
    }

    private Location findValidPasteOrigin(ClockType type, Location base, Clipboard clipboard) {
        List<Location> attempts = new ArrayList<>();
        attempts.add(base.clone());
        int[] offsets = {0, 12, -12, 24, -24};

        for (int x : offsets) {
            for (int z : offsets) {
                if (x == 0 && z == 0) continue;
                attempts.add(base.clone().add(x, 0, z));
            }
        }

        if (type == ClockType.REVERSE) {
            for (int i = 0; i < MAX_PLACEMENT_ATTEMPTS; i++) {
                attempts.add(base.clone().add(ThreadLocalRandom.current().nextInt(-32, 33), 0, ThreadLocalRandom.current().nextInt(-32, 33)));
            }
        }

        for (Location attempt : attempts) {
            if (verifyPlacement(type, attempt, clipboard)) {
                return attempt;
            }
        }
        return null;
    }

    private boolean verifyPlacement(ClockType type, Location origin, Clipboard clipboard) {
        if (type == ClockType.REVERSE) {
            return verifyAirVolume(origin, clipboard);
        }
        if (type == ClockType.BRAKE) {
            return verifyAttachmentArea(origin, clipboard);
        }
        return verifyTerrainBlend(origin, clipboard);
    }

    private boolean verifyAirVolume(Location origin, Clipboard clipboard) {
        BlockVector3 min = clipboard.getMinimumPoint();
        BlockVector3 max = clipboard.getMaximumPoint();
        World world = origin.getWorld();

        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    Location check = origin.clone().add(x, y, z);
                    if (!check.getBlock().isEmpty()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean verifyTerrainBlend(Location origin, Clipboard clipboard) {
        BlockVector3 min = clipboard.getMinimumPoint();
        BlockVector3 max = clipboard.getMaximumPoint();
        World world = origin.getWorld();
        int groundY = origin.getBlockY() - 1;
        int total = 0;
        int solidCount = 0;

        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                Location floor = new Location(world, origin.getBlockX() + x, groundY, origin.getBlockZ() + z);
                Material below = floor.getBlock().getType();
                if (below.isSolid() && below != Material.COBWEB && below != Material.AIR) {
                    solidCount++;
                }
                total++;
            }
        }
        return total > 0 && solidCount >= Math.max(1, total / 4);
    }

    private boolean verifyAttachmentArea(Location origin, Clipboard clipboard) {
        if (!verifyTerrainBlend(origin, clipboard)) return false;
        BlockVector3 min = clipboard.getMinimumPoint();
        BlockVector3 max = clipboard.getMaximumPoint();
        World world = origin.getWorld();
        int total = 0;
        int safeCount = 0;

        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                Location check = new Location(world, origin.getBlockX() + x, origin.getBlockY(), origin.getBlockZ() + z);
                Material type = check.getBlock().getType();
                if (type.isAir() || type == Material.STONE || type == Material.DIRT || type == Material.GRASS_BLOCK) {
                    safeCount++;
                }
                total++;
            }
        }
        return total > 0 && safeCount >= Math.max(1, total / 4);
    }

    private boolean pasteClipboard(Clipboard clipboard, Location origin) {
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(origin.getWorld()))) {
            Operation operation = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(BlockVector3.at(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ()))
                    .ignoreAirBlocks(true)
                    .build();
            Operations.complete(operation);
            return true;
        } catch (WorldEditException e) {
            plugin.getLogger().warning("WorldEdit paste failed: " + e.getMessage());
            return false;
        }
    }

    private void placeBrakeQuartzMarker(Location origin, Clipboard clipboard) {
        Location marker = findFirstBlock(origin, clipboard, Material.GRAY_CARPET);
        if (marker == null) {
            plugin.getLogger().info("Brake structure placed but no gray carpet was found for a quartz marker.");
            return;
        }
        Location quartzLocation = marker.clone().add(0, 1, 0);
        quartzLocation.getBlock().setType(Material.QUARTZ_BLOCK);
        progress(origin, "Placed Quartz marker at " + formatLoc(quartzLocation) + " for the brake lever.");
    }

    private Location findFirstBlock(Location origin, Clipboard clipboard, Material target) {
        BlockVector3 min = clipboard.getMinimumPoint();
        BlockVector3 max = clipboard.getMaximumPoint();
        World world = origin.getWorld();

        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    Location check = origin.clone().add(x, y, z);
                    if (check.getBlock().getType() == target) {
                        return check;
                    }
                }
            }
        }
        return null;
    }

    private void createStructureMarker(ClockType type, Location origin) {
        removeExistingMarker(type);
        NamespacedKey key = new NamespacedKey(plugin, MARKER_KEY);
        origin.getWorld().spawn(origin.clone().add(0.5, 0.5, 0.5), ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setInvulnerable(true);
            stand.setPersistent(true);
            stand.getPersistentDataContainer().set(key, PersistentDataType.STRING, type.key());
        });
    }

    private ArmorStand findStructureMarker(ClockType type) {
        NamespacedKey key = new NamespacedKey(plugin, MARKER_KEY);
        for (World world : Bukkit.getWorlds()) {
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                if (stand.getPersistentDataContainer().has(key, PersistentDataType.STRING)
                        && type.key().equals(stand.getPersistentDataContainer().get(key, PersistentDataType.STRING))) {
                    return stand;
                }
            }
        }
        return null;
    }

    private void removeExistingMarker(ClockType type) {
        NamespacedKey key = new NamespacedKey(plugin, MARKER_KEY);
        for (World world : Bukkit.getWorlds()) {
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                if (stand.getPersistentDataContainer().has(key, PersistentDataType.STRING)
                        && type.key().equals(stand.getPersistentDataContainer().get(key, PersistentDataType.STRING))) {
                    stand.remove();
                }
            }
        }
    }

    private void progress(Player admin, String message) {
        plugin.getLogger().info(message);
        Bukkit.getScheduler().runTask(plugin, () -> admin.sendMessage(ChatColor.YELLOW + message));
    }

    private void progress(Location origin, String message) {
        plugin.getLogger().info(message);
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().equals(origin.getWorld())) {
                    player.sendMessage(ChatColor.GRAY + message);
                }
            }
        });
    }

    private void error(Player admin, String message) {
        plugin.getLogger().warning(message);
        Bukkit.getScheduler().runTask(plugin, () -> admin.sendMessage(ChatColor.RED + message));
    }

    private String formatLoc(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }
}
