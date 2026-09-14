package com.novamclabs.toll;

import com.novamclabs.StarTeleport;
import com.novamclabs.storage.DataStore;
import com.novamclabs.util.EconomyUtil;
import com.novamclabs.util.TeleportUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
    // 增删改价在命令发起者的区域线程，incrementUsage 在传送者的区域线程（TeleportUtil 扣费回调），
    // 不同玩家可能位于不同区域，因此这两个 Map 必须线程安全。
    private final Map<String, TollWarp> warps = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> playerWarps = new ConcurrentHashMap<>();
    /** 「改内存 + 落盘」必须成对串行，否则两个区域线程会交叉写入同一份配置 */
    private final Object dataLock = new Object();

    private File dataFile;
    private YamlConfiguration dataConfig;

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
                    playerWarps.computeIfAbsent(ownerId, k -> new CopyOnWriteArrayList<>()).add(name);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[TollWarp] Failed to load warp " + key + ": " + e.getMessage());
            }
        }
    }

    private void saveData() {
        try {
            DataStore.atomicSave(dataConfig, dataFile);
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

        // 名称会作为 YAML 路径键写入，含 '.' 等字符会写坏数据，必须先规范化
        String normalized = DataStore.normalizeName(warpName);
        if (normalized == null) {
            player.sendMessage(plugin.getLang().tr("warps.invalid_name", "name", warpName));
            return false;
        }
        warpName = normalized;

        if (warps.containsKey(warpName)) {
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
        synchronized (dataLock) {
            warps.put(warpName, warp);
            playerWarps.computeIfAbsent(player.getUniqueId(), k -> new CopyOnWriteArrayList<>()).add(warpName);

            dataConfig.set(warpName + ".owner", player.getUniqueId().toString());
            dataConfig.set(warpName + ".location", warp.getLocation());
            dataConfig.set(warpName + ".price", price);
            dataConfig.set(warpName + ".enabled", true);
            dataConfig.set(warpName + ".usage_count", 0);
            saveData();
        }

        player.sendMessage(plugin.getLang().tr("toll.created", "name", warpName, "price", EconomyUtil.format(price)));
        return true;
    }

    /**
     * 删除付费传送点
     */
    public boolean deleteWarp(Player player, String warpName) {
        TollWarp warp = lookup(warpName);
        if (warp == null) {
            player.sendMessage(plugin.getLang().tr("toll.not_found", "name", warpName));
            return false;
        }

        if (!warp.getOwnerId().equals(player.getUniqueId()) && !player.hasPermission("novateleport.toll.delete.others")) {
            player.sendMessage(plugin.getLang().t("toll.not_owner"));
            return false;
        }

        String key = DataStore.normalizeName(warpName);
        synchronized (dataLock) {
            warps.remove(key);
            List<String> playerWarpList = playerWarps.get(warp.getOwnerId());
            if (playerWarpList != null) {
                playerWarpList.remove(warp.getName());
            }

            dataConfig.set(warp.getName(), null);
            saveData();
        }

        player.sendMessage(plugin.getLang().tr("toll.deleted", "name", warp.getName()));
        return true;
    }

    /**
     * 设置传送点价格
     */
    public boolean setPrice(Player player, String warpName, double price) {
        TollWarp warp = lookup(warpName);
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

        synchronized (dataLock) {
            warp.setPrice(price);
            dataConfig.set(warp.getName() + ".price", price);
            saveData();
        }

        player.sendMessage(plugin.getLang().tr("toll.price_updated", "name", warp.getName(), "price", EconomyUtil.format(price)));
        return true;
    }

    /**
     * 传送到付费传送点。费用在传送真正执行时才结算，倒计时被取消不会扣钱。
     * Charges are settled at teleport time so a cancelled countdown never costs the player.
     */
    public boolean teleportToWarp(Player player, String warpName) {
        TollWarp warp = lookup(warpName);
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

        boolean owner = warp.getOwnerId().equals(player.getUniqueId());
        boolean bypass = player.hasPermission("novateleport.toll.bypass");
        double price = warp.getPrice();

        final TeleportUtil.Payment payment;
        final String doneKey;
        if (owner) {
            // 所有者传送同样计入使用次数（与 bypass/免费分支一致）；payment 每次传送只回调一次
            payment = p -> { incrementUsage(warp); return true; };
            doneKey = "toll.teleported_owner";
        } else if (bypass) {
            payment = p -> { incrementUsage(warp); return true; };
            doneKey = "toll.teleported_bypass";
        } else if (price > 0) {
            payment = p -> payToll(p, warp, price);
            doneKey = "toll.teleported_toll";
        } else {
            payment = p -> { incrementUsage(warp); return true; };
            doneKey = "toll.teleported_toll";
        }

        TeleportUtil.delayedTeleportWithAnimation(plugin, player, warp.getLocation(), teleportDelaySeconds, "tollwarp", payment,
            () -> player.sendMessage(plugin.getLang().tr(doneKey, "name", warp.getName(), "price", EconomyUtil.format(price))));
        return true;
    }

    /** 支付传送费用：按比例分给所有者，其余作为服务器收入 | split the price between owner and server */
    private boolean payToll(Player player, TollWarp warp, double price) {
        // 付费传送点要求经济可用；这与「经济没开就免费」的通用降级不同，是有意为之
        if (!EconomyUtil.isEnabled(plugin) || !EconomyUtil.hasProvider()) {
            player.sendMessage(plugin.getLang().t("economy.not_available"));
            return false;
        }

        OfflinePlayer owner = Bukkit.getOfflinePlayer(warp.getOwnerId());
        com.novamclabs.util.CostModel.Spec spec = com.novamclabs.util.CostModel.Spec.builder()
                .money(price)
                .split(owner, ownerFeePercentage)
                .moneyDeniedKey("toll.insufficient_funds")
                .build();

        com.novamclabs.util.CostModel.Result result = com.novamclabs.util.CostModel.preflight(plugin, player, spec);
        if (!result.ok()) {
            com.novamclabs.util.CostModel.notifyDenied(plugin, player, spec, result);
            return false;
        }
        if (!com.novamclabs.util.CostModel.apply(plugin, player, spec, result)) {
            player.sendMessage(plugin.getLang().tr("toll.insufficient_funds", "price", EconomyUtil.format(price)));
            return false;
        }

        double ownerFee = com.novamclabs.util.CostModel.recipientShare(spec);
        if (owner.isOnline() && owner.getPlayer() != null && ownerFee > 0) {
            owner.getPlayer().sendMessage(plugin.getLang().tr(
                "toll.owner_received",
                "amount", EconomyUtil.format(ownerFee),
                "player", player.getName(),
                "name", warp.getName()
            ));
        }
        incrementUsage(warp);
        return true;
    }

    private void incrementUsage(TollWarp warp) {
        synchronized (dataLock) {
            warp.incrementUsage();
            dataConfig.set(warp.getName() + ".usage_count", warp.getUsageCount());
            saveData();
        }
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
        return lookup(name);
    }

    /** 名称先规范化再查找；非法名称（如含 '.'）永远不可能被存储，因此必然查不到 */
    private TollWarp lookup(String rawName) {
        String key = DataStore.normalizeName(rawName);
        return key == null ? null : warps.get(key);
    }
}
