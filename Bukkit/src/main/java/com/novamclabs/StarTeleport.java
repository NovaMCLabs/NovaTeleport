package com.novamclabs;

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
    private com.novamclabs.party.adapter.PartyAdapterManager extPartyAdapter;
    private boolean debug;
    private int teleportDelay;
    private com.novamclabs.storage.DataStore dataStore;
    private com.novamclabs.lang.LanguageManager lang;
    private com.novamclabs.animations.AnimationManager animationManager;
    private com.novamclabs.portals.PortalManager portalManager;
    private com.novamclabs.rtp.RtpPoolManager rtpPoolManager;
    private com.novamclabs.scrolls.ScrollManager scrollManager;
    private com.novamclabs.party.PartyManager partyManager;
    private com.novamclabs.scripting.ScriptingManager scriptingManager;
    private com.novamclabs.stele.SteleManager steleManager;
    private com.novamclabs.death.DeathManager deathManager;
    private com.novamclabs.cross.CrossServerService crossServerService;
    private com.novamclabs.offline.OfflineTeleportManager offlineTeleportManager;
    // 使用 ConcurrentHashMap 来避免并发问题 | Use concurrent map to avoid concurrency issues
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
        
        if (!sender.hasPermission("novateleport.command.reload")) {
            sender.sendMessage(lang.t("command.no_permission"));
            return true;
        }
        
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadPluginConfig();
            sender.sendMessage(lang.t("command.reload.success"));
            return true;
        }
        
        return false;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // 初始化语言系统
        this.lang = new com.novamclabs.lang.LanguageManager(this);
        this.lang.ensureDefaults("zh_CN","en_US");
        // 初始化数据存储 | init storage
        String serverName = getConfig().getString("network.server_name", "local");
        this.dataStore = new com.novamclabs.storage.DataStore(getDataFolder(), serverName);
        // Vault 经济初始化 | economy init
        com.novamclabs.util.EconomyUtil.setup(this);
        // 脚本管理 | scripting manager
        this.scriptingManager = new com.novamclabs.scripting.ScriptingManager(this);
        // 动画/传送门/RTP池/卷轴 初始化 | init subsystems
        this.animationManager = new com.novamclabs.animations.AnimationManager(this);
        this.portalManager = new com.novamclabs.portals.PortalManager(this);
        this.rtpPoolManager = new com.novamclabs.rtp.RtpPoolManager(this);
        this.scrollManager = new com.novamclabs.scrolls.ScrollManager(this);
        // 跨服服务（Redis 可选）| Cross-server service (Redis optional)
        this.crossServerService = new com.novamclabs.cross.CrossServerService(this);
        // 离线传送队列 | Offline teleport queue
        this.offlineTeleportManager = new com.novamclabs.offline.OfflineTeleportManager(this);
        // 死亡回溯 | death/back system
        this.deathManager = new com.novamclabs.death.DeathManager(this);
        // 传送石碑 | Teleportation Stele
        this.steleManager = new com.novamclabs.stele.SteleManager(this);

        loadConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getCommand("stp").setExecutor(this);
        // 注册传送相关命令 | Register commands
        com.novamclabs.commands.TeleportCommandHandler handler = new com.novamclabs.commands.TeleportCommandHandler(this);
        String[] cmds = {"tpa","tpahere","tpaccept","tpdeny","tpcancel","sethome","home","delhome","setwarp","warp","delwarp","spawn","back","rtp"};
        for (String c : cmds) {
            if (getCommand(c) != null) {
                getCommand(c).setExecutor(handler);
                getCommand(c).setTabCompleter(handler);
            }
        }
        if (getCommand("city") != null) {
            getCommand("city").setExecutor(new com.novamclabs.commands.CityCommand(this));
            if (getCommand("hub") != null) getCommand("hub").setExecutor(new com.novamclabs.commands.CityCommand(this));
        }
        // 额外命令
        if (getCommand("tpanimation") != null) {
            getCommand("tpanimation").setExecutor(new com.novamclabs.animations.AnimationCommand(this, animationManager));
        }
        if (getCommand("scroll") != null) {
            getCommand("scroll").setExecutor(new com.novamclabs.scrolls.ScrollCommand(this, scrollManager));
        }
        if (getCommand("novateleport") != null) {
            com.novamclabs.commands.BaseCommandRouter router = new com.novamclabs.commands.BaseCommandRouter(this, handler);
            getCommand("novateleport").setExecutor(router);
            getCommand("novateleport").setTabCompleter(router);
        }
        if (getCommand("party") != null) {
            // 内置组队系统 | built-in party system
            this.partyManager = new com.novamclabs.party.PartyManager(this);
            getCommand("party").setExecutor(new com.novamclabs.party.PartyCommand(this, partyManager));
        }
        // 外部队伍适配器 | external party adapter
        this.extPartyAdapter = new com.novamclabs.party.adapter.PartyAdapterManager();
        this.extPartyAdapter.detectAndRegister(this, () -> com.novamclabs.party.PartyNameDisplay.refreshAll(extPartyAdapter.getActive()));
        // 初始刷新 & 定时刷新（兜底）
        com.novamclabs.party.PartyNameDisplay.refreshAll(extPartyAdapter.getActive());
        getServer().getScheduler().runTaskTimer(this, () -> com.novamclabs.party.PartyNameDisplay.refreshAll(extPartyAdapter.getActive()), 200L, 200L);
        // 其它独立命令注册 | other commands
        if (getCommand("stele") != null) {
            getCommand("stele").setExecutor(new com.novamclabs.stele.SteleCommand(this, steleManager));
        }
        if (getCommand("deathback") != null) {
            getCommand("deathback").setExecutor(deathManager);
        }
        if (getCommand("forcetp") != null) {
            getCommand("forcetp").setExecutor(offlineTeleportManager);
        }
        getLogger().info(lang.t("plugin.startup"));
    }

    @Override
    public void onDisable() {
        // 取消所有待处理的传送任务
        taskMap.values().forEach(BukkitTask::cancel);
        taskMap.clear();
        canTriggerMap.clear();
        originalLocations.clear();
        getLogger().info(lang.t("plugin.shutdown"));
    }
    
    /**
     * 重新加载插件配置
     */
    private void reloadPluginConfig() {
        reloadConfig();
        // 重载语言与经济
        String lc = getConfig().getString("general.language", getConfig().getString("language", "zh_CN"));
        if (this.lang != null) this.lang.load(lc);
        com.novamclabs.util.EconomyUtil.setup(this);
        loadConfig();
    }
    
    /**
     * 加载配置文件
     */
    private void loadConfig() {
        // 语言
        String lc = getConfig().getString("general.language", getConfig().getString("language", "zh_CN"));
        if (this.lang != null) this.lang.load(lc);
        debug = getConfig().getBoolean("general.debug", getConfig().getBoolean(CONFIG_DEBUG, false));
        teleportDelay = getConfig().getInt("auto_world_teleport." + CONFIG_DELAY, getConfig().getInt(CONFIG_DELAY, 5));
        if (debug) {
            getLogger().info(lang.t("debug.enabled"));
            getLogger().info(lang.tr("debug.delay", "seconds", teleportDelay));
        }
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        if (!player.hasPermission("novateleport.pass")) {
            return;
        }

        // 检查是否在传送倒计时中
        if (taskMap.containsKey(player)) {
            // 只有当玩家移动了指定格数时才取消传送
            if (hasMovedFullBlock(event)) {
                cancelExistingTask(player, true);
                if (debug) {
                    getLogger().log(Level.INFO, lang.tr("debug.cancel_due_to_move", "player", player.getName()));
                }
                return;
            }
            if (debug) {
                getLogger().log(Level.INFO, lang.tr("debug.continue_due_to_small_move", "player", player.getName()));
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
            getLogger().log(Level.INFO, lang.tr("debug.move_distance", "player", player.getName(), "dx", String.format("%.2f", deltaX), "dz", String.format("%.2f", deltaZ)));
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
                player.sendTitle(lang.t("teleport.cancelled.title"), "", 10, 20, 10);
            }
        }
        // 清除原始位置记录
        originalLocations.remove(player);
    }
    
    /**
     * 查找适用的传送规则
     */
    private TeleportRule findTeleportRule(String currentWorldName) {
        ConfigurationSection rules = getConfig().getConfigurationSection("auto_world_teleport." + CONFIG_WORLDS);
        if (rules == null) {
            rules = getConfig().getConfigurationSection(CONFIG_WORLDS);
        }
        if (rules == null) {
            return null;
        }

        for (String key : rules.getKeys(false)) {
            ConfigurationSection rule = rules.getConfigurationSection(key);
            if (rule != null && currentWorldName.equals(rule.getString("world_from"))) {
                int defaultThreshold = getConfig().getInt("auto_world_teleport." + CONFIG_THRESHOLD, getConfig().getInt(CONFIG_THRESHOLD, -62));
                return new TeleportRule(
                    rule.getString("world_to"),
                    rule.getInt(CONFIG_THRESHOLD, defaultThreshold)
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
                boolean started = startTeleport(player, rule);
                if (started) {
                    canTriggerMap.put(player, false); // 防止重复触发
                }
            }
        } else {
            // 检查是否穿过阈值线（从一侧移动到另一侧）
            boolean crossedThresholdLine = isNegativeThreshold ? 
                (fromY <= rule.threshold && currentY > rule.threshold) :  // 负数阈值：从下往上穿过
                (fromY >= rule.threshold && currentY < rule.threshold);   // 正数阈值：从上往下穿过
                
            if (crossedThresholdLine) {
                canTriggerMap.put(player, true); // 允许再次触发
                if (debug) {
                    getLogger().log(Level.INFO, lang.tr("debug.cross_threshold", "player", player.getName()));
                }
            }
        }
    }
    
    /**
     * 开始传送流程
     * @return 是否成功开始（可用于控制重复触发）
     */
    private boolean startTeleport(Player player, TeleportRule rule) {
        World targetWorld = getServer().getWorld(rule.targetWorldName);
        if (targetWorld == null) {
            getLogger().log(Level.WARNING, lang.tr("warn.world_not_loaded", "world", rule.targetWorldName));
            return false;
        }

        // 经济扣费（自动阈值传送）
        double cost = com.novamclabs.util.EconomyUtil.getCost(this, "auto_world_teleport");
        if (!com.novamclabs.util.EconomyUtil.charge(this, player, cost)) {
            player.sendMessage(lang.tr("economy.not_enough", "amount", com.novamclabs.util.EconomyUtil.format(cost)));
            return false;
        }

        // 记录玩家开始传送时的位置
        originalLocations.put(player, player.getLocation().clone());
        // 记录/back 位置
        try {
            if (this.dataStore != null) {
                this.dataStore.setBack(player.getUniqueId(), player.getLocation());
            }
        } catch (Exception ignored) {}
        
        scheduleTeleport(player, targetWorld);
        
        if (debug) {
            getLogger().log(Level.INFO, lang.tr("debug.trigger_teleport", "player", player.getName(), "world", rule.targetWorldName,
                    "x", String.format("%.2f", player.getLocation().getX()),
                    "y", String.format("%.2f", player.getLocation().getY()),
                    "z", String.format("%.2f", player.getLocation().getZ())));
        }
        return true;
    }

    public com.novamclabs.storage.DataStore getDataStore() {
        return dataStore;
    }
    public com.novamclabs.lang.LanguageManager getLang() {
        return lang;
    }
    public com.novamclabs.animations.AnimationManager getAnimationManager() { return this.animationManager; }
    public com.novamclabs.rtp.RtpPoolManager getRtpPoolManager() { return this.rtpPoolManager; }
    public com.novamclabs.scripting.ScriptingManager getScriptingManager() { return this.scriptingManager; }
    public com.novamclabs.cross.CrossServerService getCrossServerService() { return this.crossServerService; }
    public com.novamclabs.party.adapter.PartyAdapterManager getExtPartyAdapter() { return this.extPartyAdapter; }
    public void setDebug(boolean enabled) { this.debug = enabled; }
    
    /**
     * 调度传送任务
     */
    private void scheduleTeleport(Player player, World targetWorld) {
        org.bukkit.Location target = targetWorld.getSpawnLocation();
        BukkitTask task = com.novamclabs.util.TeleportUtil.delayedTeleportWithAnimation(this, player, target, teleportDelay, () -> {
            // 传送完成后的回调
            BukkitTask t = taskMap.remove(player);
            if (t != null) t.cancel();
            originalLocations.remove(player);
            player.sendMessage(lang.t("teleport.completed"));
        });
        if (task != null) {
            taskMap.put(player, task);
        } else {
            // 无延迟直接传送
            player.teleport(target);
            player.sendMessage(lang.t("teleport.completed"));
        }
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
        player.sendMessage(lang.t("teleport.completed"));
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