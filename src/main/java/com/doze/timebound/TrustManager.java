package com.doze.timebound;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class TrustManager implements CommandExecutor, TabCompleter {

    private static final Map<UUID, Set<UUID>> trusts = new HashMap<>();
    private static final Map<UUID, UUID> pendingRequests = new HashMap<>();

    public static void addTrust(Player a, Player b) {
        trusts.computeIfAbsent(a.getUniqueId(), k -> new HashSet<>()).add(b.getUniqueId());
        trusts.computeIfAbsent(b.getUniqueId(), k -> new HashSet<>()).add(a.getUniqueId());
    }

    public static void removeTrust(Player a, Player b) {
        if (trusts.containsKey(a.getUniqueId())) trusts.get(a.getUniqueId()).remove(b.getUniqueId());
        if (trusts.containsKey(b.getUniqueId())) trusts.get(b.getUniqueId()).remove(a.getUniqueId());
    }

    public static boolean isTrusted(Player a, Player b) {
        if (a == null || b == null) return false;
        Set<UUID> aTrusts = trusts.get(a.getUniqueId());
        return aTrusts != null && aTrusts.contains(b.getUniqueId());
    }

    public static Set<UUID> getTrusted(Player p) {
        return trusts.getOrDefault(p.getUniqueId(), Collections.emptySet());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        String commandName = cmd.getName().toLowerCase();

        if (commandName.equals("untrust")) {
            if (args.length < 1) {
                player.sendMessage(ChatColor.RED + "Usage: /untrust <player>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }
            if (isTrusted(player, target)) {
                removeTrust(player, target);
                player.sendMessage(ChatColor.YELLOW + "You untrusted " + target.getName() + ".");
                target.sendMessage(ChatColor.RED + player.getName() + " has untrusted you.");
            } else {
                player.sendMessage(ChatColor.RED + "You are not trusted with that player.");
            }
            return true;
        }

        if (commandName.equals("trust")) {
            if (args.length < 1) {
                player.sendMessage(ChatColor.RED + "Usage: /trust <player> | /trust list");
                return true;
            }

            if (args[0].equalsIgnoreCase("list")) {
                player.sendMessage(ChatColor.GOLD + "--- Trusted Players ---");
                Set<UUID> trustedSet = getTrusted(player);
                if (trustedSet.isEmpty()) {
                    player.sendMessage(ChatColor.GRAY + "You haven't trusted anyone yet.");
                } else {
                    for (UUID uuid : trustedSet) {
                        Player p = Bukkit.getPlayer(uuid);
                        String name = p != null ? p.getName() : "Offline Player";
                        player.sendMessage(ChatColor.GREEN + "- " + name);
                    }
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("accept")) {
                if (args.length < 2) return true;
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) return true;

                UUID requester = pendingRequests.get(player.getUniqueId());
                if (requester != null && requester.equals(target.getUniqueId())) {
                    addTrust(player, target);
                    pendingRequests.remove(player.getUniqueId());
                    player.sendMessage(ChatColor.GREEN + "You accepted " + target.getName() + "'s trust request!");
                    target.sendMessage(ChatColor.GREEN + player.getName() + " accepted your trust request!");
                } else {
                    player.sendMessage(ChatColor.RED + "No pending request from that player.");
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("deny")) {
                if (args.length < 2) return true;
                Player target = Bukkit.getPlayer(args[1]);
                if (target != null) {
                    pendingRequests.remove(player.getUniqueId());
                    player.sendMessage(ChatColor.RED + "You denied " + target.getName() + "'s trust request.");
                    target.sendMessage(ChatColor.RED + player.getName() + " denied your trust request.");
                }
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }

            if (target.equals(player)) {
                player.sendMessage(ChatColor.RED + "You cannot trust yourself.");
                return true;
            }

            if (isTrusted(player, target)) {
                player.sendMessage(ChatColor.RED + "You are already trusted with this player.");
                return true;
            }

            pendingRequests.put(target.getUniqueId(), player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "Trust request sent to " + target.getName() + ".");

            TextComponent msg = new TextComponent(ChatColor.AQUA + player.getName() + " sent you a trust request! ");
            TextComponent accept = new TextComponent(ChatColor.GREEN + ChatColor.BOLD.toString() + "[YES]");
            accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trust accept " + player.getName()));
            TextComponent deny = new TextComponent(ChatColor.RED + ChatColor.BOLD.toString() + " [NO]");
            deny.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trust deny " + player.getName()));

            msg.addExtra(accept);
            msg.addExtra(deny);

            target.spigot().sendMessage(msg);
            return true;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        String commandName = cmd.getName().toLowerCase();

        if (commandName.equals("trust")) {
            if (args.length == 1) {
                completions.add("list");
                completions.add("accept");
                completions.add("deny");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!(sender instanceof Player self) || !p.getUniqueId().equals(self.getUniqueId())) {
                        completions.add(p.getName());
                    }
                }
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deny"))) {
                for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
            }
        } else if (commandName.equals("untrust")) {
            if (args.length == 1) {
                if (sender instanceof Player self) {
                    for (UUID trusted : getTrusted(self)) {
                        Player trustedPlayer = Bukkit.getPlayer(trusted);
                        if (trustedPlayer != null) completions.add(trustedPlayer.getName());
                    }
                } else {
                    for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
                }
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        List<String> filtered = new ArrayList<>();
        for (String s : completions) {
            if (s.toLowerCase().startsWith(lastArg)) {
                filtered.add(s);
            }
        }
        return filtered;
    }
}