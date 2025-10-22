package com.novamclabs.region.impl;

import com.novamclabs.region.RegionAdapter;
import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.land.Area;
import me.angeschossen.lands.api.role.enums.RoleSetting;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Lands 领地适配器（使用编译期依赖）
 * Lands region adapter (using compile-time dependency)
 */
public class LandsAdapter implements RegionAdapter {
    private LandsIntegration landsIntegration;
    
    @Override
    public String name() {
        return "Lands";
    }

    @Override
    public boolean isPresent() {
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("Lands");
            if (plugin != null && landsIntegration == null) {
                landsIntegration = LandsIntegration.of(plugin);
            }
            return plugin != null && landsIntegration != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean canEnter(Player p, Location dest) {
        if (!isPresent()) return true;
        
        try {
            Area area = landsIntegration.getArea(dest);
            
            if (area == null) {
                // 不在领地内，允许
                // Not in a land area, allow
                return true;
            }
            
            // 检查玩家是否有进入权限
            // Check if player has enter permission
            return area.hasRoleFlag(p.getUniqueId(), RoleSetting.LAND_ENTER);
            
        } catch (Throwable t) {
            return true;
        }
    }
}
