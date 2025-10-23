package com.novamclabs.region.impl;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import com.bekvon.bukkit.residence.protection.ResidencePermissions;
import com.novamclabs.region.RegionAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Residence 领地适配器（使用编译期依赖，替代反射）
 * Residence region adapter (using compile-time dependency instead of reflection)
 */
public class ResidenceAdapter implements RegionAdapter {
    
    @Override
    public String name() {
        return "Residence";
    }

    @Override
    public boolean isPresent() {
        try {
            return Bukkit.getPluginManager().getPlugin("Residence") != null
                && Residence.getInstance() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean canEnter(Player p, Location dest) {
        if (!isPresent()) return true;
        
        try {
            ClaimedResidence residence = Residence.getInstance()
                .getResidenceManager()
                .getByLoc(dest);
            
            if (residence == null) {
                // 不在领地内，允许
                // Not in a residence, allow
                return true;
            }
            
            ResidencePermissions perms = residence.getPermissions();
            
            // 检查玩家是否有进入权限
            // Check if player has enter permission
            Boolean hasEnter = perms.playerHas(p.getName(), "enter", true);
            if (hasEnter != null && !hasEnter) {
                return false;
            }
            
            // 检查传送权限（如果存在）
            // Check teleport permission if exists
            Boolean hasTp = perms.playerHas(p.getName(), "tp", true);
            if (hasTp != null && !hasTp) {
                return false;
            }
            
            return true;
            
        } catch (Throwable t) {
            return true;
        }
    }
}
