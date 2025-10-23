package com.novamclabs.guild;

import org.bukkit.Location;

/**
 * 工会传送点数据类
 * Guild warp data class
 */
public class GuildWarp {
    private final String guildId;
    private final String name;
    private final Location location;
    private final long createdTime;
    private String createdBy;
    
    public GuildWarp(String guildId, String name, Location location, String createdBy) {
        this.guildId = guildId;
        this.name = name;
        this.location = location;
        this.createdBy = createdBy;
        this.createdTime = System.currentTimeMillis();
    }
    
    public String getGuildId() {
        return guildId;
    }
    
    public String getName() {
        return name;
    }
    
    public Location getLocation() {
        return location;
    }
    
    public String getCreatedBy() {
        return createdBy;
    }
    
    public long getCreatedTime() {
        return createdTime;
    }
}
