package com.novamclabs.toll;

import com.novamclabs.StarTeleport;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 付费传送点命令
 * Toll warp command
 */
public class TollWarpCommand implements CommandExecutor, TabCompleter {
    private final StarTeleport plugin;
    private final TollWarpManager manager;
    
    public TollWarpCommand(StarTeleport plugin, TollWarpManager manager) {
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
            player.sendMessage(plugin.getLang().t("toll.not_enabled"));
            return true;
        }
        
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        
        String subCmd = args[0].toLowerCase();
        
        switch (subCmd) {
            case "create":
                if (args.length < 3) {
                    player.sendMessage("§c用法: /tollwarp create <名称> <价格>");
                    return true;
                }
                try {
                    double price = Double.parseDouble(args[2]);
                    return manager.createWarp(player, args[1], price);
                } catch (NumberFormatException e) {
                    player.sendMessage("§c价格必须是数字！");
                    return true;
                }
                
            case "delete":
            case "remove":
                if (args.length < 2) {
                    player.sendMessage("§c用法: /tollwarp delete <名称>");
                    return true;
                }
                return manager.deleteWarp(player, args[1]);
                
            case "setprice":
                if (args.length < 3) {
                    player.sendMessage("§c用法: /tollwarp setprice <名称> <价格>");
                    return true;
                }
                try {
                    double price = Double.parseDouble(args[2]);
                    return manager.setPrice(player, args[1], price);
                } catch (NumberFormatException e) {
                    player.sendMessage("§c价格必须是数字！");
                    return true;
                }
                
            case "list":
                return listWarps(player);
                
            case "mywarps":
                return listMyWarps(player);
                
            case "tp":
            case "teleport":
                if (args.length < 2) {
                    player.sendMessage("§c用法: /tollwarp tp <名称>");
                    return true;
                }
                return manager.teleportToWarp(player, args[1]);
                
            case "info":
                if (args.length < 2) {
                    player.sendMessage("§c用法: /tollwarp info <名称>");
                    return true;
                }
                return showWarpInfo(player, args[1]);
                
            default:
                // 尝试直接传送
                return manager.teleportToWarp(player, args[0]);
        }
    }
    
    private boolean listWarps(Player player) {
        List<TollWarp> warps = manager.getAllWarps();
        
        if (warps.isEmpty()) {
            player.sendMessage(plugin.getLang().t("toll.no_warps"));
            return true;
        }
        
        player.sendMessage("§6=== 公共付费传送点 ===");
        for (TollWarp warp : warps) {
            String owner = plugin.getServer().getOfflinePlayer(warp.getOwnerId()).getName();
            player.sendMessage(String.format("§e%s §7- §a%s §7(§f%s§7) - 使用次数: §6%d",
                warp.getName(),
                com.novamclabs.util.EconomyUtil.format(warp.getPrice()),
                owner,
                warp.getUsageCount()));
        }
        return true;
    }
    
    private boolean listMyWarps(Player player) {
        List<TollWarp> warps = manager.getPlayerWarps(player.getUniqueId());
        
        if (warps.isEmpty()) {
            player.sendMessage(plugin.getLang().t("toll.no_own_warps"));
            return true;
        }
        
        player.sendMessage("§6=== 我的付费传送点 ===");
        for (TollWarp warp : warps) {
            player.sendMessage(String.format("§e%s §7- §a%s §7- 使用次数: §6%d §7- 状态: %s",
                warp.getName(),
                com.novamclabs.util.EconomyUtil.format(warp.getPrice()),
                warp.getUsageCount(),
                warp.isEnabled() ? "§a启用" : "§c禁用"));
        }
        return true;
    }
    
    private boolean showWarpInfo(Player player, String warpName) {
        TollWarp warp = manager.getWarp(warpName);
        if (warp == null) {
            player.sendMessage(plugin.getLang().t("toll.not_found", warpName));
            return true;
        }
        
        String owner = plugin.getServer().getOfflinePlayer(warp.getOwnerId()).getName();
        player.sendMessage("§6=== " + warp.getName() + " ===");
        player.sendMessage("§7所有者: §e" + owner);
        player.sendMessage("§7价格: §a" + com.novamclabs.util.EconomyUtil.format(warp.getPrice()));
        player.sendMessage("§7位置: §f" + warp.getLocation().getWorld().getName() + 
            " §7(" + warp.getLocation().getBlockX() + ", " + 
            warp.getLocation().getBlockY() + ", " + 
            warp.getLocation().getBlockZ() + ")");
        player.sendMessage("§7使用次数: §6" + warp.getUsageCount());
        player.sendMessage("§7状态: " + (warp.isEnabled() ? "§a启用" : "§c禁用"));
        
        return true;
    }
    
    private void sendHelp(Player player) {
        player.sendMessage("§6=== 付费传送点帮助 ===");
        player.sendMessage("§e/tollwarp create <名称> <价格> §7- 创建付费传送点");
        player.sendMessage("§e/tollwarp delete <名称> §7- 删除付费传送点");
        player.sendMessage("§e/tollwarp setprice <名称> <价格> §7- 设置价格");
        player.sendMessage("§e/tollwarp list §7- 列出所有传送点");
        player.sendMessage("§e/tollwarp mywarps §7- 列出我的传送点");
        player.sendMessage("§e/tollwarp tp <名称> §7- 传送到传送点");
        player.sendMessage("§e/tollwarp info <名称> §7- 查看传送点信息");
        player.sendMessage("§e/tollwarp <名称> §7- 快捷传送");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.addAll(List.of("create", "delete", "setprice", "list", "mywarps", "tp", "info"));
            // 添加所有传送点名称
            completions.addAll(manager.getAllWarps().stream()
                .map(TollWarp::getName)
                .collect(Collectors.toList()));
            
            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            String subCmd = args[0].toLowerCase();
            if (subCmd.equals("delete") || subCmd.equals("setprice") || 
                subCmd.equals("tp") || subCmd.equals("info")) {
                return manager.getAllWarps().stream()
                    .map(TollWarp::getName)
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        return completions;
    }
}
