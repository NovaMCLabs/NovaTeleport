package com.novamclabs.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * 领地/区域检查（Residence/PlotSquared 等）的反射适配器
 * Region guard checks via reflection (Residence/PlotSquared)
 */
public class RegionGuardUtil {
    public static boolean canEnter(Player p, Location dest) {
        boolean allowed = true;
        // Residence: Residence.getResidenceManager().getByLoc(loc) -> if null allow; else flags check
        try {
            if (Bukkit.getPluginManager().getPlugin("Residence") != null) {
                Class<?> resClz = Class.forName("com.bekvon.bukkit.residence.Residence");
                Object res = resClz.getMethod("getInstance").invoke(null);
                Object mgr = resClz.getMethod("getResidenceManager").invoke(res);
                Object area = mgr.getClass().getMethod("getByLoc", Location.class).invoke(mgr, dest);
                if (area != null) {
                    // simple: check enter flag for player name
                    Object perms = area.getClass().getMethod("getPermissions").invoke(area);
                    Boolean flag = (Boolean) perms.getClass().getMethod("playerHas", String.class, String.class).invoke(perms, p.getName(), "enter");
                    if (flag != null && !flag) return false;
                }
            }
        } catch (Throwable ignored) {}
        // PlotSquared: com.plotsquared.core.PlotAPI
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
        return allowed;
    }
}
