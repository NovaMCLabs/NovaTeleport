package com.novamclabs.util;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

/**
 * 玩家可拥有的家数量上限。
 *
 * 权限节点形如 {@code novateleport.home.limit.10}，取其中最大的数字；
 * 被显式否定的权限（{@code -novateleport.home.limit.100}）不参与计算，
 * 否则玩家可以用否定权限反向抬高自己的上限。
 */
public final class HomeLimitUtil {

    private HomeLimitUtil() {
    }

    public static int getHomeLimit(Plugin plugin, Player player) {
        int max = plugin.getConfig().getInt("homes.default_limit", 1);
        for (PermissionAttachmentInfo pi : player.getEffectivePermissions()) {
            if (!pi.getValue()) continue;
            String perm = pi.getPermission().toLowerCase(Locale.ROOT);
            if (perm.startsWith("novateleport.home.limit.")) {
                try {
                    int v = Integer.parseInt(perm.substring("novateleport.home.limit.".length()));
                    max = Math.max(max, v);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return max;
    }
}
