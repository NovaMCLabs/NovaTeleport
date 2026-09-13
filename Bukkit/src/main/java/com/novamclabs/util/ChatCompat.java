package com.novamclabs.util;

import org.bukkit.entity.Player;

/**
 * 聊天组件兼容层。
 *
 * Bukkit/Spigot 没有原生的 Adventure（Paper 才有），跨平台实现可点击消息与 ActionBar
 * 只能走 Spigot 暴露的 {@code Player#spigot().sendMessage(BaseComponent...)}，
 * 它依赖 net.md-5:bungeecord-chat。
 *
 * 该库在 Paper 26.2 中已被标记 deprecated（仍随服务端提供），
 * 因此这里用反射做一次能力探测：不可用时自动降级为普通文本，避免直接抛 NoClassDefFoundError。
 */
public final class ChatCompat {

    private static final boolean AVAILABLE = detect();

    private ChatCompat() {
    }

    private static boolean detect() {
        try {
            Class.forName("net.md_5.bungee.api.chat.TextComponent");
            Class.forName("net.md_5.bungee.api.chat.ClickEvent");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** bungeecord-chat 是否可用（Paper/Spigot 26.x 上通常为 true）| whether clickable components are available */
    public static boolean available() {
        return AVAILABLE;
    }

    /**
     * 发送一条可点击执行命令的消息；组件库不可用时降级为普通消息。
     *
     * @param player  接收者
     * @param text    展示文本
     * @param command 点击执行的命令（不含斜杠也可）
     * @param color   颜色名（GREEN/RED/GOLD...），无法识别时使用默认
     */
    public static void sendRunCommand(Player player, String text, String command, String color) {
        if (!AVAILABLE) {
            player.sendMessage(text);
            return;
        }
        try {
            net.md_5.bungee.api.chat.TextComponent component = new net.md_5.bungee.api.chat.TextComponent(text);
            net.md_5.bungee.api.ChatColor c = resolveColor(color);
            if (c != null) component.setColor(c);
            component.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, command));
            player.spigot().sendMessage(component);
        } catch (Throwable t) {
            player.sendMessage(text);
        }
    }

    /**
     * 解析颜色名或十六进制颜色。
     *
     * 注意 {@code ChatColor.of(String)} 只接受 "#rrggbb" 形式的十六进制串，
     * 传 "GREEN" 会抛异常；命名颜色必须走枚举的 valueOf。
     */
    private static net.md_5.bungee.api.ChatColor resolveColor(String color) {
        if (color == null || color.isBlank()) return null;
        String trimmed = color.trim();
        if (trimmed.startsWith("#")) {
            try {
                return net.md_5.bungee.api.ChatColor.of(trimmed);
            } catch (Throwable t) {
                return null;
            }
        }
        try {
            return net.md_5.bungee.api.ChatColor.valueOf(trimmed.toUpperCase(java.util.Locale.ROOT));
        } catch (Throwable t) {
            return null;
        }
    }

    /** 依次发送「可点击的接受/拒绝」两个按钮 | send an accept/deny button pair */
    public static void sendAcceptDeny(Player player, String acceptText, String acceptCommand,
                                      String denyText, String denyCommand) {
        if (!AVAILABLE) {
            player.sendMessage(acceptText + " " + denyText);
            return;
        }
        try {
            net.md_5.bungee.api.chat.TextComponent yes = new net.md_5.bungee.api.chat.TextComponent(acceptText);
            yes.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            yes.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, acceptCommand));

            net.md_5.bungee.api.chat.TextComponent no = new net.md_5.bungee.api.chat.TextComponent(denyText);
            no.setColor(net.md_5.bungee.api.ChatColor.RED);
            no.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, denyCommand));

            net.md_5.bungee.api.chat.TextComponent spacer = new net.md_5.bungee.api.chat.TextComponent(" ");
            player.spigot().sendMessage(yes, spacer, no);
        } catch (Throwable t) {
            player.sendMessage(acceptText + " " + denyText);
        }
    }

    /** 发送 ActionBar 消息，组件库不可用时降级为聊天栏消息 | send an action bar message */
    public static void sendActionBar(Player player, String message) {
        if (!AVAILABLE) {
            player.sendMessage(message);
            return;
        }
        try {
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent(message));
        } catch (Throwable t) {
            player.sendMessage(message);
        }
    }
}
