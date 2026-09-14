package com.novamclabs.util;

import com.novamclabs.StarTeleport;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Vault 经济工具（使用编译期依赖，不再使用反射）
 * Vault economy utility (using compile-time dependency instead of reflection)
 */
public class EconomyUtil {
    private static Economy econProvider;

    public static void setup(StarTeleport plugin) {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("[Economy] Vault not found, economy features disabled.");
            econProvider = null;
            return;
        }
        
        try {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                econProvider = rsp.getProvider();
                plugin.getLogger().info("[Economy] Vault economy provider registered: " + econProvider.getName());
            } else {
                plugin.getLogger().warning("[Economy] Vault found but no economy provider available.");
                econProvider = null;
            }
        } catch (NoClassDefFoundError e) {
            // Vault API not available at runtime
            plugin.getLogger().warning("[Economy] Vault API classes not available at runtime. Make sure Vault is installed on the server.");
            econProvider = null;
        } catch (Throwable t) {
            plugin.getLogger().severe("[Economy] Error setting up Vault economy: " + t.getMessage());
            t.printStackTrace();
            econProvider = null;
        }
    }

    public static boolean hasProvider() {
        return econProvider != null;
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
        if (hasProvider()) {
            return econProvider.format(amount);
        }
        return String.format("%.2f", amount);
    }

    /**
     * 扣费可行性。{@link #charge} 把「余额不足」和「经济不可用」都归结为一次失败/放行，
     * 无法支撑「先校验全部、再统一扣减」的收费流程——调用方需要知道到底差在哪，
     * 才能给出正确的提示、并在校验阶段就中止而不产生部分扣减。
     */
    public enum MoneyStatus {
        /** 本次不涉及金钱（金额为 0、经济未启用、或有 bypass 权限） */
        NOT_APPLICABLE,
        /** 可以扣费 */
        OK,
        /** 玩家余额不足 */
        INSUFFICIENT_FUNDS,
        /** 配置了收费但经济系统用不了（未启用或无提供者） */
        UNAVAILABLE
    }

    /**
     * 只查询、不扣费。语义与 {@link #charge} 保持一致：
     * 金额为 0 / 经济未启用 / 玩家有 bypass 权限时视为「不涉及金钱」（放行）。
     */
    public static MoneyStatus canCharge(StarTeleport plugin, Player player, double amount) {
        if (amount <= 0) return MoneyStatus.NOT_APPLICABLE;
        if (!isEnabled(plugin)) return MoneyStatus.NOT_APPLICABLE;
        if (player.hasPermission(getBypassPermission(plugin))) return MoneyStatus.NOT_APPLICABLE;
        if (!hasProvider()) return MoneyStatus.UNAVAILABLE;

        try {
            return econProvider.getBalance(player) < amount
                    ? MoneyStatus.INSUFFICIENT_FUNDS
                    : MoneyStatus.OK;
        } catch (Throwable t) {
            plugin.getLogger().warning("[Economy] Error checking balance of " + player.getName() + ": " + t.getMessage());
            return MoneyStatus.UNAVAILABLE;
        }
    }

    public static double getBalance(Player player) {
        if (!hasProvider()) return 0.0;
        try {
            return econProvider.getBalance(player);
        } catch (Throwable t) {
            return 0.0;
        }
    }

    /**
     * 扣费，如果未启用经济或未安装Vault，则直接返回true。
     * Charge money. Returns true if economy is disabled or Vault is not available.
     */
    public static boolean charge(StarTeleport plugin, Player player, double amount) {
        if (!isEnabled(plugin)) return true;
        if (amount <= 0) return true;
        if (player.hasPermission(getBypassPermission(plugin))) return true;
        if (!hasProvider()) return true; // 没有经济提供者则跳过扣费
        
        try {
            double balance = econProvider.getBalance(player);
            if (balance < amount) {
                return false;
            }
            EconomyResponse response = econProvider.withdrawPlayer(player, amount);
            return response.transactionSuccess();
        } catch (Throwable t) {
            plugin.getLogger().warning("[Economy] Error charging player " + player.getName() + ": " + t.getMessage());
            return false;
        }
    }

    /**
     * 存款/给予金钱（支持离线玩家）
     * Deposit money to a player account (offline players supported)
     */
    public static boolean deposit(StarTeleport plugin, OfflinePlayer player, double amount) {
        if (!isEnabled(plugin)) return true;
        if (amount <= 0) return true;
        if (!hasProvider()) return true;

        try {
            EconomyResponse response = econProvider.depositPlayer(player, amount);
            return response.transactionSuccess();
        } catch (Throwable t) {
            plugin.getLogger().warning("[Economy] Error depositing to " + player.getName() + ": " + t.getMessage());
            return false;
        }
    }

    /**
     * 转账：从一个玩家转给另一个玩家
     * Transfer money from one player to another
     */
    public static boolean transfer(StarTeleport plugin, Player from, OfflinePlayer to, double amount) {
        if (!isEnabled(plugin)) return true;
        if (amount <= 0) return true;
        if (!hasProvider()) return true;
        
        try {
            // 先扣款
            double balance = econProvider.getBalance(from);
            if (balance < amount) {
                return false;
            }
            EconomyResponse withdraw = econProvider.withdrawPlayer(from, amount);
            if (!withdraw.transactionSuccess()) {
                return false;
            }
            
            // 再存款
            EconomyResponse deposit = econProvider.depositPlayer(to, amount);
            if (!deposit.transactionSuccess()) {
                // 回滚
                econProvider.depositPlayer(from, amount);
                return false;
            }
            
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("[Economy] Error transferring money: " + t.getMessage());
            return false;
        }
    }
}
