package com.novamclabs.toll;

import com.novamclabs.StarTeleport;
import com.novamclabs.util.EconomyUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 付费传送点管理器
 * Toll warp manager
 */
public class TollWarpManager {
    private final StarTeleport plugin;
    private final Map<String, TollWarp> warps = new HashMap<>();
    private final Map<UUID, List<String>> playerWarps = new HashMap<>();
    private File dataFile;
    private FileConfiguration dataConfig;
    
    private boolean enabled;
    private String mode;
    private int maxPerPlayer;
    private double minPrice;
    private double maxPrice;
    private double ownerFeePercentage;
    private boolean allowFree;
    
    public TollWarpManager(StarTeleport plugin) {
        this.plugin = plugin;
        loadData();
        reload();
    }
    
    private void loadData() {
        dataFile = new File(plugin.getDataFolder(), "toll_warps.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (Exception e) {
                plugin.getLogger().severe("[TollWarp] Failed to create data file: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        
        // 加载所有付费传送点
        for (String key : dataConfig.getKeys(false)) {
            try {
                String name = key;
                UUID ownerId = UUID.fromString(dataConfig.getString(key + ".owner"));
                Location loc = (Location) dataConfig.get(key + ".location");
                double price = dataConfig.getDouble(key + ".price");
                boolean enabled = dataConfig.getBoolean(key + ".enabled", true);
                long createdTime = dataConfig.getLong(key + ".created_time", System.currentTimeMillis());
                int usageCount = dataConfig.getInt(key + ".usage_count", 0);
                
                if (loc != null) {
                    TollWarp warp = new TollWarp(name, ownerId, loc, price);
                    warp.setEnabled(enabled);
                    warps.put(name.toLowerCase(), warp);
                    
                    playerWarps.computeIfAbsent(ownerId, k -> new ArrayList<>()).add(name);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[TollWarp] Failed to load warp " + key + ": " + e.getMessage());
            }
        }
    }
    
    private void saveData() {
        try {
            dataConfig.save(dataFile);
        } catch (Exception e) {
            plugin.getLogger().severe("[TollWarp] Failed to save data: " + e.getMessage());
        }
    }
    
    public void reload() {
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("toll_warps.enabled", false);
        this.mode = config.getString("toll_warps.mode", "toll");
        this.maxPerPlayer = config.getInt("toll_warps.max_per_player", 3);
        this.minPrice = config.getDouble("toll_warps.min_price", 0.0);
        this.maxPrice = config.getDouble("toll_warps.max_price", 10000.0);
        this.ownerFeePercentage = config.getDouble("toll_warps.owner_fee_percentage", 100.0);
        this.allowFree = config.getBoolean("toll_warps.allow_free", true);
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * 创建付费传送点
     */
    public boolean createWarp(Player player, String warpName, double price) {
        if (!enabled) {
            player.sendMessage(plugin.getLang().t("toll.not_enabled"));
            return false;
        }
        
        if (!player.hasPermission("novateleport.toll.create")) {
            player.sendMessage(plugin.getLang().t("command.no_permission"));
            return false;
        }
        
        // 检查名称是否已存在
        if (warps.containsKey(warpName.toLowerCase())) {
            player.sendMessage(plugin.getLang().t("toll.name_exists", warpName));
            return false;
        }
        
        // 检查玩家创建数量限制
        List<String> playerWarpList = playerWarps.getOrDefault(player.getUniqueId(), new ArrayList<>());
        if (playerWarpList.size() >= maxPerPlayer) {
            player.sendMessage(plugin.getLang().t("toll.limit_reached"));
            return false;
        }
        
        // 检查价格范围
        if (price < minPrice || price > maxPrice) {
            player.sendMessage(plugin.getLang().t("toll.price_out_of_range", minPrice, maxPrice));
            return false;
        }
        
        if (price == 0 && !allowFree) {
            player.sendMessage(plugin.getLang().t("toll.free_not_allowed"));
            return false;
        }
        
        TollWarp warp = new TollWarp(warpName, player.getUniqueId(), player.getLocation(), price);
        warps.put(warpName.toLowerCase(), warp);
        playerWarpList.add(warpName);
        playerWarps.put(player.getUniqueId(), playerWarpList);
        
        // 保存到文件
        dataConfig.set(warpName + ".owner", player.getUniqueId().toString());
        dataConfig.set(warpName + ".location", warp.getLocation());
        dataConfig.set(warpName + ".price", price);
        dataConfig.set(warpName + ".enabled", true);
        dataConfig.set(warpName + ".created_time", warp.getCreatedTime());
        saveData();
        
        player.sendMessage(plugin.getLang().t("toll.created", warpName, EconomyUtil.format(price)));
        return true;
    }
    
    /**
     * 删除付费传送点
     */
    public boolean deleteWarp(Player player, String warpName) {
        TollWarp warp = warps.get(warpName.toLowerCase());
        if (warp == null) {
            player.sendMessage(plugin.getLang().t("toll.not_found", warpName));
            return false;
        }
        
        // 检查权限：拥有者或管理员
        if (!warp.getOwnerId().equals(player.getUniqueId()) && 
            !player.hasPermission("novateleport.toll.delete.others")) {
            player.sendMessage(plugin.getLang().t("toll.not_owner"));
            return false;
        }
        
        warps.remove(warpName.toLowerCase());
        List<String> playerWarpList = playerWarps.get(warp.getOwnerId());
        if (playerWarpList != null) {
            playerWarpList.remove(warpName);
        }
        
        dataConfig.set(warpName, null);
        saveData();
        
        player.sendMessage(plugin.getLang().t("toll.deleted", warpName));
        return true;
    }
    
    /**
     * 设置传送点价格
     */
    public boolean setPrice(Player player, String warpName, double price) {
        TollWarp warp = warps.get(warpName.toLowerCase());
        if (warp == null) {
            player.sendMessage(plugin.getLang().t("toll.not_found", warpName));
            return false;
        }
        
        if (!warp.getOwnerId().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getLang().t("toll.not_owner"));
            return false;
        }
        
        if (price < minPrice || price > maxPrice) {
            player.sendMessage(plugin.getLang().t("toll.price_out_of_range", minPrice, maxPrice));
            return false;
        }
        
        warp.setPrice(price);
        dataConfig.set(warpName + ".price", price);
        saveData();
        
        player.sendMessage(plugin.getLang().t("toll.price_updated", warpName, EconomyUtil.format(price)));
        return true;
    }
    
    /**
     * 传送到付费传送点
     */
    public boolean teleportToWarp(Player player, String warpName) {
        TollWarp warp = warps.get(warpName.toLowerCase());
        if (warp == null) {
            player.sendMessage(plugin.getLang().t("toll.not_found", warpName));
            return false;
        }
        
        if (!warp.isEnabled()) {
            player.sendMessage(plugin.getLang().t("toll.disabled", warpName));
            return false;
        }
        
        // 如果是拥有者，免费传送
        if (warp.getOwnerId().equals(player.getUniqueId())) {
            player.teleport(warp.getLocation());
            player.sendMessage(plugin.getLang().t("toll.teleported_owner", warpName));
            return true;
        }
        
        // 检查绕过权限
        if (player.hasPermission("novateleport.toll.bypass")) {
            player.teleport(warp.getLocation());
            player.sendMessage(plugin.getLang().t("toll.teleported_bypass", warpName));
            warp.incrementUsage();
            dataConfig.set(warpName + ".usage_count", warp.getUsageCount());
            saveData();
            return true;
        }
        
        // 收费传送
        double price = warp.getPrice();
        if (price > 0) {
            if (!EconomyUtil.hasProvider()) {
                player.sendMessage(plugin.getLang().t("economy.not_available"));
                return false;
            }
            
            if (!EconomyUtil.charge(plugin, player, price)) {
                player.sendMessage(plugin.getLang().t("toll.insufficient_funds", EconomyUtil.format(price)));
                return false;
            }
            
            // 转账给拥有者
            OfflinePlayer owner = Bukkit.getOfflinePlayer(warp.getOwnerId());
            double ownerFee = price * (ownerFeePercentage / 100.0);
            if (ownerFee > 0) {
                EconomyUtil.deposit(plugin, owner.getPlayer() != null ? owner.getPlayer() : player, ownerFee);
                
                // 通知拥有者（如果在线）
                if (owner.isOnline() && owner.getPlayer() != null) {
                    owner.getPlayer().sendMessage(plugin.getLang().t("toll.owner_received", 
                        EconomyUtil.format(ownerFee), player.getName(), warpName));
                }
            }
        }
        
        player.teleport(warp.getLocation());
        player.sendMessage(plugin.getLang().t("toll.teleported_toll", warpName, EconomyUtil.format(price)));
        
        warp.incrementUsage();
        dataConfig.set(warpName + ".usage_count", warp.getUsageCount());
        saveData();
        
        return true;
    }
    
    /**
     * 获取所有付费传送点
     */
    public List<TollWarp> getAllWarps() {
        return warps.values().stream()
            .filter(TollWarp::isEnabled)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取玩家拥有的传送点
     */
    public List<TollWarp> getPlayerWarps(UUID playerId) {
        return playerWarps.getOrDefault(playerId, new ArrayList<>()).stream()
            .map(name -> warps.get(name.toLowerCase()))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    public TollWarp getWarp(String name) {
        return warps.get(name.toLowerCase());
    }
}
