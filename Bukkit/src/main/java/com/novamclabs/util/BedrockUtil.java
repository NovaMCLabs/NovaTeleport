package com.novamclabs.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

package com.novamclabs.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.UUID;

/**
 * 基于 Floodgate API 的基岩版检测（compileOnly 依赖）
 */
public class BedrockUtil {
    public static boolean isBedrock(Player player) {
        try {
            if (Bukkit.getPluginManager().getPlugin("floodgate") == null) return false;
            // 仅在安装了 Floodgate 时才引用 API，避免运行时 NoClassDefFoundError
            return floodgateCheck(player.getUniqueId());
        } catch (Throwable ignored) {
            return false;
        }
    }

    // 将直接 API 调用放在独立方法，确保在未安装 Floodgate 时不会被触发类加载
    private static boolean floodgateCheck(UUID uuid) {
        return org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(uuid);
    }
}
