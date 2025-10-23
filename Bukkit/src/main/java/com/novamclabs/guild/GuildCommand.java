package com.novamclabs.guild;

import com.novamclabs.StarTeleport;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 工会传送命令
 * Guild teleport command
 */
public class GuildCommand implements CommandExecutor, TabCompleter {
    private final StarTeleport plugin;
    private final GuildManager guildManager;
    private final GuildWarpManager warpManager;
    
    public GuildCommand(StarTeleport plugin, GuildManager guildManager, GuildWarpManager warpManager) {
        this.plugin = plugin;
        this.guildManager = guildManager;
        this.warpManager = warpManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLang().t("command.player_only"));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!guildManager.isEnabled()) {
            player.sendMessage(plugin.getLang().t("guild.not_enabled"));
            return true;
        }
        
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        
        String subCmd = args[0].toLowerCase();
        
        switch (subCmd) {
            case "home":
            case "hq":
                return teleportToHQ(player);
                
            case "sethome":
            case "sethq":
                return setHQ(player);
                
            case "warp":
                if (args.length < 2) {
                    player.sendMessage(plugin.getLang().t("guild.usage_warp"));
                    return true;
                }
                return warpManager.teleportToWarp(player, args[1]);
                
            case "setwarp":
                if (args.length < 2) {
                    player.sendMessage(plugin.getLang().t("guild.usage_setwarp"));
                    return true;
                }
                return warpManager.createWarp(player, args[1]);
                
            case "delwarp":
                if (args.length < 2) {
                    player.sendMessage(plugin.getLang().t("guild.usage_delwarp"));
                    return true;
                }
                return warpManager.deleteWarp(player, args[1]);
                
            case "list":
            case "warps":
                return listWarps(player);
                
            case "info":
                return showGuildInfo(player);
                
            default:
                sendHelp(player);
                return true;
        }
    }
    
    private boolean teleportToHQ(Player player) {
        if (!player.hasPermission("novateleport.guild.home")) {
            player.sendMessage(plugin.getLang().t("command.no_permission"));
            return true;
        }
        
        Location home = guildManager.getGuildHome(player);
        if (home == null) {
            player.sendMessage(plugin.getLang().t("guild.no_hq"));
            return true;
        }
        
        player.teleport(home);
        player.sendMessage(plugin.getLang().t("guild.teleported_to_hq"));
        return true;
    }
    
    private boolean setHQ(Player player) {
        if (!player.hasPermission("novateleport.guild.admin")) {
            player.sendMessage(plugin.getLang().t("command.no_permission"));
            return true;
        }
        
        if (!guildManager.isGuildAdmin(player)) {
            player.sendMessage(plugin.getLang().t("guild.not_admin"));
            return true;
        }
        
        boolean success = guildManager.setGuildHome(player, player.getLocation());
        if (success) {
            player.sendMessage(plugin.getLang().t("guild.headquarters_set"));
        } else {
            player.sendMessage(plugin.getLang().t("guild.failed_to_set_hq"));
        }
        return true;
    }
    
    private boolean listWarps(Player player) {
        List<GuildWarp> warps = warpManager.getPlayerGuildWarps(player);
        
        if (warps.isEmpty()) {
            player.sendMessage(plugin.getLang().t("guild.no_warps"));
            return true;
        }
        
        player.sendMessage(plugin.getLang().t("guild.warps_header"));
        for (GuildWarp warp : warps) {
            player.sendMessage(plugin.getLang().t("guild.warp_entry", 
                warp.getName(), 
                warp.getLocation().getWorld().getName(),
                warp.getLocation().getBlockX(),
                warp.getLocation().getBlockY(),
                warp.getLocation().getBlockZ()));
        }
        return true;
    }
    
    private boolean showGuildInfo(Player player) {
        String guildName = guildManager.getGuildName(player);
        if (guildName == null) {
            player.sendMessage(plugin.getLang().t("guild.no_guild"));
            return true;
        }
        
        List<java.util.UUID> members = guildManager.getGuildMembers(player);
        boolean isAdmin = guildManager.isGuildAdmin(player);
        
        player.sendMessage(plugin.getLang().t("guild.info_header", guildName));
        player.sendMessage(plugin.getLang().t("guild.info_members", members.size()));
        player.sendMessage(plugin.getLang().t("guild.info_role", isAdmin ? "管理员" : "成员"));
        
        return true;
    }
    
    private void sendHelp(Player player) {
        player.sendMessage("§6=== 工会传送帮助 ===");
        player.sendMessage("§e/gtp home §7- 传送到工会据点");
        player.sendMessage("§e/gtp sethome §7- 设置工会据点（管理员）");
        player.sendMessage("§e/gtp warp <名称> §7- 传送到工会传送点");
        player.sendMessage("§e/gtp setwarp <名称> §7- 创建工会传送点");
        player.sendMessage("§e/gtp delwarp <名称> §7- 删除工会传送点");
        player.sendMessage("§e/gtp list §7- 列出所有工会传送点");
        player.sendMessage("§e/gtp info §7- 查看工会信息");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.addAll(List.of("home", "sethome", "warp", "setwarp", "delwarp", "list", "info"));
            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 2 && (args[0].equalsIgnoreCase("warp") || args[0].equalsIgnoreCase("delwarp"))) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                return warpManager.getPlayerGuildWarps(player).stream()
                    .map(GuildWarp::getName)
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        return completions;
    }
}
