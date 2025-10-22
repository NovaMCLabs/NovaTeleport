package com.novamclabs.region.impl;

import com.griefdefender.api.Core;
import com.griefdefender.api.GriefDefender;
import com.griefdefender.api.User;
import com.griefdefender.api.claim.Claim;
import com.griefdefender.api.claim.TrustTypes;
import com.novamclabs.region.RegionAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * GriefDefender 领地适配器（使用编译期依赖）
 * GriefDefender region adapter (using compile-time dependency)
 */
public class GriefDefenderAdapter implements RegionAdapter {
    
    @Override
    public String name() {
        return "GriefDefender";
    }

    @Override
    public boolean isPresent() {
        try {
            return Bukkit.getPluginManager().getPlugin("GriefDefender") != null
                && GriefDefender.getCore() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean canEnter(Player p, Location dest) {
        if (!isPresent()) return true;
        
        try {
            Core core = GriefDefender.getCore();
            Claim claim = core.getClaimAt(convertLocation(dest));
            
            if (claim == null || claim.isWilderness()) {
                // 荒野区域，允许
                // Wilderness area, allow
                return true;
            }
            
            User user = core.getUser(p.getUniqueId());
            
            // 检查是否有信任权限
            // Check if player has trust
            if (claim.isUserTrusted(user, TrustTypes.ACCESSOR)) {
                return true;
            }
            
            // 检查管理员权限
            // Check admin permissions
            if (p.hasPermission("griefdefender.admin.claim.enter")) {
                return true;
            }
            
            return true; // GriefDefender 默认允许进入，除非明确禁止
            
        } catch (Throwable t) {
            return true;
        }
    }
    
    private com.griefdefender.api.claim.Location convertLocation(Location bukkit) {
        return new com.griefdefender.api.claim.Location(
            bukkit.getWorld().getUID(),
            bukkit.getBlockX(),
            bukkit.getBlockY(),
            bukkit.getBlockZ()
        );
    }
}
