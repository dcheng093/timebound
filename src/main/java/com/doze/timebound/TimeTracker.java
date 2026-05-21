package com.doze.timebound;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;

public class TimeTracker {
    private final List<BlockRecord> records = new ArrayList<>();
    public TimeTracker(Main plugin) {
    }

    public void record(Location loc, Material before, Material after) {
        if (loc == null || before == null || after == null) return;
        records.add(new BlockRecord(loc.clone(), before, after));
    }

    public void recordBlock(Location loc, Material before, Material after) {
        record(loc, before, after);
    }

    public List<BlockRecord> getRecords() {
        return records;
    }

    public void clear() {
        records.clear();
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