package com.novamclabs.region.impl;

import com.novamclabs.region.RegionAdapter;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownyPermission;
import com.palmergames.bukkit.towny.utils.PlayerCacheUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Towny 领地适配器（使用编译期依赖）
 * Towny region adapter (using compile-time dependency)
 */
public class TownyAdapter implements RegionAdapter {
    
    @Override
    public String name() {
        return "Towny";
    }

    @Override
    public boolean isPresent() {
        try {
            return Bukkit.getPluginManager().getPlugin("Towny") != null
                && TownyAPI.getInstance() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean canEnter(Player p, Location dest) {
        if (!isPresent()) return true;
        
        try {
            TownyAPI api = TownyAPI.getInstance();
            TownBlock townBlock = api.getTownBlock(dest);
            
            if (townBlock == null) {
                // 荒野区域，允许
                // Wilderness area, allow
                return true;
            }
            
            Town town = townBlock.getTownOrNull();
            if (town == null) {
                return true;
            }
            
            // 检查玩家是否是城镇成员
            // Check if player is a town member
            if (api.getResident(p) != null && api.getResident(p).hasTown()) {
                Town playerTown = api.getResident(p).getTownOrNull();
                if (playerTown != null && playerTown.equals(town)) {
                    return true;
                }
            }
            
            // 检查是否允许外来者进入
            // Check if outsiders can enter
            TownyPermission.ActionType action = TownyPermission.ActionType.BUILD;
            return PlayerCacheUtil.getCachePermission(p, dest, dest.getBlock().getType(), action);
            
        } catch (Throwable t) {
            return true;
        }
    }
}
