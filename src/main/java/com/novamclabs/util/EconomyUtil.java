package com.novamclabs.util;

import com.novamclabs.StarTeleport;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Vault 经济工具（无编译期依赖，使用反射接入）
 */
public class EconomyUtil {
    private static Object econProvider;
    private static Class<?> economyClass;

    public static void setup(StarTeleport plugin) {
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
                econProvider = null;
                economyClass = null;
                return;
            }
            Class<?> ecoClz = Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings({"rawtypes", "unchecked"})
            RegisteredServiceProvider rsp = Bukkit.getServicesManager().getRegistration((Class) ecoClz);
            if (rsp != null) {
                econProvider = rsp.getProvider();
                economyClass = ecoClz;
            }
        } catch (Throwable ignored) {
            econProvider = null;
            economyClass = null;
        }
    }

    public static boolean hasProvider() {
        return econProvider != null && economyClass != null;
    }

    public static boolean isEnabled(StarTeleport plugin) {
        return plugin.getConfig().getBoolean("economy.enabled", false);
    }

    public static String getBypassPermission(StarTeleport plugin) {
        return plugin.getConfig().getString("economy.bypass_permission", "novateleport.economy.bypass");
    }

    public static double getCost(StarTeleport plugin, String actionKey) {
        return plugin.getConfig().getDouble("economy.costs." + actionKey, 0.0);
    }

    public static String format(double amount) {
        return String.format("%.2f", amount);
    }

    /**
     * 扣费，如果未启用经济或未安装Vault，则直接返回true。
     */
    public static boolean charge(StarTeleport plugin, Player player, double amount) {
        if (!isEnabled(plugin)) return true;
        if (amount <= 0) return true;
        if (player.hasPermission(getBypassPermission(plugin))) return true;
        if (!hasProvider()) return true; // 没有经济提供者则跳过扣费
        try {
            // 余额
            double bal = (double) economyClass.getMethod("getBalance", OfflinePlayer.class)
                    .invoke(econProvider, (OfflinePlayer) player);
            if (bal < amount) return false;
            Object resp = economyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class)
                    .invoke(econProvider, (OfflinePlayer) player, amount);
            // 检查交易是否成功
            return (boolean) resp.getClass().getMethod("transactionSuccess").invoke(resp);
        } catch (Throwable t) {
            return false;
        }
    }
}
