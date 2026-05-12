package com.doze.timebound;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class TimeClockListener implements Listener {
    private static final long CLOCK_COOLDOWN_MS = 60_000L;
    private static final long CLOCK_RESPAWN_DELAY_MS = 300_000L;

    private final Main plugin;
    private final Map<UUID, Map<ClockType, Long>> cooldowns = new EnumMapBackedCooldowns();

    public TimeClockListener(Main plugin) {
        this.plugin = plugin;
    }

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

    @EventHandler
    public void onClockSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();

        ItemStack currentOffhand = player.getInventory().getItemInOffHand();
        ClockType offhandType = TimeClockItems.getClockType(plugin, currentOffhand);

        if (offhandType != null) {
            event.setCancelled(true);
            activateClock(player, offhandType);
            return;
        }

        ItemStack currentMainhand = player.getInventory().getItemInMainHand();
        ClockType mainhandType = TimeClockItems.getClockType(plugin, currentMainhand);

        if (mainhandType != null) {
            event.setCancelled(true);
            player.sendMessage(Component.text("You must hold the clock in your offhand to use it! Open your inventory and place it in the shield slot.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onClockInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == null) return;

        Player p = event.getPlayer();

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
                    if (playerHasClock(p, holoType)) {
                        p.sendMessage(Component.text("You already have a " + holoType.displayName() + "!", NamedTextColor.RED));
                        return;
                    }
                    
                    ItemStack clockItem = TimeClockItems.createClock(plugin, holoType);
                    p.getInventory().addItem(clockItem);
                    Location playerLocation = p.getLocation();
                    if (playerLocation != null) {
                        p.playSound(playerLocation, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                    }
                    display.getWorld().spawnParticle(Particle.CLOUD, display.getLocation().add(0, 0.5, 0), 15, 0.2, 0.2, 0.2, 0.05);
                    
                    Location displayLoc = display.getLocation();
                    display.remove();
                    
                    if (holoType != ClockType.BRAKE) {
                        scheduleClockRespawn(displayLoc, holoType);
                    }
                }
                return;
            }
        }

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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        ItemStack cursor = event.getCursor();
        ItemStack clicked = event.getCurrentItem();
        
        ClockType cursorClockType = TimeClockItems.getClockType(plugin, cursor);
        ClockType clickedClockType = TimeClockItems.getClockType(plugin, clicked);
        
        if (cursorClockType != null && clickedClockType != null && cursorClockType == clickedClockType) {
            event.setCancelled(true);
            player.sendMessage(Component.text("You cannot hold 2 of the same " + cursorClockType.displayName() + "!", NamedTextColor.RED));
            return;
        }
        
        if (cursorClockType != null && playerHasClock(player, cursorClockType)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("You already have a " + cursorClockType.displayName() + "!", NamedTextColor.RED));
        }
    }

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
            Location playerLocation = player.getLocation();
            if (playerLocation != null) {
                player.playSound(playerLocation, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 2.0f);
            }
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
        Location playerLocation = player.getLocation();
        if (playerLocation != null) {
            player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, playerLocation.add(0, 1.0, 0), 35, 0.4, 0.7, 0.4, 0.1);
        }
        return true;
    }

    private boolean useReverseClock(Player player) {
        plugin.getTimeManager().rewindHealth(player, 100);
        Location playerLocation = player.getLocation();
        if (playerLocation != null) {
            player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, playerLocation.add(0, 1.0, 0), 40, 0.5, 0.7, 0.5, 0.02);
        }
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

    private boolean playerHasClock(Player player, ClockType type) {
        int count = 0;
        ItemStack[] contents = player.getInventory().getContents();
        if (contents != null) {
            for (ItemStack item : contents) {
                if (TimeClockItems.getClockType(plugin, item) == type) {
                    count++;
                    if (count > 1) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void scheduleClockRespawn(Location originalLocation, ClockType type) {
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            plugin.getClockListener().spawnClickableClock(originalLocation, type);
            Component message = Component.text(type.displayName() + " has respawned!", type.color());
            for (Player target : Bukkit.getOnlinePlayers()) {
                target.sendMessage(message);
            }
            plugin.getLogger().log(Level.INFO, "{0} respawned at {1}, {2}, {3}", new Object[]{type.displayName(), originalLocation.getBlockX(), originalLocation.getBlockY(), originalLocation.getBlockZ()});
        }, CLOCK_RESPAWN_DELAY_MS / 50);
    }
}