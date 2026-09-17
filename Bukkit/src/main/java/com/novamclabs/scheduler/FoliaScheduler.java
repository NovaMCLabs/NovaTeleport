package com.novamclabs.scheduler;

import com.novamclabs.common.scheduler.SchedulerWrapper;
import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * FoliaLib 调度器包装器
 * FoliaLib scheduler wrapper
 */
public class FoliaScheduler implements SchedulerWrapper {
    private final JavaPlugin plugin;
    private final FoliaLib foliaLib;

    /** 「任务被丢弃」每个调度入口只报警一次，避免刷屏 | one warning per entry point, not per drop */
    private final AtomicBoolean entityTaskDropLogged = new AtomicBoolean();
    private final AtomicBoolean locationTaskDropLogged = new AtomicBoolean();

    public FoliaScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.foliaLib = new FoliaLib(plugin);
    }

    @Override
    public void runNextTick(Runnable task) {
        foliaLib.getScheduler().runNextTick(wrappedTask -> task.run());
    }

    @Override
    public void runAsync(Runnable task) {
        foliaLib.getScheduler().runAsync(wrappedTask -> task.run());
    }

    @Override
    public ScheduledTask runLater(Runnable task, long delayTicks) {
        return new FoliaScheduledTask(foliaLib.getScheduler().runLater(task, delayTicks));
    }

    @Override
    public ScheduledTask runLaterAsync(Runnable task, long delay, TimeUnit unit) {
        return new FoliaScheduledTask(foliaLib.getScheduler().runLaterAsync(task, delay, unit));
    }

    @Override
    public ScheduledTask runTimer(Runnable task, long delayTicks, long periodTicks) {
        return new FoliaScheduledTask(foliaLib.getScheduler().runTimer(task, delayTicks, periodTicks));
    }

    @Override
    public ScheduledTask runTimerAsync(Runnable task, long delay, long period, TimeUnit unit) {
        return new FoliaScheduledTask(foliaLib.getScheduler().runTimerAsync(task, delay, period, unit));
    }

    @Override
    public void runAtEntity(Object entity, Runnable task) {
        runAtEntityLive(entity, task, null);
    }

    /**
     * 与 {@link #runAtEntity} 相同，但额外报告「任务到底有没有跑」。
     *
     * FoliaLib 的 future 无法用来判断丢弃：它的 complete() 只写在 lambda 内部，
     * 任务被丢弃时 future 根本不会完成（不是异常完成），SCHEDULER_RETIRED 也只在
     * EntityScheduler.run 返回 null 时出现；而 ENTITY_RETIRED 需要 Paper 的 retired
     * 回调，FoliaLib 传的是 null，因此永远不会产生。跨区域调用或玩家已下线时就会
     * 命中这个「静默丢弃」——回调再也不来，传送/表单提示无声消失。
     *
     * 这里改为「调用后一 tick 检查任务是否未运行」：任务一旦执行，task 会同步设置 live 标记；
     * 被丢弃时不会执行，下一 tick 即报出 dropped=true 并调用 onDropped 兜底。
     *
     * FoliaLib's future cannot detect a dropped task (complete() runs inside the lambda), so a
     * cross-region or retired-entity call would silently never invoke the callback. Detect it by
     * checking whether the task ran by the following tick instead.
     */
    public void runAtEntityLive(Object entity, Runnable task, Consumer<Boolean> onDropped) {
        if (!(entity instanceof Entity)) {
            runNextTick(task);
            return;
        }
        // Folia 的 EntityScheduler 只接受“正在拥有该实体”的线程调用，否则任务被丢弃 → 改为调度到实体区域
        if (foliaLib.isFolia() && !foliaLib.getScheduler().isOwnedByCurrentRegion((Entity) entity)) {
            foliaLib.getScheduler().runAtEntity((Entity) entity, wrappedTask -> task.run());
            return;
        }
        AtomicBoolean executed = new AtomicBoolean();
        foliaLib.getScheduler().runAtEntity((Entity) entity, wrappedTask -> {
            executed.set(true);
            task.run();
        });
        foliaLib.getScheduler().runNextTick(wrappedTask -> {
            if (!executed.get()) reportDropped("runAtEntity", onDropped);
        });
    }

    /** 任务被确认未执行时的兜底：默认只告警一次，特定调用点可传回调自行恢复 */
    private void reportDropped(String where, Consumer<Boolean> onDropped) {
        if (onDropped != null) {
            onDropped.accept(false);
            return;
        }
        AtomicBoolean logged = "runAtLocation".equals(where) ? locationTaskDropLogged : entityTaskDropLogged;
        if (!logged.compareAndSet(false, true)) return;
        plugin.getLogger().warning("[Scheduler] " + where + " task never ran: the action was dropped "
                + "(entity or region no longer scheduled). Further drops here are not logged.");
    }

    @Override
    public void runAtLocation(Object world, int x, int y, int z, Runnable task) {
        if (world instanceof org.bukkit.World) {
            runAtLocationLive((org.bukkit.World) world, x, y, z, task, null);
        } else {
            runNextTick(task);
        }
    }

    /**
     * 与 {@link #runAtLocation} 相同，但额外报告「任务到底有没有跑」。
     * RegionScheduler 在区块/区域已卸载时会直接丢弃任务，FoliaLib 的 future 同样不会完成，
     * 因此与 {@link #runAtEntityLive} 一样用「下一 tick 检查是否执行过」来判定。
     * The region scheduler drops the task when the chunk/region is gone and FoliaLib's future never
     * completes, so detect it the same way as {@link #runAtEntityLive}.
     */
    public void runAtLocationLive(org.bukkit.World world, int x, int y, int z, Runnable task,
                                  Consumer<Boolean> onDropped) {
        AtomicBoolean executed = new AtomicBoolean();
        Location loc = new Location(world, x, y, z);
        foliaLib.getScheduler().runAtLocation(loc, wrappedTask -> {
            executed.set(true);
            task.run();
        });
        foliaLib.getScheduler().runNextTick(wrappedTask -> {
            if (!executed.get()) reportDropped("runAtLocation", onDropped);
        });
    }

    /**
     * 当前线程是否拥有该位置所属区域。非 Folia 平台退化为「是否主线程」，
     * 因此调用方可以据此决定「就地执行」还是「跳到目标区域」。
     * Whether the current thread owns the region containing the location (primary thread on non-Folia).
     */
    public boolean isOwnedByCurrentThread(Location location) {
        return location != null && location.getWorld() != null
                && foliaLib.getScheduler().isOwnedByCurrentRegion(location);
    }

    @Override
    public ScheduledTask runAtLocationLater(Object world, int x, int y, int z, Runnable task, long delayTicks) {
        if (world instanceof org.bukkit.World) {
            Location loc = new Location((org.bukkit.World) world, x, y, z);
            return new FoliaScheduledTask(foliaLib.getScheduler().runAtLocationLater(loc, task, delayTicks));
        }
        return runLater(task, delayTicks);
    }

    @Override
    public ScheduledTask runAtEntityTimer(Object entity, Runnable task, long delayTicks, long periodTicks) {
        if (entity instanceof Entity) {
            return new FoliaScheduledTask(foliaLib.getScheduler().runAtEntityTimer((Entity) entity, task, delayTicks, periodTicks));
        }
        return runTimer(task, delayTicks, periodTicks);
    }

    @Override
    public ScheduledTask runAtLocationTimer(Object world, int x, int y, int z, Runnable task, long delayTicks, long periodTicks) {
        if (world instanceof org.bukkit.World) {
            Location loc = new Location((org.bukkit.World) world, x, y, z);
            return new FoliaScheduledTask(foliaLib.getScheduler().runAtLocationTimer(loc, task, delayTicks, periodTicks));
        }
        return runTimer(task, delayTicks, periodTicks);
    }

    @Override
    public CompletableFuture<Boolean> teleportAsync(Object entity, Object location) {
        if (entity instanceof Entity && location instanceof Location) {
            return foliaLib.getScheduler().teleportAsync((Entity) entity, (Location) location);
        }
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public void cancelAllTasks() {
        foliaLib.getScheduler().cancelAllTasks();
    }

    @Override
    public boolean isFolia() {
        return foliaLib.isFolia();
    }

    /**
     * FoliaLib 任务包装
     * FoliaLib task wrapper
     */
    private static class FoliaScheduledTask implements ScheduledTask {
        private final WrappedTask wrapped;

        public FoliaScheduledTask(WrappedTask wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public void cancel() {
            if (wrapped != null) {
                wrapped.cancel();
            }
        }

        @Override
        public boolean isCancelled() {
            return wrapped == null || wrapped.isCancelled();
        }
    }
}
