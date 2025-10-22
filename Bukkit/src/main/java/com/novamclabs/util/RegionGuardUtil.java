package com.novamclabs.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

package com.novamclabs.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

package com.novamclabs.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * 区域进入检查（WorldGuard / PlotSquared）。
 * 若未安装相关插件，则默认放行。
 */
public class RegionGuardUtil {
    public static boolean canEnter(Player p, Location dest) {
        // PlotSquared: 若所在地块且玩家不在成员中，则拒绝
        try {
            if (Bukkit.getPluginManager().getPlugin("PlotSquared") != null) {
                com.plotsquared.core.PlotAPI api = new com.plotsquared.core.PlotAPI();
                Object plot = api.getPlot(dest);
                if (plot != null) {
                    // 运行时调用避免硬依赖 Plot 类符号
                    java.lang.reflect.Method isAdded = plot.getClass().getMethod("isAdded", java.util.UUID.class);
                    boolean added = (boolean) isAdded.invoke(plot, p.getUniqueId());
                    if (!added) return false;
                }
            }
        } catch (Throwable ignored) {}
        // WorldGuard: ENTRY flag 检查
        try {
            if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
                com.sk89q.worldguard.protection.regions.RegionContainer container = com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer();
                com.sk89q.worldguard.protection.regions.RegionQuery query = container.createQuery();
                com.sk89q.worldedit.util.Location weLoc = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(dest);
                com.sk89q.worldguard.LocalPlayer lp = com.sk89q.worldguard.bukkit.WorldGuardPlugin.inst().wrapPlayer(p);
                boolean allowed = query.testState(weLoc, lp, com.sk89q.worldguard.protection.flags.Flags.ENTRY);
                if (!allowed) return false;
            }
        } catch (Throwable ignored) {}
        return true;
    }
}
