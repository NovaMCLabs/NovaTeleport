package com.massivecraft.factions;

import org.bukkit.Location;

import java.util.Set;

/**
 * Minimal API stub for Faction.
 *
 * 已按 SaberFactions 4.1.9-STABLE 的真实签名核对：getFPlayers() 返回 Set 而非 List，
 * 声明成 List 会在运行期抛 NoSuchMethodError（描述符不匹配）。
 */
public class Faction {
    public String getId() {
        return null;
    }

    public String getTag() {
        return null;
    }

    public Location getHome() {
        return null;
    }

    public void setHome(Location location) {
    }

    public Set<FPlayer> getFPlayers() {
        return java.util.Collections.emptySet();
    }
}
