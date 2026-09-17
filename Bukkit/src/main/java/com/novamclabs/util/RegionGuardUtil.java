package com.novamclabs.util;

import com.novamclabs.StarTeleport;
import com.novamclabs.common.scheduler.SchedulerWrapper;
import com.novamclabs.region.RegionAdapterManager;
import com.novamclabs.scheduler.FoliaScheduler;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Region protection integration entrypoint.
 *
 * TeleportUtil calls this helper to decide whether a destination is allowed.
 */
public final class RegionGuardUtil {
    private static volatile RegionAdapterManager manager;

    private RegionGuardUtil() {
    }

    /** 目标位置被拒绝的原因 | why a destination was rejected */
    public enum Denial {
        /** 通过 | allowed */
        NONE(null),
        /** 缺少空间锚点 | spatial anchor missing */
        ANCHOR_MISSING("anchor.missing"),
        /** 领地保护拒绝 | blocked by region protection */
        REGION_DENIED("command.no_permission");

        private final String langKey;

        Denial(String langKey) {
            this.langKey = langKey;
        }

        /** 提示玩家用的 lang key | lang key for the caller to message the player */
        public String langKey() {
            return langKey;
        }
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

    /**
     * 查询领地适配器是否放行。适配器可能读目标位置的区块状态，因此在 Folia 上同样应当
     * 在目标所属区域线程调用（直接调用请用 {@link #checkDestination}）。
     * Adapters may read block/chunk state at the destination, so on Folia call this on the
     * destination's owning region thread — use {@link #checkDestination} instead.
     */
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

    /**
     * 传送前校验目标位置：空间锚点 + 领地保护。
     *
     * 两项校验都要读目标位置的方块与区块状态，因此在 Folia 上必须跑在目标所属区域线程：
     * 在移动者线程上读是跨区域访问（Folia 会抛异常），领地适配器还可能顺带强制加载目标区块。
     * 结果经 {@code result} 回传，且保证在**玩家所属区域线程**上回调（非 Folia 即主线程），
     * 这样调用方可以在回调里继续扣费/提示/传送 —— 校验未通过前不会发生任何扣费。
     *
     * 目标与玩家都归当前线程所属区域时直接同步回调，因此 Spigot/Paper（永远主线程）以及
     * Folia 上同区域的传送与旧行为逐字节一致，只有真正跨区域时才多跳一次调度。
     * Runs the anchor + region guard checks on the region owning the destination; the callback always
     * fires on the player's owning region (inline on single-threaded platforms, so behaviour is unchanged).
     */
    public static void checkDestination(StarTeleport plugin, Player player, Location target, String type,
                                        Consumer<Denial> result) {
        if (target == null || target.getWorld() == null || player == null) {
            result.accept(Denial.NONE);
            return;
        }

        SchedulerWrapper scheduler = plugin == null ? null : plugin.getScheduler();
        if (!(scheduler instanceof FoliaScheduler)) {
            // 非本插件的调度器实现：保持同步语义
            result.accept(check(plugin, player, target, type));
            return;
        }

        FoliaScheduler folia = (FoliaScheduler) scheduler;
        Location playerLoc = player.getLocation();
        if (folia.isOwnedByCurrentThread(target) && folia.isOwnedByCurrentThread(playerLoc)) {
            result.accept(check(plugin, player, target, type));
            return;
        }

        // 跨区域：把真正的方块/区块读取挪到目标所属区域线程
        AtomicBoolean decided = new AtomicBoolean();
        Consumer<Denial> once = denial -> {
            if (decided.compareAndSet(false, true)) result.accept(denial);
        };
        Consumer<Boolean> fallback = ran -> {
            // 目标区域已卸载/任务被丢弃：退回「在调用方线程上直接校验」，
            // 与本次改动前的行为一致 —— 宁可按旧语义放行/拒绝，也不要永远不回调导致传送静默消失。
            if (!ran) once.accept(check(plugin, player, target, type));
        };

        folia.runAtLocationLive(target.getWorld(), target.getBlockX(), target.getBlockY(), target.getBlockZ(), () -> {
            Denial denial = check(plugin, player, target, type);
            // 校验已在目标区域完成，结果本身与线程无关；玩家可能已经移进目标区域，那时就地回调
            if (folia.isOwnedByCurrentThread(player.getLocation())) {
                once.accept(denial);
            } else {
                folia.runAtEntityLive(player, () -> once.accept(denial), fallback);
            }
        }, fallback);
    }

    /** 真正的校验，必须在目标所属区域线程上调用 | the real check; must run on the destination's region thread */
    private static Denial check(StarTeleport plugin, Player player, Location target, String type) {
        if (plugin != null && SpatialAnchorUtil.isRequired(plugin, type)
                && !SpatialAnchorUtil.hasAnchor(plugin, target)) {
            return Denial.ANCHOR_MISSING;
        }
        if (!canEnter(player, target)) {
            return Denial.REGION_DENIED;
        }
        return Denial.NONE;
    }
}
