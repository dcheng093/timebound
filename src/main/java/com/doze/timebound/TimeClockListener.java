package com.doze.timebound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class TimeClockListener implements Listener {
    private static final long CLOCK_COOLDOWN_MS = 60_000L;

    private final Main plugin;
    private final Map<UUID, Map<ClockType, Long>> cooldowns = new EnumMapBackedCooldowns();

    public TimeClockListener(Main plugin) {
        this.plugin = plugin;
    }

    // ==========================================
    // HOLOGRAM SPAWNING LOGIC
    // ==========================================
    public void spawnClickableClock(Location loc, ClockType type) {
        ItemDisplay display = loc.getWorld().spawn(loc, ItemDisplay.class, entity -> {
            entity.setItemStack(TimeClockItems.createClock(plugin, type));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);

            entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "clickable_clock"), PersistentDataType.STRING, type.key());

            entity.setDisplayWidth(1.5f);
            entity.setDisplayHeight(1.5f);

            entity.setCustomNameVisible(true);
            entity.customName(Component.text(type.displayName(), type.color(), net.kyori.adventure.text.format.TextDecoration.BOLD));
        });

        new BukkitRunnable() {
            float yaw = 0;
            @Override
            public void run() {
                if (!display.isValid()) {
                    this.cancel();
                    return;
                }
                yaw += 3;
                display.setRotation(yaw, 0);
                display.getWorld().spawnParticle(type.ambientParticle(), display.getLocation().add(0, 0.5, 0), 1, 0.2, 0.2, 0.2, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==========================================
    // ABILITY LOGIC (PRESS F TO USE OFFHAND)
    // ==========================================
    @EventHandler
    public void onClockSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();

        // If they have the clock in their offhand and press F:
        ItemStack currentOffhand = player.getInventory().getItemInOffHand();
        ClockType offhandType = TimeClockItems.getClockType(plugin, currentOffhand);

        if (offhandType != null) {
            event.setCancelled(true);
            activateClock(player, offhandType);
            return;
        }

        // If they try to press F while it's in their main hand:
        ItemStack currentMainhand = player.getInventory().getItemInMainHand();
        ClockType mainhandType = TimeClockItems.getClockType(plugin, currentMainhand);

        if (mainhandType != null) {
            event.setCancelled(true);
            player.sendMessage(Component.text("You must hold the clock in your offhand to use it! Open your inventory and place it in the shield slot.", NamedTextColor.RED));
        }
    }

    // ==========================================
    // UNIFIED INTERACT LISTENER (HOLOGRAMS & RIGHT CLICK)
    // ==========================================
    @EventHandler
    public void onClockInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == null) return;

        Player p = event.getPlayer();

        // 1. HOLOGRAM RETRIEVAL LOGIC (Main Hand)
        if (event.getHand() == EquipmentSlot.HAND) {
            NamespacedKey clickKey = new NamespacedKey(plugin, "clickable_clock");

            RayTraceResult ray = p.getWorld().rayTraceEntities(
                    p.getEyeLocation(),
                    p.getEyeLocation().getDirection(),
                    4.0,
                    0.5,
                    entity -> entity instanceof ItemDisplay && entity.getPersistentDataContainer().has(clickKey, PersistentDataType.STRING)
            );

            if (ray != null && ray.getHitEntity() instanceof ItemDisplay display) {
                event.setCancelled(true);
                String typeStr = display.getPersistentDataContainer().get(clickKey, PersistentDataType.STRING);
                ClockType holoType = ClockType.fromKey(typeStr);

                if (holoType != null) {
                    p.getInventory().addItem(TimeClockItems.createClock(plugin, holoType));
                    p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);

                    display.getWorld().spawnParticle(Particle.CLOUD, display.getLocation().add(0, 0.5, 0), 15, 0.2, 0.2, 0.2, 0.05);
                    display.remove();
                }
                return;
            }
        }

        // 2. BACKUP RIGHT-CLICK ABILITY TRIGGER
        // If the client *does* send a right-click packet (e.g. clicking a block), trigger it!
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            ClockType type = TimeClockItems.getClockType(plugin, p.getInventory().getItemInOffHand());
            if (type != null) {
                event.setCancelled(true);
                activateClock(p, type);
            }
        } else if (event.getHand() == EquipmentSlot.HAND) {
            ClockType type = TimeClockItems.getClockType(plugin, p.getInventory().getItemInMainHand());
            if (type != null) {
                event.setCancelled(true);
                p.sendMessage(Component.text("You must hold the clock in your offhand to use it!", NamedTextColor.RED));
            }
        }
    }

    // ==========================================
    // ABILITY ACTIVATION LOGIC
    // ==========================================
    private void activateClock(Player player, ClockType type) {
        long left = cooldownLeft(player.getUniqueId(), type);
        if (left > 0) {
            player.sendMessage(Component.text(type.displayName() + " cooldown: " + (left / 1000) + "s", NamedTextColor.RED));
            return;
        }

        boolean ok = switch (type) {
            case FREEZE -> useFreezeClock(player);
            case BRAKE -> useBrakeClock(player);
            case SKIP -> useSkipClock(player);
            case REVERSE -> useReverseClock(player);
        };

        if (ok) {
            setCooldown(player.getUniqueId(), type, System.currentTimeMillis() + CLOCK_COOLDOWN_MS);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 2.0f);
        }
    }

    private boolean useFreezeClock(Player player) {
        LivingEntity target = getTarget(player);
        if (target == null) return false;
        target.setFreezeTicks(target.getMaxFreezeTicks() + 20);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 10, false, true, true));
        target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1.0, 0), 25, 0.4, 0.7, 0.4, 0.02);
        return true;
    }

    private boolean useBrakeClock(Player player) {
        LivingEntity target = getTarget(player);
        if (target == null) return false;
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1, false, true, true));
        target.getWorld().spawnParticle(Particle.ASH, target.getLocation().add(0, 1.0, 0), 30, 0.4, 0.7, 0.4, 0.02);
        target.getWorld().spawnParticle(Particle.SMOKE, target.getLocation().add(0, 1.0, 0), 20, 0.4, 0.7, 0.4, 0.02);
        return true;
    }

    private boolean useSkipClock(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 2, false, true, true));
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0, 1.0, 0), 35, 0.4, 0.7, 0.4, 0.1);
        return true;
    }

    private boolean useReverseClock(Player player) {
        plugin.getTimeManager().rewindHealth(player, 100);
        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0, 1.0, 0), 40, 0.5, 0.7, 0.5, 0.02);
        return true;
    }

    private LivingEntity getTarget(Player player) {
        RayTraceResult ray = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                30,
                0.75,
                e -> e instanceof LivingEntity && !e.equals(player)
        );
        if (ray == null || !(ray.getHitEntity() instanceof LivingEntity living)) {
            player.sendMessage(Component.text("No target found in range.", NamedTextColor.RED));
            return null;
        }
        return living;
    }

    private long cooldownLeft(UUID player, ClockType type) {
        Map<ClockType, Long> map = cooldowns.computeIfAbsent(player, ignored -> new EnumMap<>(ClockType.class));
        return Math.max(0L, map.getOrDefault(type, 0L) - System.currentTimeMillis());
    }

    private void setCooldown(UUID player, ClockType type, long until) {
        cooldowns.computeIfAbsent(player, ignored -> new EnumMap<>(ClockType.class)).put(type, until);
    }

    private static class EnumMapBackedCooldowns extends java.util.HashMap<UUID, Map<ClockType, Long>> {
    }
}