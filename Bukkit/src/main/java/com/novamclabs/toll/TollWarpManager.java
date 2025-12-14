package com.novamclabs.toll;

import com.novamclabs.StarTeleport;
import com.novamclabs.util.EconomyUtil;
import com.novamclabs.util.TeleportUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
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
    public enum Mode {
        TOLL,
        PERSONAL_FREE
    }

    private final StarTeleport plugin;
    private final Map<String, TollWarp> warps = new HashMap<>();
    private final Map<UUID, List<String>> playerWarps = new HashMap<>();

    private File dataFile;
    private FileConfiguration dataConfig;

    private FileConfiguration config;

    private boolean enabled;
    private Mode mode;
    private int maxPerPlayer;
    private double minPrice;
    private double maxPrice;
    private double ownerFeePercentage;
    private boolean allowFree;
    private int teleportDelaySeconds;

    public TollWarpManager(StarTeleport plugin) {
        this.plugin = plugin;
        loadConfig();
        loadData();
        reload();
    }

    private void loadConfig() {
        File f = new File(plugin.getDataFolder(), "toll_warps_config.yml");
        if (!f.exists()) {
            try {
                plugin.saveResource("toll_warps_config.yml", false);
            } catch (IllegalArgumentException ignored) {
            }
        }
        this.config = YamlConfiguration.loadConfiguration(f);
    }

    private void loadData() {
        dataFile = new File(plugin.getDataFolder(), "toll_warps.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (Exception e) {
                plugin.getLogger().severe("[TollWarp] Failed to create data file: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        warps.clear();
        playerWarps.clear();

        for (String key : dataConfig.getKeys(false)) {
            try {
                String name = key;
                UUID ownerId = UUID.fromString(dataConfig.getString(key + ".owner"));
                Location loc = (Location) dataConfig.get(key + ".location");
                double price = dataConfig.getDouble(key + ".price");
                boolean enabled = dataConfig.getBoolean(key + ".enabled", true);
                int usageCount = dataConfig.getInt(key + ".usage_count", 0);

                if (loc != null) {
                    TollWarp warp = new TollWarp(name, ownerId, loc, price);
                    warp.setEnabled(enabled);
                    for (int i = 0; i < usageCount; i++) {
                        warp.incrementUsage();
                    }
                    warps.put(name.toLowerCase(Locale.ROOT), warp);
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
        loadConfig();

        this.enabled = config.getBoolean("enabled", false);
        String modeStr = config.getString("mode", "toll");
        if (modeStr != null && modeStr.equalsIgnoreCase("personal_free")) {
            this.mode = Mode.PERSONAL_FREE;
        } else {
            this.mode = Mode.TOLL;
        }

        this.maxPerPlayer = config.getInt("max_per_player", 3);
        this.minPrice = config.getDouble("min_price", 0.0);
        this.maxPrice = config.getDouble("max_price", 10000.0);
        this.ownerFeePercentage = config.getDouble("owner_fee_percentage", 100.0);
        this.allowFree = config.getBoolean("allow_free", true);
        this.teleportDelaySeconds = config.getInt("teleport_delay_seconds", 3);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Mode getMode() {
        return mode;
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

        if (mode == Mode.PERSONAL_FREE) {
            price = 0.0;
        }

        if (warps.containsKey(warpName.toLowerCase(Locale.ROOT))) {
            player.sendMessage(plugin.getLang().tr("toll.name_exists", "name", warpName));
            return false;
        }

        List<String> playerWarpList = playerWarps.getOrDefault(player.getUniqueId(), new ArrayList<>());
        if (playerWarpList.size() >= maxPerPlayer) {
            player.sendMessage(plugin.getLang().t("toll.limit_reached"));
            return false;
        }

        if (price < minPrice || price > maxPrice) {
            player.sendMessage(plugin.getLang().tr("toll.price_out_of_range", "min", minPrice, "max", maxPrice));
            return false;
        }

        if (price == 0 && !allowFree) {
            player.sendMessage(plugin.getLang().t("toll.free_not_allowed"));
            return false;
        }

        if (price > 0 && (!EconomyUtil.isEnabled(plugin) || !EconomyUtil.hasProvider())) {
            player.sendMessage(plugin.getLang().t("economy.not_available"));
            return false;
        }

        TollWarp warp = new TollWarp(warpName, player.getUniqueId(), player.getLocation(), price);
        warps.put(warpName.toLowerCase(Locale.ROOT), warp);
        playerWarpList.add(warpName);
        playerWarps.put(player.getUniqueId(), playerWarpList);

        dataConfig.set(warpName + ".owner", player.getUniqueId().toString());
        dataConfig.set(warpName + ".location", warp.getLocation());
        dataConfig.set(warpName + ".price", price);
        dataConfig.set(warpName + ".enabled", true);
        dataConfig.set(warpName + ".usage_count", 0);
        saveData();

        player.sendMessage(plugin.getLang().tr("toll.created", "name", warpName, "price", EconomyUtil.format(price)));
        return true;
    }

    /**
     * 删除付费传送点
     */
    public boolean deleteWarp(Player player, String warpName) {
        TollWarp warp = warps.get(warpName.toLowerCase(Locale.ROOT));
        if (warp == null) {
            player.sendMessage(plugin.getLang().tr("toll.not_found", "name", warpName));
            return false;
        }

        if (!warp.getOwnerId().equals(player.getUniqueId()) && !player.hasPermission("novateleport.toll.delete.others")) {
            player.sendMessage(plugin.getLang().t("toll.not_owner"));
            return false;
        }

        warps.remove(warpName.toLowerCase(Locale.ROOT));
        List<String> playerWarpList = playerWarps.get(warp.getOwnerId());
        if (playerWarpList != null) {
            playerWarpList.remove(warp.getName());
        }

        dataConfig.set(warp.getName(), null);
        saveData();

        player.sendMessage(plugin.getLang().tr("toll.deleted", "name", warp.getName()));
        return true;
    }

    /**
     * 设置传送点价格
     */
    public boolean setPrice(Player player, String warpName, double price) {
        TollWarp warp = warps.get(warpName.toLowerCase(Locale.ROOT));
        if (warp == null) {
            player.sendMessage(plugin.getLang().tr("toll.not_found", "name", warpName));
            return false;
        }

        if (!warp.getOwnerId().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getLang().t("toll.not_owner"));
            return false;
        }

        if (mode == Mode.PERSONAL_FREE) {
            price = 0.0;
        }

        if (price < minPrice || price > maxPrice) {
            player.sendMessage(plugin.getLang().tr("toll.price_out_of_range", "min", minPrice, "max", maxPrice));
            return false;
        }

        if (price == 0 && !allowFree) {
            player.sendMessage(plugin.getLang().t("toll.free_not_allowed"));
            return false;
        }

        warp.setPrice(price);
        dataConfig.set(warp.getName() + ".price", price);
        saveData();

        player.sendMessage(plugin.getLang().tr("toll.price_updated", "name", warp.getName(), "price", EconomyUtil.format(price)));
        return true;
    }

    /**
     * 传送到付费传送点
     */
    public boolean teleportToWarp(Player player, String warpName) {
        TollWarp warp = warps.get(warpName.toLowerCase(Locale.ROOT));
        if (warp == null) {
            player.sendMessage(plugin.getLang().tr("toll.not_found", "name", warpName));
            return false;
        }

        if (!warp.isEnabled()) {
            player.sendMessage(plugin.getLang().tr("toll.disabled", "name", warp.getName()));
            return false;
        }

        if (mode == Mode.PERSONAL_FREE && !warp.getOwnerId().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getLang().t("toll.mode_personal_only"));
            return false;
        }

        // owner uses it for free
        if (warp.getOwnerId().equals(player.getUniqueId())) {
            TeleportUtil.delayedTeleportWithAnimation(plugin, player, warp.getLocation(), teleportDelaySeconds, "tollwarp",
                () -> player.sendMessage(plugin.getLang().tr("toll.teleported_owner", "name", warp.getName())));
            return true;
        }

        // bypass permission
        if (player.hasPermission("novateleport.toll.bypass")) {
            TeleportUtil.delayedTeleportWithAnimation(plugin, player, warp.getLocation(), teleportDelaySeconds, "tollwarp",
                () -> player.sendMessage(plugin.getLang().tr("toll.teleported_bypass", "name", warp.getName())));
            incrementUsage(warp);
            return true;
        }

        double price = warp.getPrice();
        if (price > 0) {
            if (!EconomyUtil.isEnabled(plugin) || !EconomyUtil.hasProvider()) {
                player.sendMessage(plugin.getLang().t("economy.not_available"));
                return false;
            }

            double balance = EconomyUtil.getBalance(player);
            if (balance < price) {
                player.sendMessage(plugin.getLang().tr("toll.insufficient_funds", "price", EconomyUtil.format(price)));
                return false;
            }

            OfflinePlayer owner = Bukkit.getOfflinePlayer(warp.getOwnerId());
            double ownerFee = price * (ownerFeePercentage / 100.0);
            ownerFee = Math.min(price, Math.max(0.0, ownerFee));
            double serverFee = Math.max(0.0, price - ownerFee);

            if (ownerFee > 0) {
                if (!EconomyUtil.transfer(plugin, player, owner, ownerFee)) {
                    player.sendMessage(plugin.getLang().tr("toll.insufficient_funds", "price", EconomyUtil.format(price)));
                    return false;
                }
            }
            if (serverFee > 0) {
                if (!EconomyUtil.charge(plugin, player, serverFee)) {
                    player.sendMessage(plugin.getLang().tr("toll.insufficient_funds", "price", EconomyUtil.format(price)));
                    return false;
                }
            }

            if (owner.isOnline() && owner.getPlayer() != null && ownerFee > 0) {
                owner.getPlayer().sendMessage(plugin.getLang().tr(
                    "toll.owner_received",
                    "amount", EconomyUtil.format(ownerFee),
                    "player", player.getName(),
                    "name", warp.getName()
                ));
            }
        }

        try {
            if (plugin.getDataStore() != null) {
                plugin.getDataStore().setBack(player.getUniqueId(), player.getLocation());
            }
        } catch (Exception ignored) {
        }

        TeleportUtil.delayedTeleportWithAnimation(plugin, player, warp.getLocation(), teleportDelaySeconds, "tollwarp",
            () -> player.sendMessage(plugin.getLang().tr("toll.teleported_toll", "name", warp.getName(), "price", EconomyUtil.format(price))));

        incrementUsage(warp);
        return true;
    }

    private void incrementUsage(TollWarp warp) {
        warp.incrementUsage();
        dataConfig.set(warp.getName() + ".usage_count", warp.getUsageCount());
        saveData();
    }

    public List<TollWarp> getAllWarps() {
        return warps.values().stream()
            .filter(TollWarp::isEnabled)
            .collect(Collectors.toList());
    }

    public List<TollWarp> getPlayerWarps(UUID playerId) {
        return playerWarps.getOrDefault(playerId, new ArrayList<>()).stream()
            .map(name -> warps.get(name.toLowerCase(Locale.ROOT)))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    public TollWarp getWarp(String name) {
        return warps.get(name.toLowerCase(Locale.ROOT));
    }

    public ConfigurationSection getGuiConfig() {
        return config.getConfigurationSection("gui");
    }
}
