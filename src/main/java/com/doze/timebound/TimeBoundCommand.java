package com.doze.timebound;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class TimeBoundCommand implements CommandExecutor, TabCompleter {
    public TimeBoundCommand(TimeClockListener ignoredClockListener) {
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("timebound")) {
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("help")) {
            sendUsage(sender);
            return true;
        }

        if (sub.equals("generate")) {
            if (!(sender instanceof Player adminPlayer)) {
                sender.sendMessage(ChatColor.RED + "Only players can run this command!");
                return true;
            }
            if (!isAdmin(adminPlayer)) {
                adminPlayer.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
            if (args.length < 2) {
                adminPlayer.sendMessage(ChatColor.YELLOW + "Usage: /timebound generate <freeze|brake|reverse|skip|all>");
                return true;
            }

            String target = args[1].toLowerCase();
            if (target.equals("all")) {
                new TimeStructureManager(Main.getInstance()).generateAll(adminPlayer);
                return true;
            }

            ClockType clockType = ClockType.fromKey(target);
            if (clockType == null) {
                adminPlayer.sendMessage(ChatColor.RED + "Unknown clock. Use freeze, brake, reverse, skip, or all.");
                return true;
            }
            new TimeStructureManager(Main.getInstance()).generateSingle(adminPlayer, clockType);
            return true;
        }

        if (sub.equals("locate")) {
            if (!(sender instanceof Player adminPlayer)) {
                sender.sendMessage(ChatColor.RED + "Only players can run this command!");
                return true;
            }
            if (!isAdmin(adminPlayer)) {
                adminPlayer.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
            if (args.length < 2) {
                adminPlayer.sendMessage(ChatColor.YELLOW + "Usage: /timebound locate <freeze|brake|reverse|skip>");
                return true;
            }

            ClockType clockType = ClockType.fromKey(args[1].toLowerCase());
            if (clockType == null) {
                adminPlayer.sendMessage(ChatColor.RED + "Unknown clock. Use freeze, brake, reverse, or skip.");
                return true;
            }
            new TimeStructureManager(Main.getInstance()).locateStructure(adminPlayer, clockType);
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage(ChatColor.RED + "Players only for this subcommand.");
            return true;
        }

        if (sub.equals("give")) {
            if (!isAdmin(p)) {
                p.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
            if (args.length < 2) {
                p.sendMessage(ChatColor.RED + "Usage: /timebound give <freeze|brake|reverse|skip>");
                return true;
            }
            giveWeapon(p, args[1].toLowerCase());
            return true;
        }

        if (sub.equals("spawnclock")) {
            if (!isAdmin(p)) {
                p.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
            if (args.length < 2) {
                p.sendMessage(ChatColor.RED + "Usage: /timebound spawnclock <freeze|brake|reverse|skip>");
                return true;
            }
            ClockType clockType = ClockType.fromKey(args[1].toLowerCase());
            if (clockType == null) {
                p.sendMessage(ChatColor.RED + "Unknown clock. Use freeze, brake, reverse, or skip.");
                return true;
            }
            // Spawn the clickable hologram 1 block above where the admin is standing
            Main.getInstance().getClockListener().spawnClickableClock(p.getLocation().add(0, 1, 0), clockType);
            p.sendMessage(ChatColor.GREEN + "Spawned a clickable " + clockType.displayName() + "!");
            return true;
        }

        if (sub.equals("cooldowns")) {
            if (!isAdmin(p)) {
                p.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
            Player target = args.length >= 2 ? Bukkit.getPlayer(args[1]) : p;
            if (target == null) {
                p.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }
            Main.getInstance().getListener().resetCooldowns(target);
            p.sendMessage(ChatColor.GREEN + "Refreshed all cooldowns and charges for " + target.getName() + ".");
            return true;
        }

        if (sub.equals("clearstacks")) {
            if (!isAdmin(p)) {
                p.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
            Player target = args.length >= 2 ? Bukkit.getPlayer(args[1]) : p;
            if (target == null) {
                p.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }
            Main.getInstance().getListener().clearSkipStacks(target);
            p.sendMessage(ChatColor.GREEN + "Cleared Time Skip stacks for " + target.getName() + ".");
            return true;
        }

        sendUsage(p);
        return true;
    }

    private void giveWeapon(Player player, String type) {
        ClockType clockType = ClockType.fromKey(type);
        if (clockType == null) {
            player.sendMessage(ChatColor.RED + "Unknown clock. Use freeze, brake, reverse, or skip.");
            return;
        }
        ItemStack item = TimeClockItems.createClock(Main.getInstance(), clockType);
        player.getInventory().addItem(item);
        player.sendMessage(ChatColor.GREEN + "Given: " + item.getItemMeta().getDisplayName());
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "TimeBound commands:");
        if (isAdmin(sender)) {
            sender.sendMessage(ChatColor.YELLOW + "/timebound generate <freeze|brake|reverse|skip|all>" + ChatColor.GRAY + " - generate structure(s)");
            sender.sendMessage(ChatColor.YELLOW + "/timebound locate <freeze|brake|reverse|skip>" + ChatColor.GRAY + " - locate a generated structure");
            sender.sendMessage(ChatColor.YELLOW + "/timebound give <freeze|brake|reverse|skip>" + ChatColor.GRAY + " - give a clock");
            sender.sendMessage(ChatColor.YELLOW + "/timebound spawnclock <type>" + ChatColor.GRAY + " - spawn a clickable clock");
            sender.sendMessage(ChatColor.YELLOW + "/timebound cooldowns [player]" + ChatColor.GRAY + " - reset blade/clock cooldowns");
            sender.sendMessage(ChatColor.YELLOW + "/timebound clearstacks [player]" + ChatColor.GRAY + " - clear Time Skip stacks");
        }
    }

    private boolean isAdmin(CommandSender sender) {
        return sender.hasPermission("timebound.admin") || sender.isOp();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("timebound")) return Collections.emptyList();
        boolean admin = isAdmin(sender);
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();

        if (args.length == 1) {
            out.add("help");
            if (admin) {
                out.add("generate");
                out.add("locate");
                out.add("give");
                out.add("spawnclock");
                out.add("cooldowns");
                out.add("clearstacks");
            }
            return filter(out, last);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("generate") || sub.equals("locate") || sub.equals("give") || sub.equals("spawnclock")) {
                if (!admin) return Collections.emptyList();
                out.add("freeze");
                out.add("brake");
                out.add("reverse");
                out.add("skip");
                if (sub.equals("generate")) {
                    out.add("all");
                }
                return filter(out, last);
            }
            if (sub.equals("help")) {
                return Collections.emptyList();
            }
            if (sub.equals("cooldowns") || sub.equals("clearstacks")) {
                if (!admin) return Collections.emptyList();
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
                return filter(out, last);
            }
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(option);
            }
        }
        return result;
    }
}