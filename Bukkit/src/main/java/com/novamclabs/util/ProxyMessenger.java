package com.novamclabs.util;

import org.bukkit.entity.Player;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

/**
 * 代理消息工具（Bungee/Velocity 兼容 Connect 指令）
 * Proxy messaging util for basic Connect
 */
public class ProxyMessenger {
    public static void connect(org.bukkit.plugin.java.JavaPlugin plugin, Player player, String server) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(server);
            player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
        } catch (Exception ignored) {}
    }
}
