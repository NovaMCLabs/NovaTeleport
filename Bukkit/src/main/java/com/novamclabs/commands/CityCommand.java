package com.novamclabs.commands;

import com.novamclabs.StarTeleport;
import com.novamclabs.util.TeleportUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 回城命令 /city | /hub
 * 支持本服世界或代理跨服
 */
public class CityCommand implements CommandExecutor {
    private final StarTeleport plugin;

    public CityCommand(StarTeleport plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        Player p = (Player) sender;
        String mode = plugin.getConfig().getString("city.mode", "local");
        if ("local".equalsIgnoreCase(mode)) {
            String worldName = plugin.getConfig().getString("city.local.world", "world");
            double x = plugin.getConfig().getDouble("city.local.x", 0.5);
            double y = plugin.getConfig().getDouble("city.local.y", 80);
            double z = plugin.getConfig().getDouble("city.local.z", 0.5);
            World w = Bukkit.getWorld(worldName);
            if (w == null) { p.sendMessage(plugin.getLang().tr("warn.world_not_loaded", "world", worldName)); return true; }
            Location dest = new Location(w, x, y, z);
            int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
            TeleportUtil.delayedTeleportWithAnimation(plugin, p, dest, delay, "city", () -> p.sendMessage(plugin.getLang().t("teleport.completed")));
            return true;
        } else if ("proxy".equalsIgnoreCase(mode)) {
            String server = plugin.getConfig().getString("city.proxy.server", "hub");
            com.novamclabs.util.ProxyMessenger.connect(plugin, p, server);
            p.sendMessage(plugin.getLang().tr("city.proxy", "server", server));
            return true;
        }
        return true;
    }
}
