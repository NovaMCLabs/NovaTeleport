package com.novamclabs.util;

import com.novamclabs.region.RegionAdapterManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Region protection integration entrypoint.
 *
 * TeleportUtil calls this helper to decide whether a destination is allowed.
 */
public final class RegionGuardUtil {
    private static volatile RegionAdapterManager manager;

    private RegionGuardUtil() {
    }

    public static void init(Plugin plugin) {
        try {
            manager = new RegionAdapterManager(plugin);
        } catch (Throwable t) {
            manager = null;
            // 不能静默：领地检查失效意味着传送会绕过所有领地保护
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "[RegionAdapter] Region protection could not be initialised — "
                            + "all teleports will skip region checks.", t);
        }
    }

    public static boolean canEnter(Player player, Location destination) {
        RegionAdapterManager m = manager;
        if (m == null) {
            return true;
        }
        return m.canEnter(player, destination);
    }

    /** 当前已注册的领地适配器管理器，未初始化时为 null | null when no adapters were initialised */
    public static RegionAdapterManager getManager() {
        return manager;
    }
}
