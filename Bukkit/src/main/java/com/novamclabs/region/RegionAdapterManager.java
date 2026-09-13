package com.novamclabs.region;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
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

    /** 同一个适配器的报错只打印一次，避免刷屏 | log each adapter error only once */
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
    private static Logger staticLogger;

    public RegionAdapterManager(Plugin plugin) {
        this.logger = plugin.getLogger();
        staticLogger = this.logger;
        registerAdapters();
    }

    /**
     * 记录一次适配器内部错误（同 key 只输出一次）。
     * 适配器失败时默认放行传送，但静默失败会让运维无从排查，因此至少要留一条日志。
     */
    public static void logOnce(String key, Throwable t) {
        if (key == null || !LOGGED.add(key)) return;
        Logger l = staticLogger;
        if (l == null) return;
        l.log(Level.WARNING, "[RegionAdapter] " + key + " failed (" + t.getClass().getSimpleName()
                + ": " + t.getMessage() + ") — allowing teleport; further errors from this adapter are silenced.");
    }

    /**
     * 逐个构造适配器。
     *
     * 每个适配器的字节码都直接引用对应领地插件的类型；插件不在服务端时，JVM 在链接
     * 该适配器类时会抛 NoClassDefFoundError。因此绝不能用 List.of(new A(), new B(), ...)
     * 一次性构造——只要有一个插件缺席，后面的适配器一个都注册不上。
     * 这里改为按类名反射实例化，把每个适配器的失败隔离在各自的方法里。
     */
    private static final String[][] ADAPTERS = {
            {"WorldGuard", "com.novamclabs.region.impl.WorldGuardAdapter"},
            {"PlotSquared", "com.novamclabs.region.impl.PlotSquaredAdapter"},
            {"Residence", "com.novamclabs.region.impl.ResidenceAdapter"},
            {"GriefDefender", "com.novamclabs.region.impl.GriefDefenderAdapter"},
            {"Lands", "com.novamclabs.region.impl.LandsAdapter"},
            {"Towny", "com.novamclabs.region.impl.TownyAdapter"},
    };

    private void registerAdapters() {
        for (String[] entry : ADAPTERS) {
            tryRegister(entry[0], entry[1]);
        }
        if (adapters.isEmpty()) {
            logger.info("[RegionAdapter] No region plugins detected. All teleports will be allowed.");
        }
    }

    private void tryRegister(String name, String className) {
        Class<?> type;
        try {
            type = Class.forName(className);
        } catch (ClassNotFoundException e) {
            // 自身类缺失属于打包问题，需要报出来 | our own class missing = packaging bug
            logger.warning("[RegionAdapter] Adapter class missing from the jar: " + className);
            return;
        } catch (Throwable t) {
            // 目标领地插件未安装：Class.forName 会初始化该类，链接时抛 NoClassDefFoundError
            return;
        }

        RegionAdapter adapter;
        try {
            adapter = (RegionAdapter) type.getDeclaredConstructor().newInstance();
        } catch (Throwable t) {
            // 目标领地插件未安装时，适配器类初始化会抛 NoClassDefFoundError，属正常情况
            return;
        }

        try {
            if (adapter.isPresent()) {
                adapters.add(adapter);
                logger.info("[RegionAdapter] Registered: " + adapter.name());
            }
        } catch (Throwable t) {
            logger.warning("[RegionAdapter] Failed to register " + name + ": " + t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : ": " + t.getMessage()));
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
                logOnce(adapter.name(), t);
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
