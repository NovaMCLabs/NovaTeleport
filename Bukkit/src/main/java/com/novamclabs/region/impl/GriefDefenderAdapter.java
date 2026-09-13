package com.novamclabs.region.impl;

import com.griefdefender.api.Core;
import com.griefdefender.api.GriefDefender;
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
            Claim claim = core.getClaimAt(dest.getWorld().getUID(), dest.getBlockX(), dest.getBlockY(), dest.getBlockZ());

            if (claim == null || claim.isWilderness()) {
                return true;
            }

            // 管理员/可忽略该领地保护的玩家直接放行。
            // 注意 "griefdefender.admin.claim.enter" 并不是 GriefDefender 的权限节点，
            // 真实的判定入口是 User#getPlayerData()#canIgnoreClaim(Claim)。
            com.griefdefender.api.User user = core.getUser(p.getUniqueId());
            if (user != null && user.getPlayerData() != null
                    && user.getPlayerData().canIgnoreClaim(claim)) {
                return true;
            }

            // 只有被授予 ACCESSOR 及以上信任的玩家才能进入他人领地
            // （GD 另有更细的 enter-claim 标志，读取它需要走完整的权限解析 API）
            return claim.isUserTrusted(p.getUniqueId(), TrustTypes.ACCESSOR);

        } catch (Throwable t) {
            // 出错时默认允许，但要留下日志便于排查
            com.novamclabs.region.RegionAdapterManager.logOnce(name(), t);
            return true;
        }
    }
}
