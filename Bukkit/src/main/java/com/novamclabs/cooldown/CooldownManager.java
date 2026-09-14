package com.novamclabs.cooldown;

import com.novamclabs.StarTeleport;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按传送类型分别配置的冷却。
 *
 * 默认关闭。检查发生在传送发起时，登记发生在传送<b>真正执行之后</b>
 * （{@code TeleportUtil.execute}）——因此倒计时被移动/受伤取消、或校验与扣费失败，
 * 都不会消耗冷却。这与「费用在真正执行时才扣」是同一套设计。
 *
 * 跨服传送不经过 {@code TeleportUtil}，由调用方自行检查与登记。
 */
public class CooldownManager implements Listener {

    private final StarTeleport plugin;

    /** 传送类型 → (玩家 → 冷却结束时间毫秒戳) */
    private final Map<String, Map<UUID, Long>> byType = new ConcurrentHashMap<>();

    private volatile boolean enabled;
    private volatile String bypassPermission = "";
    /** 传送类型 → 冷却秒数（只包含配置里显式写了的类型） */
    private volatile Map<String, Long> seconds = Map.of();

    public CooldownManager(StarTeleport plugin) {
        this.plugin = plugin;
        reload();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("teleport_cooldowns.enabled", false);
        this.bypassPermission = plugin.getConfig().getString("teleport_cooldowns.bypass_permission", "");

        Map<String, Long> parsed = new ConcurrentHashMap<>();
        org.bukkit.configuration.ConfigurationSection section =
                plugin.getConfig().getConfigurationSection("teleport_cooldowns.types");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                long value = section.getLong(key, 0L);
                if (value > 0) parsed.put(key.toLowerCase(Locale.ROOT), value);
            }
        }
        this.seconds = parsed;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        for (Map<UUID, Long> perType : byType.values()) {
            perType.remove(uuid);
        }
    }

    /**
     * 该传送类型的剩余冷却秒数。
     *
     * @return 0 表示可以传送（功能关闭 / 该类型没配冷却 / 有 bypass 权限 / 冷却已过）
     */
    public long remainingSeconds(Player player, String type) {
        if (!enabled || type == null || type.isBlank()) return 0;

        String key = type.toLowerCase(Locale.ROOT);
        Long configured = seconds.get(key);
        if (configured == null || configured <= 0) return 0;
        if (!bypassPermission.isEmpty() && player.hasPermission(bypassPermission)) return 0;

        Map<UUID, Long> perType = byType.get(key);
        if (perType == null) return 0;

        Long until = perType.get(player.getUniqueId());
        if (until == null) return 0;

        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0) {
            perType.remove(player.getUniqueId());
            return 0;
        }
        return (remaining + 999) / 1000;
    }

    /** 传送成功后登记冷却。由 {@code TeleportUtil.execute} 与跨服分支调用。 */
    public void record(Player player, String type) {
        if (!enabled || type == null || type.isBlank()) return;

        String key = type.toLowerCase(Locale.ROOT);
        Long configured = seconds.get(key);
        if (configured == null || configured <= 0) return;

        byType.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
                .put(player.getUniqueId(), System.currentTimeMillis() + configured * 1000L);
    }

    /** 该类型是否配置了冷却（用于跳过无意义的检查） */
    public boolean hasCooldown(String type) {
        return enabled && type != null && seconds.containsKey(type.toLowerCase(Locale.ROOT));
    }
}
