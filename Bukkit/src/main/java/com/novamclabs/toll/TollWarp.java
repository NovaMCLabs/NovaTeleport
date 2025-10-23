package com.novamclabs.toll;

import org.bukkit.Location;

import java.util.UUID;

/**
 * 付费传送点数据类
 * Toll warp data class
 */
public class TollWarp {
    private final String name;
    private final UUID ownerId;
    private final Location location;
    private double price;
    private boolean enabled;
    private long createdTime;
    private int usageCount;
    
    public TollWarp(String name, UUID ownerId, Location location, double price) {
        this.name = name;
        this.ownerId = ownerId;
        this.location = location;
        this.price = price;
        this.enabled = true;
        this.createdTime = System.currentTimeMillis();
        this.usageCount = 0;
    }
    
    public String getName() {
        return name;
    }
    
    public UUID getOwnerId() {
        return ownerId;
    }
    
    public Location getLocation() {
        return location;
    }
    
    public double getPrice() {
        return price;
    }
    
    public void setPrice(double price) {
        this.price = price;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public long getCreatedTime() {
        return createdTime;
    }
    
    public int getUsageCount() {
        return usageCount;
    }
    
    public void incrementUsage() {
        this.usageCount++;
    }
}
