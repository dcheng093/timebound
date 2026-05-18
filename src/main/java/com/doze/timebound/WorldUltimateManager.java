package com.doze.timebound;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Centralized, per-world ultimate coordinator.
 *
 * Requirements:
 * - World-manipulating ultimates last exactly 10 seconds (200 ticks).
 * - No async world access.
 * - No overlapping/conflicting ultimates per world.
 */
public final class WorldUltimateManager {
    public enum Ultimate {
        CHRONO_LOCK_DECELERATION,
        FLASHSTEP_ACCELERATION,
        LUNAR_DIAL_DOMAIN,
        ETERNITY_SANCTUARY
    }

    private static final int DURATION_TICKS = 200; // 10s

    private final Main plugin;
    private final Map<UUID, Active> activeByWorld = new ConcurrentHashMap<>();

    private record Active(Ultimate ultimate, UUID caster, BukkitTask task, int startedTick) {
    }

    public WorldUltimateManager(Main plugin) {
        this.plugin = plugin;
    }

    public boolean isActive(World world) {
        return activeByWorld.containsKey(world.getUID());
    }

    public Ultimate activeUltimate(World world) {
        Active a = activeByWorld.get(world.getUID());
        return a == null ? null : a.ultimate();
    }

    public boolean tryStart(World world, Ultimate ult, Player caster, Runnable onTick, Runnable onEnd) {
        UUID wid = world.getUID();
        Active existing = activeByWorld.get(wid);
        if (existing != null) {
            caster.sendMessage(Component.text("Time is already being bent in this world (" + existing.ultimate + ").", NamedTextColor.RED));
            caster.playSound(caster.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
            return false;
        }

        int startTick = Bukkit.getCurrentTick();
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Ensure exact duration in ticks.
            int elapsed = Bukkit.getCurrentTick() - startTick;
            if (elapsed >= DURATION_TICKS) {
                end(world, onEnd);
                return;
            }
            onTick.run();
        }, 0L, 1L);

        activeByWorld.put(wid, new Active(ult, caster.getUniqueId(), task, startTick));
        return true;
    }

    public void end(World world, Runnable onEnd) {
        UUID wid = world.getUID();
        Active a = activeByWorld.remove(wid);
        if (a != null) {
            a.task.cancel();
        }
        try {
            onEnd.run();
        } catch (Throwable t) {
            plugin.getLogger().warning("Ultimate end hook failed: " + t.getMessage());
        }
    }

    public void broadcastUltimate(Player caster, String text, TextColor accent) {
        Component msg = Component.text("TIMEBOUND", accent, TextDecoration.BOLD)
                .append(Component.text(" \u00bb ", NamedTextColor.DARK_GRAY))
                .append(Component.text(text, NamedTextColor.WHITE));
        Bukkit.broadcast(msg);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.35f, 0.85f);
        }
        caster.getWorld().spawnParticle(Particle.END_ROD, caster.getLocation().add(0, 1.0, 0), 35, 1.1, 0.6, 1.1, 0.01);
    }

    public void applyWorldDebuff(World world, Player caster, int slownessAmp, int weaknessAmp) {
        for (LivingEntity le : world.getLivingEntities()) {
            if (le instanceof Player other) {
                if (other.equals(caster)) continue;
                if (TrustManager.isTrusted(caster, other)) continue;
            }
            le.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 25, slownessAmp, false, true, true));
            le.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS, 25, weaknessAmp, false, true, true));
        }
    }
}

