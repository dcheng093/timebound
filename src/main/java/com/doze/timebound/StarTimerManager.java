package com.doze.timebound;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class StarTimerManager {
    private static final Map<UUID, BossBar> bars = new HashMap<>();
    private static final Map<UUID, Integer> tasks = new HashMap<>();
    public static void startTimer(JavaPlugin plugin, Player player, String title, int seconds) {
        stop(player);
        BossBar bar = Bukkit.createBossBar(title, BarColor.BLUE, BarStyle.SOLID);
        bar.addPlayer(player);
        bar.setVisible(true);
        UUID id = player.getUniqueId();
        bars.put(id, bar);
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            int time = seconds;
            @Override
            public void run() {
                if (!player.isOnline()) {
                    stop(player);
                    return;
                }
                if (time <= 0) {
                    stop(player);
                    return;
                }
                double progress = (double) time / seconds;
                bar.setProgress(Math.max(0, Math.min(1, progress)));
                String titleStr = title + " " + time + "s";
                bar.setTitle(titleStr);
                time--;
            }

        }, 0L, 20L);
        tasks.put(id, taskId);
    }

    public static void stop(Player player) {
        UUID id = player.getUniqueId();
        BossBar bar = bars.remove(id);
        if (bar != null) bar.removeAll();
        Integer task = tasks.remove(id);
        if (task != null) Bukkit.getScheduler().cancelTask(task);
    }
}