package com.novamclabs.guild;

import com.novamclabs.StarTeleport;
import com.novamclabs.util.BedrockFormsUtil;
import com.novamclabs.util.BedrockUtil;
import com.novamclabs.util.TeleportUtil;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

        String subCmd = args[0].toLowerCase(Locale.ROOT);

        switch (subCmd) {
            case "home":
            case "hq":
                return teleportToHQ(player);

            case "sethome":
            case "sethq":
                return setHQ(player);

            case "warp":
                if (!player.hasPermission("novateleport.guild.warp")) {
                    player.sendMessage(plugin.getLang().t("command.no_permission"));
                    return true;
                }
                if (args.length < 2) {
                    // 基岩版没有命令补全，用表单列出工会传送点；Java 版保持原提示
                    if (BedrockUtil.isBedrock(player)) {
                        List<GuildWarp> warps = warpManager.getPlayerGuildWarps(player);
                        if (warps.isEmpty()) {
                            player.sendMessage(plugin.getLang().t("guild.no_warps"));
                            return true;
                        }
                        List<String> names = warps.stream().map(GuildWarp::getName).collect(Collectors.toList());
                        boolean ok = BedrockFormsUtil.showListCommandForm(plugin, player,
                            plugin.getLang().t("guild.menu.title"), names, names, "gtp warp");
                        if (ok) return true;
                        return listWarps(player);
                    }
                    player.sendMessage(plugin.getLang().t("guild.usage_warp"));
                    return true;
                }
                return warpManager.teleportToWarp(player, args[1]);

            case "setwarp":
                if (!player.hasPermission("novateleport.guild.admin")) {
                    player.sendMessage(plugin.getLang().t("command.no_permission"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(plugin.getLang().t("guild.usage_setwarp"));
                    return true;
                }
                return warpManager.createWarp(player, args[1]);

            case "delwarp":
                if (!player.hasPermission("novateleport.guild.admin")) {
                    player.sendMessage(plugin.getLang().t("command.no_permission"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(plugin.getLang().t("guild.usage_delwarp"));
                    return true;
                }
                return warpManager.deleteWarp(player, args[1]);

            case "list":
            case "warps":
                if (!player.hasPermission("novateleport.guild.use")) {
                    player.sendMessage(plugin.getLang().t("command.no_permission"));
                    return true;
                }
                return listWarps(player);

            case "info":
                if (!player.hasPermission("novateleport.guild.use")) {
                    player.sendMessage(plugin.getLang().t("command.no_permission"));
                    return true;
                }
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

        FileConfiguration cfg = guildManager.getConfig();
        if (!cfg.getBoolean("headquarters.enabled", true)) {
            player.sendMessage(plugin.getLang().t("guild.not_enabled"));
            return true;
        }

        Location home = guildManager.getGuildHome(player);
        if (home == null) {
            player.sendMessage(plugin.getLang().t("guild.no_hq"));
            return true;
        }

        int delay = cfg.getInt("headquarters.delay", 5);

        // 费用在传送真正执行时扣除，价格延后读取以支持配置热重载与全局默认值层
        TeleportUtil.Payment payment = com.novamclabs.util.CostModel.asPayment(plugin,
                () -> com.novamclabs.util.CostModel.Spec.builder()
                        .money(com.novamclabs.util.CostModel.resolveMoney(plugin, cfg, "guild", "headquarters.cost"))
                        .build());

        TeleportUtil.delayedTeleportWithAnimation(plugin, player, home, delay, "guild", payment, () ->
            player.sendMessage(plugin.getLang().t("guild.teleported_to_hq")));

        return true;
    }

    private boolean setHQ(Player player) {
        if (!player.hasPermission("novateleport.guild.admin")) {
            player.sendMessage(plugin.getLang().t("command.no_permission"));
            return true;
        }

        FileConfiguration cfg = guildManager.getConfig();
        if (!cfg.getBoolean("headquarters.enabled", true)) {
            player.sendMessage(plugin.getLang().t("guild.not_enabled"));
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
            player.sendMessage(plugin.getLang().tr(
                "guild.warp_entry",
                "name", warp.getName(),
                "world", warp.getLocation().getWorld().getName(),
                "x", warp.getLocation().getBlockX(),
                "y", warp.getLocation().getBlockY(),
                "z", warp.getLocation().getBlockZ()
            ));
        }
        return true;
    }

    private boolean showGuildInfo(Player player) {
        String guildName = guildManager.getGuildName(player);
        if (guildName == null) {
            player.sendMessage(plugin.getLang().t("guild.no_guild"));
            return true;
        }

        int members = guildManager.getGuildMembers(player).size();
        boolean isAdmin = guildManager.isGuildAdmin(player);

        player.sendMessage(plugin.getLang().tr("guild.info_header", "guild", guildName));
        player.sendMessage(plugin.getLang().tr("guild.info_members", "count", members));
        player.sendMessage(plugin.getLang().tr("guild.info_role", "role", isAdmin ? "Admin" : "Member"));

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(plugin.getLang().t("guild.help.header"));
        player.sendMessage(plugin.getLang().t("guild.help.home"));
        player.sendMessage(plugin.getLang().t("guild.help.sethome"));
        player.sendMessage(plugin.getLang().t("guild.help.warp"));
        player.sendMessage(plugin.getLang().t("guild.help.setwarp"));
        player.sendMessage(plugin.getLang().t("guild.help.delwarp"));
        player.sendMessage(plugin.getLang().t("guild.help.list"));
        player.sendMessage(plugin.getLang().t("guild.help.info"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(List.of("home", "sethome", "warp", "setwarp", "delwarp", "list", "info"));
            return completions.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("warp") || args[0].equalsIgnoreCase("delwarp"))) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                return warpManager.getPlayerGuildWarps(player).stream()
                    .map(GuildWarp::getName)
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
            }
        }

        return completions;
    }
}
