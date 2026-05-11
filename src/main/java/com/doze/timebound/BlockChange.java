package com.doze.timebound;

import org.bukkit.Location;
import org.bukkit.Material;

public class BlockChange {

    public final Location location;
    public final Material oldType;
    public final Material newType;

    public BlockChange(Location location, Material oldType, Material newType) {
        this.location = location;
        this.oldType = oldType;
        this.newType = newType;
    }
}