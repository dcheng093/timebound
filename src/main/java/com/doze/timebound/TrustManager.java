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
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class TrustManager implements CommandExecutor, TabCompleter {

    private static final Map<UUID, Set<UUID>> trusts = new HashMap<>();

    public static void addTrust(Player a, Player b) {
        if (a == null || b == null) return;
        trusts.computeIfAbsent(a.getUniqueId(), k -> new HashSet<>()).add(b.getUniqueId());
        trusts.computeIfAbsent(b.getUniqueId(), k -> new HashSet<>()).add(a.getUniqueId());
    }

    public static void removeTrust(Player a, Player b) {
        if (a == null || b == null) return;
        Set<UUID> aSet = trusts.get(a.getUniqueId());
        if (aSet != null) aSet.remove(b.getUniqueId());
        
        Set<UUID> bSet = trusts.get(b.getUniqueId());
        if (bSet != null) bSet.remove(a.getUniqueId());
    }

    public static boolean isTrusted(Player a, Player b) {
        if (a == null || b == null) return false;
        Set<UUID> aTrusts = trusts.get(a.getUniqueId());
        return aTrusts != null && aTrusts.contains(b.getUniqueId());
    }

    public static Set<UUID> getTrusted(Player p) {
        if (p == null) return Collections.emptySet();
        return trusts.getOrDefault(p.getUniqueId(), Collections.emptySet());
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
                player.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
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
                        String name = p != null ? p.getName() : "Offline Player";
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

                addTrust(target, player);
                player.sendMessage(Component.text("You accepted trust from " + target.getName() + ".", NamedTextColor.GREEN));
                target.sendMessage(Component.text(player.getName() + " accepted your trust request.", NamedTextColor.GREEN));
                return true;
            }
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
                    completions.add(p.getName());
                }
            }
        } else if (cmd.getName().equalsIgnoreCase("untrust")) {
            if (args.length == 1) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    completions.add(p.getName());
                }
            }
        }

        return completions;
    }
}