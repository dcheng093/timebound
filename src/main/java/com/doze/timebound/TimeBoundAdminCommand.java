package com.doze.timebound;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

public class TimeBoundAdminCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("timebound.admin")) {
            player.sendMessage("No permission.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("bypasscooldown")) {

            player.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(Main.getInstance(), "cooldown_bypass"),
                    PersistentDataType.BYTE,
                    (byte) 1
            );
            player.sendMessage("Cooldown bypass ENABLED");
        }
        return true;
    }
}