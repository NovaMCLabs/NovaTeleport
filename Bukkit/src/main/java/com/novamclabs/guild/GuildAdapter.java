package com.novamclabs.guild;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * 工会适配器接口
 * Guild adapter interface
 */
public interface GuildAdapter {
    
    /**
     * 获取适配器名称
     * Get adapter name
     */
    String name();
    
    /**
     * 检查插件是否存在
     * Check if plugin is present
     */
    boolean isPresent();
    
    /**
     * 获取玩家的工会ID
     * Get player's guild ID
     */
    String getGuildId(Player player);
    
    /**
     * 检查两个玩家是否在同一个工会
     * Check if two players are in the same guild
     */
    boolean isSameGuild(Player p1, Player p2);
    
    /**
     * 获取工会的所有成员UUID
     * Get all member UUIDs of a guild
     */
    List<UUID> getGuildMembers(String guildId);
    
    /**
     * 获取工会名称
     * Get guild name
     */
    String getGuildName(String guildId);
    
    /**
     * 获取工会主页/据点位置（如果插件支持）
     * Get guild home/HQ location (if plugin supports)
     */
    Location getGuildHome(String guildId);
    
    /**
     * 设置工会主页/据点位置（如果插件支持）
     * Set guild home/HQ location (if plugin supports)
     */
    boolean setGuildHome(String guildId, Location location);
    
    /**
     * 检查玩家是否是工会管理员/会长
     * Check if player is guild admin/master
     */
    boolean isGuildAdmin(Player player);
}
