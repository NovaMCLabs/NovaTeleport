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
        }
    }

    public static boolean canEnter(Player player, Location destination) {
        RegionAdapterManager m = manager;
        if (m == null) {
            return true;
        }
        return m.canEnter(player, destination);
    }
}
