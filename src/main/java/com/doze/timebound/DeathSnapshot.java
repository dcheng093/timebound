package com.doze.timebound;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

public class DeathSnapshot {

    public final ItemStack[] inventory;
    public final double health;
    public final int food;
    public final Location location;

    public DeathSnapshot(ItemStack[] inventory, double health, int food, Location location) {
        this.inventory = inventory;
        this.health = health;
        this.food = food;
        this.location = location;
    }
}