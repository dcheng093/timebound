package com.doze.timebound;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;

public class ChunkyMonitor implements Listener {
    private static final long CHUNKY_ACTIVE_WINDOW_MS = 120_000L;
    private volatile long lastChunkyStart = 0L;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().toLowerCase(Locale.ROOT).trim();
        if (message.startsWith("/chunky start") || message.startsWith("/chunky start ")) {
            markChunkyStart();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(ServerCommandEvent event) {
        String command = event.getCommand().toLowerCase(Locale.ROOT).trim();
        if (command.startsWith("chunky start") || command.startsWith("chunky start ")) {
            markChunkyStart();
        }
    }

    private void markChunkyStart() {
        lastChunkyStart = System.currentTimeMillis();
    }

    public boolean isChunkyActive() {
        return System.currentTimeMillis() - lastChunkyStart < CHUNKY_ACTIVE_WINDOW_MS;
    }

    public long millisSinceLastChunkyStart() {
        if (lastChunkyStart == 0L) return Long.MAX_VALUE;
        return System.currentTimeMillis() - lastChunkyStart;
    }
}
