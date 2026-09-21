package com.novamclabs.util;

import org.bukkit.entity.Player;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

/**
 * 代理消息工具（Bungee/Velocity 兼容 Connect 指令）
 * Proxy messaging util for basic Connect
 */
public class ProxyMessenger {
    /**
     * 发送切服请求。返回 false 表示这条消息根本没发出去（玩家不在线 / 代理未连接 /
     * 通道未注册），调用方据此决定是否提示玩家。
     *
     * 注意：返回 true 只代表消息已交给代理，代理是否会真的切服无法从这里得知。
     * Returns false when the message could not be delivered at all; true only means it was handed
     * to the proxy, not that the switch will happen.
     */
    public static boolean connect(org.bukkit.plugin.java.JavaPlugin plugin, Player player, String server) {
        if (server == null || server.isEmpty()) {
            plugin.getLogger().warning("[Proxy] Refused to send " + (player == null ? "?" : player.getName())
                    + " to an empty server name.");
            return false;
        }
        if (player == null || !player.isOnline()) {
            plugin.getLogger().warning("[Proxy] Cannot send " + (player == null ? "an offline player" : player.getName())
                    + " to '" + server + "': the player is no longer online.");
            return false;
        }
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(server);
            player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
            return true;
        } catch (Throwable t) {
            // 不吞：通道未注册/代理不在线时玩家会一直留在本服，必须能排查
            plugin.getLogger().warning("[Proxy] Failed to send " + player.getName() + " to '" + server + "': "
                    + t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage()));
            return false;
        }
    }
}
