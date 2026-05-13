package com.doze.timebound;

import org.bukkit.Location;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;

public class TimeFreezeManager {
    private static final double MAX_FREEZE_BLADE_DAMAGE = 20.0;

    private static final Set<UUID> frozen = new HashSet<>();
    private static final Map<UUID, Location> lockedLocation = new HashMap<>();

    private static final Map<UUID, Double> damageBuffer = new HashMap<>();
    private static final Map<UUID, Player> damageSourceBuffer = new HashMap<>();
    private static final Map<UUID, ItemStack> weaponBuffer = new HashMap<>();
    private static final Map<UUID, Vector> knockbackBuffer = new HashMap<>();

    public static void freeze(Entity e) {
        frozen.add(e.getUniqueId());
        lockedLocation.put(e.getUniqueId(), e.getLocation().clone());
    }

    public static void unfreeze(Entity e) {
        frozen.remove(e.getUniqueId());
        lockedLocation.remove(e.getUniqueId());
    }

    public static boolean isFrozen(Entity e) {
        return frozen.contains(e.getUniqueId());
    }

    public static void lockPosition(Entity e) {
        Location loc = lockedLocation.get(e.getUniqueId());
        if (loc != null) {
            if (e.getLocation().distanceSquared(loc) > 0.001) {
                e.teleport(loc);
            }
        }
    }

    public static void bufferDamage(Entity target, double damage) {
        UUID id = target.getUniqueId();
        damageBuffer.put(id, Math.min(MAX_FREEZE_BLADE_DAMAGE, damageBuffer.getOrDefault(id, 0.0) + Math.max(0.0, damage)));
    }

    public static void bufferDamage(Entity target, double damage, Player attacker, ItemStack weapon) {
        UUID id = target.getUniqueId();
        damageBuffer.put(id, Math.min(MAX_FREEZE_BLADE_DAMAGE, damageBuffer.getOrDefault(id, 0.0) + Math.max(0.0, damage)));
        damageSourceBuffer.put(id, attacker);
        if (weapon != null) {
            weaponBuffer.put(id, weapon.clone());
        }
    }

    public static void applyBufferedDamage(Entity e) {
        UUID id = e.getUniqueId();
        if (!damageBuffer.containsKey(id)) return;

        double damage = Math.min(MAX_FREEZE_BLADE_DAMAGE, damageBuffer.get(id));
        if (damage > 0 && e instanceof LivingEntity le) {
            Player attacker = damageSourceBuffer.get(id);

            le.setNoDamageTicks(0);

            if (attacker != null) {
                le.damage(damage, attacker);
                applyDelayedFireAspect(le, weaponBuffer.get(id));
            } else {
                le.damage(damage);
            }
        }
    }

    private static void applyDelayedFireAspect(LivingEntity target, ItemStack weapon) {
        if (weapon == null) return;
        int fireAspect = weapon.getEnchantmentLevel(Enchantment.FIRE_ASPECT);
        if (fireAspect <= 0) return;
        target.setFireTicks(Math.max(target.getFireTicks(), fireAspect * 80));
    }

    public static void bufferKnockback(Entity e, Vector v) {
        UUID id = e.getUniqueId();
        Vector current = knockbackBuffer.getOrDefault(id, new Vector(0, 0, 0));
        current.add(v);

        if (current.lengthSquared() > 9.0) {
            current.normalize().multiply(3.0);
        }
        knockbackBuffer.put(id, current);
    }

    public static void applyBufferedKnockback(Entity e) {
        Vector v = knockbackBuffer.get(e.getUniqueId());
        if (v != null && e instanceof LivingEntity le) {
            le.setVelocity(v);
        }
    }

    public static void clear(Entity e) {
        UUID id = e.getUniqueId();
        damageBuffer.remove(id);
        damageSourceBuffer.remove(id);
        weaponBuffer.remove(id);
        knockbackBuffer.remove(id);
    }
}
