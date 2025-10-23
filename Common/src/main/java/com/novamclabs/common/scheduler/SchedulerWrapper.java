package com.novamclabs.common.scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 统一调度器接口，抽象Bukkit/Folia调度差异
 * Unified scheduler interface abstracting Bukkit/Folia scheduling differences
 */
public interface SchedulerWrapper {
    
    /**
     * 在下一个 tick 执行任务
     * Execute task on next tick
     */
    void runNextTick(Runnable task);
    
    /**
     * 异步执行任务
     * Execute task asynchronously
     */
    void runAsync(Runnable task);
    
    /**
     * 延迟执行任务（主线程）
     * Execute task with delay (main thread)
     */
    ScheduledTask runLater(Runnable task, long delayTicks);
    
    /**
     * 异步延迟执行任务
     * Execute task asynchronously with delay
     */
    ScheduledTask runLaterAsync(Runnable task, long delay, TimeUnit unit);
    
    /**
     * 定时重复执行任务（主线程）
     * Execute task repeatedly (main thread)
     */
    ScheduledTask runTimer(Runnable task, long delayTicks, long periodTicks);
    
    /**
     * 异步定时重复执行任务
     * Execute task repeatedly asynchronously
     */
    ScheduledTask runTimerAsync(Runnable task, long delay, long period, TimeUnit unit);
    
    /**
     * 在实体的区域执行任务（Folia）或主线程（Bukkit）
     * Execute task at entity's region (Folia) or main thread (Bukkit)
     */
    void runAtEntity(Object entity, Runnable task);
    
    /**
     * 在指定位置执行任务（Folia）或主线程（Bukkit）
     * Execute task at location (Folia) or main thread (Bukkit)
     */
    void runAtLocation(Object world, int x, int y, int z, Runnable task);
    
    /**
     * 传送实体（异步安全）
     * Teleport entity (async-safe)
     */
    CompletableFuture<Boolean> teleportAsync(Object entity, Object location);
    
    /**
     * 取消所有任务
     * Cancel all tasks
     */
    void cancelAllTasks();
    
    /**
     * 检查是否运行在 Folia
     * Check if running on Folia
     */
    boolean isFolia();
    
    /**
     * 调度任务句柄
     * Scheduled task handle
     */
    interface ScheduledTask {
        void cancel();
        boolean isCancelled();
    }
}
