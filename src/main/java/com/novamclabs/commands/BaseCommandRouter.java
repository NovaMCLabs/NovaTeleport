package com.novamclabs.commands;

import com.novamclabs.StarTeleport;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 主命令路由器（/novateleport | /ntp | /novatp）
 * Base command router
 */
public class BaseCommandRouter implements CommandExecutor, TabCompleter {
    private final StarTeleport plugin;
    private final TeleportCommandHandler legacy;

    public BaseCommandRouter(StarTeleport plugin, TeleportCommandHandler legacy) {
        this.plugin = plugin;
        this.legacy = legacy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.getLang().t("base.help.header"));
            return true;
        }
        String sub = args[0].toLowerCase();
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        // 直接路由到已有命令 | Route to existing commands
        switch (sub) {
            case "help":
                sender.sendMessage(plugin.getLang().t("base.help.header"));
                return true;
            case "debug":
                if (!sender.hasPermission("novateleport.admin")) { sender.sendMessage(plugin.getLang().t("command.no_permission")); return true; }
                if (rest.length < 1) { sender.sendMessage(plugin.getLang().tr("debug.state", "state", plugin.getConfig().getBoolean("general.debug", false)?plugin.getLang().t("debug.on"):plugin.getLang().t("debug.off"))); return true; }
                boolean on = rest[0].equalsIgnoreCase("on") || rest[0].equalsIgnoreCase("true");
                plugin.getConfig().set("general.debug", on);
                plugin.saveConfig();
                plugin.setDebug(on);
                sender.sendMessage(on?plugin.getLang().t("debug.on"):plugin.getLang().t("debug.off"));
                return true;
            case "tpa": return route(sender, "tpa", rest);
            case "tpahere": return route(sender, "tpahere", rest);
            case "tpaccept": return route(sender, "tpaccept", rest);
            case "tpdeny": return route(sender, "tpdeny", rest);
            case "tpcancel": return route(sender, "tpcancel", rest);
            case "sethome": return route(sender, "sethome", rest);
            case "home": return route(sender, "home", rest);
            case "delhome": return route(sender, "delhome", rest);
            case "homes": return route(sender, "homes", rest);
            case "setwarp": return route(sender, "setwarp", rest);
            case "warp": return route(sender, "warp", rest);
            case "delwarp": return route(sender, "delwarp", rest);
            case "warps": return route(sender, "warps", rest);
            case "spawn": return route(sender, "spawn", rest);
            case "back": return route(sender, "back", rest);
            case "rtp": return route(sender, "rtp", rest);
            case "rtpgui": return route(sender, "rtpgui", rest);
            case "tpmenu": return route(sender, "tpmenu", rest);
            case "city": return route(sender, "city", rest);
            case "tpanimation": return route(sender, "tpanimation", rest);
            case "scroll": return route(sender, "scroll", rest);
            case "party": return route(sender, "party", rest);
            case "stele": return route(sender, "stele", rest);
            case "deathback": return route(sender, "deathback", rest);
            case "forcetp": return route(sender, "forcetp", rest);
            default:
                sender.sendMessage(plugin.getLang().t("base.unknown"));
                return true;
        }
    }

    private boolean route(CommandSender sender, String cmd, String[] args) {
        PluginCommand c = plugin.getCommand(cmd);
        if (c == null) return false;
        return c.execute(sender, cmd, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> root = Arrays.asList("tpa","tpahere","tpaccept","tpdeny","tpcancel","sethome","home","delhome","homes","setwarp","warp","delwarp","warps","spawn","back","rtp","rtpgui","tpmenu","city","tpanimation","scroll","party","stele","deathback","forcetp");
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String s : root) if (s.startsWith(args[0].toLowerCase())) out.add(s);
            return out;
        }
        // 暂不提供更深层补全（可在未来扩展）| Deep tab-completion can be added in future
        return new ArrayList<>();
    }
}
