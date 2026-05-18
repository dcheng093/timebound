package com.doze.timebound;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class TimeManager {

    private final Map<UUID, Deque<EntityState>> entityHistory = new HashMap<>();
    private final List<BlockRecord> blockHistory = new ArrayList<>();
    private final Set<UUID> rewindingEntities = new HashSet<>();

    // Tracking task (main thread).
    private BukkitTask trackTask;

    public TimeManager(Main plugin) {}

    /**
     * Starts lightweight tracking. This intentionally does not iterate every entity in every world every tick.
     *
     * We track:
     * - All online players every tick (for Requiem/Eternity rewind mechanics).
     * - Nearby living entities around players every 5 ticks (bounded by player count).
     */
    public synchronized void start(Plugin plugin) {
        if (trackTask != null) return;
        trackTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Players every tick.
            for (Player p : Bukkit.getOnlinePlayers()) {
                trackEntity(p);
            }
            // Nearby living entities less frequently to reduce load.
            if ((Bukkit.getCurrentTick() % 5) != 0) return;
            for (Player p : Bukkit.getOnlinePlayers()) {
                for (LivingEntity le : p.getWorld().getNearbyLivingEntities(p.getLocation(), 32, 16, 32)) {
                    if (le instanceof Player) continue;
                    trackEntity(le);
                }
            }
        }, 1L, 1L);
    }

    public void setRewinding(Entity e, boolean rewinding) {
        if (rewinding) rewindingEntities.add(e.getUniqueId());
        else rewindingEntities.remove(e.getUniqueId());
    }

    public void trackEntity(Entity e) {
        if (rewindingEntities.contains(e.getUniqueId())) return;

        entityHistory.putIfAbsent(e.getUniqueId(), new ArrayDeque<>());
        Deque<EntityState> list = entityHistory.get(e.getUniqueId());

        double hp = e instanceof LivingEntity le ? le.getHealth() : 0.0;
        list.addFirst(new EntityState(e.getLocation().clone(), hp));

        if (list.size() > 100) {
            list.removeLast();
        }
    }

    public void rewindSmooth(Entity e, int ticks) {
        Deque<EntityState> list = entityHistory.get(e.getUniqueId());
        if (list == null || list.isEmpty()) return;

        EntityState past = list.pollFirst();
        if (past != null) {
            e.teleport(past.location);
        }
    }

    /**
     * Instant rewind to an older sample without consuming history.
     * @param ticksBack roughly how many ticks to go back (clamped to stored window)
     */
    public EntityState peekPast(Entity e, int ticksBack) {
        Deque<EntityState> list = entityHistory.get(e.getUniqueId());
        if (list == null || list.isEmpty()) return null;
        int idx = Math.max(0, Math.min(ticksBack, list.size() - 1));
        // Deque doesn't support random access; walk.
        int i = 0;
        for (EntityState s : list) {
            if (i == idx) return s;
            i++;
        }
        return null;
    }
    public void rewindHealth(LivingEntity e, int ticks) {
    Deque<EntityState> list = entityHistory.get(e.getUniqueId());
    if (list == null || list.isEmpty()) return;

    // Oldest sample within our window (100 ticks) approximates "rewind within N seconds".
    EntityState past = list.peekLast();
    if (past != null) {
        var maxHealthAttr = e.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = (maxHealthAttr != null) ? maxHealthAttr.getValue() : 20.0;
        
        e.setHealth(Math.min(maxHealth, Math.max(0.0, past.health)));
        }
    }

    public void recordBlock(Location loc, Material before, Material after) {
        blockHistory.add(new BlockRecord(loc.clone(), before, after));
    }

    public void rewindBlocks(int amount) {
        for (int i = 0; i < amount && !blockHistory.isEmpty(); i++) {
            BlockRecord r = blockHistory.remove(blockHistory.size() - 1);
            r.location.getBlock().setType(r.before);
        }
    }

    public static class EntityState {
        public final Location location;
        public final double health;

        public EntityState(Location location, double health) {
            this.location = location;
            this.health = health;
        }
    }

    public static class BlockRecord {
        public final Location location;
        public final Material before;
        public final Material after;

        public BlockRecord(Location location, Material before, Material after) {
            this.location = location;
            this.before = before;
            this.after = after;
        }
    }
}
