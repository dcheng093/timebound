package com.doze.timebound;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Unified keybind system using only F and Shift+F.
 * 
 * Keybind mapping:
 * - F: Activate regular ability (with charge mechanic where applicable)
 * - Shift+F: Activate ultimate (with charge mechanic where applicable)
 * 
 * Charge mechanics:
 * - Player holds F or Shift+F
 * - Charge bar fills up
 * - When fully charged, ability activates automatically
 * - If player releases early, charge is cancelled
 */
public class KeybindManager {
    private final Main plugin;
    private final Map<UUID, Charging> charging = new HashMap<>();
    private final Map<UUID, BossBar> chargeBars = new HashMap<>();
    
    private record Charging(Weapon weapon, boolean ultimate, long startTime, long chargeDurationMs) {}
    
    public enum Weapon {
        // Time Blades
        FREEZE("Lunar Dial", true, true, 1000), // ability charged, ult charged
        BRAKE("Chrono Lock", true, true, 1000),
        SKIP("Flashstep", false, true, 1500), // ability NOT charged (dash), ult charged
        REVERSE("Requiem", true, true, 1000),
        // Master of Time
        MASTER("Master of Time", true, true, 1200),
        // Time Clocks
        TIME_CLOCK("Time Clock", true, false, 800); // ability charged, no ult
        
        private final String displayName;
        private final boolean abilityCharged;
        private final boolean ultCharged;
        private final long chargeDurationMs;
        
        Weapon(String displayName, boolean abilityCharged, boolean ultCharged, long chargeDurationMs) {
            this.displayName = displayName;
            this.abilityCharged = abilityCharged;
            this.ultCharged = ultCharged;
            this.chargeDurationMs = chargeDurationMs;
        }
    }
    
    public KeybindManager(Main plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Called when F or Shift+F is pressed.
     */
    public void onKeyPress(Player player, boolean isUltimate) {
        UUID id = player.getUniqueId();
        
        // Prevent rapid successive presses
        if (charging.containsKey(id)) {
            return;
        }
        
        Weapon weapon = getCurrentWeapon(player);
        if (weapon == null) {
            sendError(player, "No weapon equipped!");
            return;
        }
        
        boolean shouldCharge = isUltimate ? weapon.ultCharged : weapon.abilityCharged;
        
        if (!shouldCharge) {
            // Instant activation (no charge)
            activateAbility(player, weapon, isUltimate);
        } else {
            // Start charge sequence
            startCharge(player, weapon, isUltimate);
        }
    }
    
    public void onKeyRelease(Player player) {
        UUID id = player.getUniqueId();
        Charging c = charging.get(id);
        
        if (c == null) {
            return; // Not charging
        }
        
        long chargeTime = System.currentTimeMillis() - c.startTime;
        
        // If fully charged, it already activated
        if (chargeTime >= c.chargeDurationMs) {
            return;
        }
        
        // Release before fully charged = cancel
        cancelCharge(player);
        sendError(player, "Charge cancelled - held too short!");
    }
    
    private void startCharge(Player player, Weapon weapon, boolean ultimate) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        long duration = weapon.chargeDurationMs;
        
        charging.put(id, new Charging(weapon, ultimate, now, duration));
        showChargeBar(player, weapon, ultimate, 0.0);
        
        playAt(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 1.0f);
        
        // Charge progress loop
        new BukkitRunnable() {
            @Override
            public void run() {
                Charging c = charging.get(id);
                if (c == null) {
                    cancel();
                    return;
                }
                
                if (!player.isOnline()) {
                    cancelCharge(player);
                    cancel();
                    return;
                }
                
                long elapsed = System.currentTimeMillis() - c.startTime;
                double progress = Math.min(1.0, elapsed / (double) duration);
                
                showChargeBar(player, weapon, ultimate, progress);
                spawnChargeParticles(player, weapon, progress);
                
                if (elapsed >= duration) {
                    cancel();
                    // Auto-activate when fully charged
                    finishCharge(player, weapon, ultimate);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    
    private void finishCharge(Player player, Weapon weapon, boolean ultimate) {
        UUID id = player.getUniqueId();
        charging.remove(id);
        
        BossBar bar = chargeBars.remove(id);
        if (bar != null) {
            bar.removeAll();
        }
        
        // Play charge complete sound
        playAt(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.5f);
        
        // Activate the ability
        activateAbility(player, weapon, ultimate);
    }
    
    private void cancelCharge(Player player) {
        UUID id = player.getUniqueId();
        charging.remove(id);
        BossBar bar = chargeBars.remove(id);
        if (bar != null) {
            bar.removeAll();
        }
    }
    
    private void activateAbility(Player player, Weapon weapon, boolean ultimate) {
        cancelCharge(player);
        
        if (ultimate) {
            activateUltimate(player, weapon);
        } else {
            activateRegularAbility(player, weapon);
        }
    }
    
    private void activateRegularAbility(Player player, Weapon weapon) {
        plugin.getListener().activateWeaponAbility(player, weapon);
    }
    
    private void activateUltimate(Player player, Weapon weapon) {
        plugin.getListener().activateWeaponUltimate(player, weapon);
    }
    
    private Weapon getCurrentWeapon(Player player) {
        // Check for Time Clock in offhand
        if (TimeBoundItems.getClockType(plugin, player.getInventory().getItemInOffHand()) != null) {
            return Weapon.TIME_CLOCK;
        }
        
        // Check for weapons in main hand
        if (TimeBoundItems.isMasterOfTime(plugin, player.getInventory().getItemInMainHand())) {
            return Weapon.MASTER;
        }
        
        // Check for Time Blades
        String bladeType = TimeBoundItems.getWeaponType(player.getInventory().getItemInMainHand());
        if (bladeType != null) {
            return switch (bladeType.toLowerCase()) {
                case "freeze" -> Weapon.FREEZE;
                case "brake" -> Weapon.BRAKE;
                case "skip" -> Weapon.SKIP;
                case "reverse" -> Weapon.REVERSE;
                default -> null;
            };
        }
        
        return null;
    }
    
    private void showChargeBar(Player player, Weapon weapon, boolean ultimate, double progress) {
        UUID id = player.getUniqueId();
        
        BarColor color = switch (weapon) {
            case FREEZE -> BarColor.BLUE;
            case BRAKE -> BarColor.WHITE;
            case SKIP -> BarColor.YELLOW;
            case REVERSE -> BarColor.PURPLE;
            case MASTER -> BarColor.GREEN;
            case TIME_CLOCK -> BarColor.CYAN;
        };
        
        BossBar bar = chargeBars.computeIfAbsent(id, ignored -> 
            Bukkit.createBossBar("", color, BarStyle.SOLID)
        );
        
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
        
        bar.setVisible(true);
        bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        
        String action = ultimate ? "Ultimate" : "Ability";
        bar.setTitle("Charging " + action + ": " + weapon.displayName);
        
        int pct = (int) Math.round(progress * 100.0);
        player.sendActionBar(Component.text(
            "Charging " + action + "... " + pct + "%",
            NamedTextColor.YELLOW
        ));
    }
    
    private void spawnChargeParticles(Player player, Weapon weapon, double progress) {
        org.bukkit.Particle particle = switch (weapon) {
            case FREEZE -> org.bukkit.Particle.SNOWFLAKE;
            case BRAKE -> org.bukkit.Particle.ASH;
            case SKIP -> org.bukkit.Particle.ELECTRIC_SPARK;
            case REVERSE -> org.bukkit.Particle.REVERSE_PORTAL;
            case MASTER -> org.bukkit.Particle.SOUL_FIRE_FLAME;
            case TIME_CLOCK -> org.bukkit.Particle.GLOW;
        };
        
        Location loc = player.getLocation().add(0, 1.0, 0);
        int count = 1 + (int) Math.round(progress * 5);
        player.getWorld().spawnParticle(particle, loc, count, 0.4, 0.6, 0.4, 0.01);
    }
    
    private void playAt(Location loc, Sound sound, float volume, float pitch) {
        if (loc != null && loc.getWorld() != null) {
            loc.getWorld().playSound(loc, sound, volume, pitch);
        }
    }
    
    private void sendError(Player player, String message) {
        player.sendMessage(Component.text(message, NamedTextColor.RED));
        playAt(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
    }
    
    public void cleanup(Player player) {
        UUID id = player.getUniqueId();
        charging.remove(id);
        BossBar bar = chargeBars.remove(id);
        if (bar != null) {
            bar.removeAll();
        }
    }
}
