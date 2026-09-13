package com.novamclabs;

import com.novamclabs.common.scheduler.SchedulerWrapper;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.concurrent.ConcurrentHashMap;

public class StarTeleport extends JavaPlugin implements Listener, CommandExecutor {
    private boolean debug;
    private int teleportDelay;
    private SchedulerWrapper scheduler;
    private com.novamclabs.storage.DataStore dataStore;
    private com.novamclabs.lang.LanguageManager lang;
    private com.novamclabs.animations.AnimationManager animationManager;
    private com.novamclabs.portals.PortalManager portalManager;
    private com.novamclabs.rtp.RtpPoolManager rtpPoolManager;
    private com.novamclabs.scrolls.ScrollManager scrollManager;
    private com.novamclabs.party.PartyManager partyManager;
    private com.novamclabs.party.adapter.PartyAdapterManager partyAdapterManager;
    private com.novamclabs.menu.JavaMenuConfig javaMenus;
    private com.novamclabs.scripting.ScriptingManager scriptingManager;
    private com.novamclabs.stele.SteleManager steleManager;
    private com.novamclabs.death.DeathManager deathManager;
    private com.novamclabs.cross.CrossServerService crossServerService;
    private com.novamclabs.offline.OfflineTeleportManager offlineTeleportManager;

    // Optional feature managers
    private com.novamclabs.guild.GuildManager guildManager;
    private com.novamclabs.guild.GuildWarpManager guildWarpManager;
    private com.novamclabs.toll.TollWarpManager tollWarpManager;
    private com.novamclabs.towny.TownyTeleportManager townyTeleportManager;
    private com.novamclabs.log.TeleportLogManager teleportLogManager;

    /** PlaceholderAPI 扩展（服务端未安装时为 null）| null unless PlaceholderAPI is installed */
    private com.novamclabs.hook.NovaPlaceholderExpansion placeholderExpansion;

    // 配置键常量
    private static final String CONFIG_DEBUG = "debug";
    private static final String CONFIG_DELAY = "delay_seconds";
    private static final String CONFIG_THRESHOLD = "threshold_y";
    private static final String CONFIG_WORLDS = "worlds";

    /**
     * 一次待执行的传送（倒计时中）。
     * A pending (counting down) teleport.
     */
    public static final class TeleportSession {
        final SchedulerWrapper.ScheduledTask task;
        final Location origin;
        final boolean cancelOnMove;
        final String type;

        TeleportSession(SchedulerWrapper.ScheduledTask task, Location origin, boolean cancelOnMove, String type) {
            this.task = task;
            this.origin = origin;
            this.cancelOnMove = cancelOnMove;
            this.type = type;
        }
    }

    // 所有待执行传送（含自动世界传送与命令传送），用 UUID 作 key 避免 Player 引用泄漏
    private final Map<UUID, TeleportSession> sessions = new ConcurrentHashMap<>();
    // 自动世界传送：是否允许再次触发
    private final Map<UUID, Boolean> canTriggerMap = new ConcurrentHashMap<>();

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
        // 调度器必须最先创建：其他管理器在构造期即会用它 | scheduler must exist before other managers
        this.scheduler = new com.novamclabs.scheduler.FoliaScheduler(this);
        getLogger().info("[Scheduler] Folia=" + scheduler.isFolia());

        // 初始化语言系统
        this.lang = new com.novamclabs.lang.LanguageManager(this);
        this.lang.ensureDefaults("zh_CN","en_US");

        // Java 菜单配置（独立文件）
        this.javaMenus = new com.novamclabs.menu.JavaMenuConfig(this);

        // 传送日志
        this.teleportLogManager = new com.novamclabs.log.TeleportLogManager(this);

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
        // 传送石碑 | Teleportation Stele
        this.steleManager = new com.novamclabs.stele.SteleManager(this);
        // 死亡回溯 | death/back system
        this.deathManager = new com.novamclabs.death.DeathManager(this);

        loadConfig();

        // 初始化领地保护适配器
        com.novamclabs.util.RegionGuardUtil.init(this);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getCommand("stp").setExecutor(this);
        // 注册传送相关命令 | Register commands
        com.novamclabs.commands.TeleportCommandHandler handler = new com.novamclabs.commands.TeleportCommandHandler(this);
        String[] cmds = {"tpa","tpahere","tpaccept","tpdeny","tpcancel","sethome","home","delhome","homes","setwarp","warp","delwarp","warps","spawn","back","rtp","rtpgui","tpmenu"};
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
        // 外部队伍适配器 | external party adapter
        this.partyAdapterManager = new com.novamclabs.party.adapter.PartyAdapterManager();
        this.partyAdapterManager.detectAndRegister(this, this::refreshPartyDisplay);
        refreshPartyDisplay();
        scheduler.runTimer(this::refreshPartyDisplay, 200L, 200L);

        if (getCommand("party") != null) {
            // 内置组队系统 | built-in party system
            this.partyManager = new com.novamclabs.party.PartyManager(this);
            getCommand("party").setExecutor(new com.novamclabs.party.PartyCommand(this, partyManager, this.partyAdapterManager));
        }
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

        // 工会统一传送
        this.guildManager = new com.novamclabs.guild.GuildManager(this);
        this.guildWarpManager = new com.novamclabs.guild.GuildWarpManager(this, this.guildManager);
        if (getCommand("gtp") != null) {
            com.novamclabs.guild.GuildCommand gcmd = new com.novamclabs.guild.GuildCommand(this, this.guildManager, this.guildWarpManager);
            getCommand("gtp").setExecutor(gcmd);
            getCommand("gtp").setTabCompleter(gcmd);
        }

        // Towny 城镇传送
        this.townyTeleportManager = new com.novamclabs.towny.TownyTeleportManager(this);
        if (getCommand("towntp") != null) {
            com.novamclabs.towny.TownyCommand tcmd = new com.novamclabs.towny.TownyCommand(this, this.townyTeleportManager);
            getCommand("towntp").setExecutor(tcmd);
            getCommand("towntp").setTabCompleter(tcmd);
        }

        // 付费传送点
        this.tollWarpManager = new com.novamclabs.toll.TollWarpManager(this);
        if (getCommand("tollwarp") != null) {
            com.novamclabs.toll.TollWarpCommand twcmd = new com.novamclabs.toll.TollWarpCommand(this, this.tollWarpManager);
            getCommand("tollwarp").setExecutor(twcmd);
            getCommand("tollwarp").setTabCompleter(twcmd);
            getServer().getPluginManager().registerEvents(twcmd, this);
        }

        // 传送日志 / 回溯
        if (getCommand("tplog") != null) {
            com.novamclabs.log.TeleportLogCommand lcmd = new com.novamclabs.log.TeleportLogCommand(this, this.teleportLogManager);
            getCommand("tplog").setExecutor(lcmd);
            getCommand("tplog").setTabCompleter(lcmd);
            getServer().getPluginManager().registerEvents(lcmd, this);
        }

        registerPlaceholderExpansion();

        getLogger().info(lang.t("plugin.startup"));
    }

    /**
     * 注册 PlaceholderAPI 扩展（软依赖；未安装时跳过）。
     * PlaceholderAPI 是编译期 provided 依赖，类缺失时不能让整个插件加载失败。
     */
    private void registerPlaceholderExpansion() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) return;
        try {
            this.placeholderExpansion = new com.novamclabs.hook.NovaPlaceholderExpansion(this);
            if (this.placeholderExpansion.register()) {
                getLogger().info("[PlaceholderAPI] Registered expansion: %novateleport_<key>%");
            } else {
                this.placeholderExpansion = null;
            }
        } catch (Throwable t) {
            this.placeholderExpansion = null;
            getLogger().warning("[PlaceholderAPI] Expansion could not be registered: "
                    + t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage()));
        }
    }

    /** 刷新队伍名前缀（同时考虑外部适配器与内置组队）| refresh party name prefixes */
    private void refreshPartyDisplay() {
        com.novamclabs.party.PartyNameDisplay.refreshAll(
                this.partyAdapterManager != null ? this.partyAdapterManager.getActive() : null,
                this.partyManager,
                this.scheduler);
    }

    @Override
    public void onDisable() {
        // 取消所有待处理的传送任务
        sessions.values().forEach(s -> s.task.cancel());
        sessions.clear();
        canTriggerMap.clear();
        if (placeholderExpansion != null) {
            try { placeholderExpansion.unregister(); } catch (Throwable ignored) {}
            placeholderExpansion = null;
        }
        if (scheduler != null) scheduler.cancelAllTasks();
        if (crossServerService != null) crossServerService.close();
        if (teleportLogManager != null) teleportLogManager.shutdown();
        if (lang != null) getLogger().info(lang.t("plugin.shutdown"));
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
        if (this.javaMenus != null) this.javaMenus.reload();
        if (this.scriptingManager != null) this.scriptingManager.reload();
        com.novamclabs.util.RegionGuardUtil.init(this);
        if (this.steleManager != null) this.steleManager.reload();
        if (this.portalManager != null) this.portalManager.reload();
        if (this.rtpPoolManager != null) this.rtpPoolManager.reload();
        if (this.scrollManager != null) this.scrollManager.loadConfig();
        if (this.guildManager != null) this.guildManager.reload();
        if (this.guildWarpManager != null) this.guildWarpManager.reload();
        if (this.townyTeleportManager != null) this.townyTeleportManager.reload();
        if (this.tollWarpManager != null) this.tollWarpManager.reload();
        if (this.teleportLogManager != null) this.teleportLogManager.reloadAll();
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

    // ===== 传送会话管理 | teleport session management =====

    /** 登记一次待执行传送（倒计时中）| register a pending teleport */
    public void trackTeleport(Player player, SchedulerWrapper.ScheduledTask task, boolean cancelOnMove, String type) {
        Location origin = player.getLocation().clone();
        sessions.put(player.getUniqueId(), new TeleportSession(task, origin, cancelOnMove, type));
    }

    /** 注销传送会话 | unregister a pending teleport */
    public void untrackTeleport(UUID uuid) {
        sessions.remove(uuid);
    }

    public boolean isTeleporting(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    private boolean isInteractionBlocked(UUID uuid) {
        if (!getConfig().getBoolean("commands.block_interactions", false)) return false;
        return sessions.containsKey(uuid);
    }

    /** 取消玩家当前待执行的传送 | cancel the pending teleport of a player */
    public void cancelTeleport(Player player, boolean showTitle) {
        TeleportSession session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        session.task.cancel();
        // 注意：这里不能重置 canTriggerMap。阈值传送在玩家仍处于阈值下方时每次移动都会重新判定，
        // 若取消后立刻允许重触发，就会变成“取消 → 重新开始倒计时 → 再取消”的循环。
        // 重新允许触发只由两处负责：成功传送后的回调、以及扣费/校验失败的中止回调。
        if (showTitle && lang != null) {
            player.sendTitle(lang.t("teleport.cancelled.title"), "", 10, 20, 10);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // 倒计时中的传送：按配置的移动距离取消
        TeleportSession session = sessions.get(uuid);
        if (session != null) {
            if (session.cancelOnMove && hasMovedTooFar(event, session.origin)) {
                cancelTeleport(player, true);
                if (debug) {
                    getLogger().log(Level.INFO, lang.tr("debug.cancel_due_to_move", "player", player.getName()));
                }
            } else if (debug) {
                getLogger().log(Level.INFO, lang.tr("debug.continue_due_to_small_move", "player", player.getName()));
            }
            return;
        }

        // 以下为世界阈值自动传送 | auto world threshold teleport
        if (!player.hasPermission("novateleport.pass")) {
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

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        cancelTeleport(player, false);
        canTriggerMap.remove(player.getUniqueId());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getPlayer() == null) return;
        if (isInteractionBlocked(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (isInteractionBlocked(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (isInteractionBlocked(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * 检查玩家是否已离开会话起点超过配置的距离（默认 2 格）。
     * 只比较水平位移与 Y 轴，忽略仅转头造成的微小变化。
     */
    private boolean hasMovedTooFar(PlayerMoveEvent event, Location origin) {
        if (event.getTo() == null || origin == null) {
            return false;
        }
        // 配置为 0/负数时按默认值处理：否则纯转头（位移为 0）也会满足阈值而被取消
        double distance = getConfig().getDouble("commands.cancel_move_distance", 2.0);
        if (!(distance > 0)) distance = 2.0;
        if (event.getTo().getWorld() != origin.getWorld()) {
            return true;
        }
        double deltaX = Math.abs(event.getTo().getX() - origin.getX());
        double deltaZ = Math.abs(event.getTo().getZ() - origin.getZ());
        double deltaY = Math.abs(event.getTo().getY() - origin.getY());

        if (debug) {
            getLogger().log(Level.INFO, lang.tr("debug.move_distance", "player", event.getPlayer().getName(),
                    "dx", String.format("%.2f", deltaX), "dz", String.format("%.2f", deltaZ)));
        }

        return deltaX > distance || deltaZ > distance || deltaY > distance;
    }

    /**
     * 检查玩家位置是否发生变化（包括微小移动）
     */
    private boolean hasPositionChanged(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return false;
        }
        return event.getFrom().getBlockX() != event.getTo().getBlockX() ||
               event.getFrom().getBlockY() != event.getTo().getBlockY() ||
               event.getFrom().getBlockZ() != event.getTo().getBlockZ();
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
        if (event.getTo() == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        int currentY = event.getTo().getBlockY();
        int fromY = event.getFrom().getBlockY();
        boolean isNegativeThreshold = rule.threshold < 0;

        // 检查是否在阈值位置移动
        if ((isNegativeThreshold && currentY <= rule.threshold) ||
            (!isNegativeThreshold && currentY >= rule.threshold)) {

            // 如果在阈值位置移动，取消传送但不显示Title
            if (fromY == currentY) {
                cancelTeleport(player, false);
                return;
            }

            // 检查是否可以触发传送
            Boolean canTrigger = canTriggerMap.get(uuid);
            if (canTrigger == null || canTrigger) {
                // 首次触发或允许触发
                boolean started = startTeleport(player, rule);
                if (started) {
                    canTriggerMap.put(uuid, false); // 防止重复触发
                }
            }
        } else {
            // 检查是否穿过阈值线（从一侧移动到另一侧）
            boolean crossedThresholdLine = isNegativeThreshold ?
                (fromY <= rule.threshold && currentY > rule.threshold) :  // 负数阈值：从下往上穿过
                (fromY >= rule.threshold && currentY < rule.threshold);   // 正数阈值：从上往下穿过

            if (crossedThresholdLine) {
                canTriggerMap.put(uuid, true); // 允许再次触发
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
    public com.novamclabs.menu.JavaMenuConfig getJavaMenus() {
        return javaMenus;
    }
    public com.novamclabs.log.TeleportLogManager getTeleportLogManager() {
        return teleportLogManager;
    }
    public com.novamclabs.animations.AnimationManager getAnimationManager() { return this.animationManager; }
    public com.novamclabs.rtp.RtpPoolManager getRtpPoolManager() { return this.rtpPoolManager; }
    public com.novamclabs.scripting.ScriptingManager getScriptingManager() { return this.scriptingManager; }
    public com.novamclabs.cross.CrossServerService getCrossServerService() { return this.crossServerService; }
    public SchedulerWrapper getScheduler() { return this.scheduler; }
    public com.novamclabs.region.RegionAdapterManager getRegionManager() {
        return com.novamclabs.util.RegionGuardUtil.getManager();
    }
    public com.novamclabs.guild.GuildManager getGuildManager() { return this.guildManager; }
    public com.novamclabs.guild.GuildWarpManager getGuildWarpManager() { return this.guildWarpManager; }
    public com.novamclabs.towny.TownyTeleportManager getTownyTeleportManager() { return this.townyTeleportManager; }
    public com.novamclabs.toll.TollWarpManager getTollWarpManager() { return this.tollWarpManager; }
    public com.novamclabs.party.PartyManager getPartyManager() { return this.partyManager; }
    public com.novamclabs.party.adapter.PartyAdapterManager getPartyAdapterManager() { return this.partyAdapterManager; }
    public com.novamclabs.stele.SteleManager getSteleManager() { return this.steleManager; }
    public com.novamclabs.offline.OfflineTeleportManager getOfflineTeleportManager() { return this.offlineTeleportManager; }
    public void setDebug(boolean enabled) { this.debug = enabled; }
    public boolean isDebug() { return this.debug; }

    /**
     * 调度自动世界传送 | schedule auto world teleport
     */
    private void scheduleTeleport(Player player, World targetWorld) {
        org.bukkit.Location target = targetWorld.getSpawnLocation();
        com.novamclabs.util.TeleportUtil.delayedTeleportWithAnimation(this, player, target, teleportDelay, "auto_world_teleport",
                com.novamclabs.util.TeleportUtil.economyPayment(this, "auto_world_teleport"),
                () -> {
                    player.sendMessage(lang.t("teleport.completed"));
                    canTriggerMap.remove(player.getUniqueId());
                },
                // 扣费/校验失败时允许重新触发，否则玩家要重新穿过阈值线
                () -> canTriggerMap.remove(player.getUniqueId()));
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
