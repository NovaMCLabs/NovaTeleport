package com.novamclabs.towny;

import com.novamclabs.StarTeleport;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Towny 城镇传送管理器
 * Towny town teleport manager
 */
public class TownyTeleportManager {
    private final StarTeleport plugin;
    private boolean enabled;
    
    public TownyTeleportManager(StarTeleport plugin) {
        this.plugin = plugin;
        reload();
    }
    
    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("towny.enabled", false);
    }
    
    public boolean isEnabled() {
        return enabled && Bukkit.getPluginManager().getPlugin("Towny") != null;
    }
    
    /**
     * 传送玩家到其所属城镇的spawn点
     * Teleport player to their town spawn
     */
    public boolean teleportToTownSpawn(Player player) {
        if (!isEnabled()) {
            return false;
        }
        
        try {
            TownyAPI api = TownyAPI.getInstance();
            Resident resident = api.getResident(player);
            
            if (resident == null || !resident.hasTown()) {
                player.sendMessage(plugin.getLang().t("towny.no_town"));
                return false;
            }
            
            Town town = resident.getTownOrNull();
            if (town == null) {
                return false;
            }
            
            Location spawnLoc = town.getSpawnOrNull();
            if (spawnLoc == null) {
                player.sendMessage(plugin.getLang().t("towny.no_spawn"));
                return false;
            }
            
            // 使用插件的传送系统
            // Use plugin's teleport system
            player.teleport(spawnLoc);
            player.sendMessage(plugin.getLang().t("towny.teleported_to_town", town.getName()));
            return true;
            
        } catch (Throwable t) {
            plugin.getLogger().warning("[Towny] Error teleporting player: " + t.getMessage());
            return false;
        }
    }
    
    /**
     * 传送玩家到指定城镇（如果有权限）
     * Teleport player to specified town (if has permission)
     */
    public boolean teleportToTown(Player player, String townName) {
        if (!isEnabled()) {
            return false;
        }
        
        try {
            TownyAPI api = TownyAPI.getInstance();
            Town town = api.getTown(townName);
            
            if (town == null) {
                player.sendMessage(plugin.getLang().t("towny.town_not_found", townName));
                return false;
            }
            
            if (!town.isPublic() && !isResidentOfTown(player, town)) {
                player.sendMessage(plugin.getLang().t("towny.town_private"));
                return false;
            }
            
            Location spawnLoc = town.getSpawnOrNull();
            if (spawnLoc == null) {
                player.sendMessage(plugin.getLang().t("towny.no_spawn"));
                return false;
            }
            
            player.teleport(spawnLoc);
            player.sendMessage(plugin.getLang().t("towny.teleported_to_town", town.getName()));
            return true;
            
        } catch (Throwable t) {
            plugin.getLogger().warning("[Towny] Error teleporting to town: " + t.getMessage());
            return false;
        }
    }
    
    /**
     * 检查玩家是否是指定城镇的居民
     * Check if player is a resident of specified town
     */
    public boolean isResidentOfTown(Player player, Town town) {
        try {
            Resident resident = TownyAPI.getInstance().getResident(player);
            if (resident == null || !resident.hasTown()) {
                return false;
            }
            Town playerTown = resident.getTownOrNull();
            return playerTown != null && playerTown.equals(town);
        } catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * 检查两个玩家是否在同一个城镇
     * Check if two players are in the same town
     */
    public boolean isSameTown(Player p1, Player p2) {
        try {
            TownyAPI api = TownyAPI.getInstance();
            Resident r1 = api.getResident(p1);
            Resident r2 = api.getResident(p2);
            
            if (r1 == null || r2 == null || !r1.hasTown() || !r2.hasTown()) {
                return false;
            }
            
            Town t1 = r1.getTownOrNull();
            Town t2 = r2.getTownOrNull();
            
            return t1 != null && t2 != null && t1.equals(t2);
        } catch (Throwable t) {
            return false;
        }
    }
}
