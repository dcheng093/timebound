package com.doze.timebound;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.*;

public class TimeManager {

    private final Map<UUID, Deque<EntityState>> entityHistory = new HashMap<>();
    private final List<BlockRecord> blockHistory = new ArrayList<>();
    private final Set<UUID> rewindingEntities = new HashSet<>();

    public TimeManager(Main plugin) {}

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

    public void rewindHealth(LivingEntity e, int ticks) {
        Deque<EntityState> list = entityHistory.get(e.getUniqueId());
        if (list == null || list.isEmpty()) return;

        EntityState past = list.peekLast();
        if (past != null) {
            double maxHealth = e.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null
                    ? e.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()
                    : 20.0;
            e.setHealth(Math.min(maxHealth, Math.max(1.0, past.health)));
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