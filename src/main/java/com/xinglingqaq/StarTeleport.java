package com.xinglingqaq;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.concurrent.ConcurrentHashMap;

public class StarTeleport extends JavaPlugin implements Listener, CommandExecutor {
    private boolean debug;
    private int teleportDelay;
    // 使用 ConcurrentHashMap 来避免并发问题
    private final Map<Player, BukkitTask> taskMap = new ConcurrentHashMap<>();
    // 记录玩家是否可以触发传送（用于控制重复触发）
    private final Map<Player, Boolean> canTriggerMap = new ConcurrentHashMap<>();
    // 记录玩家开始传送时的位置
    private final Map<Player, org.bukkit.Location> originalLocations = new ConcurrentHashMap<>();
    
    // 配置键常量
    private static final String CONFIG_DEBUG = "debug";
    private static final String CONFIG_DELAY = "delay_seconds";
    private static final String CONFIG_THRESHOLD = "threshold_y";
    private static final String CONFIG_WORLDS = "worlds";
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("stp")) {
            return false;
        }
        
        if (!sender.hasPermission("starteleport.command.reload")) {
            sender.sendMessage("§c你没有权限执行此命令！");
            return true;
        }
        
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadPluginConfig();
            sender.sendMessage("§a配置已成功重载！");
            return true;
        }
        
        return false;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("stp").setExecutor(this);
        getLogger().info("[XingLingQAQ]StarTeleport——已成功启动！");
    }

    @Override
    public void onDisable() {
        // 取消所有待处理的传送任务
        taskMap.values().forEach(BukkitTask::cancel);
        taskMap.clear();
        canTriggerMap.clear();
        originalLocations.clear();
        getLogger().info("[XingLingQAQ]StarTeleport——已成功卸载！");
    }
    
    /**
     * 重新加载插件配置
     */
    private void reloadPluginConfig() {
        reloadConfig();
        loadConfig();
    }
    
    /**
     * 加载配置文件
     */
    private void loadConfig() {
        debug = getConfig().getBoolean(CONFIG_DEBUG, false);
        teleportDelay = getConfig().getInt(CONFIG_DELAY, 5);
        if (debug) {
            getLogger().info("Debug 模式已启用");
            getLogger().info("传送延迟设置为: " + teleportDelay + "秒");
        }
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        if (!player.hasPermission("starteleport.pass")) {
            return;
        }

        // 检查是否在传送倒计时中
        if (taskMap.containsKey(player)) {
            // 只有当玩家移动了指定格数时才取消传送
            if (hasMovedFullBlock(event)) {
                cancelExistingTask(player, true);
                if (debug) {
                    getLogger().log(Level.INFO, "[调试] 玩家 {0} 移动超过2格，取消传送", player.getName());
                }
                return;
            }
            if (debug) {
                getLogger().log(Level.INFO, "[调试] 玩家 {0} 移动未超过2格，继续传送", player.getName());
            }
            // 如果只是微小移动，继续保持传送状态
            return;
        }

        // 检测三维坐标变化
        if (!hasPositionChanged(event)) {
            return;
        }

        World currentWorld = player.getWorld();
        TeleportRule teleportRule = findTeleportRule(currentWorld.getName());
        
        if (teleportRule == null) {
            return;
        }

        handleTeleport(player, teleportRule, event);
    }
    
    /**
     * 检查玩家是否移动了指定格数（2格）
     */
    private boolean hasMovedFullBlock(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        org.bukkit.Location originalLoc = originalLocations.get(player);
        
        // 如果没有原始位置记录，说明是新的传送，记录当前位置
        if (originalLoc == null) {
            originalLoc = event.getFrom().clone(); // 克隆位置以避免引用问题
            originalLocations.put(player, originalLoc);
            return false;
        }
        
        // 使用精确坐标计算移动距离
        double deltaX = Math.abs(event.getTo().getX() - originalLoc.getX());
        double deltaZ = Math.abs(event.getTo().getZ() - originalLoc.getZ());
        
        if (debug) {
            getLogger().log(Level.INFO, "[调试] 玩家 {0} 移动距离 - X: {1} 格, Z: {2} 格", 
                new Object[]{player.getName(), String.format("%.2f", deltaX), String.format("%.2f", deltaZ)});
        }
        
        // 如果任一方向移动超过2格，则取消传送
        return deltaX >= 2.0 || deltaZ >= 2.0;
    }
    
    /**
     * 检查玩家位置是否发生变化（包括微小移动）
     */
    private boolean hasPositionChanged(PlayerMoveEvent event) {
        return event.getFrom().getBlockX() != event.getTo().getBlockX() ||
               event.getFrom().getBlockY() != event.getTo().getBlockY() ||
               event.getFrom().getBlockZ() != event.getTo().getBlockZ();
    }
    
    /**
     * 取消玩家现有的传送任务
     * @param showTitle 是否显示取消传送的Title
     */
    private void cancelExistingTask(Player player, boolean showTitle) {
        BukkitTask existingTask = taskMap.remove(player);
        if (existingTask != null) {
            existingTask.cancel();
            if (showTitle) {
                player.sendTitle("§c传送取消!", "", 10, 20, 10);
            }
        }
        // 清除原始位置记录
        originalLocations.remove(player);
    }
    
    /**
     * 查找适用的传送规则
     */
    private TeleportRule findTeleportRule(String currentWorldName) {
        ConfigurationSection rules = getConfig().getConfigurationSection(CONFIG_WORLDS);
        if (rules == null) {
            return null;
        }

        for (String key : rules.getKeys(false)) {
            ConfigurationSection rule = rules.getConfigurationSection(key);
            if (rule != null && currentWorldName.equals(rule.getString("world_from"))) {
                return new TeleportRule(
                    rule.getString("world_to"),
                    rule.getInt(CONFIG_THRESHOLD, getConfig().getInt(CONFIG_THRESHOLD, -62))
                );
            }
        }
        
        return null;
    }
    
    /**
     * 处理传送逻辑
     */
    private void handleTeleport(Player player, TeleportRule rule, PlayerMoveEvent event) {
        int currentY = event.getTo().getBlockY();
        int fromY = event.getFrom().getBlockY();
        boolean isNegativeThreshold = rule.threshold < 0;
        
        // 检查是否在阈值位置移动
        if ((isNegativeThreshold && currentY <= rule.threshold) || 
            (!isNegativeThreshold && currentY >= rule.threshold)) {
            
            // 如果在阈值位置移动，取消传送但不显示Title
            if (fromY == currentY) {
                cancelExistingTask(player, false);
                return;
            }
            
            // 检查是否可以触发传送
            Boolean canTrigger = canTriggerMap.get(player);
            if (canTrigger == null || canTrigger) {
                // 首次触发或允许触发
                startTeleport(player, rule);
                canTriggerMap.put(player, false); // 防止重复触发
            }
        } else {
            // 检查是否穿过阈值线（从一侧移动到另一侧）
            boolean crossedThresholdLine = isNegativeThreshold ? 
                (fromY <= rule.threshold && currentY > rule.threshold) :  // 负数阈值：从下往上穿过
                (fromY >= rule.threshold && currentY < rule.threshold);   // 正数阈值：从上往下穿过
                
            if (crossedThresholdLine) {
                canTriggerMap.put(player, true); // 允许再次触发
                if (debug) {
                    getLogger().log(Level.INFO, "[调试] 玩家 {0} 穿过阈值线，允许再次触发传送", player.getName());
                }
            }
        }
    }
    
    /**
     * 开始传送流程
     */
    private void startTeleport(Player player, TeleportRule rule) {
        World targetWorld = getServer().getWorld(rule.targetWorldName);
        if (targetWorld == null) {
            getLogger().log(Level.WARNING, "目标世界 {0} 未正确加载！", rule.targetWorldName);
            return;
        }

        // 记录玩家开始传送时的位置
        originalLocations.put(player, player.getLocation().clone());
        
        scheduleTeleport(player, targetWorld);
        
        if (debug) {
            getLogger().log(Level.INFO, "[调试] 玩家 {0} 触发传送，目标世界：{1}，初始位置：({2}, {3}, {4})", 
                new Object[]{player.getName(), rule.targetWorldName, 
                    String.format("%.2f", player.getLocation().getX()),
                    String.format("%.2f", player.getLocation().getY()),
                    String.format("%.2f", player.getLocation().getZ())});
        }
    }
    
    /**
     * 调度传送任务
     */
    private void scheduleTeleport(Player player, World targetWorld) {
        BukkitTask task = getServer().getScheduler().runTaskTimer(this, new Runnable() {
            private int remaining = teleportDelay;
            
            @Override
            public void run() {
                if (remaining > 0) {
                    player.sendTitle("§e即将传送", "剩余 " + remaining + " 秒", 0, 20, 0);
                    remaining--;
                } else {
                    executeTeleport(player, targetWorld);
                }
            }
        }, 0L, 20L);
        
        taskMap.put(player, task);
    }
    
    /**
     * 执行传送
     */
    private void executeTeleport(Player player, World targetWorld) {
        BukkitTask task = taskMap.remove(player);
        if (task != null) {
            task.cancel();
        }
        
        // 清除原始位置记录
        originalLocations.remove(player);
        
        player.teleport(targetWorld.getSpawnLocation());
        player.sendMessage("§a传送完成！");
    }
    
    /**
     * 传送规则数据类
     */
    private static class TeleportRule {
        final String targetWorldName;
        final int threshold;
        
        TeleportRule(String targetWorldName, int threshold) {
            this.targetWorldName = targetWorldName;
            this.threshold = threshold;
        }
    }
}