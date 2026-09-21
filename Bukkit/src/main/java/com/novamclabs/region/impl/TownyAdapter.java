package com.novamclabs.region.impl;

import com.novamclabs.region.RegionAdapter;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownyPermission;
import com.palmergames.bukkit.towny.utils.PlayerCacheUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Towny 领地适配器（使用编译期依赖）
 * Towny region adapter (using compile-time dependency)
 */
public class TownyAdapter implements RegionAdapter {

    /**
     * 地块上任意一项操作被允许，即视为可进入。
     * Towny 没有独立的 enter 权限，只测 BUILD 会把「默认允许切换/使用但禁止破坏」的地块整体挡掉。
     */
    private static final TownyPermission.ActionType[] ENTER_ACTIONS = {
            TownyPermission.ActionType.BUILD,
            TownyPermission.ActionType.DESTROY,
            TownyPermission.ActionType.SWITCH,
            TownyPermission.ActionType.ITEM_USE,
    };

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
            Town playerTown = resident != null && resident.hasTown() ? resident.getTownOrNull() : null;

            if (playerTown != null) {
                // 自己的城镇
                if (playerTown.equals(town)) return true;

                // 同国：只有两国都存在且相等才放行
                Nation destNation = town.getNationOrNull();
                Nation playerNation = playerTown.getNationOrNull();
                if (destNation != null && destNation.equals(playerNation)) return true;

                // 盟友城镇
                if (town.hasAlly(playerTown)) return true;
            }

            // isPublic() 表示「城镇对外开放（spawn 对外开放）」，不是「地块可进入」，
            // 但城镇自报公开时不应再按私有地块拦截
            if (town.isPublic()) return true;

            // 私有地块：四个 ActionType 全部为拒才拒绝，任一通过即视为可进入
            Material type = dest.getBlock().getType();
            for (TownyPermission.ActionType action : ENTER_ACTIONS) {
                if (PlayerCacheUtil.getCachePermission(p, dest, type, action)) return true;
            }
            return false;

        } catch (Throwable t) {
            com.novamclabs.region.RegionAdapterManager.logOnce(name(), t);
            return true;
        }
    }
}
