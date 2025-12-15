package com.novamclabs.guild;

import com.novamclabs.StarTeleport;
import com.novamclabs.guild.impl.FactionsUUIDAdapter;
import com.novamclabs.guild.impl.GuildsPluginAdapter;
import com.novamclabs.guild.impl.SimpleClansAdapter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * 工会管理器
 * Guild manager
 */
public class GuildManager {
    private final StarTeleport plugin;
    private final List<GuildAdapter> adapters = new ArrayList<>();

    private FileConfiguration config;
    private boolean enabled;

    public GuildManager(StarTeleport plugin) {
        this.plugin = plugin;
        loadConfig();
        registerAdapters();
        reload();
    }

    private void loadConfig() {
        File f = new File(plugin.getDataFolder(), "guild_config.yml");
        if (!f.exists()) {
            try {
                plugin.saveResource("guild_config.yml", false);
            } catch (IllegalArgumentException ignored) {
            }
        }
        this.config = YamlConfiguration.loadConfiguration(f);
    }

    private void registerAdapters() {
        List<String> allowed = config.getStringList("plugins");
        boolean filter = allowed != null && !allowed.isEmpty();
        Set<String> allowSet = new HashSet<>();
        if (filter) {
            for (String s : allowed) {
                if (s != null) allowSet.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }

        List<GuildAdapter> candidates = List.of(
            new GuildsPluginAdapter(),
            new SimpleClansAdapter(),
            new FactionsUUIDAdapter()
        );

        for (GuildAdapter adapter : candidates) {
            if (filter && !allowSet.contains(adapter.name().toLowerCase(Locale.ROOT))) {
                continue;
            }
            try {
                if (adapter.isPresent()) {
                    adapters.add(adapter);
                    plugin.getLogger().info("[Guild] Registered adapter: " + adapter.name());
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[Guild] Failed to register " + adapter.name() + ": " + t.getMessage());
            }
        }

        if (adapters.isEmpty()) {
            plugin.getLogger().info("[Guild] No guild plugins detected.");
        }
    }

    public void reload() {
        loadConfig();
        this.enabled = config.getBoolean("enabled", false);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public boolean isEnabled() {
        return enabled && !adapters.isEmpty();
    }

    /**
     * 获取玩家的工会ID
     */
    public String getGuildId(Player player) {
        for (GuildAdapter adapter : adapters) {
            try {
                String guildId = adapter.getGuildId(player);
                if (guildId != null) return guildId;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * 检查两个玩家是否在同一工会
     */
    public boolean isSameGuild(Player p1, Player p2) {
        for (GuildAdapter adapter : adapters) {
            try {
                if (adapter.isSameGuild(p1, p2)) return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    /**
     * 获取工会所有成员
     */
    public List<UUID> getGuildMembers(Player player) {
        String guildId = getGuildId(player);
        if (guildId == null) return new ArrayList<>();

        for (GuildAdapter adapter : adapters) {
            try {
                List<UUID> members = adapter.getGuildMembers(guildId);
                if (!members.isEmpty()) return members;
            } catch (Throwable ignored) {
            }
        }
        return new ArrayList<>();
    }

    /**
     * 获取工会名称
     */
    public String getGuildName(Player player) {
        String guildId = getGuildId(player);
        if (guildId == null) return null;

        for (GuildAdapter adapter : adapters) {
            try {
                String name = adapter.getGuildName(guildId);
                if (name != null) return name;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * 获取工会据点位置
     */
    public Location getGuildHome(Player player) {
        String guildId = getGuildId(player);
        if (guildId == null) return null;

        for (GuildAdapter adapter : adapters) {
            try {
                Location home = adapter.getGuildHome(guildId);
                if (home != null) return home;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * 设置工会据点
     */
    public boolean setGuildHome(Player player, Location location) {
        String guildId = getGuildId(player);
        if (guildId == null) return false;

        for (GuildAdapter adapter : adapters) {
            try {
                if (adapter.setGuildHome(guildId, location)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    /**
     * 检查玩家是否是工会管理员
     */
    public boolean isGuildAdmin(Player player) {
        for (GuildAdapter adapter : adapters) {
            try {
                if (adapter.isGuildAdmin(player)) return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public List<GuildAdapter> getAdapters() {
        return new ArrayList<>(adapters);
    }
}
