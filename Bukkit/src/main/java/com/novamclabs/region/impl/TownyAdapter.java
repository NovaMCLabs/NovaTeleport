package com.novamclabs.region.impl;

import com.novamclabs.region.RegionAdapter;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
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

            Resident resident = api.getResident(p);
            if (resident != null && resident.hasTown()) {
                Town playerTown = resident.getTownOrNull();
                if (playerTown != null && playerTown.equals(town)) {
                    return true;
                }
            }

            // 公共城镇任何人可进入
            if (town.isPublic()) return true;

            // 私有城镇：只有在地块上具备建筑权限的玩家才允许传送进入。
            // （Towny 没有独立的 "enter" 权限，BUILD 是社区惯例的等价判断，
            //   因此不能对所有城镇一律套用，否则公共区域会被整体挡掉。）
            TownyPermission.ActionType action = TownyPermission.ActionType.BUILD;
            return PlayerCacheUtil.getCachePermission(p, dest, dest.getBlock().getType(), action);

        } catch (Throwable t) {
            com.novamclabs.region.RegionAdapterManager.logOnce(name(), t);
            return true;
        }
    }
}
