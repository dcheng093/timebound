package com.doze.timebound.event;

import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class BrakeClockUnlockedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Location roomOrigin;

    public BrakeClockUnlockedEvent(Location roomOrigin) {
        this.roomOrigin = roomOrigin;
    }

    public Location getRoomOrigin() {
        return roomOrigin;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
