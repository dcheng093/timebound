package com.doze.timebound;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class TimeBoundTab implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("timebound")) return Collections.emptyList();
        boolean isAdmin = sender.hasPermission("timebound.admin") || sender.isOp();
        List<String> options = new ArrayList<>();
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            if (isAdmin) {
                options.add("give");
                options.add("spawnclock");
                options.add("cooldowns");
                options.add("clearstacks");
            }
            return filter(options, last);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("give") || sub.equals("spawnclock")) {
                if (!isAdmin) return Collections.emptyList();
                options.add("brake");
                options.add("freeze");
                options.add("reverse");
                options.add("skip");
                return filter(options, last);
            }
            if (sub.equals("cooldowns") || sub.equals("clearstacks")) {
                if (!isAdmin) return Collections.emptyList();
                for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
                return filter(options, last);
            }
        }
        return Collections.emptyList();
    }
    private List<String> filter(List<String> input, String prefix) {
        List<String> out = new ArrayList<>();
        for (String entry : input) {
            if (entry.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(entry);
            }
        }
        return out;
    }
}