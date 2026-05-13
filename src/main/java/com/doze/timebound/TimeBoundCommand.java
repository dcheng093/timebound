package com.doze.timebound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

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



        if (!(sender instanceof Player p)) {
            sendColored(sender, NamedTextColor.RED, "Players only for this subcommand.");
            return true;
        }

        if (sub.equals("give")) {
            if (!isAdmin(p)) {
                sendColored(p, NamedTextColor.RED, "You do not have permission to use this command.");
                return true;
            }
            if (args.length < 2) {
                sendColored(p, NamedTextColor.YELLOW, "Usage: /timebound give weapons <freeze|brake|reverse|skip>");
                sendColored(p, NamedTextColor.YELLOW, "Usage: /timebound give clocks <freeze|brake|reverse|skip>");
                return true;
            }
            String itemType = args[1].toLowerCase();
            if (!itemType.equals("weapons") && !itemType.equals("clocks")) {
                sendColored(p, NamedTextColor.RED, "Use 'weapons' or 'clocks'.");
                return true;
            }
            if (args.length < 3) {
                sendColored(p, NamedTextColor.RED, "Usage: /timebound give " + itemType + " <freeze|brake|reverse|skip>");
                return true;
            }
            if (itemType.equals("weapons")) {
                giveWeapon(p, args[2].toLowerCase());
            } else {
                giveClock(p, args[2].toLowerCase());
            }
            return true;
        }

        if (sub.equals("spawnclock")) {
            if (!isAdmin(p)) {
                sendColored(p, NamedTextColor.RED, "You do not have permission to use this command.");
                return true;
            }
            if (args.length < 2) {
                sendColored(p, NamedTextColor.RED, "Usage: /timebound spawnclock <freeze|brake|reverse|skip> [timer_seconds]");
                return true;
            }
            ClockType clockType = ClockType.fromKey(args[1].toLowerCase());
            if (clockType == null) {
                sendColored(p, NamedTextColor.RED, "Unknown clock. Use freeze, brake, reverse, or skip.");
                return true;
            }
            
            long timerSeconds = 0;
            if (args.length >= 3) {
                try {
                    timerSeconds = Long.parseLong(args[2]);
                    if (timerSeconds < 0) {
                        sendColored(p, NamedTextColor.RED, "Timer must be 0 or positive.");
                        return true;
                    }
                } catch (NumberFormatException e) {
                    sendColored(p, NamedTextColor.RED, "Invalid timer value. Must be a number.");
                    return true;
                }
            }
            
            Main main = Main.getInstance();
            if (main == null) {
                sendColored(p, NamedTextColor.RED, "Unable to spawn the clock at this time.");
                return true;
            }
            TimeClockListener clockListener = main.getClockListener();
            if (clockListener == null) {
                sendColored(p, NamedTextColor.RED, "Unable to spawn the clock at this time.");
                return true;
            }
            Location targetLocation = p.getLocation();
            if (targetLocation == null) {
                sendColored(p, NamedTextColor.RED, "Unable to determine spawn location.");
                return true;
            }
            
            if (timerSeconds > 0) {
                clockListener.spawnTimedClock(targetLocation.add(0, 1, 0), clockType, timerSeconds);
                sendColored(p, NamedTextColor.GREEN, "Spawned a timed " + clockType.displayName() + " with " + timerSeconds + " second(s) timer!");
            } else {
                clockListener.spawnClickableClock(targetLocation.add(0, 1, 0), clockType);
                sendColored(p, NamedTextColor.GREEN, "Spawned a clickable " + clockType.displayName() + "!");
            }
            return true;
        }

        if (sub.equals("cooldowns")) {
            if (!isAdmin(p)) {
                sendColored(p, NamedTextColor.RED, "You do not have permission to use this command.");
                return true;
            }
            Player target = args.length >= 2 ? Bukkit.getPlayer(args[1]) : p;
            if (target == null) {
                sendColored(p, NamedTextColor.RED, "Player not found.");
                return true;
            }
            Main.getInstance().getListener().resetCooldowns(target);
            sendColored(p, NamedTextColor.GREEN, "Refreshed all cooldowns and charges for " + target.getName() + ".");
            return true;
        }

        if (sub.equals("clearstacks")) {
            if (!isAdmin(p)) {
                sendColored(p, NamedTextColor.RED, "You do not have permission to use this command.");
                return true;
            }
            Player target = args.length >= 2 ? Bukkit.getPlayer(args[1]) : p;
            if (target == null) {
                sendColored(p, NamedTextColor.RED, "Player not found.");
                return true;
            }
            Main.getInstance().getListener().clearSkipStacks(target);
            sendColored(p, NamedTextColor.GREEN, "Cleared Time Skip stacks for " + target.getName() + ".");
            return true;
        }

        sendUsage(p);
        return true;
    }

    private void giveWeapon(Player player, String type) {
        ClockType clockType = ClockType.fromKey(type);
        if (clockType == null) {
            sendColored(player, NamedTextColor.RED, "Unknown type. Use freeze, brake, reverse, or skip.");
            return;
        }
        Main main = Main.getInstance();
        if (main != null && main.getConfig().getBoolean("claimed.weapons." + clockType.key(), false)) {
            sendColored(player, NamedTextColor.RED, "That legendary weapon already exists.");
            return;
        }
        ItemStack item = TimeBladeItems.createBlade(type);
        if (item == null) {
            sendColored(player, NamedTextColor.RED, "Failed to create weapon.");
            return;
        }
        if (main != null) {
            main.getConfig().set("claimed.weapons." + clockType.key(), true);
            main.saveConfig();
        }
        player.getInventory().addItem(item);
        Component name = item.getItemMeta().displayName();
        if (name == null) name = Component.text(clockType.displayName(), NamedTextColor.GRAY);
        player.sendMessage(Component.text("Given: ", NamedTextColor.GREEN).append(name));
    }

    private void giveClock(Player player, String type) {
        ClockType clockType = ClockType.fromKey(type);
        if (clockType == null) {
            sendColored(player, NamedTextColor.RED, "Unknown clock. Use freeze, brake, reverse, or skip.");
            return;
        }
        Main main = Main.getInstance();
        if (main != null && main.getConfig().getBoolean("claimed.clocks." + clockType.key(), false)) {
            sendColored(player, NamedTextColor.RED, "That Time Clock has already been claimed.");
            return;
        }
        ItemStack item = TimeClockItems.createClock(Main.getInstance(), clockType);
        if (main != null) {
            main.getConfig().set("claimed.clocks." + clockType.key(), true);
            main.saveConfig();
        }
        player.getInventory().addItem(item);
        Component name = item.getItemMeta().displayName();
        if (name == null) name = Component.text(clockType.displayName(), NamedTextColor.GRAY);
        player.sendMessage(Component.text("Given: ", NamedTextColor.GREEN).append(name));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("TimeBound commands:", NamedTextColor.GOLD));
        if (isAdmin(sender)) {
            sender.sendMessage(Component.text("/timebound give weapons <freeze|brake|reverse|skip>", NamedTextColor.YELLOW).append(Component.text(" - give a weapon", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/timebound give clocks <freeze|brake|reverse|skip>", NamedTextColor.YELLOW).append(Component.text(" - give a clock", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/timebound spawnclock <type>", NamedTextColor.YELLOW).append(Component.text(" - spawn a clickable clock", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/timebound cooldowns [player]", NamedTextColor.YELLOW).append(Component.text(" - reset blade/clock cooldowns", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/timebound clearstacks [player]", NamedTextColor.YELLOW).append(Component.text(" - clear Time Skip stacks", NamedTextColor.GRAY)));
        }
    }

    private boolean isAdmin(CommandSender sender) {
        return sender.hasPermission("timebound.admin") || sender.isOp();
    }

    private void sendColored(CommandSender sender, NamedTextColor color, String message) {
        sender.sendMessage(Component.text(message, color));
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
                out.add("give");
                out.add("spawnclock");
                out.add("cooldowns");
                out.add("clearstacks");
            }
            return filter(out, last);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("spawnclock")) {
                if (!admin) return Collections.emptyList();
                out.add("freeze");
                out.add("brake");
                out.add("reverse");
                out.add("skip");
                return filter(out, last);
            }
            if (sub.equals("give")) {
                if (!admin) return Collections.emptyList();
                out.add("weapons");
                out.add("clocks");
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
        if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("spawnclock")) {
                if (!admin) return Collections.emptyList();
                out.add("0");
                out.add("30");
                out.add("60");
                out.add("120");
                out.add("300");
                return filter(out, last);
            }
            if (sub.equals("give")) {
                if (!admin) return Collections.emptyList();
                String type = args[1].toLowerCase(Locale.ROOT);
                if (type.equals("weapons") || type.equals("clocks")) {
                    out.add("freeze");
                    out.add("brake");
                    out.add("reverse");
                    out.add("skip");
                    return filter(out, last);
                }
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
