package com.novamclabs.hook;

import com.novamclabs.StarTeleport;
import com.novamclabs.util.HomeLimitUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.List;

/**
 * PlaceholderAPI 扩展（软依赖，仅在服务端安装了 PlaceholderAPI 时注册）。
 *
 * 提供 %novateleport_<key>% 形式的占位符：
 * <ul>
 *   <li>homes / homes_max — 已设置的家数量与上限</li>
 *   <li>warps — 全局传送点数量</li>
 *   <li>teleporting — 当前是否处于传送倒计时中</li>
 *   <li>can_back / death_back — /back 与 /deathback 是否可用</li>
 * </ul>
 */
public class NovaPlaceholderExpansion extends PlaceholderExpansion {

    private final StarTeleport plugin;

    public NovaPlaceholderExpansion(StarTeleport plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "novateleport";
    }

    @Override
    public String getAuthor() {
        return "NovaMC Labs";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        // 重载 PlaceholderAPI 时保留本扩展
        return true;
    }

    @Override
    public List<String> getPlaceholders() {
        return List.of("homes", "homes_max", "warps", "teleporting", "can_back", "death_back");
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) return null;
        String key = params.toLowerCase(java.util.Locale.ROOT);

        switch (key) {
            case "warps":
                return String.valueOf(plugin.getDataStore().listWarps().size());
            case "teleporting":
                return player == null ? "false" : String.valueOf(plugin.isTeleporting(player.getUniqueId()));
            case "can_back":
                return player == null ? "false"
                        : String.valueOf(plugin.getDataStore().getBack(player.getUniqueId()) != null);
            case "death_back":
                return player == null ? "false" : String.valueOf(hasDeathPoint(player));
            case "homes":
                return player == null ? "0"
                        : String.valueOf(plugin.getDataStore().listHomes(player.getUniqueId()).size());
            case "homes_max":
                org.bukkit.entity.Player online = player == null ? null : player.getPlayer();
                // 离线玩家没有有效权限集合，退回配置文件默认值
                return online == null
                        ? String.valueOf(plugin.getConfig().getInt("homes.default_limit", 1))
                        : String.valueOf(HomeLimitUtil.getHomeLimit(plugin, online));
            default:
                return null;
        }
    }

    private boolean hasDeathPoint(OfflinePlayer player) {
        org.bukkit.configuration.file.YamlConfiguration cfg =
                plugin.getDataStore().readPlayer(player.getUniqueId());
        return cfg.getConfigurationSection("death.last") != null;
    }
}
