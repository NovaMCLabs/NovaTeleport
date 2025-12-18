package com.novamclabs.scheduler;

import com.novamclabs.common.scheduler.SchedulerWrapper;
import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * FoliaLib 调度器包装器
 * FoliaLib scheduler wrapper
 */
public class FoliaScheduler implements SchedulerWrapper {
    private final FoliaLib foliaLib;
    
    public FoliaScheduler(JavaPlugin plugin) {
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
        WrappedTask wrapped = foliaLib.getScheduler().runLater(task, delayTicks);
        return new FoliaScheduledTask(wrapped);
    }
    
    @Override
    public ScheduledTask runLaterAsync(Runnable task, long delay, TimeUnit unit) {
        WrappedTask wrapped = foliaLib.getScheduler().runLaterAsync(task, delay, unit);
        return new FoliaScheduledTask(wrapped);
    }
    
    @Override
    public ScheduledTask runTimer(Runnable task, long delayTicks, long periodTicks) {
        WrappedTask wrapped = foliaLib.getScheduler().runTimer(task, delayTicks, periodTicks);
        return new FoliaScheduledTask(wrapped);
    }
    
    @Override
    public ScheduledTask runTimerAsync(Runnable task, long delay, long period, TimeUnit unit) {
        WrappedTask wrapped = foliaLib.getScheduler().runTimerAsync(task, delay, period, unit);
        return new FoliaScheduledTask(wrapped);
    }
    
    @Override
    public void runAtEntity(Object entity, Runnable task) {
        if (entity instanceof Entity) {
            foliaLib.getScheduler().runAtEntity((Entity) entity, wrappedTask -> task.run());
        } else {
            runNextTick(task);
        }
    }
    
    @Override
    public void runAtLocation(Object world, int x, int y, int z, Runnable task) {
        if (world instanceof org.bukkit.World) {
            Location loc = new Location((org.bukkit.World) world, x, y, z);
            foliaLib.getScheduler().runAtLocation(loc, wrappedTask -> task.run());
        } else {
            runNextTick(task);
        }
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
