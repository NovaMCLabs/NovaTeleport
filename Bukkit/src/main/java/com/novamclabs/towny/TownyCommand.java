package com.novamclabs.towny;

import com.novamclabs.StarTeleport;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Towny 城镇传送命令
 * Towny town teleport command
 */
public class TownyCommand implements CommandExecutor, TabCompleter {
    private final StarTeleport plugin;
    private final TownyTeleportManager manager;
    
    public TownyCommand(StarTeleport plugin, TownyTeleportManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLang().t("command.player_only"));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!manager.isEnabled()) {
            player.sendMessage(plugin.getLang().t("towny.not_enabled"));
            return true;
        }
        
        // /towntp - 传送到自己的城镇
        // /towntp <townname> - 传送到指定城镇
        
        if (args.length == 0) {
            // 传送到自己的城镇
            if (!player.hasPermission("novateleport.towny.home")) {
                player.sendMessage(plugin.getLang().t("command.no_permission"));
                return true;
            }
            manager.teleportToTownSpawn(player);
        } else {
            // 传送到指定城镇
            if (!player.hasPermission("novateleport.towny.other")) {
                player.sendMessage(plugin.getLang().t("command.no_permission"));
                return true;
            }
            String townName = args[0];
            manager.teleportToTown(player, townName);
        }
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // TODO: 可以添加城镇名称补全
            // Can add town name completion here
        }
        
        return completions;
    }
}
