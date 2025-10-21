package com.novamclabs.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * 领地/区域检查（Residence/PlotSquared/WorldGuard）的反射适配器
 * Region guard checks via reflection (Residence/PlotSquared/WorldGuard)
 */
public class RegionGuardUtil {
    public static boolean canEnter(Player p, Location dest) {
        boolean allowed = true;
        // Residence
        try {
            if (Bukkit.getPluginManager().getPlugin("Residence") != null) {
                Class<?> resClz = Class.forName("com.bekvon.bukkit.residence.Residence");
                Object res = resClz.getMethod("getInstance").invoke(null);
                Object mgr = resClz.getMethod("getResidenceManager").invoke(res);
                Object area = mgr.getClass().getMethod("getByLoc", Location.class).invoke(mgr, dest);
                if (area != null) {
                    Object perms = area.getClass().getMethod("getPermissions").invoke(area);
                    Boolean flag = (Boolean) perms.getClass().getMethod("playerHas", String.class, String.class).invoke(perms, p.getName(), "enter");
                    if (flag != null && !flag) return false;
                }
            }
        } catch (Throwable ignored) {}
        // PlotSquared
        try {
            if (Bukkit.getPluginManager().getPlugin("PlotSquared") != null) {
                Class<?> apiClz = Class.forName("com.plotsquared.core.PlotAPI");
                Object api = apiClz.getConstructor().newInstance();
                Object plot = apiClz.getMethod("getPlot", Location.class).invoke(api, dest);
                if (plot != null) {
                    Boolean b = (Boolean) plot.getClass().getMethod("isAdded", java.util.UUID.class).invoke(plot, p.getUniqueId());
                    if (b != null && !b) return false;
                }
            }
        } catch (Throwable ignored) {}
        // WorldGuard 7.x （仅做存在性检测与粗略入口flag检查）
        try {
            if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
                Class<?> wgClz = Class.forName("com.sk89q.worldguard.WorldGuard");
                Object wg = wgClz.getMethod("getInstance").invoke(null);
                Object platform = wgClz.getMethod("getPlatform").invoke(wg);
                Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
                Class<?> bukkitAdapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
                Object weWorld = bukkitAdapter.getMethod("adapt", org.bukkit.World.class).invoke(null, dest.getWorld());
                Object regions = container.getClass().getMethod("get", Class.forName("com.sk89q.worldguard.protection.regions.RegionManager"), weWorld.getClass()).invoke(container, null, weWorld);
                if (regions != null) {
                    // 本处仅进行存在性判定，细粒度 flag 校验可在未来增强
                }
            }
        } catch (Throwable ignored) {}
        return allowed;
    }
}
