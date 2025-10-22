package com.novamclabs.region.impl;

import com.novamclabs.region.RegionAdapter;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * WorldGuard 7.x 领地适配器（使用编译期依赖）
 * WorldGuard 7.x region adapter (using compile-time dependency)
 */
public class WorldGuardAdapter implements RegionAdapter {
    
    @Override
    public String name() {
        return "WorldGuard";
    }

    @Override
    public boolean isPresent() {
        try {
            return Bukkit.getPluginManager().getPlugin("WorldGuard") != null
                && WorldGuard.getInstance() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean canEnter(Player p, Location dest) {
        if (!isPresent()) return true;
        
        try {
            LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(p);
            com.sk89q.worldedit.util.Location weLoc = BukkitAdapter.adapt(dest);
            
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            
            // 检查 ENTRY 和 BUILD 标志
            // Check ENTRY and BUILD flags
            if (!query.testState(weLoc, localPlayer, Flags.ENTRY)) {
                return false;
            }
            
            // 可选：也检查 BUILD 标志
            // Optional: also check BUILD flag
            if (!query.testState(weLoc, localPlayer, Flags.BUILD)) {
                return false;
            }
            
            return true;
        } catch (Throwable t) {
            // 出错时默认允许
            // Allow by default on error
            return true;
        }
    }
}
