package com.doze.timebound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class TrustManager implements CommandExecutor, TabCompleter {
    private static final Map<UUID, Set<UUID>> trusts = new HashMap<>();
    private static final String TRUST_KEY = "trusted_players";
    public TrustManager() {
        loadTrustedPlayers();
    }

    private static void loadTrustedPlayers() {
        trusts.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadTrustForPlayer(player);
        }
    }

    private static void loadTrustForPlayer(Player player) {
        UUID playerUuid = player.getUniqueId();
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        NamespacedKey trustKey = new NamespacedKey(Main.getInstance(), TRUST_KEY);
        String trustedUuidsStr = pdc.get(trustKey, PersistentDataType.STRING);
        if (trustedUuidsStr != null && !trustedUuidsStr.isEmpty()) {
            Set<UUID> trustedSet = new HashSet<>();
            for (String uuid : trustedUuidsStr.split(",")) {
                try {
                    UUID parsedUuid = UUID.fromString(uuid.trim());
                    trustedSet.add(parsedUuid);
                } catch (IllegalArgumentException e) {
                }
            }
            if (!trustedSet.isEmpty()) {
                trusts.put(playerUuid, trustedSet);
            }
        }
    }

    private static void saveTrustForPlayer(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) return;
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        NamespacedKey trustKey = new NamespacedKey(Main.getInstance(), TRUST_KEY);
        Set<UUID> trustedSet = trusts.getOrDefault(playerUuid, new HashSet<>());
        if (trustedSet.isEmpty()) {
            pdc.remove(trustKey);
        } else {
            String uuidString = String.join(",", trustedSet.stream()
                    .map(UUID::toString)
                    .toArray(String[]::new));
            pdc.set(trustKey, PersistentDataType.STRING, uuidString);
        }
    }

    public static void addTrust(Player a, Player b) {
        if (a == null || b == null) return;
        UUID aUuid = a.getUniqueId();
        UUID bUuid = b.getUniqueId();
        if (aUuid.equals(bUuid)) return;
        boolean aChanged = trusts.computeIfAbsent(aUuid, k -> new HashSet<>()).add(bUuid);
        boolean bChanged = trusts.computeIfAbsent(bUuid, k -> new HashSet<>()).add(aUuid);
        if (aChanged) saveTrustForPlayer(aUuid);
        if (bChanged) saveTrustForPlayer(bUuid);
    }
    public static void removeTrust(Player a, Player b) {
        if (a == null || b == null) return;
        UUID aUuid = a.getUniqueId();
        UUID bUuid = b.getUniqueId();
        Set<UUID> aSet = trusts.get(aUuid);
        Set<UUID> bSet = trusts.get(bUuid);
        boolean aChanged = aSet != null && aSet.remove(bUuid);
        boolean bChanged = bSet != null && bSet.remove(aUuid);
        if (aSet != null && aSet.isEmpty()) trusts.remove(aUuid);
        if (bSet != null && bSet.isEmpty()) trusts.remove(bUuid);
        if (aChanged) saveTrustForPlayer(aUuid);
        if (bChanged) saveTrustForPlayer(bUuid);
    }

    public static boolean isTrusted(Player a, Player b) {
        if (a == null || b == null) return false;
        Set<UUID> aTrusts = trusts.get(a.getUniqueId());
        return aTrusts != null && aTrusts.contains(b.getUniqueId());
    }

    public static Set<UUID> getTrusted(Player p) {
        if (p == null) return Collections.emptySet();
        Set<UUID> trustedSet = trusts.getOrDefault(p.getUniqueId(), new HashSet<>());
        return new HashSet<>(trustedSet);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd == null || !(sender instanceof Player)) {
            return true;
        }
        Player player = (Player) sender;
        String commandName = cmd.getName().toLowerCase();
        if (commandName.equals("untrust")) {
            if (args.length < 1) {
                player.sendMessage(Component.text("Usage: /untrust <player>", NamedTextColor.RED));
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(Component.text("Player not found (offline players not supported for untrust).", NamedTextColor.RED));
                return true;
            }
            if (isTrusted(player, target)) {
                removeTrust(player, target);
                player.sendMessage(Component.text("You untrusted " + target.getName() + ".", NamedTextColor.YELLOW));
                target.sendMessage(Component.text(player.getName() + " has untrusted you.", NamedTextColor.RED));
            } else {
                player.sendMessage(Component.text("You are not trusted with that player.", NamedTextColor.RED));
            }
            return true;
        }
        if (commandName.equals("trust")) {
            if (args.length < 1) {
                player.sendMessage(Component.text("Usage: /trust <player> | /trust list", NamedTextColor.RED));
                return true;
            }
            if (args[0].equalsIgnoreCase("list")) {
                player.sendMessage(Component.text("--- Trusted Players ---", NamedTextColor.GOLD));
                Set<UUID> trustedSet = getTrusted(player);
                if (trustedSet.isEmpty()) {
                    player.sendMessage(Component.text("You haven't trusted anyone yet.", NamedTextColor.GRAY));
                } else {
                    for (UUID uuid : trustedSet) {
                        Player p = Bukkit.getPlayer(uuid);
                        String name = p != null ? p.getName() : "Unknown Player (" + uuid + ")";
                        player.sendMessage(Component.text("  • " + name, NamedTextColor.YELLOW));
                    }
                }
                return true;
            }
            if (args[0].equalsIgnoreCase("accept")) {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /trust accept <player>", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                if (isTrusted(player, target)) {
                    player.sendMessage(Component.text("You are already trusted with " + target.getName() + ".", NamedTextColor.YELLOW));
                    return true;
                }
                addTrust(player, target);
                player.sendMessage(Component.text("You now trust " + target.getName() + ".", NamedTextColor.GREEN));
                target.sendMessage(Component.text(player.getName() + " now trusts you.", NamedTextColor.GREEN));
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return true;
            }
            if (target.equals(player)) {
                player.sendMessage(Component.text("You cannot trust yourself.", NamedTextColor.RED));
                return true;
            }
            if (isTrusted(player, target)) {
                player.sendMessage(Component.text("You are already trusted with " + target.getName() + ".", NamedTextColor.YELLOW));
                return true;
            }
            addTrust(player, target);
            player.sendMessage(Component.text("You now trust " + target.getName() + ".", NamedTextColor.GREEN));
            target.sendMessage(Component.text(player.getName() + " now trusts you.", NamedTextColor.GREEN));
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }
        List<String> completions = new ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("trust")) {
            if (args.length == 1) {
                completions.add("list");
                completions.add("accept");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(sender)) {
                        completions.add(p.getName());
                    }
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("accept")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(sender)) {
                        completions.add(p.getName());
                    }
                }
            }
        } else if (cmd.getName().equalsIgnoreCase("untrust")) {
            if (args.length == 1) {
                Player player = (Player) sender;
                Set<UUID> trusted = getTrusted(player);
                for (UUID uuid : trusted) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        completions.add(p.getName());
                    }
                }
            }
        }
        return completions;
    }
}
