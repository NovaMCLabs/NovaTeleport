package com.novamclabs.towny;

import com.novamclabs.StarTeleport;
import com.novamclabs.util.EconomyUtil;
import com.novamclabs.util.TeleportUtil;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;

/**
 * Towny 城镇传送管理器
 */
public class TownyTeleportManager {
    private final StarTeleport plugin;

    private FileConfiguration config;
    private boolean enabled;
    private int homeDelay;
    private int otherDelay;
    private double homeCost;
    private double otherCost;

    public TownyTeleportManager(StarTeleport plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "features_config.yml");
        if (!f.exists()) {
            try {
                plugin.saveResource("features_config.yml", false);
            } catch (IllegalArgumentException ignored) {
            }
        }
        this.config = YamlConfiguration.loadConfiguration(f);

        this.enabled = config.getBoolean("towny.enabled", false);
        this.homeDelay = config.getInt("towny.home_delay", 3);
        this.otherDelay = config.getInt("towny.other_delay", 5);
        this.homeCost = config.getDouble("towny.home_cost", 0.0);
        this.otherCost = config.getDouble("towny.other_cost", 10.0);
    }

    public boolean isEnabled() {
        return enabled && Bukkit.getPluginManager().getPlugin("Towny") != null;
    }

    /**
     * 传送玩家到其所属城镇的spawn点
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
                player.sendMessage(plugin.getLang().t("towny.no_town"));
                return false;
            }

            Location spawnLoc = town.getSpawnOrNull();
            if (spawnLoc == null) {
                player.sendMessage(plugin.getLang().t("towny.no_spawn"));
                return false;
            }

            if (!EconomyUtil.charge(plugin, player, homeCost)) {
                player.sendMessage(plugin.getLang().tr("economy.not_enough", "amount", EconomyUtil.format(homeCost)));
                return false;
            }

            try {
                if (plugin.getDataStore() != null) {
                    plugin.getDataStore().setBack(player.getUniqueId(), player.getLocation());
                }
            } catch (Exception ignored) {
            }

            TeleportUtil.delayedTeleportWithAnimation(plugin, player, spawnLoc, homeDelay, "towny", () ->
                player.sendMessage(plugin.getLang().tr("towny.teleported_to_town", "town", town.getName())));

            return true;

        } catch (Throwable t) {
            plugin.getLogger().warning("[Towny] Error teleporting player: " + t.getMessage());
            return false;
        }
    }

    /**
     * 传送玩家到指定城镇（如果有权限）
     */
    public boolean teleportToTown(Player player, String townName) {
        if (!isEnabled()) {
            return false;
        }

        try {
            TownyAPI api = TownyAPI.getInstance();
            Town town = api.getTown(townName);

            if (town == null) {
                player.sendMessage(plugin.getLang().tr("towny.town_not_found", "town", townName));
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

            if (!EconomyUtil.charge(plugin, player, otherCost)) {
                player.sendMessage(plugin.getLang().tr("economy.not_enough", "amount", EconomyUtil.format(otherCost)));
                return false;
            }

            try {
                if (plugin.getDataStore() != null) {
                    plugin.getDataStore().setBack(player.getUniqueId(), player.getLocation());
                }
            } catch (Exception ignored) {
            }

            TeleportUtil.delayedTeleportWithAnimation(plugin, player, spawnLoc, otherDelay, "towny", () ->
                player.sendMessage(plugin.getLang().tr("towny.teleported_to_town", "town", town.getName())));

            return true;

        } catch (Throwable t) {
            plugin.getLogger().warning("[Towny] Error teleporting to town: " + t.getMessage());
            return false;
        }
    }

    /**
     * 检查玩家是否是指定城镇的居民
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
