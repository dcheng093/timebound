package com.doze.timebound.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class BrakeClockTrialTriggeredEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Location roomOrigin;

    public BrakeClockTrialTriggeredEvent(Player player, Location roomOrigin) {
        this.player = player;
        this.roomOrigin = roomOrigin;
    }

    public Player getPlayer() {
        return player;
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
