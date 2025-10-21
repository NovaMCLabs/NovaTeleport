package com.novamclabs.animations;

import com.novamclabs.StarTeleport;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AnimationCommand implements CommandExecutor {
    private final StarTeleport plugin;
    private final AnimationManager manager;

    public AnimationCommand(StarTeleport plugin, AnimationManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLang().t("common.only_player"));
            return true;
        }
        Player p = (Player) sender;
        if (args.length < 2 || !args[0].equalsIgnoreCase("select")) {
            p.sendMessage(plugin.getLang().t("usage.tpanimation"));
            return true;
        }
        String style = args[1].toLowerCase();
        AnimationManager.Style target = AnimationManager.Style.fromString(style, manager.getDefaultStyle());
        if (target == AnimationManager.Style.TECH && !p.hasPermission("novateleport.animation.tech")) {
            p.sendMessage(plugin.getLang().t("command.no_permission"));
            return true;
        }
        if (target == AnimationManager.Style.NATURAL && !p.hasPermission("novateleport.animation.natural")) {
            p.sendMessage(plugin.getLang().t("command.no_permission"));
            return true;
        }
        manager.setStyle(p, target);
        p.sendMessage("§a动画已设置为: §e" + target.key());
        return true;
    }
}
