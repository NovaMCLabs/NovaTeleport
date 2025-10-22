package com.novamclabs.region;

import com.novamclabs.region.impl.*;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * 领地适配器管理器
 * 自动检测并注册所有可用的领地插件适配器
 * Region adapter manager
 * Automatically detects and registers all available region plugin adapters
 */
public class RegionAdapterManager {
    private final List<RegionAdapter> adapters = new ArrayList<>();
    private final Logger logger;
    
    public RegionAdapterManager(Plugin plugin) {
        this.logger = plugin.getLogger();
        registerAdapters();
    }
    
    private void registerAdapters() {
        // 注册所有适配器
        // Register all adapters
        List<RegionAdapter> candidates = List.of(
            new WorldGuardAdapter(),
            new PlotSquaredAdapter(),
            new ResidenceAdapter(),
            new GriefDefenderAdapter(),
            new LandsAdapter(),
            new TownyAdapter()
        );
        
        for (RegionAdapter adapter : candidates) {
            try {
                if (adapter.isPresent()) {
                    adapters.add(adapter);
                    logger.info("[RegionAdapter] Registered: " + adapter.name());
                }
            } catch (Throwable t) {
                logger.warning("[RegionAdapter] Failed to register " + adapter.name() + ": " + t.getMessage());
            }
        }
        
        if (adapters.isEmpty()) {
            logger.info("[RegionAdapter] No region plugins detected. All teleports will be allowed.");
        }
    }
    
    /**
     * 检查玩家是否可以传送到目标位置
     * Check if player can teleport to destination
     */
    public boolean canEnter(Player player, Location destination) {
        // 所有适配器都必须允许才能传送
        // All adapters must allow teleport
        for (RegionAdapter adapter : adapters) {
            try {
                if (!adapter.canEnter(player, destination)) {
                    return false;
                }
            } catch (Throwable t) {
                logger.warning("[RegionAdapter] Error checking " + adapter.name() + ": " + t.getMessage());
                // 出错时默认允许
                // Allow by default on error
            }
        }
        return true;
    }
    
    /**
     * 获取所有已注册的适配器
     * Get all registered adapters
     */
    public List<RegionAdapter> getAdapters() {
        return new ArrayList<>(adapters);
    }
    
    /**
     * 获取指定名称的适配器
     * Get adapter by name
     */
    public RegionAdapter getAdapter(String name) {
        for (RegionAdapter adapter : adapters) {
            if (adapter.name().equalsIgnoreCase(name)) {
                return adapter;
            }
        }
        return null;
    }
}
