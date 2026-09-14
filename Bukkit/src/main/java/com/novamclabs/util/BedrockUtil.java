package com.novamclabs.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

public class BedrockUtil {

    // 反射解析只做一次；本方法位于传送提示等热路径上 | resolve once, this is a hot path
    private static volatile boolean resolved = false;
    private static Object floodgateApi;
    private static Method isFloodgatePlayerMethod;

    // 未装 Floodgate、只装 Geyser-Spigot 时的探测路径 | Geyser-only detection
    private static volatile boolean geyserResolved = false;
    private static Method geyserApiMethod;
    private static Method geyserIsBedrockMethod;

    private static void resolve() {
        if (resolved) return;
        synchronized (BedrockUtil.class) {
            if (resolved) return;
            try {
                if (Bukkit.getPluginManager().getPlugin("floodgate") != null) {
                    Class<?> apiClz = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                    floodgateApi = apiClz.getMethod("getInstance").invoke(null);
                    isFloodgatePlayerMethod = apiClz.getMethod("isFloodgatePlayer", UUID.class);
                }
            } catch (Throwable ignored) {
                floodgateApi = null;
                isFloodgatePlayerMethod = null;
            }
            resolved = true;
        }
    }

    /**
     * 解析 Geyser API。只有 Geyser 与后端同 JVM（Geyser-Spigot）时这些类才存在；
     * Geyser 以独立代理方式运行时本服看不到它们，Class.forName 直接失败。
     * 不按插件名判断：Geyser-Spigot 的插件名是 "Geyser-Spigot" 而非 softdepend 里的 "Geyser"。
     */
    private static void resolveGeyser() {
        if (geyserResolved) return;
        synchronized (BedrockUtil.class) {
            if (geyserResolved) return;
            try {
                Class<?> apiClz = Class.forName("org.geysermc.geyser.api.GeyserApi");
                geyserApiMethod = apiClz.getMethod("api");
                geyserIsBedrockMethod = apiClz.getMethod("isBedrockPlayer", UUID.class);
            } catch (Throwable ignored) {
                geyserApiMethod = null;
                geyserIsBedrockMethod = null;
            }
            geyserResolved = true;
        }
    }

    /**
     * GeyserApi.api() 在 Geyser 注册完成前会抛异常而不是返回 null，且首次调用可能早于注册，
     * 因此这里不缓存结果，只缓存反射句柄。任何异常都视为“无法判定”。
     */
    private static boolean isGeyserBedrock(Player player) {
        try {
            resolveGeyser();
            Method apiMethod = geyserApiMethod;
            Method bedrockMethod = geyserIsBedrockMethod;
            if (apiMethod == null || bedrockMethod == null) return false;
            Object api = apiMethod.invoke(null);
            return api != null && (boolean) bedrockMethod.invoke(api, player.getUniqueId());
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 按名字找在线玩家，兼容 Floodgate 的基岩版名字前缀。
     *
     * Floodgate 默认给基岩玩家的名字加前缀 "."，于是 Bukkit.getPlayerExact("Steve") 找不到
     * ".Steve"，所有按名字查人的命令（/tpa、/forcetp …）对基岩玩家都会报“玩家不在线”。
     * 先按输入原样查，再补一次前缀查。
     */
    public static Player findPlayer(String name) {
        if (name == null || name.isEmpty()) return null;
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) return p;
        return Bukkit.getPlayerExact("." + name);
    }

    public static boolean isBedrock(Player player) {
        if (player == null) return false;
        try {
            resolve();
            Method m = isFloodgatePlayerMethod;
            if (m != null && floodgateApi != null) {
                return (boolean) m.invoke(floodgateApi, player.getUniqueId());
            }
        } catch (Throwable ignored) {
            // 绝不向外抛出：判定失败最多让基岩玩家用聊天菜单
        }
        if (isGeyserBedrock(player)) return true;
        // 兜底：Floodgate 的基岩 UUID 高 64 位恒为 0（低 64 位是 XUID），Java 玩家不可能命中
        return player.getUniqueId().getMostSignificantBits() == 0L;
    }
}
