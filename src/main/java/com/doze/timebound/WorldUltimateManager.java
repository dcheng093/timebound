package com.doze.timebound;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class WorldUltimateManager {
    public enum Ultimate {
        CHRONO_LOCK_DECELERATION,
        FLASHSTEP_ACCELERATION,
        LUNAR_DIAL_DOMAIN,
        ETERNITY_SANCTUARY
    }
    private static final int DURATION_TICKS = 200;
    private final Main plugin;
    private final Map<UUID, Active> activeByWorld = new ConcurrentHashMap<>();
    private final Map<UUID, Long> worldTimeSnapshots = new HashMap<>();
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
        worldTimeSnapshots.put(wid, world.getTime());
        int startTick = Bukkit.getCurrentTick();
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int elapsed = Bukkit.getCurrentTick() - startTick;
            manipulateWorldTime(world, ult, elapsed);
            if (elapsed >= DURATION_TICKS) {
                end(world, onEnd);
                return;
            }
            onTick.run();
        }, 0L, 1L);
        activeByWorld.put(wid, new Active(ult, caster.getUniqueId(), task, startTick));
        return true;
    }

    private void manipulateWorldTime(World world, Ultimate ult, int elapsedTicks) {
        long originalTime = worldTimeSnapshots.getOrDefault(world.getUID(), world.getTime());
        long currentTime = world.getTime();
        long newTime = switch (ult) {
            case FLASHSTEP_ACCELERATION -> originalTime + (elapsedTicks * 20L);
            case CHRONO_LOCK_DECELERATION -> originalTime + (elapsedTicks / 5L);
            case LUNAR_DIAL_DOMAIN, ETERNITY_SANCTUARY -> originalTime;
            default -> currentTime;
        };
        newTime = newTime % 24000L;
        world.setTime(newTime);
    }

    public void end(World world, Runnable onEnd) {
        UUID wid = world.getUID();
        Active a = activeByWorld.remove(wid);
        if (a != null) {
            a.task.cancel();
        }
        Long snapshotTime = worldTimeSnapshots.remove(wid);
        if (snapshotTime != null) {
            long currentTime = world.getTime();
            int transitionTicks = 10;
            new BukkitRunnable() {
                int tick = 0;
                @Override
                public void run() {
                    tick++;
                    long progress = (long) (currentTime + (snapshotTime - currentTime) * (tick / (double) transitionTicks));
                    world.setTime(progress % 24000L);
                    if (tick >= transitionTicks) {
                        world.setTime(snapshotTime);
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
        try {
            onEnd.run();
        } catch (Throwable t) {
            String msg = t.getMessage() != null ? t.getMessage() : "unknown error";
            plugin.getLogger().warning(String.format("Ultimate end hook failed: %s", msg));
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
            if (le == null) continue;
            if (le instanceof Player other) {
                if (other.equals(caster)) continue;
                if (TrustManager.isTrusted(caster, other)) continue;
            }
            le.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 25, slownessAmp, false, true, true));
            le.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS, 25, weaknessAmp, false, true, true));
        }
    }
}

