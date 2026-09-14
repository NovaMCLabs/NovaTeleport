package com.novamclabs.combat;

import com.novamclabs.StarTeleport;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 战斗标签与「受伤打断倒计时」。
 *
 * 这是两个独立开关：
 * <ul>
 *   <li>{@code combat_tag} —— 战斗中禁止<b>发起</b>传送。判定是双向的：玩家造成或受到伤害
 *       都算进入战斗（含玩家↔生物），因此既堵住「被追着打时传送逃跑」，
 *       也堵住「打一下就跑」。</li>
 *   <li>{@code damage_interrupt} —— 受伤时打断<b>进行中</b>的倒计时。即使战斗标签关闭，
 *       这一半也生效。</li>
 * </ul>
 *
 * 两者默认都关闭：纯便利型/建筑服通常不希望传送被战斗状态限制。
 *
 * 状态只放在内存里（{@link ConcurrentHashMap}），退出游戏时清理。伤害事件在受害者的区域线程
 * 触发，本类不触碰任何世界/区块 API，因此在 Folia 下也是安全的。
 */
public class CombatManager implements Listener {

    private final StarTeleport plugin;

    /** UUID → 战斗状态结束时间（毫秒时间戳） */
    private final Map<UUID, Long> combatUntil = new ConcurrentHashMap<>();

    private volatile boolean enabled;
    private volatile int durationSeconds;
    private volatile boolean refreshOnDamage;
    private volatile String bypassPermission = "";
    private volatile List<String> exemptTypes = List.of();

    private volatile boolean interruptEnabled;
    private volatile String interruptBypassPermission = "";
    private volatile List<String> interruptExemptTypes = List.of();

    public CombatManager(StarTeleport plugin) {
        this.plugin = plugin;
        reload();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("combat_tag.enabled", false);
        this.durationSeconds = Math.max(0, plugin.getConfig().getInt("combat_tag.duration_seconds", 15));
        this.refreshOnDamage = plugin.getConfig().getBoolean("combat_tag.refresh_on_damage", true);
        this.bypassPermission = plugin.getConfig().getString("combat_tag.bypass_permission", "");
        this.exemptTypes = lower(plugin.getConfig().getStringList("combat_tag.exempt_types"));

        this.interruptEnabled = plugin.getConfig().getBoolean("damage_interrupt.enabled", false);
        this.interruptBypassPermission = plugin.getConfig().getString("damage_interrupt.bypass_permission", "");
        this.interruptExemptTypes = lower(plugin.getConfig().getStringList("damage_interrupt.exempt_types"));
    }

    private static List<String> lower(List<String> raw) {
        if (raw == null) return List.of();
        return raw.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .toList();
    }

    // ==================== 事件 ====================

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        // 受伤打断倒计时（独立于战斗标签）
        if (interruptEnabled && event.getEntity() instanceof Player victim) {
            interruptPendingTeleport(victim);
        }

        if (!enabled || durationSeconds <= 0) return;
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) return;

        Entity damager = resolveDamager(byEntity.getDamager());
        if (damager == null) return;

        // 双向：被打的玩家标记，动手的玩家也标记
        if (event.getEntity() instanceof Player damaged) {
            tag(damaged);
        }
        if (damager instanceof Player attacker) {
            tag(attacker);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        combatUntil.remove(event.getPlayer().getUniqueId());
    }

    /** 解包投掷物与 TNT，取出真正的动手方 */
    private Entity resolveDamager(Entity damager) {
        if (damager instanceof Projectile projectile) {
            Object shooter = projectile.getShooter();
            return shooter instanceof Entity shooterEntity ? shooterEntity : null;
        }
        if (damager instanceof TNTPrimed tnt) {
            return tnt.getSource();
        }
        return damager;
    }

    private void interruptPendingTeleport(Player victim) {
        // 只有倒计时中的传送才会被 track，立即传送没有可打断的东西
        String type = plugin.getPendingTeleportType(victim.getUniqueId());
        if (type == null) return;
        if (interruptExemptTypes.contains(type.toLowerCase(Locale.ROOT))) return;
        if (!interruptBypassPermission.isEmpty() && victim.hasPermission(interruptBypassPermission)) return;

        plugin.cancelTeleport(victim, true);
        victim.sendMessage(plugin.getLang().t("teleport.cancelled.damage"));
    }

    // ==================== 查询 ====================

    /**
     * 标记玩家进入战斗。
     * 只在「从非战斗进入战斗」时发提示，避免每次受击都刷屏。
     */
    public void tag(Player player) {
        if (!enabled || durationSeconds <= 0) return;

        long now = System.currentTimeMillis();
        long until = now + durationSeconds * 1000L;

        Long existing = combatUntil.get(player.getUniqueId());
        boolean wasTagged = existing != null && existing > now;
        if (wasTagged && !refreshOnDamage) return;

        combatUntil.put(player.getUniqueId(), until);
        if (!wasTagged) {
            player.sendMessage(plugin.getLang().tr("combat.tag.start", "seconds", durationSeconds));
        }
    }

    /**
     * 该传送类型是否因战斗标签被拦截。
     *
     * @return 剩余秒数；0 表示放行（功能关闭 / 该类型豁免 / 有 bypass 权限 / 不在战斗中）
     */
    public long remainingSeconds(Player player, String type) {
        if (!enabled || durationSeconds <= 0) return 0;
        if (type != null && exemptTypes.contains(type.toLowerCase(Locale.ROOT))) return 0;
        if (!bypassPermission.isEmpty() && player.hasPermission(bypassPermission)) return 0;

        Long until = combatUntil.get(player.getUniqueId());
        if (until == null) return 0;

        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0) {
            combatUntil.remove(player.getUniqueId());
            return 0;
        }
        return (remaining + 999) / 1000;
    }

    public boolean isTagged(Player player) {
        if (!enabled) return false;
        Long until = combatUntil.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }
}
