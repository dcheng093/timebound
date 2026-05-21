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
            if (args.length < 3) {
                sendColored(p, NamedTextColor.YELLOW, "Usage: /timebound give weapon <player> <freeze|brake|reverse|skip|master>");
                sendColored(p, NamedTextColor.YELLOW, "Usage: /timebound give clock <player> <freeze|brake|reverse|skip>");
                sendColored(p, NamedTextColor.YELLOW, "Usage: /timebound give weapons <freeze|brake|reverse|skip|master>");
                sendColored(p, NamedTextColor.YELLOW, "Usage: /timebound give clocks <freeze|brake|reverse|skip>");
                return true;
            }
            String type = args[1].toLowerCase(Locale.ROOT);
            boolean isWeapon = type.equals("weapon") || type.equals("weapons");
            boolean isClock = type.equals("clock") || type.equals("clocks");
            if (!isWeapon && !isClock) {
                sendColored(p, NamedTextColor.RED, "Use 'weapon(s)' or 'clock(s)'.");
                return true;
            }
            Player target = p;
            String itemArg;
            if (args.length >= 4) {
                Player found = Bukkit.getPlayer(args[2]);
                if (found == null) {
                    sendColored(p, NamedTextColor.RED, "Player not found.");
                    return true;
                }
                target = found;
                itemArg = args[3].toLowerCase(Locale.ROOT);
            } else {
                itemArg = args[2].toLowerCase(Locale.ROOT);
            }

            if (isWeapon) {
                giveWeapon(target, itemArg, true);
            } else {
                giveClock(target, itemArg);
            }
            return true;
        }

        if (sub.equals("test")) {
            if (!p.hasPermission("timebound.test") && !p.isOp()) {
                sendColored(p, NamedTextColor.RED, "You do not have permission to use this command.");
                return true;
            }
            if (args.length < 2) {
                sendColored(p, NamedTextColor.YELLOW, "Usage: /timebound test <on|off>");
                return true;
            }
            boolean enable = args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true") || args[1].equalsIgnoreCase("enable");
            boolean disable = args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("false") || args[1].equalsIgnoreCase("disable");
            if (!enable && !disable) {
                sendColored(p, NamedTextColor.RED, "Usage: /timebound test <on|off>");
                return true;
            }
            Main main = Main.getInstance();
            if (main == null) return true;
            main.getConfig().set("testMode", enable);
            main.saveConfig();
            Component msg = Component.text("TimeBound Test Mode is now ", NamedTextColor.YELLOW)
                    .append(Component.text(enable ? "ON" : "OFF", enable ? NamedTextColor.GREEN : NamedTextColor.RED));
            Bukkit.broadcast(msg);
            main.getGlobalScanner().requestScan(GlobalTimeItemScanner.Reason.ADMIN);
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

    private void giveWeapon(Player player, String type, boolean adminBypassAllowed) {
        Main main = Main.getInstance();
        if (main == null) return;
        boolean isMaster = type.equalsIgnoreCase("master") || type.equalsIgnoreCase("masteroftime") || type.equalsIgnoreCase("master_of_time");
        if (isMaster) {
            boolean testMode = main.getConfig().getBoolean("testMode", false);
            if (main.getGlobalRegistry().anyMasterExists() && !testMode && !adminBypassAllowed) {
                sendColored(player, NamedTextColor.RED, "Eternity already exists.");
                return;
            }
            if (main.getGlobalRegistry().anyMasterExists() && (testMode || adminBypassAllowed)) {
                main.getGlobalRegistry().logDuplicateViolation(player.getName() + " used /timebound give to create duplicate Eternity.");
            }
            ItemStack item = MasterOfTimeItems.createCrafted(main);
            player.getInventory().addItem(item);
            player.sendMessage(Component.text("Given: Eternity", NamedTextColor.GREEN));
            main.getGlobalScanner().requestScan(GlobalTimeItemScanner.Reason.ADMIN);
            return;
        }
        ClockType clockType = ClockType.fromKey(type);
        if (clockType == null) {
            sendColored(player, NamedTextColor.RED, "Unknown type. Use freeze, brake, reverse, skip, or master.");
            return;
        }
        boolean testMode = main.getConfig().getBoolean("testMode", false);
        if (main.getGlobalRegistry().anyWeaponExists(clockType.key())) {
            if (!testMode && !adminBypassAllowed) {
                sendColored(player, NamedTextColor.RED, "That legendary weapon already exists.");
                return;
            }
            main.getGlobalRegistry().logDuplicateViolation(player.getName() + " used /timebound give to create duplicate weapon: " + clockType.key());
        }
        ItemStack item = TimeBladeItems.createBlade(type);
        if (item == null) {
            sendColored(player, NamedTextColor.RED, "Failed to create weapon.");
            return;
        }
        TimeItemUid.ensure(main, item);
        player.getInventory().addItem(item);
        Component name = item.getItemMeta().displayName();
        if (name == null) name = Component.text(clockType.displayName(), NamedTextColor.GRAY);
        player.sendMessage(Component.text("Given: ", NamedTextColor.GREEN).append(name));
        main.getGlobalScanner().requestScan(GlobalTimeItemScanner.Reason.ADMIN);
    }

    private void giveClock(Player player, String type) {
        ClockType clockType = ClockType.fromKey(type);
        if (clockType == null) {
            sendColored(player, NamedTextColor.RED, "Unknown clock. Use freeze, brake, reverse, or skip.");
            return;
        }
        Main main = Main.getInstance();
        if (main == null) return;
        if (playerHasClock(player, clockType)) {
            sendColored(player, NamedTextColor.RED, "You already have a " + clockType.displayName() + ".");
            return;
        }
        ItemStack item = TimeClockItems.createClock(Main.getInstance(), clockType);
        TimeItemUid.ensure(main, item);
        player.getInventory().addItem(item);
        Component name = item.getItemMeta().displayName();
        if (name == null) name = Component.text(clockType.displayName(), NamedTextColor.GRAY);
        player.sendMessage(Component.text("Given: ", NamedTextColor.GREEN).append(name));
        main.getGlobalScanner().requestScan(GlobalTimeItemScanner.Reason.ADMIN);
    }

    private boolean playerHasClock(Player player, ClockType type) {
        ItemStack[] contents = player.getInventory().getContents();
        if (contents != null) {
            for (ItemStack item : contents) {
                if (TimeClockItems.getClockType(Main.getInstance(), item) == type) return true;
            }
        }
        return TimeClockItems.getClockType(Main.getInstance(), player.getInventory().getItemInOffHand()) == type;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("TimeBound commands:", NamedTextColor.GOLD));
        if (isAdmin(sender)) {
            sender.sendMessage(Component.text("/timebound give weapons <freeze|brake|reverse|skip>", NamedTextColor.YELLOW).append(Component.text(" - give a weapon", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/timebound give clocks <freeze|brake|reverse|skip>", NamedTextColor.YELLOW).append(Component.text(" - give a clock", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/timebound spawnclock <type>", NamedTextColor.YELLOW).append(Component.text(" - spawn a clickable clock", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/timebound cooldowns [player]", NamedTextColor.YELLOW).append(Component.text(" - reset blade/clock cooldowns", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/timebound clearstacks [player]", NamedTextColor.YELLOW).append(Component.text(" - clear Time Skip stacks", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/timebound test <on|off>", NamedTextColor.YELLOW).append(Component.text(" - toggle duplicate restriction test mode", NamedTextColor.GRAY)));
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
                out.add("test");
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
                out.add("weapon");
                out.add("clock");
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
            if (sub.equals("test")) {
                if (!admin) return Collections.emptyList();
                out.add("on");
                out.add("off");
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
                String kind = args[1].toLowerCase(Locale.ROOT);
                if (kind.equals("weapon") || kind.equals("clock")) {
                    for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
                    return filter(out, last);
                }
                if (kind.equals("weapons")) {
                    out.add("freeze");
                    out.add("brake");
                    out.add("reverse");
                    out.add("skip");
                    out.add("master");
                    return filter(out, last);
                }
                if (kind.equals("clocks")) {
                    out.add("freeze");
                    out.add("brake");
                    out.add("reverse");
                    out.add("skip");
                    return filter(out, last);
                }
            }
        }
        if (args.length == 4) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("give")) {
                if (!admin) return Collections.emptyList();
                String kind = args[1].toLowerCase(Locale.ROOT);
                if (kind.equals("weapon")) {
                    out.add("freeze");
                    out.add("brake");
                    out.add("reverse");
                    out.add("skip");
                    out.add("master");
                    return filter(out, last);
                }
                if (kind.equals("clock")) {
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
