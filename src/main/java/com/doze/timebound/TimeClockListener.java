package com.doze.timebound;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Clock systems:
 * - Hold-to-claim popup near clickable clocks (sneak channel for 5s).
 * - Clock activation: swap hands (F) while holding the clock in offhand.
 *
 * Bugfix: players can obtain a clock as long as they do not already have that SAME clock type in inventory/offhand.
 */
public class TimeClockListener implements Listener {
    private static final int CLAIM_RADIUS_BLOCKS = 4;
    private static final int CLAIM_HOLD_TICKS = 100; // 5s
    private static final long DOUBLE_SNEAK_WINDOW_MS = 300L;
    private static final int CLAIM_FREEZE_TICKS = 40; // brief lock after claim

    private final Main plugin;
    private final Map<UUID, Map<ClockType, Long>> cooldowns = new EnumMapBackedCooldowns();
    private final Map<UUID, Long> lastSneakToggle = new HashMap<>();
    private final Map<UUID, ClaimFreeze> claimFrozen = new HashMap<>();

    private record ClaimFreeze(Location lockAt, int untilTick) {}

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
            UUID lockedClaimer = null;
            int claimSoundTaskId = -1;
            final Map<UUID, Integer> progress = new HashMap<>();

            @Override
            public void run() {
                if (!display.isValid()) {
                    // Cleanup any leftover sound task
                    if (claimSoundTaskId >= 0) {
                        Bukkit.getScheduler().cancelTask(claimSoundTaskId);
                    }
                    cancel();
                    return;
                }

                yaw += 3;
                display.setRotation(yaw, 0);
                display.getWorld().spawnParticle(type.ambientParticle(), display.getLocation().add(0, 0.5, 0), 1, 0.2, 0.2, 0.2, 0.01);

                for (Player p : display.getWorld().getNearbyPlayers(display.getLocation(), CLAIM_RADIUS_BLOCKS)) {
                    if (p == null || !p.isOnline()) continue;

                    // Clock obtain restriction: only prevent duplicates of the SAME clock in the SAME inventory/offhand.
                    if (playerHasClock(p, type)) {
                        p.sendActionBar(Component.text("You already possess this clock.", NamedTextColor.RED));
                        continue;
                    }

                    if (lockedClaimer != null && !lockedClaimer.equals(p.getUniqueId())) {
                        p.sendActionBar(Component.text("Someone is claiming this clock...", NamedTextColor.DARK_GRAY));
                        continue;
                    }

                    if (!p.isSneaking()) {
                        // Cancel claim sound if player stops sneaking
                        if (lockedClaimer != null && lockedClaimer.equals(p.getUniqueId()) && claimSoundTaskId >= 0) {
                            Bukkit.getScheduler().cancelTask(claimSoundTaskId);
                            claimSoundTaskId = -1;
                        }
                        
                        progress.put(p.getUniqueId(), 0);
                        if (lockedClaimer != null && lockedClaimer.equals(p.getUniqueId())) {
                            lockedClaimer = null;
                        }
                        p.sendActionBar(Component.text("Hold Sneak for 5 seconds to claim this clock", NamedTextColor.YELLOW));
                        continue;
                    }

                    if (lockedClaimer == null) {
                        lockedClaimer = p.getUniqueId();
                        
                        // Start global claim sound (5 seconds, dramatic)
                        claimSoundTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                            // Play layered cinematic sounds every 10 ticks
                            for (Player nearbyPlayer : display.getWorld().getNearbyPlayers(display.getLocation(), 50)) {
                                nearbyPlayer.playSound(display.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, org.bukkit.SoundCategory.MASTER, 1.2f, 0.8f);
                                nearbyPlayer.playSound(display.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, org.bukkit.SoundCategory.MASTER, 0.8f, 1.0f);
                            }
                        }, 0L, 10L).getTaskId();
                    }

                    int t = progress.getOrDefault(p.getUniqueId(), 0) + 1;
                    progress.put(p.getUniqueId(), t);
                    int pct = (int) Math.min(100, Math.round((t / (double) CLAIM_HOLD_TICKS) * 100.0));
                    int secondsLeft = (int) Math.ceil((CLAIM_HOLD_TICKS - t) / 20.0);
                    p.sendActionBar(Component.text("Claiming... " + pct + "% (" + secondsLeft + "s)", NamedTextColor.GOLD));

                    if (t >= CLAIM_HOLD_TICKS) {
                        // Claim complete - stop sound
                        if (claimSoundTaskId >= 0) {
                            Bukkit.getScheduler().cancelTask(claimSoundTaskId);
                            claimSoundTaskId = -1;
                        }
                        
                        ItemStack clockItem = TimeClockItems.createClock(plugin, type);
                        TimeItemUid.ensure(plugin, clockItem);
                        var leftovers = p.getInventory().addItem(clockItem);
                        for (ItemStack leftover : leftovers.values()) {
                            p.getWorld().dropItemNaturally(p.getLocation(), leftover);
                        }

                        RecipeUnlockListener recipes = plugin.getRecipeUnlockListener();
                        if (recipes != null) recipes.recordClockClaim(p, type);

                        Component clockName = Component.text(type.displayName(), type.color(), net.kyori.adventure.text.format.TextDecoration.BOLD);
                        Bukkit.broadcast(Component.text(p.getName(), NamedTextColor.WHITE)
                                .append(Component.text(" has claimed the ", NamedTextColor.YELLOW))
                                .append(clockName)
                                .append(Component.text("!", NamedTextColor.YELLOW)));

                        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
                        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.0f);
                        freezeAfterClaim(p);
                        display.getWorld().spawnParticle(Particle.CLOUD, display.getLocation().add(0, 0.5, 0), 20, 0.25, 0.25, 0.25, 0.05);
                        display.remove();
                        plugin.getGlobalScanner().requestScan(GlobalTimeItemScanner.Reason.CRAFT);
                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }


    public void spawnTimedClock(Location loc, ClockType type, long timerSeconds) {
        ItemDisplay display = loc.getWorld().spawn(loc, ItemDisplay.class, entity -> {
            entity.setItemStack(TimeClockItems.createClock(plugin, type));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "timed_clock"), PersistentDataType.STRING, type.key());
            entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "timer_end"), PersistentDataType.LONG, System.currentTimeMillis() + timerSeconds * 1000);
            entity.setDisplayWidth(1.5f);
            entity.setDisplayHeight(1.5f);
            entity.setCustomNameVisible(true);
            entity.customName(Component.text(type.displayName() + " (Locked)", type.color(), net.kyori.adventure.text.format.TextDecoration.BOLD));
        });

        new BukkitRunnable() {
            float yaw = 0;
            final long timerEndTime = System.currentTimeMillis() + timerSeconds * 1000;

            @Override
            public void run() {
                if (!display.isValid()) {
                    cancel();
                    return;
                }

                long timeLeft = timerEndTime - System.currentTimeMillis();
                if (timeLeft <= 0) {
                    display.getPersistentDataContainer().remove(new NamespacedKey(plugin, "timed_clock"));
                    display.getPersistentDataContainer().remove(new NamespacedKey(plugin, "timer_end"));
                    display.getPersistentDataContainer().set(new NamespacedKey(plugin, "clickable_clock"), PersistentDataType.STRING, type.key());
                    display.customName(Component.text(type.displayName(), type.color(), net.kyori.adventure.text.format.TextDecoration.BOLD));

                    display.getWorld().playSound(display.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.2f);
                    display.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, display.getLocation(), 20, 0.3, 0.3, 0.3, 0.05);

                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getWorld().equals(display.getWorld()) && player.getLocation().distance(display.getLocation()) <= 50) {
                            Component message = Component.text("The ", NamedTextColor.YELLOW)
                                    .append(Component.text(type.displayName(), type.color(), net.kyori.adventure.text.format.TextDecoration.BOLD))
                                    .append(Component.text(" is now available for claiming!", NamedTextColor.YELLOW));
                            player.sendMessage(message);
                        }
                    }

                    cancel();
                    return;
                }

                yaw += 3;
                display.setRotation(yaw, 0);
                long secondsLeft = (timeLeft + 999) / 1000;
                display.customName(Component.text(type.displayName() + " (" + secondsLeft + "s)", type.color(), net.kyori.adventure.text.format.TextDecoration.BOLD));
                display.getWorld().spawnParticle(type.ambientParticle(), display.getLocation().add(0, 0.5, 0), 1, 0.2, 0.2, 0.2, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        Player p = event.getPlayer();
        long now = System.currentTimeMillis();
        UUID id = p.getUniqueId();
        long last = lastSneakToggle.getOrDefault(id, 0L);
        lastSneakToggle.put(id, now);

        if (now - last > DOUBLE_SNEAK_WINDOW_MS) {
        }

        // No-op: reserved for future double-sneak combos.
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        handleSwapHands(event);
    }

    private void handleSwapHands(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        
        // Block swapping if player is frozen after claim
        if (isClaimFrozen(p)) {
            event.setCancelled(true);
            return;
        }

        // CRITICAL: Only activate clocks when held in OFFHAND
        // Main hand clock items are weapons, not clocks
        ClockType type = TimeClockItems.getClockType(plugin, event.getOffHandItem());
        if (type == null) return;

        // Use swap-hands as the activation key; don't actually swap items.
        event.setCancelled(true);

        // Sneak + F = Charged activation
        // F = Instant activation
        if (p.isSneaking()) {
            startChargedClockActivation(p, type);
        } else {
            activateClock(p, type);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        ClaimFreeze f = claimFrozen.get(p.getUniqueId());
        if (f == null) return;
        if (Bukkit.getCurrentTick() >= f.untilTick()) {
            claimFrozen.remove(p.getUniqueId());
            return;
        }
        // Hard-freeze: snap back.
        event.setTo(f.lockAt());
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

    /**
     * Handle Time Clock items in creative mode inventory without causing desync.
     * DO NOT immediately update inventory - this causes items to disappear.
     * Instead, defer operations until after the client transaction completes.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryCreative(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        ItemStack cursor = event.getCursor();
        
        // Just track the creative action - don't modify during event
        // Modifying during creative events causes client desync
        if (TimeClockItems.getClockType(plugin, cursor) != null) {
            // Defer any validation to after the client settles
            // No immediate updateInventory() - this causes disappearing items!
        }
    }

    private void activateClock(Player player, ClockType type) {
        long left = cooldownLeft(player.getUniqueId(), type);
        if (left > 0) {
            player.sendMessage(Component.text(type.displayName() + " cooldown: " + (left / 1000) + "s", NamedTextColor.RED));
            return;
        }

        boolean ok = switch (type) {
            case FREEZE -> useLunarDialClock(player);
            case BRAKE -> useChronoLockClock(player);
            case SKIP -> useFlashstepClock(player);
            case REVERSE -> useRequiemClock(player);
        };

        if (ok) {
            setCooldown(player.getUniqueId(), type, System.currentTimeMillis() + cooldownMs(type));
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 2.0f);
        }
    }

    private boolean useChronoLockClock(Player player) {
        LivingEntity target = getTarget(player);
        if (target == null) return false;
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 140, 1, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 140, 0, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 140, 0, false, true, true));
        target.getWorld().spawnParticle(Particle.ASH, target.getLocation().add(0, 1.0, 0), 35, 0.4, 0.7, 0.4, 0.02);
        target.getWorld().spawnParticle(Particle.SMOKE, target.getLocation().add(0, 1.0, 0), 25, 0.4, 0.7, 0.4, 0.02);
        return true;
    }

    private boolean useFlashstepClock(Player player) {
        // 8-block directional teleport.
        Location from = player.getLocation();
        RayTraceResult ray = player.getWorld().rayTraceBlocks(player.getEyeLocation(), player.getEyeLocation().getDirection(), 8);
        Location target = ray != null
                ? ray.getHitPosition().toLocation(player.getWorld()).subtract(player.getEyeLocation().getDirection().normalize().multiply(0.6))
                : from.clone().add(from.getDirection().normalize().multiply(8));
        target.setYaw(from.getYaw());
        target.setPitch(from.getPitch());
        player.teleport(target);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 2, false, true, true)); // Speed III 8s
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.add(0, 1.0, 0), 40, 0.4, 0.7, 0.4, 0.1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.9f, 1.4f);
        return true;
    }

    private boolean useRequiemClock(Player player) {
        // Heal 2.5 hearts + restore saturation.
        org.bukkit.attribute.AttributeInstance maxHealthAttr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double max = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
        player.setHealth(Math.min(max, player.getHealth() + 5.0));
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        Location loc = player.getLocation();
        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc.add(0, 1.0, 0), 45, 0.5, 0.7, 0.5, 0.02);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.3f);
        return true;
    }

    private boolean useLunarDialClock(Player player) {
        // 5-second local timestop in a radius, plus lingering freezing after.
        int radius = 20;
        for (LivingEntity e : player.getWorld().getNearbyLivingEntities(player.getLocation(), radius, radius / 2.0, radius)) {
            if (e.equals(player)) continue;
            TimeFreezeManager.freeze(e);
            e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 10, false, true, true));
            e.setFreezeTicks(e.getMaxFreezeTicks() + 200); // lingering freeze overlay
        }
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0, 1.0, 0), 80, 2.0, 1.0, 2.0, 0.03);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 0.9f, 0.8f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (LivingEntity e : player.getWorld().getNearbyLivingEntities(player.getLocation(), radius, radius / 2.0, radius)) {
                if (!TimeFreezeManager.isFrozen(e)) continue;
                TimeFreezeManager.unfreeze(e);
                TimeFreezeManager.applyBufferedDamage(e);
                TimeFreezeManager.applyBufferedKnockback(e);
                TimeFreezeManager.clear(e);
                e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 1, false, true, true));
            }
        }, 100L);
        return true;
    }

    private long cooldownMs(ClockType type) {
        return switch (type) {
            case BRAKE -> 60_000L;
            case FREEZE -> 180_000L;
            case REVERSE -> 45_000L;
            case SKIP -> 45_000L;
        };
    }

    private boolean isClaimFrozen(Player p) {
        ClaimFreeze f = claimFrozen.get(p.getUniqueId());
        return f != null && Bukkit.getCurrentTick() < f.untilTick();
    }

    /**
     * Freeze player after claim with proper cleanup.
     * Ensures: movement locked, controls disabled, complete unfrozen state after.
     */
    private void freezeAfterClaim(Player p) {
        UUID playerId = p.getUniqueId();
        Location lock = p.getLocation().clone();
        int until = Bukkit.getCurrentTick() + CLAIM_FREEZE_TICKS;
        
        // Set freeze state
        claimFrozen.put(playerId, new ClaimFreeze(lock, until));
        
        // Apply freeze effects
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, CLAIM_FREEZE_TICKS, 10, false, false, false));
        
        // Show title and action bar
        p.showTitle(net.kyori.adventure.title.Title.title(
                Component.text("CLOCK CLAIMED", NamedTextColor.GOLD, net.kyori.adventure.text.format.TextDecoration.BOLD),
                Component.empty(),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(0), java.time.Duration.ofMillis(900), java.time.Duration.ofMillis(150))
        ));
        p.sendActionBar(Component.text("Time holds you still...", NamedTextColor.GRAY));
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, lock.add(0, 1.0, 0), 20, 0.4, 0.6, 0.4, 0.02);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.7f, 1.4f);
        
        // Schedule cleanup after freeze duration expires
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Remove freeze state and ensure complete unfrozen
            claimFrozen.remove(playerId);
            
            // Only unfroze if player is still online
            if (p.isOnline()) {
                // Remove all slowness effects applied during freeze
                p.removePotionEffect(PotionEffectType.SLOWNESS);
                
                // Reset velocity to ensure movement restoration
                if (p.getVelocity().length() < 0.01) {
                    p.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                }
                
                // Update inventory to ensure sync
                p.updateInventory();
                
                // Broadcast completion (optional, for debugging)
                // plugin.getLogger().info("Claim freeze ended for " + p.getName());
            }
        }, CLAIM_FREEZE_TICKS + 1);
    }

    private void startChargedClockActivation(Player player, ClockType type) {
        // Minimal anti-spam: if already on cooldown, don't start.
        long left = cooldownLeft(player.getUniqueId(), type);
        if (left > 0) {
            player.sendMessage(Component.text(type.displayName() + " cooldown: " + (left / 1000) + "s", NamedTextColor.RED));
            return;
        }
        player.sendActionBar(Component.text("Charging " + type.displayName() + "...", NamedTextColor.YELLOW));
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (!player.isOnline() || !player.isSneaking()) {
                    cancel();
                    return;
                }
                t += 2;
                player.getWorld().spawnParticle(type.ambientParticle(), player.getLocation().add(0, 1.0, 0), 2, 0.3, 0.5, 0.3, 0.01);
                if (t >= 6) {
                    cancel();
                    activateClock(player, type);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
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

    /**
     * Reset all clock cooldowns for a player (public API for /timebound cooldown command).
     */
    public void resetClockCooldowns(Player player) {
        Map<ClockType, Long> map = cooldowns.get(player.getUniqueId());
        if (map != null) {
            map.clear();
        }
        player.sendMessage(Component.text("All clock cooldowns have been reset.", NamedTextColor.GREEN));
    }

    private boolean playerHasClock(Player player, ClockType type) {
        ItemStack[] contents = player.getInventory().getContents();
        if (contents != null) {
            for (ItemStack item : contents) {
                if (TimeClockItems.getClockType(plugin, item) == type) return true;
            }
        }
        return TimeClockItems.getClockType(plugin, player.getInventory().getItemInOffHand()) == type;
    }
}
