package com.novamclabs.towny;

import com.novamclabs.StarTeleport;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

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
        if (!(sender instanceof Player)) {
            return List.of();
        }
        Player player = (Player) sender;
        if (!manager.isEnabled()) {
            return List.of();
        }
        if (!player.hasPermission("novateleport.towny.other")) {
            return List.of();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            try {
                return TownyAPI.getInstance().getTowns().stream()
                    .map(Town::getName)
                    .filter(n -> n != null && n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .limit(50)
                    .collect(Collectors.toList());
            } catch (Throwable ignored) {
                return List.of();
            }
        }

        return List.of();
    }
}
