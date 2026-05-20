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
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Activation system (sequence-friendly, low conflict):
 * - Blitz: swap hands (F) while holding Eternity in main-hand
 * - Blink: double swap-hands (double-F) while holding Master of Time in main-hand
 * - Time Disturbance: sneak + F
 * - Ultimate: sneak + double-F
 *
 * Cooldowns persist via Player PersistentDataContainer (saved in playerdata).
 */
public final class MasterOfTimeListener implements Listener {
    private static final long BLITZ_CD = 6_000L;
    private static final long BLINK_CD = 15_000L;
    private static final long DISTURB_CD = 50_000L;
    private static final long ULT_CD = 600_000L; // 10m

    private static final int ULT_DURATION_TICKS = 200; // 10s

    private final Main plugin;

    private final Map<UUID, Long> lastSwapToggle = new HashMap<>();

    private final Map<UUID, BossBar> bars = new HashMap<>();

    public MasterOfTimeListener(Main plugin) {
        this.plugin = plugin;

        // Smooth bossbar updates while any cooldown is active.
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateBossbar(p);
                }
            }
        }.runTaskTimer(plugin, 10L, 10L);

        // Passive upkeep loop: keep short-duration buffs applied while held.
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
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        removePassives(e.getPlayer());
        BossBar bar = bars.remove(e.getPlayer().getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
        lastSwapToggle.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeld(PlayerItemHeldEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> updateBossbar(e.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        // Disabled - now handled by KeybindManager via KeybindListener
        // Previous code kept for reference:
        // handleSwapHands(e);
    }

    @Deprecated
    private void handleSwapHands(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!TimeBoundItems.isMasterOfTime(plugin, e.getMainHandItem())) return;

        // Use swap-hands as the ability key; don't actually swap items.
        e.setCancelled(true);

        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        long last = lastSwapToggle.getOrDefault(id, 0L);
        lastSwapToggle.put(id, now);
        boolean doubleTap = (now - last) <= 300L;
        boolean sneaking = p.isSneaking();

        if (sneaking && doubleTap) {
            if (!tryStartCooldown(p, "mot_ult", ULT_CD)) return;
            ultimate(p);
            return;
        }

        if (sneaking) {
            if (!tryStartCooldown(p, "mot_disturb", DISTURB_CD)) return;
            disturbance(p);
            return;
        }

        if (doubleTap) {
            if (!tryStartCooldown(p, "mot_blink", BLINK_CD)) return;
            blink(p);
            return;
        }

        if (!tryStartCooldown(p, "mot_blitz", BLITZ_CD)) return;
        blitz(p);
    }

    private boolean isHoldingMaster(Player p) {
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();
        return TimeBoundItems.isMasterOfTime(plugin, main) || TimeBoundItems.isMasterOfTime(plugin, off);
    }

    private void applyPassives(Player p) {
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 60, 1, false, false, true)); // Speed II
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH, 60, 0, false, false, true)); // Strength I
        // Health Boost V => +20 health (20 hearts total = 40 HP).
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.HEALTH_BOOST, 60, 4, false, false, true));
    }

    private void removePassives(Player p) {
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH);
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.HEALTH_BOOST);
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

    private void blitz(Player p) {
        Location loc = p.getLocation();
        Vector v = loc.getDirection().normalize().multiply(1.55).setY(Math.max(0.08, Math.min(0.35, loc.getDirection().getY() * 0.18)));
        p.setVelocity(v);
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 45, 2, false, true, true));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.6f, 1.25f);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1.0, 0), 22, 0.28, 0.28, 0.28, 0.02);
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1.0, 0), 12, 0.25, 0.35, 0.25, 0.01);
    }

    private void blink(Player p) {
        Location before = p.getLocation();
        RayTraceResult ray = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 28);
        Location target;
        if (ray == null) {
            target = before.clone().add(before.getDirection().normalize().multiply(14));
        } else {
            target = ray.getHitPosition().toLocation(p.getWorld()).subtract(before.getDirection().normalize());
        }
        target.setYaw(before.getYaw());
        target.setPitch(before.getPitch());

        p.teleportAsync(target).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            before.getWorld().spawnParticle(Particle.REVERSE_PORTAL, before.add(0, 1.0, 0), 50, 0.4, 0.6, 0.4, 0.02);
            p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1.0, 0), 50, 0.4, 0.6, 0.4, 0.02);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.9f, 1.1f);
        }));
    }

    private void disturbance(Player p) {
        Location loc = p.getLocation();
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.6f);
        p.getWorld().spawnParticle(Particle.DUST_PLUME, loc.add(0, 0.2, 0), 140, 3.0, 0.7, 3.0, 0.03);
        p.getWorld().spawnParticle(Particle.SONIC_BOOM, p.getLocation().add(0, 1.0, 0), 1, 0, 0, 0, 0);

        for (LivingEntity e : p.getWorld().getNearbyLivingEntities(p.getLocation(), 100, 50, 100)) {
            if (e.equals(p)) continue;
            if (e instanceof Player other && TrustManager.isTrusted(p, other)) continue;
            e.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 200, 1, false, true, true));
            e.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS, 200, 0, false, true, true));
            e.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.GLOWING, 200, 0, false, true, true));
        }
    }

    private void ultimate(Player p) {
        Component title = Component.text("HOURGLASS'S SANCTUARY", TextColor.color(0xFFD66E), TextDecoration.BOLD);
        p.showTitle(net.kyori.adventure.title.Title.title(title, Component.text(""), net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(150), java.time.Duration.ofMillis(900), java.time.Duration.ofMillis(250))));

        // Global timestop for exactly 10 seconds, coordinated per-world.
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
                // Roll back worlds we already started to avoid partial-global domain.
                for (var sw : started) {
                    plugin.getWorldUltimateManager().end(sw, () -> {});
                }
                return;
            }
            started.add(w);
        }

        plugin.getWorldUltimateManager().broadcastUltimate(p, "Eternity bends all timelines to its will!", TextColor.color(0xFFD66E));

        // Post-domain debuffs.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 200, 1, false, true, true));
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS, 200, 1, false, true, true));
        }, ULT_DURATION_TICKS);
    }



    private void updateBossbar(Player p) {
        UUID id = p.getUniqueId();
        boolean holding = isHoldingMaster(p);

        long dash = cooldownLeftMillis(p, "mot_blitz");
        long blink = cooldownLeftMillis(p, "mot_blink");
        long distort = cooldownLeftMillis(p, "mot_disturb");
        long ult = cooldownLeftMillis(p, "mot_ult");

        long max = 0L;
        String label = null;
        long left = 0L;

        // Prefer showing ultimate > distortion > blink > dash
        if (ult > 0) { label = "Hourglass's Sanctuary"; left = ult; max = ULT_CD; }
        else if (distort > 0) { label = "Time Disturbance"; left = distort; max = DISTURB_CD; }
        else if (blink > 0) { label = "Blink"; left = blink; max = BLINK_CD; }
        else if (dash > 0) { label = "Blitz"; left = dash; max = BLITZ_CD; }

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
        bar.setTitle("Eternity: " + label + "  " + Math.max(0, (left + 999) / 1000) + "s");
    }

    // ============ Public API for KeybindManager ============

    /**
     * Public method for F (regular ability) - uses Blitz for now
     */
    public void activateMasterAbility(Player p) {
        if (!tryStartCooldown(p, "mot_blitz", BLITZ_CD)) return;
        blitz(p);
    }

    /**
     * Public method for Shift+F (ultimate)
     */
    public void activateMasterUltimate(Player p) {
        if (!tryStartCooldown(p, "mot_ult", ULT_CD)) return;
        ultimate(p);
    }
}
