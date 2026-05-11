package com.doze.timebound.event;

import com.doze.timebound.ClockType;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class TimeClockRetrieveEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ClockType clockType;
    private final Location location;

    public TimeClockRetrieveEvent(Player player, ClockType clockType, Location location) {
        this.player = player;
        this.clockType = clockType;
        this.location = location;
    }

    public Player getPlayer() {
        return player;
    }

    public ClockType getClockType() {
        return clockType;
    }

    public Location getLocation() {
        return location;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
