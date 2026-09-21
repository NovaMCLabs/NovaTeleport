package com.novamclabs.guild;

import com.novamclabs.StarTeleport;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 工会管理器
 * Guild manager
 */
public class GuildManager {
    private final StarTeleport plugin;
    // 适配器列表在 reload 时整体替换：若像原来那样 add 到同一个 ArrayList，其他区域线程
    // 正在 for-each 迭代时会 ConcurrentModificationException / 看到半填状态。
    // 全部构建完再一次性 volatile 赋值，读者只会看到旧列表或新列表。
    private volatile List<GuildAdapter> adapters = new ArrayList<>();

    /** 同一个方法的报错只输出一次 | log each failing call site once */
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    // /stp reload 会在某个区域线程上替换这两个字段，而读取发生在各区域线程：非 volatile 时读者
    // 可能长期停留在旧配置上（见 death/DeathManager 的同类处理）
    private volatile FileConfiguration config;
    private volatile boolean enabled;

    public GuildManager(StarTeleport plugin) {
        this.plugin = plugin;
        loadConfig();
        registerAdapters();
        reload();
    }

    private void logOnce(String where, Throwable t) {
        if (!LOGGED.add(where)) return;
        plugin.getLogger().log(Level.WARNING, "[Guild] " + where + " failed ("
                + t.getClass().getSimpleName() + ": " + t.getMessage() + ") — further errors here are silenced.");
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

    /**
     * 按类名反射构造：适配器字节码直接引用对应工会插件类型，插件缺席时 JVM 链接该类会抛
     * NoClassDefFoundError。一次性构造会让一个缺席的插件连累其余适配器，因此逐个隔离。
     */
    private static final String[][] GUILD_ADAPTERS = {
            {"Guilds", "com.novamclabs.guild.impl.GuildsPluginAdapter"},
            {"SimpleClans", "com.novamclabs.guild.impl.SimpleClansAdapter"},
            {"FactionsUUID", "com.novamclabs.guild.impl.FactionsUUIDAdapter"},
    };

    private void registerAdapters() {
        List<String> allowed = config.getStringList("plugins");
        boolean filter = allowed != null && !allowed.isEmpty();
        Set<String> allowSet = new HashSet<>();
        if (filter) {
            for (String s : allowed) {
                if (s != null) allowSet.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }

        // 先构建到局部列表，最后一次性发布：读者不会看到「注册到一半」的列表
        List<GuildAdapter> built = new ArrayList<>();
        for (String[] entry : GUILD_ADAPTERS) {
            String name = entry[0];
            if (filter && !allowSet.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            GuildAdapter adapter;
            try {
                adapter = (GuildAdapter) Class.forName(entry[1]).getDeclaredConstructor().newInstance();
            } catch (Throwable t) {
                // 对应插件未安装/未启用
                continue;
            }
            try {
                if (adapter.isPresent()) {
                    built.add(adapter);
                    plugin.getLogger().info("[Guild] Registered adapter: " + adapter.name());
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[Guild] Failed to register " + name + ": " + t.getMessage());
            }
        }

        this.adapters = built;
        if (built.isEmpty()) {
            plugin.getLogger().info("[Guild] No guild plugins detected.");
        }
    }

    public void reload() {
        loadConfig();
        this.enabled = config.getBoolean("enabled", false);
        // 插件列表也来自配置：不在 reload 时重建的话，改 plugins 列表要重启才生效（且旧列表会被并发读到）
        registerAdapters();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public boolean isEnabled() {
        return enabled && !adapters.isEmpty();
    }

    /**
     * 产出该工会 ID 的适配器。ID 的格式由适配器自己定义（Guilds 是 UUID、SimpleClans 是 tag），
     * 必须还给它解析 | the adapter that produced the id owns its format
     */
    private record GuildRef(GuildAdapter adapter, String id) {
    }

    /**
     * 找到第一个能回答「玩家属于哪个工会」的适配器，并记住是哪一个。
     * 把 Guilds 的 UUID 交给 SimpleClans 的 getClan(String) 必然解析失败，
     * 因此后续所有按 ID 取数的调用都要走回同一个适配器，不能遍历全部。
     */
    private GuildRef resolve(Player player) {
        for (GuildAdapter adapter : adapters) {
            try {
                String guildId = adapter.getGuildId(player);
                if (guildId != null) return new GuildRef(adapter, guildId);
            } catch (Throwable t) {
                logOnce("getGuildId/" + adapter.name(), t);
            }
        }
        return null;
    }

    /**
     * 获取玩家的工会ID
     */
    public String getGuildId(Player player) {
        GuildRef ref = resolve(player);
        return ref == null ? null : ref.id();
    }

    /**
     * 检查两个玩家是否在同一工会
     */
    public boolean isSameGuild(Player p1, Player p2) {
        for (GuildAdapter adapter : adapters) {
            try {
                if (adapter.isSameGuild(p1, p2)) return true;
            } catch (Throwable t) {
                logOnce("isSameGuild/" + adapter.name(), t);
            }
        }
        return false;
    }

    /**
     * 获取工会所有成员
     */
    public List<UUID> getGuildMembers(Player player) {
        GuildRef ref = resolve(player);
        if (ref == null) return new ArrayList<>();

        try {
            List<UUID> members = ref.adapter().getGuildMembers(ref.id());
            return members == null ? new ArrayList<>() : members;
        } catch (Throwable t) {
            logOnce("getGuildMembers/" + ref.adapter().name(), t);
            return new ArrayList<>();
        }
    }

    /**
     * 获取工会名称
     */
    public String getGuildName(Player player) {
        GuildRef ref = resolve(player);
        if (ref == null) return null;

        try {
            return ref.adapter().getGuildName(ref.id());
        } catch (Throwable t) {
            logOnce("getGuildName/" + ref.adapter().name(), t);
            return null;
        }
    }

    /**
     * 获取工会据点位置
     */
    public Location getGuildHome(Player player) {
        GuildRef ref = resolve(player);
        if (ref == null) return null;

        try {
            return ref.adapter().getGuildHome(ref.id());
        } catch (Throwable t) {
            logOnce("getGuildHome/" + ref.adapter().name(), t);
            return null;
        }
    }

    /**
     * 设置工会据点
     */
    public boolean setGuildHome(Player player, Location location) {
        GuildRef ref = resolve(player);
        if (ref == null) return false;

        try {
            return ref.adapter().setGuildHome(ref.id(), location);
        } catch (Throwable t) {
            logOnce("setGuildHome/" + ref.adapter().name(), t);
            return false;
        }
    }

    /**
     * 检查玩家是否是工会管理员
     */
    public boolean isGuildAdmin(Player player) {
        for (GuildAdapter adapter : adapters) {
            try {
                if (adapter.isGuildAdmin(player)) return true;
            } catch (Throwable t) {
                logOnce("isGuildAdmin/" + adapter.name(), t);
            }
        }
        return false;
    }

    public List<GuildAdapter> getAdapters() {
        return new ArrayList<>(adapters);
    }
}
