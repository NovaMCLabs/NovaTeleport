package com.novamclabs.stele;

import com.novamclabs.StarTeleport;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;

/**
 * /stele 管理与使用指令（中英）
 * Admin & user commands for teleportation steles
 */
public class SteleCommand implements CommandExecutor {
    private final StarTeleport plugin; private final SteleManager manager;
    public SteleCommand(StarTeleport plugin, SteleManager manager) { this.plugin = plugin; this.manager = manager; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("/stele list | locate | create <name> | remove <name> | travel <name>");
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "list": {
                if (!requireUse(sender)) return true;
                sender.sendMessage("Steles: " + String.join(", ", manager.listSteles()));
                return true;
            }
            case "locate": {
                if (!requireUse(sender)) return true;
                if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
                Player p = (Player) sender;
                Location me = p.getLocation();
                String nearest = manager.listSteles().stream().min(Comparator.comparingDouble(n -> {
                    Location l = manager.getSteleLocation(n); if (l == null || !l.getWorld().equals(me.getWorld())) return Double.MAX_VALUE; return l.distanceSquared(me);
                })).orElse(null);
                if (nearest == null) { p.sendMessage(plugin.getLang().t("stele.none")); } else { p.sendMessage("Nearest: " + nearest); }
                return true;
            }
            case "create": {
                if (!sender.hasPermission("novateleport.admin")) { sender.sendMessage(plugin.getLang().t("command.no_permission")); return true; }
                if (!(sender instanceof Player) || args.length < 2) { sender.sendMessage("/stele create <name>"); return true; }
                Player p = (Player) sender;
                String name = args[1];
                manager.setStele(name, p.getLocation());
                sender.sendMessage("Created stele: " + name);
                return true;
            }
            case "remove": {
                if (!sender.hasPermission("novateleport.admin")) { sender.sendMessage(plugin.getLang().t("command.no_permission")); return true; }
                if (args.length < 2) { sender.sendMessage("/stele remove <name>"); return true; }
                manager.removeStele(args[1]); sender.sendMessage("Removed."); return true;
            }
            case "travel": {
                if (!requireUse(sender)) return true;
                if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
                Player p = (Player) sender;
                if (args.length < 2) { manager.openSteleMenu(p); return true; }
                manager.travelTo(p, args[1]);
                return true;
            }
            case "activatefor": {
                if (!sender.hasPermission("novateleport.admin")) { sender.sendMessage(plugin.getLang().t("command.no_permission")); return true; }
                if (args.length < 3) { sender.sendMessage("/stele activatefor <player> <key>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { sender.sendMessage(plugin.getLang().t("common.no_online_player")); return true; }
                final String key = args[2];
                plugin.getDataStore().updatePlayer(target.getUniqueId(), cfg -> {
                    java.util.List<String> unlocked = cfg.getStringList("steles.unlocked");
                    if (!unlocked.contains(key)) {
                        unlocked.add(key);
                        cfg.set("steles.unlocked", unlocked);
                    }
                });
                sender.sendMessage("Unlocked for " + target.getName());
                return true;
            }
            default:
                sender.sendMessage("/stele list | locate | create <name> | remove <name> | travel <name>");
                return true;
        }
    }

    private boolean requireUse(CommandSender sender) {
        if (sender.hasPermission("novateleport.stele.use")) return true;
        sender.sendMessage(plugin.getLang().t("command.no_permission"));
        return false;
    }
}
