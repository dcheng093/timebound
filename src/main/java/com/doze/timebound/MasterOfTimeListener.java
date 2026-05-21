package com.doze.timebound;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class MasterOfTimeListener implements Listener {
    private static final long FLASH_CD = 15_000L;
    private static final long DISTURB_CD = 120_000L;
    private static final int KILLS_FOR_ULT = 7;
    private static final int ULT_DURATION_TICKS = 400; // 20s
    private static final int DISTURB_RADIUS = 100;
    private final Main plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Integer> killCounter = new HashMap<>();
    public MasterOfTimeListener(Main plugin) {
        this.plugin = plugin;
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateBossbar(p);
                }
            }
        }.runTaskTimer(plugin, 10L, 10L);
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (isHoldingMaster(p)) {
                        applyPassives(p);
                    } else {
                        removePassives(p);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (isHoldingMaster(e.getPlayer())) applyPassives(e.getPlayer());
            updateBossbar(e.getPlayer());
            killCounter.put(e.getPlayer().getUniqueId(), 0);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        removePassives(e.getPlayer());
        BossBar bar = bars.remove(id);
        if (bar != null) {
            bar.removeAll();
        }
        killCounter.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeld(PlayerItemHeldEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> updateBossbar(e.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (e.getEntity().getKiller() instanceof Player killer) {
            UUID id = killer.getUniqueId();
            if (isHoldingMaster(killer)) {
                killCounter.put(id, killCounter.getOrDefault(id, 0) + 1);
            }
        }
    }

    private boolean isHoldingMaster(Player p) {
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();
        return TimeBoundItems.isMasterOfTime(plugin, main) || TimeBoundItems.isMasterOfTime(plugin, off);
    }

    private void applyPassives(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, 0, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, 60, 4, false, false, true));
    }

    private void removePassives(Player p) {
        p.removePotionEffect(PotionEffectType.SPEED);
        p.removePotionEffect(PotionEffectType.STRENGTH);
        p.removePotionEffect(PotionEffectType.HEALTH_BOOST);
    }

    private NamespacedKey cdKey(String id) {
        return new NamespacedKey(plugin, id);
    }

    private long cooldownLeftMillis(Player p, String key) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        Long until = pdc.get(cdKey(key), PersistentDataType.LONG);
        if (until == null) return 0L;
        return Math.max(0L, until - System.currentTimeMillis());
    }

    private boolean tryStartCooldown(Player p, String key, long cdMs) {
        long left = cooldownLeftMillis(p, key);
        if (left > 0) {
            p.sendActionBar(Component.text("Cooldown: " + (left / 1000) + "s", NamedTextColor.RED));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
            return false;
        }
        p.getPersistentDataContainer().set(cdKey(key), PersistentDataType.LONG, System.currentTimeMillis() + cdMs);
        updateBossbar(p);
        return true;
    }

    private void flash(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 3, false, true, true));
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.5f);
        p.getWorld().spawnParticle(Particle.END_ROD, p.getEyeLocation(), 15, 0.3, 0.3, 0.3, 0.1);
    }

    private void temporalDisturbance(Player p) {
        Location center = p.getLocation();
        p.getWorld().playSound(center, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 0.8f);
        for (LivingEntity entity : p.getWorld().getNearbyLivingEntities(center, DISTURB_RADIUS)) {
            if (entity.equals(p) || (entity instanceof Player other && TrustManager.isTrusted(p, other))) {
                continue;
            }
            
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1, false, true, true));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, true, true));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0, false, true, true));
        }
        p.getWorld().spawnParticle(Particle.SONIC_BOOM, center, 1);
        for (int i = 0; i < 360; i += 15) {
            double angle = Math.toRadians(i);
            double x = Math.cos(angle) * DISTURB_RADIUS;
            double z = Math.sin(angle) * DISTURB_RADIUS;
            p.getWorld().spawnParticle(Particle.SMALL_FLAME, 
                    center.clone().add(x, 1.0, z), 3, 0.1, 0.1, 0.1, 0.05);
        }
        
        p.sendActionBar(Component.text("Temporal Disturbance - 100 blocks affected!", NamedTextColor.GOLD));
    }

    private void ultimate(Player p) {
        UUID id = p.getUniqueId();
        int kills = killCounter.getOrDefault(id, 0);
        if (kills < KILLS_FOR_ULT) {
            p.sendActionBar(Component.text("Need " + (KILLS_FOR_ULT - kills) + " more kills for ultimate!", NamedTextColor.RED));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
            return;
        }
        killCounter.put(id, 0);
        Component title = Component.text("HOURGLASS'S SANCTUARY", TextColor.color(0xFFD66E), TextDecoration.BOLD);
        p.showTitle(net.kyori.adventure.title.Title.title(title, Component.text(""), net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(150), java.time.Duration.ofMillis(900), java.time.Duration.ofMillis(250))));
        var worlds = Bukkit.getWorlds();
        java.util.List<org.bukkit.World> started = new java.util.ArrayList<>();
        for (var w : worlds) {
            boolean ok = plugin.getWorldUltimateManager().tryStart(
                    w,
                    WorldUltimateManager.Ultimate.ETERNITY_SANCTUARY,
                    p,
                    () -> {
                        for (LivingEntity e : w.getLivingEntities()) {
                            if (e.equals(p)) continue;
                            if (e instanceof Player other && TrustManager.isTrusted(p, other)) continue;
                            TimeFreezeManager.freeze(e);
                            e.setVelocity(new Vector(0, Math.min(e.getVelocity().getY(), 0.1), 0));
                            e.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, ULT_DURATION_TICKS, 0, false, true, true));
                            e.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ULT_DURATION_TICKS, 0, false, true, true));
                        }
                        w.spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1.0, 0), 6, 1.4, 0.5, 1.4, 0.01);
                    },
                    () -> {
                        for (LivingEntity e : w.getLivingEntities()) {
                            if (!TimeFreezeManager.isFrozen(e)) continue;
                            TimeFreezeManager.unfreeze(e);
                            TimeFreezeManager.applyBufferedDamage(e);
                            TimeFreezeManager.applyBufferedKnockback(e);
                            TimeFreezeManager.clear(e);
                        }
                    }
            );
            if (!ok) {
                for (var sw : started) {
                    plugin.getWorldUltimateManager().end(sw, () -> {});
                }
                return;
            }
            started.add(w);
        }
        plugin.getWorldUltimateManager().broadcastUltimate(p, "Eternity bends all timelines to its will!", TextColor.color(0xFFD66E));
    }

    private void updateBossbar(Player p) {
        UUID id = p.getUniqueId();
        boolean holding = isHoldingMaster(p);
        long flash = cooldownLeftMillis(p, "mot_flash");
        long disturb = cooldownLeftMillis(p, "mot_disturb");
        long max = 0L;
        String label = null;
        long left = 0L;
        if (disturb > 0) { label = "Temporal Disturbance"; left = disturb; max = DISTURB_CD; }
        else if (flash > 0) { label = "Flash"; left = flash; max = FLASH_CD; }
        BossBar bar = bars.get(id);
        if (!holding || label == null) {
            if (bar != null) {
                bar.setVisible(false);
                bar.removePlayer(p);
                if (bar.getPlayers().isEmpty()) {
                    bars.remove(id);
                }
            }
            return;
        }
        if (bar == null) {
            bar = Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SOLID);
            bars.put(id, bar);
        }
        if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
        bar.setVisible(true);
        double progress = Math.max(0.0, Math.min(1.0, 1.0 - (left / (double) max)));
        bar.setProgress(progress);
        int kills = killCounter.getOrDefault(id, 0);
        String ultStatus = kills >= KILLS_FOR_ULT ? " [ULT READY]" : " (Kills: " + kills + "/" + KILLS_FOR_ULT + ")";
        bar.setTitle("Eternity: " + label + "  " + Math.max(0, (left + 999) / 1000) + "s" + ultStatus);
    }

    public void activateMasterAbility(Player p) {
        if (!tryStartCooldown(p, "mot_flash", FLASH_CD)) return;
        flash(p);
    }

    public void activateMasterCharge(Player p) {
        if (!tryStartCooldown(p, "mot_disturb", DISTURB_CD)) return;
        temporalDisturbance(p);
    }

    public void activateMasterUltimate(Player p) {
        ultimate(p);
    }
}
