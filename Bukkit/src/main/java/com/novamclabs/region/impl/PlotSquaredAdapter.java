package com.novamclabs.region.impl;

import com.novamclabs.region.RegionAdapter;
import com.plotsquared.bukkit.util.BukkitUtil;
import com.plotsquared.core.PlotSquared;
import com.plotsquared.core.player.PlotPlayer;
import com.plotsquared.core.plot.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * PlotSquared 领地适配器（使用编译期依赖）
 * PlotSquared region adapter (using compile-time dependency)
 */
public class PlotSquaredAdapter implements RegionAdapter {
    
    @Override
    public String name() {
        return "PlotSquared";
    }

    @Override
    public boolean isPresent() {
        try {
            return Bukkit.getPluginManager().getPlugin("PlotSquared") != null
                && PlotSquared.get() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean canEnter(Player p, Location dest) {
        if (!isPresent()) return true;
        
        try {
            com.plotsquared.core.location.Location psLoc = BukkitUtil.adapt(dest);
            Plot plot = psLoc.getPlot();
            
            if (plot == null) {
                // 不在地皮内，允许
                // Not in a plot, allow
                return true;
            }
            
            PlotPlayer<?> plotPlayer = BukkitUtil.adapt(p);
            
            // 检查是否是地皮成员或拥有者
            // Check if player is member or owner of the plot
            if (plot.isAdded(plotPlayer.getUUID())) {
                return true;
            }
            
            return com.plotsquared.core.plot.flag.implementations.DenyTeleportFlag.allowsTeleport(plotPlayer, plot);

        } catch (Throwable t) {
            return true;
        }
    }
}
