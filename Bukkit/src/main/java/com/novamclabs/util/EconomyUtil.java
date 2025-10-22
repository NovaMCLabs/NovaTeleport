package com.novamclabs.util;

import com.novamclabs.StarTeleport;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

package com.novamclabs.util;

import com.novamclabs.StarTeleport;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Vault 经济工具（使用 compileOnly 依赖，去除反射）
 */
public class EconomyUtil {
    private static Economy economy;

    public static void setup(StarTeleport plugin) {
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
                economy = null;
                return;
            }
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
            }
        } catch (Throwable ignored) {
            economy = null;
        }
    }

    public static boolean hasProvider() {
        return economy != null;
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
            double bal = economy.getBalance((OfflinePlayer) player);
            if (bal < amount) return false;
            EconomyResponse resp = economy.withdrawPlayer((OfflinePlayer) player, amount);
            return resp != null && resp.transactionSuccess();
        } catch (Throwable t) {
            return false;
        }
    }
}
