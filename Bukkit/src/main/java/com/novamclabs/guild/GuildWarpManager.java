package com.novamclabs.guild;

import com.novamclabs.StarTeleport;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 工会传送点管理器
 * Guild warp manager
 */
public class GuildWarpManager {
    private final StarTeleport plugin;
    private final GuildManager guildManager;
    private final Map<String, List<GuildWarp>> guildWarps = new HashMap<>();
    private File dataFile;
    private FileConfiguration dataConfig;
    private int maxWarpsPerGuild;
    
    public GuildWarpManager(StarTeleport plugin, GuildManager guildManager) {
        this.plugin = plugin;
        this.guildManager = guildManager;
        loadData();
        reload();
    }
    
    private void loadData() {
        dataFile = new File(plugin.getDataFolder(), "guild_warps.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (Exception e) {
                plugin.getLogger().severe("[GuildWarp] Failed to create data file: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        
        // 加载所有工会传送点
        for (String key : dataConfig.getKeys(false)) {
            try {
                String guildId = dataConfig.getString(key + ".guild_id");
                String name = dataConfig.getString(key + ".name");
                Location loc = (Location) dataConfig.get(key + ".location");
                String createdBy = dataConfig.getString(key + ".created_by");
                
                if (guildId != null && name != null && loc != null) {
                    GuildWarp warp = new GuildWarp(guildId, name, loc, createdBy);
                    guildWarps.computeIfAbsent(guildId, k -> new ArrayList<>()).add(warp);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[GuildWarp] Failed to load warp " + key + ": " + e.getMessage());
            }
        }
    }
    
    private void saveData() {
        try {
            dataConfig.save(dataFile);
        } catch (Exception e) {
            plugin.getLogger().severe("[GuildWarp] Failed to save data: " + e.getMessage());
        }
    }
    
    public void reload() {
        this.maxWarpsPerGuild = plugin.getConfig().getInt("guild.warps.max_per_guild", 5);
    }
    
    /**
     * 创建工会传送点
     */
    public boolean createWarp(Player player, String warpName) {
        if (!guildManager.isEnabled()) return false;
        
        String guildId = guildManager.getGuildId(player);
        if (guildId == null) {
            player.sendMessage(plugin.getLang().t("guild.no_guild"));
            return false;
        }
        
        boolean adminOnly = plugin.getConfig().getBoolean("guild.warps.admin_only", true);
        if (adminOnly && !guildManager.isGuildAdmin(player)) {
            player.sendMessage(plugin.getLang().t("guild.not_admin"));
            return false;
        }
        
        List<GuildWarp> warps = guildWarps.getOrDefault(guildId, new ArrayList<>());
        if (warps.size() >= maxWarpsPerGuild) {
            player.sendMessage(plugin.getLang().t("guild.warp_limit_reached"));
            return false;
        }
        
        // 检查是否已存在同名传送点
        if (warps.stream().anyMatch(w -> w.getName().equalsIgnoreCase(warpName))) {
            player.sendMessage(plugin.getLang().t("guild.warp_already_exists", warpName));
            return false;
        }
        
        GuildWarp warp = new GuildWarp(guildId, warpName, player.getLocation(), player.getName());
        warps.add(warp);
        guildWarps.put(guildId, warps);
        
        // 保存到文件
        String key = guildId + "." + warpName;
        dataConfig.set(key + ".guild_id", guildId);
        dataConfig.set(key + ".name", warpName);
        dataConfig.set(key + ".location", warp.getLocation());
        dataConfig.set(key + ".created_by", warp.getCreatedBy());
        saveData();
        
        player.sendMessage(plugin.getLang().t("guild.warp_created", warpName));
        return true;
    }
    
    /**
     * 删除工会传送点
     */
    public boolean deleteWarp(Player player, String warpName) {
        if (!guildManager.isEnabled()) return false;
        
        String guildId = guildManager.getGuildId(player);
        if (guildId == null) {
            player.sendMessage(plugin.getLang().t("guild.no_guild"));
            return false;
        }
        
        boolean adminOnly = plugin.getConfig().getBoolean("guild.warps.admin_only", true);
        if (adminOnly && !guildManager.isGuildAdmin(player)) {
            player.sendMessage(plugin.getLang().t("guild.not_admin"));
            return false;
        }
        
        List<GuildWarp> warps = guildWarps.get(guildId);
        if (warps == null) return false;
        
        boolean removed = warps.removeIf(w -> w.getName().equalsIgnoreCase(warpName));
        if (removed) {
            String key = guildId + "." + warpName;
            dataConfig.set(key, null);
            saveData();
            player.sendMessage(plugin.getLang().t("guild.warp_deleted", warpName));
            return true;
        }
        
        player.sendMessage(plugin.getLang().t("guild.warp_not_found", warpName));
        return false;
    }
    
    /**
     * 传送到工会传送点
     */
    public boolean teleportToWarp(Player player, String warpName) {
        if (!guildManager.isEnabled()) return false;
        
        String guildId = guildManager.getGuildId(player);
        if (guildId == null) {
            player.sendMessage(plugin.getLang().t("guild.no_guild"));
            return false;
        }
        
        List<GuildWarp> warps = guildWarps.get(guildId);
        if (warps == null) {
            player.sendMessage(plugin.getLang().t("guild.no_warps"));
            return false;
        }
        
        GuildWarp warp = warps.stream()
            .filter(w -> w.getName().equalsIgnoreCase(warpName))
            .findFirst()
            .orElse(null);
            
        if (warp == null) {
            player.sendMessage(plugin.getLang().t("guild.warp_not_found", warpName));
            return false;
        }
        
        player.teleport(warp.getLocation());
        player.sendMessage(plugin.getLang().t("guild.teleported_to_warp", warpName));
        return true;
    }
    
    /**
     * 列出工会的所有传送点
     */
    public List<GuildWarp> getGuildWarps(String guildId) {
        return new ArrayList<>(guildWarps.getOrDefault(guildId, new ArrayList<>()));
    }
    
    /**
     * 列出玩家所在工会的所有传送点
     */
    public List<GuildWarp> getPlayerGuildWarps(Player player) {
        String guildId = guildManager.getGuildId(player);
        if (guildId == null) return new ArrayList<>();
        return getGuildWarps(guildId);
    }
}
