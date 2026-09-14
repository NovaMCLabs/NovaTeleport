package com.novamclabs.animations;

import com.novamclabs.StarTeleport;
import com.novamclabs.util.BedrockFormsUtil;
import com.novamclabs.util.BedrockUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

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

        // 无参数 / select 无参数：基岩版玩家没有键盘，走表单选择；表单发不出去再退回聊天提示
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("select"))) {
            if (BedrockUtil.isBedrock(p) && showStyleForm(p)) {
                return true;
            }
            p.sendMessage(plugin.getLang().t("usage.tpanimation"));
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("select")) {
            p.sendMessage(plugin.getLang().t("usage.tpanimation"));
            return true;
        }
        String style = args[1].toLowerCase();
        AnimationManager.Style target = AnimationManager.Style.fromString(style, null);
        if (target == null) {
            p.sendMessage(plugin.getLang().tr("animation.invalid_style", "style", args[1]));
            return true;
        }
        if (!hasStylePermission(p, target)) {
            p.sendMessage(plugin.getLang().t("command.no_permission"));
            return true;
        }
        manager.setStyle(p, target);
        p.sendMessage(plugin.getLang().tr("animation.selected", "style", target.key()));
        return true;
    }

    /**
     * 风格选择表单。按钮文字与命令参数分开传：本地化后的按钮文字会被当成子命令执行。
     * 返回 false 表示表单没送出去（无 Floodgate / 反射失败），调用方退回聊天提示。
     */
    private boolean showStyleForm(Player p) {
        List<String> labels = new ArrayList<>();
        List<String> styleArgs = new ArrayList<>();
        for (AnimationManager.Style s : AnimationManager.Style.values()) {
            if (!hasStylePermission(p, s)) continue;
            labels.add(plugin.getLang().t("animation.style." + s.key()));
            styleArgs.add(s.key());
        }
        if (labels.isEmpty()) return false;
        return BedrockFormsUtil.showListCommandForm(plugin, p, plugin.getLang().t("animation.select.title"),
                labels, styleArgs, "tpanimation select");
    }

    private boolean hasStylePermission(Player p, AnimationManager.Style style) {
        switch (style) {
            case TECH:
                return p.hasPermission("novateleport.animation.tech");
            case NATURAL:
                return p.hasPermission("novateleport.animation.natural");
            default:
                return true;
        }
    }
}
