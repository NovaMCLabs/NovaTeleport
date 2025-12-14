package com.novamclabs.guild;

import com.novamclabs.StarTeleport;
import com.novamclabs.util.EconomyUtil;
import com.novamclabs.util.TeleportUtil;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

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

    private boolean warpsEnabled;
    private int maxWarpsPerGuild;
    private boolean adminOnly;
    private int teleportDelaySeconds;
    private double teleportCost;

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
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (Exception e) {
                plugin.getLogger().severe("[GuildWarp] Failed to create data file: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        guildWarps.clear();
        ConfigurationSection root = dataConfig.getConfigurationSection("guilds");
        if (root == null) return;

        for (String guildId : root.getKeys(false)) {
            ConfigurationSection wsec = root.getConfigurationSection(guildId);
            if (wsec == null) continue;

            for (String warpName : wsec.getKeys(false)) {
                try {
                    Location loc = (Location) wsec.get(warpName + ".location");
                    String createdBy = wsec.getString(warpName + ".created_by");
                    if (loc == null) continue;

                    GuildWarp warp = new GuildWarp(guildId, warpName, loc, createdBy);
                    guildWarps.computeIfAbsent(guildId, k -> new ArrayList<>()).add(warp);
                } catch (Exception e) {
                    plugin.getLogger().warning("[GuildWarp] Failed to load warp " + guildId + "." + warpName + ": " + e.getMessage());
                }
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
        FileConfiguration cfg = guildManager.getConfig();
        this.warpsEnabled = cfg.getBoolean("warps.enabled", true);
        this.maxWarpsPerGuild = cfg.getInt("warps.max_per_guild", 5);
        this.adminOnly = cfg.getBoolean("warps.admin_only", true);
        this.teleportDelaySeconds = cfg.getInt("warps.delay", 3);
        this.teleportCost = cfg.getDouble("warps.cost", 0.0);
    }

    /**
     * 创建工会传送点
     */
    public boolean createWarp(Player player, String warpName) {
        if (!guildManager.isEnabled()) return false;
        if (!warpsEnabled) {
            player.sendMessage(plugin.getLang().t("guild.not_enabled"));
            return false;
        }

        String guildId = guildManager.getGuildId(player);
        if (guildId == null) {
            player.sendMessage(plugin.getLang().t("guild.no_guild"));
            return false;
        }

        if (adminOnly && !guildManager.isGuildAdmin(player)) {
            player.sendMessage(plugin.getLang().t("guild.not_admin"));
            return false;
        }

        List<GuildWarp> warps = guildWarps.getOrDefault(guildId, new ArrayList<>());
        if (warps.size() >= maxWarpsPerGuild) {
            player.sendMessage(plugin.getLang().t("guild.warp_limit_reached"));
            return false;
        }

        if (warps.stream().anyMatch(w -> w.getName().equalsIgnoreCase(warpName))) {
            player.sendMessage(plugin.getLang().tr("guild.warp_already_exists", "name", warpName));
            return false;
        }

        GuildWarp warp = new GuildWarp(guildId, warpName, player.getLocation(), player.getName());
        warps.add(warp);
        guildWarps.put(guildId, warps);

        String path = "guilds." + guildId + "." + warpName;
        dataConfig.set(path + ".location", warp.getLocation());
        dataConfig.set(path + ".created_by", warp.getCreatedBy());
        saveData();

        player.sendMessage(plugin.getLang().tr("guild.warp_created", "name", warpName));
        return true;
    }

    /**
     * 删除工会传送点
     */
    public boolean deleteWarp(Player player, String warpName) {
        if (!guildManager.isEnabled()) return false;
        if (!warpsEnabled) {
            player.sendMessage(plugin.getLang().t("guild.not_enabled"));
            return false;
        }

        String guildId = guildManager.getGuildId(player);
        if (guildId == null) {
            player.sendMessage(plugin.getLang().t("guild.no_guild"));
            return false;
        }

        if (adminOnly && !guildManager.isGuildAdmin(player)) {
            player.sendMessage(plugin.getLang().t("guild.not_admin"));
            return false;
        }

        List<GuildWarp> warps = guildWarps.get(guildId);
        if (warps == null) {
            player.sendMessage(plugin.getLang().tr("guild.warp_not_found", "name", warpName));
            return false;
        }

        boolean removed = warps.removeIf(w -> w.getName().equalsIgnoreCase(warpName));
        if (!removed) {
            player.sendMessage(plugin.getLang().tr("guild.warp_not_found", "name", warpName));
            return false;
        }

        dataConfig.set("guilds." + guildId + "." + warpName, null);
        saveData();

        player.sendMessage(plugin.getLang().tr("guild.warp_deleted", "name", warpName));
        return true;
    }

    /**
     * 传送到工会传送点
     */
    public boolean teleportToWarp(Player player, String warpName) {
        if (!guildManager.isEnabled()) return false;
        if (!warpsEnabled) {
            player.sendMessage(plugin.getLang().t("guild.not_enabled"));
            return false;
        }

        String guildId = guildManager.getGuildId(player);
        if (guildId == null) {
            player.sendMessage(plugin.getLang().t("guild.no_guild"));
            return false;
        }

        List<GuildWarp> warps = guildWarps.get(guildId);
        if (warps == null || warps.isEmpty()) {
            player.sendMessage(plugin.getLang().t("guild.no_warps"));
            return false;
        }

        GuildWarp warp = warps.stream()
            .filter(w -> w.getName().equalsIgnoreCase(warpName))
            .findFirst()
            .orElse(null);

        if (warp == null) {
            player.sendMessage(plugin.getLang().tr("guild.warp_not_found", "name", warpName));
            return false;
        }

        if (!EconomyUtil.charge(plugin, player, teleportCost)) {
            player.sendMessage(plugin.getLang().tr("economy.not_enough", "amount", EconomyUtil.format(teleportCost)));
            return false;
        }

        try {
            if (plugin.getDataStore() != null) {
                plugin.getDataStore().setBack(player.getUniqueId(), player.getLocation());
            }
        } catch (Exception ignored) {
        }

        TeleportUtil.delayedTeleportWithAnimation(plugin, player, warp.getLocation(), teleportDelaySeconds,
            () -> player.sendMessage(plugin.getLang().tr("guild.teleported_to_warp", "name", warpName)));
        return true;
    }

    public List<GuildWarp> getGuildWarps(String guildId) {
        return new ArrayList<>(guildWarps.getOrDefault(guildId, new ArrayList<>()));
    }

    public List<GuildWarp> getPlayerGuildWarps(Player player) {
        String guildId = guildManager.getGuildId(player);
        if (guildId == null) return new ArrayList<>();
        return getGuildWarps(guildId);
    }
}
