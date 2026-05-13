package com.doze.timebound;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

@SuppressWarnings("null")
public class TimeBoundListener implements Listener {

    private static final int PASSIVE_CHANCE = 15;
    private static final int ULT_CHARGE_REQUIRED = 5;
    private static final int FREEZE_PASSIVE_TICKS = 100;
    private static final int SERVER_RADIUS = 50;
    private static final int FREEZE_TICKS = 100;
    private static final int REVERSED_CONTROLS_TICKS = 100;
    private static final double MAX_FREEZE_BLADE_DAMAGE = 20.0;

    private final Main plugin;
    private final Map<String, Long> abilityCooldowns = new HashMap<>();
    private final Map<String, Integer> ultCharges = new HashMap<>();
    private final Map<String, BossBar> ultBars = new HashMap<>();
    private final Map<String, BossBar> cooldownBars = new HashMap<>();
    private final Map<String, BossBar> victimBars = new HashMap<>();

    private final Deque<ReverseItemAction> reverseItemActions = new ArrayDeque<>();
    private final Map<UUID, Long> reverseAbsorbUntil = new HashMap<>();
    private final Map<UUID, Double> reverseAbsorbedDamage = new HashMap<>();
    private final Map<UUID, Long> reversedControlsUntil = new HashMap<>();
    private final Map<UUID, DeathSnapshot> recentDeaths = new HashMap<>();

    private final Map<UUID, Long> brakeSprintBlockedUntil = new HashMap<>();
    private final Map<UUID, Integer> skipStacks = new HashMap<>();
    private final Map<UUID, Integer> skipCharges = new HashMap<>();
    private final Map<UUID, Long> skipLastRegen = new HashMap<>();
    private final Map<UUID, Long> skipInternalCooldowns = new HashMap<>();
    private final Map<UUID, BossBar> skipChargeBars = new HashMap<>();
    private final Map<UUID, Long> skipLastHitTime = new HashMap<>();

    public TimeBoundListener(Main plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshHeldUltMeters, 10L, 10L);

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID id = player.getUniqueId();

                int charges = skipCharges.getOrDefault(id, 3);
                if (charges < 3) {
                    long lastRegen = skipLastRegen.getOrDefault(id, now);
                    if (now - lastRegen >= 10000) {
                        charges++;
                        skipCharges.put(id, charges);
                        skipLastRegen.put(id, now);
                        sendColored(player, NamedTextColor.YELLOW, "Time Skip Charge Restored (" + charges + "/3)");
                        Location playerLocation = player.getLocation();
                        if (playerLocation != null) {
                            playAt(playerLocation, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.2f);
                        }
                    }
                }

                Blade heldBlade = getBlade(player.getInventory().getItemInMainHand());
                if (heldBlade == Blade.SKIP) {
                    updateSkipChargeBar(player, charges, skipLastRegen.getOrDefault(id, now), now);
                } else {
                    removeSkipChargeBar(player);
                }

                int stacks = skipStacks.getOrDefault(id, 0);
                if (stacks > 0) {
                    long lastHit = skipLastHitTime.getOrDefault(id, now);
                    if (now - lastHit >= 4000) {
                        skipStacks.put(id, stacks - 1);
                        applySkipStackEffects(player, stacks - 1);
                        skipLastHitTime.put(id, now);

                        if (stacks - 1 > 0) {
                            sendColored(player, NamedTextColor.RED, "⚡ Stacks Decaying: " + (stacks - 1) + "/9");
                        } else {
                            sendColored(player, NamedTextColor.DARK_RED, "⚡ Time Skip Stacks Lost!");
                        }
                    }
                }
            }
        }, 20L, 20L);
    }

    public void resetCooldowns(Player player) {
        UUID id = player.getUniqueId();

        abilityCooldowns.entrySet().removeIf(entry -> entry.getKey().startsWith(id.toString()));
        ultCharges.entrySet().removeIf(entry -> entry.getKey().startsWith(id.toString()));

        skipInternalCooldowns.remove(id);
        skipCharges.put(id, 3);
        skipLastRegen.put(id, System.currentTimeMillis());

        cooldownBars.entrySet().removeIf(entry -> {
            if (entry.getKey().contains(id.toString())) {
                entry.getValue().removeAll();
                return true;
            }
            return false;
        });

        ultBars.entrySet().removeIf(entry -> {
            if (entry.getKey().contains(id.toString())) {
                entry.getValue().removeAll();
                return true;
            }
            return false;
        });

        removeSkipChargeBar(player);
        playAt(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
    }

    public void clearSkipStacks(Player player) {
        UUID id = player.getUniqueId();
        skipStacks.remove(id);
        skipLastHitTime.remove(id);
        applySkipStackEffects(player, 0);
        sendColored(player, NamedTextColor.DARK_RED, "⚡ Stacks Cleared!");
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        enforceSingleHeldBlade(player);
    }

    private void useAbility(Player player, Blade blade) {
        if (!checkAbilityCooldown(player, blade)) return;

        boolean used = switch (blade) {
            case FREEZE -> freezeLookedAtEntity(player);
            case BRAKE -> brakeLookedAtEntity(player);
            case SKIP -> dashForward(player);
            case REVERSE -> startDamageAbsorb(player);
        };

        if (used) {
            startAbilityCooldown(player, blade);
        }
    }

    private void useUlt(Player player, Blade blade) {
        if (!consumeUltCharge(player, blade)) return;

        switch (blade) {
            case FREEZE -> freezeServer(player);
            case BRAKE -> brakeServer(player);
            case SKIP -> skipServer(player);
            case REVERSE -> reverseWorld(player);
        }
    }

    private boolean checkAbilityCooldown(Player player, Blade blade) {
        if (blade == Blade.SKIP) return true;

        long cooldownMillis = blade.abilityCooldownMillis;
        if (cooldownMillis <= 0) return true;

        String key = chargeKey(player, blade);
        long now = System.currentTimeMillis();
        long readyAt = abilityCooldowns.getOrDefault(key, 0L);
        if (readyAt > now) {
            long secondsLeft = (long) Math.ceil((readyAt - now) / 1000.0);
            sendColored(player, NamedTextColor.RED, blade.displayName + " ability is on cooldown for " + secondsLeft + "s.");
            Location playerLocation = player.getLocation();
            if (playerLocation != null) {
                playAt(playerLocation, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            }
            return false;
        }

        return true;
    }

    private void startAbilityCooldown(Player player, Blade blade) {
        if (blade.abilityCooldownMillis <= 0) return;
        abilityCooldowns.put(chargeKey(player, blade), System.currentTimeMillis() + blade.abilityCooldownMillis);
        showCooldownBar(player, blade);
    }

    private boolean consumeUltCharge(Player player, Blade blade) {
        String key = chargeKey(player, blade);
        int charge = ultCharges.getOrDefault(key, 0);
        if (charge < ULT_CHARGE_REQUIRED) {
            sendColored(player, NamedTextColor.RED, blade.displayName + " ult is not charged. " + charge + "/" + ULT_CHARGE_REQUIRED + " player kills.");
            updateUltBar(player, blade);
            Location playerLocation = player.getLocation();
            if (playerLocation != null) {
                playAt(playerLocation, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            }
            return false;
        }

        ultCharges.put(key, 0);
        updateUltBar(player, blade);
        Location playerLocation = player.getLocation();
        if (playerLocation != null) {
            playAt(playerLocation, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.2f);
        }
        return true;
    }

    private boolean freezeLookedAtEntity(Player player) {
        LivingEntity target = getLookedAtLivingEntity(player, 30);
        if (target == null) {
            player.sendMessage("No entity in sight.");
            return false;
        }

        if (target instanceof Player p && TrustManager.isTrusted(player, p)) {
            sendColored(player, NamedTextColor.AQUA, "You cannot freeze a trusted player!");
            return false;
        }

        spawnThrownBlade(player, target);

        freezeEntity(target, FREEZE_TICKS, true);
        spawnLineParticles(player, target.getLocation(), Particle.SNOWFLAKE, 15);
        target.getWorld().spawnParticle(Particle.CLOUD, target.getLocation().add(0, 1.0, 0), 18, 0.35, 0.7, 0.35, 0.01);
        playAt(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.0f, 1.2f);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_POWDER_SNOW_PLACE, 1.0f, 0.7f);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 0.8f, 1.0f);
        return true;
    }

    private void spawnThrownBlade(Player player, LivingEntity target) {
        ItemStack weapon = player.getInventory().getItemInMainHand().clone();
        Location startLoc = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(1.0));
        Location targetLoc = target.getLocation().add(0, 1.0, 0);

        Vector dir = targetLoc.toVector().subtract(startLoc.toVector()).normalize();
        double distance = startLoc.distance(targetLoc);
        double speed = 2.5;
        int maxTicks = (int) Math.ceil(distance / speed);

        Location displayLoc = startLoc.clone();
        displayLoc.setDirection(dir);
        displayLoc.setPitch(displayLoc.getPitch() + 90f);

        ItemDisplay display = player.getWorld().spawn(displayLoc, ItemDisplay.class, entity -> {
            entity.setItemStack(weapon);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
            entity.setTeleportDuration(1);
        });

        new BukkitRunnable() {
            int ticks = 0;
            Location currentLoc = displayLoc.clone();

            @Override
            public void run() {
                if (ticks >= maxTicks || !display.isValid() || !target.isValid()) {
                    display.getWorld().spawnParticle(Particle.SNOWFLAKE, display.getLocation(), 25, 0.4, 0.4, 0.4, 0.05);
                    display.getWorld().spawnParticle(Particle.BLOCK, display.getLocation(), 15, 0.3, 0.3, 0.3, 0.05, Material.BLUE_ICE.createBlockData());
                    display.getWorld().playSound(display.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 1.2f);
                    display.remove();
                    cancel();
                    return;
                }

                currentLoc.add(dir.clone().multiply(speed));
                display.teleport(currentLoc);

                display.getWorld().spawnParticle(Particle.SNOWFLAKE, currentLoc, 3, 0.1, 0.1, 0.1, 0.01);

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private boolean brakeLookedAtEntity(Player player) {
        LivingEntity target = getLookedAtLivingEntity(player, 30);
        if (target == null) {
            player.sendMessage("No entity in sight.");
            return false;
        }

        if (target instanceof Player p && TrustManager.isTrusted(player, p)) {
            sendColored(player, NamedTextColor.AQUA, "You cannot brake a trusted player!");
            return false;
        }

        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 4, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 4, false, true, true));

        if (target instanceof Player targetPlayer) {
            brakeSprintBlockedUntil.put(targetPlayer.getUniqueId(), System.currentTimeMillis() + 10000);
            targetPlayer.setCooldown(Material.SHIELD, 200);
            targetPlayer.setFreezeTicks(100);
            showVictimTimer(targetPlayer, "Weakened", 5, BarColor.WHITE);
            showVictimTimer(targetPlayer, "Shield Disabled", 10, BarColor.RED);
            showVictimTimer(targetPlayer, "Cannot Jump", 5, BarColor.GREEN);
            targetPlayer.setSprinting(false);
        }
        spawnLineParticles(player, target.getLocation(), Particle.SMOKE, 18);
        target.getWorld().spawnParticle(Particle.SMOKE, target.getLocation().add(0, 1.0, 0), 35, 0.5, 0.8, 0.5, 0.03);
        target.getWorld().spawnParticle(Particle.ASH, target.getLocation().add(0, 1.0, 0), 18, 0.4, 0.6, 0.4, 0.01);
        playAt(player.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.8f, 0.6f);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WARDEN_LISTENING, 0.7f, 1.5f);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_IRON_GOLEM_DAMAGE, 0.6f, 0.8f);
        return true;
    }

    private boolean dashForward(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Location playerLocation = player.getLocation();
        if (playerLocation == null) return false;

        long nextAllowed = skipInternalCooldowns.getOrDefault(id, 0L);
        if (now < nextAllowed) {
            long left = (long) Math.ceil((nextAllowed - now) / 1000.0);
            sendColored(player, NamedTextColor.RED, "Time Skip ability is on cooldown for " + left + "s.");
            playAt(playerLocation, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            return false;
        }

        int charges = skipCharges.getOrDefault(id, 3);
        if (charges <= 0) {
            sendColored(player, NamedTextColor.RED, "No Time Skip charges left! Regenerating...");
            playAt(playerLocation, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            return false;
        }

        double maxDistance = 14.0;
        Vector dir = player.getEyeLocation().getDirection().normalize();
        RayTraceResult ray = player.getWorld().rayTraceBlocks(player.getEyeLocation(), dir, maxDistance, FluidCollisionMode.NEVER, true);

        double dist = ray != null && ray.getHitBlock() != null ? player.getEyeLocation().toVector().distance(ray.getHitPosition()) : maxDistance;

        if (dist < 1.5) {
            sendColored(player, NamedTextColor.RED, "Path is blocked!");
            return false;
        }

        Location target = playerLocation.clone().add(dir.multiply(dist - 0.5));
        target.setYaw(playerLocation.getYaw());
        target.setPitch(playerLocation.getPitch());

        if (!isSafeDashLocation(target)) {
            Location up = target.clone().add(0, 1, 0);
            if (isSafeDashLocation(up)) {
                target = up;
            } else {
                sendColored(player, NamedTextColor.RED, "No safe opening to teleport to!");
                playAt(playerLocation, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return false;
            }
        }

        player.getWorld().spawnParticle(Particle.PORTAL, playerLocation.clone().add(0, 1.0, 0), 50, 0.5, 1.0, 0.5, 0.1);
        playAt(playerLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.4f);

        player.teleport(target);

        player.getWorld().spawnParticle(Particle.PORTAL, target.clone().add(0, 1.0, 0), 50, 0.5, 1.0, 0.5, 0.1);
        playAt(target, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.4f);

        skipInternalCooldowns.put(id, now + 3000L);
        if (charges >= 3) {
            skipLastRegen.put(id, System.currentTimeMillis());
        }
        skipCharges.put(id, charges - 1);

        return true;
    }

    private boolean startDamageAbsorb(Player player) {
        UUID id = player.getUniqueId();
        reverseAbsorbedDamage.put(id, 0.0);
        long expiresAt = System.currentTimeMillis() + 5000;
        reverseAbsorbUntil.put(id, expiresAt);
        StarTimerManager.startTimer(plugin, player, "Reverse Absorb", 5);
        Location playerLocation = player.getLocation();
        if (playerLocation != null) {
            player.getWorld().spawnParticle(Particle.PORTAL, playerLocation.clone().add(0, 1.0, 0), 55, 0.7, 0.9, 0.7, 0.12);
            player.getWorld().spawnParticle(Particle.WITCH, playerLocation.clone().add(0, 1.0, 0), 25, 0.4, 0.7, 0.4, 0.02);
            playAt(playerLocation, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.4f, 1.0f);
            playAt(playerLocation, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.6f);
            playAt(playerLocation, Sound.ENTITY_WARDEN_ROAR, 1.2f, 1.0f);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Long currentExpiry = reverseAbsorbUntil.get(id);
            if (currentExpiry != null && currentExpiry == expiresAt) {
                releaseAbsorbedDamage(player);
            }
        }, 100L);
        return true;
    }

    private void freezeServer(Player caster) {
        StarTimerManager.startTimer(plugin, caster, "Freeze Blade Ult", 10);
        List<Entity> frozen = new ArrayList<>();
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 1.0f, 0.6f);
        caster.getWorld().spawnParticle(Particle.SNOWFLAKE, caster.getLocation().add(0, 1.0, 0), 180, 3.0, 2.0, 3.0, 0.04);

        for (LivingEntity entity : allLivingEntities()) {
            if (entity.equals(caster)) continue;
            if (entity instanceof Player p && TrustManager.isTrusted(caster, p)) continue;

            freezeEntity(entity, 200, true);
            frozen.add(entity);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Entity entity : frozen) {
                TimeFreezeManager.unfreeze(entity);
                TimeFreezeManager.applyBufferedDamage(entity);
                TimeFreezeManager.applyBufferedKnockback(entity);
                TimeFreezeManager.clear(entity);
            }
        }, 200L);
    }

    private void brakeServer(Player caster) {
        StarTimerManager.startTimer(plugin, caster, "Brake Blade Ult", 10);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 1.2f);
        caster.getWorld().spawnParticle(Particle.ASH, caster.getLocation().add(0, 1.0, 0), 120, 3.0, 1.5, 3.0, 0.02);

        for (LivingEntity entity : allLivingEntities()) {
            if (entity.equals(caster)) continue;
            if (entity instanceof Player p && TrustManager.isTrusted(caster, p)) continue;

            entity.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 200, 4, false, true, true));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, true, true));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 4, false, true, true));
            if (entity instanceof Player player) {
                player.setSprinting(false);
                player.setCooldown(Material.SHIELD, 200);
                player.setFreezeTicks(100);
                showVictimTimer(player, "Weakened", 10, BarColor.WHITE);
                showVictimTimer(player, "Cannot Jump", 10, BarColor.RED);
            }
            entity.getWorld().spawnParticle(Particle.SMOKE, entity.getLocation().add(0, 1.0, 0), 30, 0.5, 0.8, 0.5, 0.03);
            entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.25f, 0.7f);
        }
    }

    private void skipServer(Player caster) {
        StarTimerManager.startTimer(plugin, caster, "Skip Blade Ult", 10);

        caster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 3, false, true, true));
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8f, 1.7f);
        caster.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, caster.getLocation().add(0, 1.0, 0), 90, 1.4, 1.0, 1.4, 0.12);

        for (LivingEntity entity : allLivingEntities()) {
            if (entity.equals(caster)) continue;
            if (entity instanceof Player p && TrustManager.isTrusted(caster, p)) continue;

            entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 6, false, true, true));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 200, 255, false, true, true));
            entity.getWorld().spawnParticle(Particle.REVERSE_PORTAL, entity.getLocation().add(0, 1.0, 0), 22, 0.5, 0.8, 0.5, 0.02);
        }
    }

    private void reverseWorld(Player caster) {
        StarTimerManager.startTimer(plugin, caster, "Reverse Blade Ult", 5);
        reviveRecentDeaths(caster);
        undoRecentItemActions(caster);
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 0.5f);
        caster.getWorld().spawnParticle(Particle.PORTAL, caster.getLocation().add(0, 1.0, 0), 120, 2.5, 1.5, 2.5, 0.18);

        Set<Entity> affected = new HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getWorld().equals(caster.getWorld()) && entity.getLocation().distance(caster.getLocation()) <= SERVER_RADIUS) {
                    if (entity.equals(caster)) continue;
                    if (entity instanceof Player p && TrustManager.isTrusted(caster, p)) continue;

                    affected.add(entity);
                    plugin.getTimeManager().setRewinding(entity, true);
                    if (entity instanceof LivingEntity living) {
                        living.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 2, false, true, true));
                    }
                }
            }
        }

        new BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                if (ticks >= 100) {
                    for (Entity e : affected) plugin.getTimeManager().setRewinding(e, false);
                    cancel();
                    return;
                }

                for (Entity e : affected) {
                    plugin.getTimeManager().rewindSmooth(e, 1);
                }

                plugin.getTimeManager().rewindBlocks(1);
                caster.getWorld().spawnParticle(Particle.REVERSE_PORTAL, caster.getLocation().add(0, 1.0, 0), 28, 1.4, 1.0, 1.4, 0.03);
                if (ticks % 20 == 0) {
                    caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.7f, 0.6f + ticks / 100.0f);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        Entity target = event.getEntity();

        if (TimeFreezeManager.isFrozen(target)) {
            event.setCancelled(true);

            if (event.getCause() == EntityDamageEvent.DamageCause.FREEZE) return;

            if (event instanceof EntityDamageByEntityEvent byEntity) {
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 0.4f, 1.2f);
                if (byEntity.getDamager() instanceof Player attacker) {
                    ItemStack weapon = attacker.getInventory().getItemInMainHand();
                    TimeFreezeManager.bufferDamage(target, event.getDamage(), attacker, weapon);

                    if (getBlade(weapon) == Blade.FREEZE) {
                        spawnIceSlash(target.getLocation().clone().add(0, 1.0, 0));
                    }

                } else {
                    TimeFreezeManager.bufferDamage(target, event.getDamage());
                }
                TimeFreezeManager.bufferKnockback(target, target.getLocation().toVector()
                        .subtract(byEntity.getDamager().getLocation().toVector())
                        .normalize()
                        .multiply(0.5));
            } else {
                TimeFreezeManager.bufferDamage(target, event.getDamage());
            }
            return;
        }

        if (!(event.getEntity() instanceof Player player)) return;

        UUID id = player.getUniqueId();
        Long until = reverseAbsorbUntil.get(id);
        if (until != null && until >= System.currentTimeMillis()) {
            event.setCancelled(true);
            reverseAbsorbedDamage.merge(id, event.getDamage(), Double::sum);
            player.getWorld().spawnParticle(Particle.WITCH, player.getLocation(), 10, 0.5, 0.5, 0.5, 0.01);
            return;
        }

        if (until != null) {
            releaseAbsorbedDamage(player);
        }

        int stacks = skipStacks.getOrDefault(id, 0);
        if (stacks > 0) {
            skipStacks.put(id, stacks - 1);
            applySkipStackEffects(player, stacks - 1);

            if (stacks - 1 > 0) {
                sendColored(player, NamedTextColor.RED, "⚡ Stacks Lowered: " + (stacks - 1) + "/9");
            } else {
                sendColored(player, NamedTextColor.DARK_RED, "⚡ Time Skip Stacks Lost!");
            }
        }
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        Entity target = event.getEntity();

        if (TimeFreezeManager.isFrozen(target)) {
            return;
        }

        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(target instanceof LivingEntity victim)) return;

        Blade blade = getBlade(attacker.getInventory().getItemInMainHand());
        if (blade == null) return;

        if (victim instanceof Player p && TrustManager.isTrusted(attacker, p)) return;

        if (blade == Blade.FREEZE) {
            spawnIceSlash(victim.getLocation().clone().add(0, 1.0, 0));
        }

        boolean isCriticalHit = attacker.getFallDistance() > 0.0f && !attacker.isInsideVehicle() && !(attacker.getLocation().getY() <= attacker.getWorld().getMinHeight());

        switch (blade) {
            case FREEZE -> {
                if (isCriticalHit) {
                    applyFreezePassive(victim, event);
                }
            }
            case BRAKE -> applyBrakePassive(victim);
            case SKIP -> {
                if (isCriticalHit) {
                    applySkipPassive(attacker);
                }
            }
            case REVERSE -> applyReversePassive(attacker, victim);
        }
    }

    private void spawnIceSlash(Location loc) {
        if (loc.getWorld() == null) return;
        loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 1);
        loc.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 15, 0.4, 0.4, 0.4, 0.05);
        loc.getWorld().spawnParticle(Particle.BLOCK, loc, 12, 0.3, 0.3, 0.3, 0.05, Material.BLUE_ICE.createBlockData());
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        Blade blade = getBlade(killer.getInventory().getItemInMainHand());
        if (blade == null) return;

        String key = chargeKey(killer, blade);
        int charge = Math.min(ULT_CHARGE_REQUIRED, ultCharges.getOrDefault(key, 0) + 1);
        ultCharges.put(key, charge);
        updateUltBar(killer, blade);

        if (charge >= ULT_CHARGE_REQUIRED) {
            sendColored(killer, NamedTextColor.GREEN, blade.displayName + " ult is fully charged.");
            Location killerLocation = killer.getLocation();
            if (killerLocation != null) {
                playAt(killerLocation, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.5f);
                killer.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, killerLocation.add(0, 1.0, 0), 24, 0.4, 0.6, 0.4, 0.02);
            }
        } else {
            Location killerLocation = killer.getLocation();
            if (killerLocation != null) {
                playAt(killerLocation, Sound.BLOCK_NOTE_BLOCK_PLING, 0.45f, 1.0f + charge * 0.12f);
            }
        }
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        Item item = event.getItemDrop();
        reverseItemActions.addLast(new ReverseItemAction(
                ReverseItemActionType.DROP,
                System.currentTimeMillis(),
                event.getPlayer().getUniqueId(),
                item.getUniqueId(),
                item.getItemStack().clone(),
                item.getLocation().clone()
        ));
        trimReverseItemActions();
    }

    @EventHandler
    public void onPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Item item = event.getItem();
        boolean isDeathDrop = false;

        for (DeathSnapshot snapshot : recentDeaths.values()) {
            if (snapshot.droppedItemEntities.contains(item.getUniqueId())) {
                snapshot.pickedUpItems.add(new PickedUp(player.getUniqueId(), item.getItemStack().clone()));
                isDeathDrop = true;
            }
        }

        if (!isDeathDrop) {
            reverseItemActions.addLast(new ReverseItemAction(
                    ReverseItemActionType.PICKUP,
                    System.currentTimeMillis(),
                    player.getUniqueId(),
                    item.getUniqueId(),
                    item.getItemStack().clone(),
                    item.getLocation().clone()
            ));
            trimReverseItemActions();
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Long sprintBlockedUntil = brakeSprintBlockedUntil.get(player.getUniqueId());
        if (sprintBlockedUntil != null) {
            if (sprintBlockedUntil < System.currentTimeMillis()) {
                brakeSprintBlockedUntil.remove(player.getUniqueId());
            } else if (player.isSprinting()) {
                player.setSprinting(false);
            }
        }

        Long until = reversedControlsUntil.get(player.getUniqueId());
        if (until == null) return;

        if (until < System.currentTimeMillis()) {
            reversedControlsUntil.remove(player.getUniqueId());
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) return;

        Vector delta = to.toVector().subtract(from.toVector());
        delta.setY(0);

        if (delta.lengthSquared() <= 0.0001) return;

        Location reversed = from.clone().subtract(delta);
        reversed.setY(to.getY());
        reversed.setYaw(to.getYaw());
        reversed.setPitch(to.getPitch());
        event.setTo(reversed);
    }

    @EventHandler
    public void onToggleSprint(PlayerToggleSprintEvent event) {
        Long until = brakeSprintBlockedUntil.get(event.getPlayer().getUniqueId());
        if (until == null) return;

        if (until < System.currentTimeMillis()) {
            brakeSprintBlockedUntil.remove(event.getPlayer().getUniqueId());
            return;
        }

        if (event.isSprinting()) {
            event.setCancelled(true);
            event.getPlayer().setSprinting(false);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        List<UUID> droppedUUIDs = new ArrayList<>();
        for (ItemStack drop : event.getDrops()) {
            Item item = player.getWorld().dropItemNaturally(player.getLocation(), drop);
            droppedUUIDs.add(item.getUniqueId());
        }
        event.getDrops().clear();

        recentDeaths.put(player.getUniqueId(), new DeathSnapshot(
                player.getInventory().getContents().clone(),
                Math.max(1.0, player.getHealth()),
                player.getFoodLevel(),
                player.getLocation().clone(),
                droppedUUIDs,
                new ArrayList<>()
        ));

        Bukkit.getScheduler().runTaskLater(plugin, () -> recentDeaths.remove(player.getUniqueId()), 100L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeSkipChargeBar(event.getPlayer());
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        plugin.getTimeManager().recordBlock(event.getBlock().getLocation(), event.getBlock().getType(), Material.AIR);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        plugin.getTimeManager().recordBlock(event.getBlock().getLocation(), Material.AIR, event.getBlock().getType());
    }

    private void applyFreezePassive(LivingEntity victim, EntityDamageByEntityEvent event) {
        applyPowderSnowPassive(victim);
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, FREEZE_PASSIVE_TICKS, 9, false, true, true));
        double damageIncrease = Math.min(6.0, Math.max(1.0, victim.getFreezeTicks() / 100.0));
        event.setDamage(Math.min(MAX_FREEZE_BLADE_DAMAGE, Math.max(0.0, event.getDamage() + damageIncrease)));
        victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_POWDER_SNOW_BREAK, 0.8f, 1.1f);
    }

    private void applyPowderSnowPassive(LivingEntity victim) {
        victim.setFreezeTicks(victim.getMaxFreezeTicks() + FREEZE_PASSIVE_TICKS);

        keepPowderSnowOverlay(victim, FREEZE_PASSIVE_TICKS, false);
        if (victim instanceof Player player) {
            showVictimTimer(player, "Powdered Snow", FREEZE_PASSIVE_TICKS / 20, BarColor.BLUE);
        }

        new BukkitRunnable() {
            private int elapsed;

            @Override
            public void run() {
                if (!victim.isValid() || elapsed >= FREEZE_PASSIVE_TICKS) {
                    cancel();
                    return;
                }

                victim.setFreezeTicks(victim.getMaxFreezeTicks() + 40);
                elapsed += 20;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void applyBrakePassive(LivingEntity victim) {
        if (!rollPassive()) return;
        victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 1, false, true, true));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, true, true));
        if (victim instanceof Player player) {
            showVictimTimer(player, "Weakened", 5, BarColor.WHITE);
        }
        victim.getWorld().spawnParticle(Particle.ASH, victim.getLocation().add(0, 1.0, 0), 18, 0.4, 0.6, 0.4, 0.01);
        victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_CHAIN_PLACE, 0.7f, 0.55f);
    }

    private void applySkipPassive(Player attacker) {
        UUID id = attacker.getUniqueId();
        int currentStacks = skipStacks.getOrDefault(id, 0);

        int stacks = Math.min(9, currentStacks + 1);
        skipStacks.put(id, stacks);
        skipLastHitTime.put(id, System.currentTimeMillis());

        sendColored(attacker, NamedTextColor.YELLOW, "⚡ Time Skip Stacks: " + stacks + "/9");

        applySkipStackEffects(attacker, stacks);
        Location attackerLocation = attacker.getLocation();
        if (attackerLocation != null) {
            attacker.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, attackerLocation.add(0, 1.0, 0), 8 + stacks * 4, 0.3, 0.4, 0.3, 0.04);
            playAt(attackerLocation, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.45f, 1.0f + stacks * 0.2f);
        }
    }

    private void applySkipStackEffects(Player player, int stacks) {
        AttributeInstance speedAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        AttributeInstance attackAttr = player.getAttribute(Attribute.ATTACK_DAMAGE);

        NamespacedKey speedKey = new NamespacedKey(plugin, "skip_speed");
        NamespacedKey attackKey = new NamespacedKey(plugin, "skip_attack");

        if (speedAttr != null) {
            for (AttributeModifier mod : speedAttr.getModifiers()) {
                if (mod.getKey().equals(speedKey)) speedAttr.removeModifier(mod);
            }
        }
        if (attackAttr != null) {
            for (AttributeModifier mod : attackAttr.getModifiers()) {
                if (mod.getKey().equals(attackKey)) attackAttr.removeModifier(mod);
            }
        }

        if (stacks <= 0) {
            skipStacks.remove(player.getUniqueId());
            return;
        }

        int speedLevel = Math.min(5, stacks);
        int strengthLevel = Math.min(2, stacks / 2);

        if (speedLevel > 0 && speedAttr != null) {
            speedAttr.addModifier(new AttributeModifier(speedKey, speedLevel * 0.20, AttributeModifier.Operation.ADD_SCALAR));
        }

        if (strengthLevel > 0 && attackAttr != null) {
            attackAttr.addModifier(new AttributeModifier(attackKey, strengthLevel * 3.0, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    private void applyReversePassive(Player attacker, LivingEntity victim) {
        if (!rollPassive()) return;

        plugin.getTimeManager().rewindHealth(attacker, 100);
        Location attackerLocation = attacker.getLocation();
        if (attackerLocation != null) {
            attacker.getWorld().spawnParticle(Particle.HEART, attackerLocation.add(0, 1.5, 0), 10, 0.5, 0.5, 0.5, 0.1);
            playAt(attackerLocation, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.8f, 1.2f);
        }
        sendColored(attacker, NamedTextColor.LIGHT_PURPLE, "Your health was reversed to 5 seconds ago!");

        if (victim instanceof Player player) {
            reversedControlsUntil.put(player.getUniqueId(), System.currentTimeMillis() + REVERSED_CONTROLS_TICKS * 50L);
            showVictimTimer(player, "Controls Reversed", REVERSED_CONTROLS_TICKS / 20, BarColor.PURPLE);
            Location playerLocation = player.getLocation();
            if (playerLocation != null) {
                player.getWorld().spawnParticle(Particle.PORTAL, playerLocation.add(0, 1.0, 0), 28, 0.5, 0.8, 0.5, 0.1);
                playAt(playerLocation, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 0.6f);
            }
        }
    }

    private void releaseAbsorbedDamage(Player player) {
        UUID id = player.getUniqueId();
        reverseAbsorbUntil.remove(id);
        Double storedDamage = reverseAbsorbedDamage.remove(id);
        double damage = storedDamage == null ? 0.0 : storedDamage;
        if (damage <= 0) return;

        double radius = Math.min(12.0, 3.0 + damage / 4.0);
        Location origin = player.getLocation();
        playAt(origin, Sound.ENTITY_GENERIC_EXPLODE, 1.8f, 0.9f);
        playAt(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.6f, 1.25f);
        playAt(origin, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.3f, 0.7f);
        player.getWorld().spawnParticle(Particle.EXPLOSION, origin, 1);
        player.getWorld().spawnParticle(Particle.SONIC_BOOM, origin.clone().add(0, 1.0, 0), 1);
        player.getWorld().spawnParticle(Particle.WITCH, origin.clone().add(0, 1.0, 0), 60, radius / 3, 1.0, radius / 3, 0.05);

        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity.equals(player) || !(entity instanceof LivingEntity living)) continue;
            if (living instanceof Player p && TrustManager.isTrusted(player, p)) continue;

            double distance = Math.max(1.0, living.getLocation().distance(origin));
            double falloff = Math.max(0.35, 1.0 - (distance / radius));
            double shockwaveDamage = Math.min(18.0, Math.max(2.0, damage * 0.75 * falloff));
            living.damage(shockwaveDamage, player);
            living.setVelocity(living.getLocation().toVector()
                    .subtract(origin.toVector())
                    .normalize()
                    .multiply(0.8 + falloff * 0.8));
            living.getWorld().spawnParticle(Particle.REVERSE_PORTAL, living.getLocation().clone().add(0, 1.0, 0), 20, 0.35, 0.6, 0.35, 0.05);
        }

        double absorptionAmount = damage * 0.375;
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 0, false, true, true));
        if (player instanceof LivingEntity living) {
            AttributeInstance absorbAttribute = living.getAttribute(Attribute.MAX_ABSORPTION);
            if (absorbAttribute != null) {
                double currentHealth = player.getHealth();
                double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
                double targetHealth = Math.min(maxHealth + absorptionAmount, currentHealth + absorptionAmount);
                if (targetHealth > currentHealth) {
                    player.setAbsorptionAmount(Math.min(absorptionAmount, 16.0));
                }
            }
        }

    }

    private void reviveRecentDeaths(Player caster) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.equals(caster)) continue;

            DeathSnapshot snapshot = recentDeaths.remove(player.getUniqueId());
            if (snapshot == null) continue;

            for (UUID itemId : snapshot.droppedItemEntities) {
                Entity entity = Bukkit.getEntity(itemId);
                if (entity instanceof Item) {
                    entity.remove();
                }
            }

            for (PickedUp pu : snapshot.pickedUpItems) {
                Player picker = Bukkit.getPlayer(pu.picker);
                if (picker != null && picker.isOnline()) {
                    removeFromInventory(picker.getInventory(), pu.item);
                }
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                if (player.isDead()) {
                    player.spigot().respawn();
                }
                player.teleport(snapshot.location);
                player.getInventory().setContents(snapshot.inventory);
                player.setHealth(Math.min(20.0, Math.max(1.0, snapshot.health)));
                player.setFoodLevel(snapshot.food);
                player.setGameMode(GameMode.SURVIVAL);
                player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation(), 30, 0.5, 1.0, 0.5, 0.01);
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 0.9f);
            }, 1L);
        }
    }

    private void undoRecentItemActions(Player caster) {
        long cutoff = System.currentTimeMillis() - 5000;
        Map<UUID, List<ReverseItemAction>> actionsByItem = new HashMap<>();

        while (!reverseItemActions.isEmpty()) {
            ReverseItemAction action = reverseItemActions.removeLast();
            if (action.timestamp < cutoff) break;
            if (action.location.getWorld() != null
                    && action.location.getWorld().equals(caster.getWorld())
                    && action.location.distance(caster.getLocation()) <= SERVER_RADIUS) {
                actionsByItem.computeIfAbsent(action.itemEntityId, ignored -> new ArrayList<>()).add(action);
            }
        }

        for (List<ReverseItemAction> actions : actionsByItem.values()) {
            actions.sort(Comparator.comparingLong(ReverseItemAction::timestamp));

            ReverseItemAction firstDrop = null;
            ReverseItemAction lastPickup = null;
            for (ReverseItemAction action : actions) {
                if (action.type == ReverseItemActionType.DROP && firstDrop == null) {
                    firstDrop = action;
                } else if (action.type == ReverseItemActionType.PICKUP) {
                    lastPickup = action;
                }
            }

            if (firstDrop != null) {
                removeTrackedItemEntity(firstDrop.itemEntityId);
                if (lastPickup != null) {
                    Player picker = Bukkit.getPlayer(lastPickup.playerId);
                    if (picker != null && picker.isOnline()) {
                        removeFromInventory(picker.getInventory(), lastPickup.item.clone());
                    }
                }

                Player dropper = Bukkit.getPlayer(firstDrop.playerId);
                if (dropper != null && dropper.isOnline()) {
                    giveOrDrop(dropper, firstDrop.item.clone());
                }
            } else if (lastPickup != null) {
                Player picker = Bukkit.getPlayer(lastPickup.playerId);
                if (picker == null || !picker.isOnline()) continue;

                removeFromInventory(picker.getInventory(), lastPickup.item.clone());
                lastPickup.location.getWorld().dropItemNaturally(lastPickup.location, lastPickup.item.clone());
            } else {
                ReverseItemAction action = actions.get(0);
                removeTrackedItemEntity(action.itemEntityId);
                Player player = Bukkit.getPlayer(action.playerId);
                if (player != null && player.isOnline()) {
                    giveOrDrop(player, action.item.clone());
                }
            }
        }
    }

    private void removeTrackedItemEntity(UUID itemEntityId) {
        for (World world : Bukkit.getWorlds()) {
            Entity entity = world.getEntity(itemEntityId);
            if (entity != null) {
                entity.remove();
                return;
            }
        }
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private void removeFromInventory(PlayerInventory inventory, ItemStack item) {
        int remaining = item.getAmount();
        ItemStack template = item.clone();
        template.setAmount(1);

        for (ItemStack content : inventory.getContents()) {
            if (content == null || !content.isSimilar(template)) continue;

            int removed = Math.min(remaining, content.getAmount());
            content.setAmount(content.getAmount() - removed);
            remaining -= removed;
            if (remaining <= 0) return;
        }
    }

    private void trimReverseItemActions() {
        long cutoff = System.currentTimeMillis() - 5000;
        while (!reverseItemActions.isEmpty() && reverseItemActions.peekFirst().timestamp < cutoff) {
            reverseItemActions.removeFirst();
        }
    }

    private void freezeEntity(LivingEntity entity, int ticks, boolean powderedSnow) {
        TimeFreezeManager.freeze(entity);
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 100, false, true, true));
        entity.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, ticks, -10, false, false, false));
        if (powderedSnow) {
            entity.setFreezeTicks(entity.getMaxFreezeTicks() + ticks);
            keepPowderSnowOverlay(entity, ticks, true);
        }
        if (entity instanceof Player player) {
            showVictimTimer(player, "Frozen", ticks / 20, BarColor.BLUE);
        }
        entity.getWorld().spawnParticle(Particle.SNOWFLAKE, entity.getLocation(), 30, 0.4, 0.6, 0.4, 0.01);
        entity.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, entity.getLocation().add(0, 1.0, 0), 12, 0.3, 0.5, 0.3, 0.02);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            TimeFreezeManager.unfreeze(entity);
            TimeFreezeManager.applyBufferedDamage(entity);
            TimeFreezeManager.applyBufferedKnockback(entity);
            TimeFreezeManager.clear(entity);
        }, ticks);
    }

    private void keepPowderSnowOverlay(LivingEntity entity, int ticks, boolean requireFrozen) {
        new BukkitRunnable() {
            private int elapsed;

            @Override
            public void run() {
                if (!entity.isValid() || (requireFrozen && !TimeFreezeManager.isFrozen(entity)) || elapsed >= ticks) {
                    cancel();
                    return;
                }

                entity.setFreezeTicks(entity.getMaxFreezeTicks() + 40);
                elapsed += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private LivingEntity getLookedAtLivingEntity(Player player, double range) {
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                range,
                0.75,
                entity -> entity instanceof LivingEntity && !entity.equals(player)
        );

        if (result == null || !(result.getHitEntity() instanceof LivingEntity living)) return null;
        return living;
    }

    private boolean isSafeDashLocation(Location location) {
        if (location.getWorld() == null) return false;
        org.bukkit.block.Block feet = location.getBlock();
        org.bukkit.block.Block head = location.clone().add(0, 1, 0).getBlock();
        return feet.isPassable() && head.isPassable();
    }

    @EventHandler
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (getBlade(event.getItem()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        Blade mainHandBlade = getBlade(event.getMainHandItem());
        Blade offHandBlade = getBlade(event.getOffHandItem());

        if (mainHandBlade != null && offHandBlade != null) {
            event.setCancelled(true);
            sendColored(player, NamedTextColor.RED, "You cannot hold Time weapons in both hands.");
            return;
        }

        if (mainHandBlade != null) {
            event.setCancelled(true);
            if (player.isSneaking()) {
                useUlt(player, mainHandBlade);
            } else {
                useAbility(player, mainHandBlade);
            }
            Bukkit.getScheduler().runTask(plugin, () -> moveTimeWeaponToMainHand(player, event.getMainHandItem()));
        } else if (offHandBlade != null) {
            event.setCancelled(true);
            ItemStack weapon = event.getOffHandItem().clone();
            moveTimeWeaponToMainHand(player, weapon);
            if (player.isSneaking()) {
                useUlt(player, offHandBlade);
            } else {
                useAbility(player, offHandBlade);
            }
        }
    }

    @EventHandler
    public void onHeldSlotChange(PlayerItemHeldEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> enforceSingleHeldBlade(event.getPlayer()));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Bukkit.getScheduler().runTask(plugin, () -> enforceSingleHeldBlade(player));
    }

    private List<LivingEntity> allLivingEntities() {
        List<LivingEntity> result = new ArrayList<>();

        for (World world : Bukkit.getWorlds()) {
            result.addAll(world.getLivingEntities());
        }

        return result;
    }

    private boolean rollPassive() {
        return ThreadLocalRandom.current().nextInt(100) < PASSIVE_CHANCE;
    }

    private void updateUltBar(Player player, Blade blade) {
        String key = chargeKey(player, blade);
        int charge = ultCharges.getOrDefault(key, 0);
        BossBar bar = ultBars.computeIfAbsent(key, ignored -> {
            return Bukkit.createBossBar("", blade.barColor, BarStyle.SEGMENTED_10);
        });

        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }

        double progress = Math.max(0.0, Math.min(1.0, (double) charge / ULT_CHARGE_REQUIRED));
        bar.setProgress(progress);
        bar.setTitle(blade.displayName + " Ult Charge: " + charge + "/" + ULT_CHARGE_REQUIRED + " players");
        bar.setVisible(true);
    }

    private String chargeKey(Player player, Blade blade) {
        return player.getUniqueId() + ":" + blade.name();
    }

    private void sendColored(Player player, NamedTextColor color, String message) {
        player.sendMessage(Component.text(message, color));
    }

    private void playAt(Location location, Sound sound, float volume, float pitch) {
        if (location.getWorld() != null) {
            location.getWorld().playSound(location, sound, volume, pitch);
        }
    }

    private void refreshHeldUltMeters() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Blade heldBlade = getBlade(player.getInventory().getItemInMainHand());

            for (Blade blade : Blade.values()) {
                BossBar bar = ultBars.get(chargeKey(player, blade));
                if (bar != null) {
                    if (heldBlade != blade) {
                        bar.setVisible(false);
                    }
                }
            }

            if (heldBlade != null) {
                updateUltBar(player, heldBlade);
            }
        }
    }

    private void showCooldownBar(Player player, Blade blade) {
        String key = "cooldown:" + chargeKey(player, blade);
        BossBar old = cooldownBars.remove(key);
        if (old != null) old.removeAll();

        BossBar bar = Bukkit.createBossBar(blade.displayName + " Cooldown", blade.barColor, BarStyle.SOLID);
        bar.addPlayer(player);
        cooldownBars.put(key, bar);

        long totalMillis = blade.abilityCooldownMillis;
        long startedAt = System.currentTimeMillis();

        new BukkitRunnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - startedAt;
                long remaining = Math.max(0L, totalMillis - elapsed);
                double progress = Math.max(0.0, Math.min(1.0, (double) remaining / totalMillis));

                bar.setProgress(progress);
                bar.setTitle(blade.displayName + " Cooldown: " + (long) Math.ceil(remaining / 1000.0) + "s");

                if (remaining <= 0 || !player.isOnline()) {
                    bar.removeAll();
                    cooldownBars.remove(key);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void updateSkipChargeBar(Player player, int charges, long lastRegen, long now) {
        BossBar bar = skipChargeBars.computeIfAbsent(player.getUniqueId(), k -> {
            BossBar newBar = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SOLID);
            newBar.addPlayer(player);
            return newBar;
        });

        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }

        if (charges >= 3) {
            bar.setProgress(1.0);
            bar.setTitle("Time Skip: 3/3 Charges");
        } else {
            long elapsed = now - lastRegen;
            long remaining = 10000 - elapsed;
            double progress = Math.max(0.0, Math.min(1.0, (double) elapsed / 10000.0));
            bar.setProgress(progress);
            long secondsLeft = (long) Math.ceil(remaining / 1000.0);
            bar.setTitle("Time Skip: " + charges + "/3 (Next in " + secondsLeft + "s)");
        }
        bar.setVisible(true);
    }

    private void removeSkipChargeBar(Player player) {
        BossBar bar = skipChargeBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }

    private void showVictimTimer(Player player, String label, int seconds, BarColor color) {
        String key = "victim:" + player.getUniqueId() + ":" + label;
        BossBar old = victimBars.remove(key);
        if (old != null) old.removeAll();

        BossBar bar = Bukkit.createBossBar(label, color, BarStyle.SOLID);
        bar.addPlayer(player);
        victimBars.put(key, bar);

        long totalMillis = seconds * 1000L;
        long startedAt = System.currentTimeMillis();

        new BukkitRunnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - startedAt;
                long remaining = Math.max(0L, totalMillis - elapsed);
                double progress = Math.max(0.0, Math.min(1.0, (double) remaining / totalMillis));

                bar.setProgress(progress);
                long secondsRemaining = (long) Math.ceil(remaining / 1000.0);
                bar.setTitle(label + ": " + secondsRemaining + "s");

                if (remaining <= 0 || !player.isOnline()) {
                    bar.removeAll();
                    victimBars.remove(key);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void spawnLineParticles(Player player, Location target, Particle particle, int points) {
        Location start = player.getEyeLocation();
        Vector step = target.clone().add(0, 1.0, 0).toVector()
                .subtract(start.toVector())
                .multiply(1.0 / points);

        Location current = start.clone();
        for (int i = 0; i < points; i++) {
            current.add(step);
            player.getWorld().spawnParticle(particle, current, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private void enforceSingleHeldBlade(Player player) {
        PlayerInventory inventory = player.getInventory();
        if (getBlade(inventory.getItemInMainHand()) == null || getBlade(inventory.getItemInOffHand()) == null) return;

        ItemStack offhandBlade = inventory.getItemInOffHand().clone();
        inventory.setItemInOffHand(null);

        Map<Integer, ItemStack> leftovers = inventory.addItem(offhandBlade);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        sendColored(player, NamedTextColor.RED, "You cannot hold Time weapons in both hands. The offhand item was moved.");
        
        enforceSingleClockPerType(player);
    }

    private void enforceSingleClockPerType(Player player) {
        Map<ClockType, Integer> clockCount = new HashMap<>();
        
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            ClockType type = TimeClockItems.getClockType(plugin, item);
            if (type != null) {
                int count = clockCount.getOrDefault(type, 0);
                if (count > 0) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                    player.getInventory().setItem(i, null);
                    sendColored(player, NamedTextColor.RED, "You can only hold one " + type.displayName() + " at a time. The extra was dropped.");
                } else {
                    clockCount.put(type, 1);
                }
            }
        }
    }

    private void moveTimeWeaponToMainHand(Player player, ItemStack weapon) {
        if (weapon == null || getBlade(weapon) == null) return;

        PlayerInventory inventory = player.getInventory();
        ItemStack main = inventory.getItemInMainHand();
        ItemStack offhand = inventory.getItemInOffHand();

        if (getBlade(main) != null && main.isSimilar(weapon)) {
            inventory.setItemInMainHand(weapon.clone());
            return;
        }

        if (getBlade(offhand) != null && offhand.isSimilar(weapon)) {
            inventory.setItemInOffHand(main == null || main.getType() == Material.AIR ? null : main);
            inventory.setItemInMainHand(weapon.clone());
            return;
        }

        inventory.setItemInMainHand(weapon.clone());
    }

    private Blade getBlade(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;

        String type = TimeBladeItems.getTaggedType(item);
        if (type == null) return null;

        return switch (type.toLowerCase(Locale.ROOT)) {
            case "freeze" -> Blade.FREEZE;
            case "brake" -> Blade.BRAKE;
            case "skip" -> Blade.SKIP;
            case "reverse" -> Blade.REVERSE;
            default -> null;
        };
    }

    private enum Blade {
        FREEZE("Freeze Time Blade", 60_000L, BarColor.BLUE),
        BRAKE("Time Brake Mace", 60_000L, BarColor.WHITE),
        SKIP("Time Skip Blade", 0L, BarColor.YELLOW),
        REVERSE("Time Reverse Blade", 30_000L, BarColor.PURPLE);

        private final String displayName;
        private final long abilityCooldownMillis;
        private final BarColor barColor;

        Blade(String displayName, long abilityCooldownMillis, BarColor barColor) {
            this.displayName = displayName;
            this.abilityCooldownMillis = abilityCooldownMillis;
            this.barColor = barColor;
        }
    }

    private enum ReverseItemActionType {
        DROP,
        PICKUP
    }

    private record ReverseItemAction(
            ReverseItemActionType type,
            long timestamp,
            UUID playerId,
            UUID itemEntityId,
            ItemStack item,
            Location location
    ) {
    }

    private record PickedUp(UUID picker, ItemStack item) {}

    private record DeathSnapshot(
            ItemStack[] inventory,
            double health,
            int food,
            Location location,
            List<UUID> droppedItemEntities,
            List<PickedUp> pickedUpItems
    ) {
    }
}
