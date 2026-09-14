# NovaTeleport 架构文档
# NovaTeleport Architecture Documentation

本文档说明 NovaTeleport 2.0 的实际结构。

This document describes how NovaTeleport 2.0 is actually put together.

---

## 📐 模块结构 | Modules

```
NovaTeleport-Parent/            pom 聚合
├── Common/                     跨平台接口（SchedulerWrapper、Constants），无 Bukkit 依赖
├── Sqlit-Lib/ (nova-storage)   独立存储库：JDBC 实现 + StorageProvider 接口（当前未被主插件使用）
├── ExternalPluginStubs/        仅编译期使用的第三方 API 桩（Factions/SaberFactions）
├── Bukkit/                     服务端插件本体：命令、管理器、适配器、资源
├── BungeeCore/                 代理侧占位插件（仅打印启动日志）
├── Velocity/                   代理侧占位插件（仅打印启动日志）
├── Folia/                      说明性占位模块（packaging: pom，不产出 jar）
└── Dist/                       把上面三个 jar 汇总复制到 target/dist
```

- **Common** 不依赖 Bukkit，只定义 `SchedulerWrapper`（含 `ScheduledTask` 句柄）与配置键常量。
- **ExternalPluginStubs** 在 `provided` 作用域下提供编译期符号，不会被打进发布包。
- **Folia** 与 **BungeeCore/Velocity** 不包含功能代码；Folia 支持由 Bukkit 模块内的调度器适配实现。

---

## ⚙️ 调度抽象 | Scheduler Abstraction

`Common` 中的 `SchedulerWrapper` 抽象了 Bukkit 与 Folia 的调度差异：

```java
public interface SchedulerWrapper {
    void runNextTick(Runnable task);
    void runAsync(Runnable task);
    ScheduledTask runLater(Runnable task, long delayTicks);
    ScheduledTask runAtLocationLater(Object world, int x, int y, int z, Runnable task, long delayTicks);
    ScheduledTask runTimer(Runnable task, long delayTicks, long periodTicks);
    void runAtEntity(Object entity, Runnable task);
    ScheduledTask runAtEntityTimer(Object entity, Runnable task, long delayTicks, long periodTicks);
    ScheduledTask runAtLocationTimer(Object world, int x, int y, int z, Runnable task, long delayTicks, long periodTicks);
    CompletableFuture<Boolean> teleportAsync(Object entity, Object location);
    boolean isFolia();
}
```

`Bukkit` 模块的 `FoliaScheduler` 基于 FoliaLib 实现它，并在 `StarTeleport#onEnable` 中最先实例化。
**插件内所有调度都必须走 `plugin.getScheduler()`**，不再使用 `Bukkit.getScheduler()`：

- 传送倒计时 → `runAtEntityTimer`（Folia 下在玩家所属区域线程执行）
- 传送本身 → Folia 用 `teleportAsync`，其他平台用同步 `teleport()`
- 粒子/音效 → 在实体或目标位置所属区域调度
- 传送日志落盘 → `runAsync` 写文件，序列化留在主线程

---

## 🔌 适配器 | Adapters

### 领地 | Region

```java
public interface RegionAdapter {
    String name();
    boolean isPresent();
    boolean canEnter(Player player, Location destination);
}
```

实现：WorldGuard、PlotSquared、Residence、GriefDefender、Lands、Towny。
`RegionAdapterManager#canEnter` 要求**所有已注册适配器都放行**；适配器内部抛异常时默认放行，
但会通过 `logOnce` 记录一次警告（静默失败会让运维无从排查）。

适配器**不是**在 `List.of(new A(), new B(), …)` 里一次性构造的：每个适配器的字节码都直接引用
对应领地插件的类型，插件缺席时 JVM 在链接该类时会抛 `NoClassDefFoundError`，一次性构造会让
「装着 WorldGuard 却因为没装 Residence 而一个适配器都注册不上」。因此改为按类名逐个
`Class.forName(...).newInstance()`，把失败隔离在各自的方法里。`GuildManager` 与
`PartyAdapterManager` 同理。`RegionGuardUtil.init` 若整体失败会打印警告，而不是静默把领地检查关掉。

### 工会 | Guild

```java
public interface GuildAdapter {
    String name();
    boolean isPresent();
    String getGuildId(Player player);
    boolean isSameGuild(Player a, Player b);
    List<UUID> getGuildMembers(String guildId);
    String getGuildName(String guildId);
    Location getGuildHome(String guildId);
    boolean setGuildHome(String guildId, Location location);
    boolean isGuildAdmin(Player player);
}
```

实现：Guilds、SimpleClans、FactionsUUID，可用 `guild_config.yml` 的 `plugins` 列表过滤。

### 组队 | Party

`PartyAdapter` 只保留纯组队插件（Parties、BetterTeams）；工会插件已迁到 `guild` 包。
未检测到外部插件时回退到内置组队系统（`PartyManager` + `PartyCommand`）。

### 玩法限制 | Gameplay restrictions

两个独立的内存态管理器，默认关闭，`/stp reload` 时重新读取配置：

- `CombatManager` —— 战斗标签（双向：造成或受到伤害都标记，投掷物与 TNT 会解包出真正的动手方）
  与「受伤打断倒计时」。状态是 `ConcurrentHashMap`，退出游戏时清理；伤害事件在受害者所属区域
  线程触发，该类不触碰任何世界/区块 API，因此在 Folia 下安全。
- `CooldownManager` —— 按传送类型分别记录结束时间戳，检查在发起时、登记在传送成功之后。

本地传送通过 `TeleportUtil` 内联检查，跨服传送通过 `TeleportGates` 调用同一个管理器，
两者不会分叉。

---

## 💾 数据管理 | Data

所有数据都是 YAML 文件，位于 `plugins/NovaTeleport/`：

| 数据 | 位置 | 写入方 |
|---|---|---|
| 家 | `data/homes.yml` | `DataStore` |
| 公共传送点 | `data/warps.yml` | `DataStore` |
| 玩家级数据（/back、死亡点、石碑解锁、动画风格） | `data/players/<uuid>.yml` | `DataStore`（统一入口） |
| 传送日志 | `data/teleport_logs.yml` | `TeleportLogManager` |
| 离线传送队列 | `data/offline.yml` | `OfflineTeleportManager` |
| 已激活传送门 | `data/portals_state.yml` | `PortalManager` |
| 石碑索引 | `data/steles_index.yml` | `SteleManager` |
| 工会传送点 | `guild_warps.yml` | `GuildWarpManager` |
| 付费传送点 | `toll_warps.yml` | `TollWarpManager` |

一致性措施：

- `DataStore#updatePlayer` 是玩家级文件的**唯一**读写入口，按 UUID 加锁串行化，
  避免多个管理器各自 read-modify-write 导致丢数据。
- 所有写入使用"写临时文件 + 原子替换"（`DataStore.atomicSave`），避免写一半崩溃损坏数据。
- 传送日志只在内存中追加，每 10 秒把脏数据合并后异步落盘一次，关闭时同步落盘。
- 家/传送点名称会被 `DataStore.normalizeName` 规范化（小写、只允许 `[a-z0-9_-]`、≤32 字符），
  防止用户输入破坏 YAML 路径。

---

## 🔄 传送流程 | Teleport Flow

```
命令 / 触发
   ↓
权限检查
   ↓
解析目标（本地 / 跨服 → 交给代理 Connect）
   ↓
记录 /back
   ↓
延迟 ≤ 0 ? ──是─→ 立即执行
   │否
   ↓
按实体区域调度每秒倒计时（runAtEntityTimer）
   ├─ 玩家离线 → 取消
   └─ 移动超过 cancel_move_distance（且类型不在豁免列表）→ 取消（不扣费）
   ↓
执行体 execute()
   ├─ 战斗标签复查（倒计时期间可能刚被打上标签）
   ├─ 空间锚点校验
   ├─ 领地校验（所有适配器）
   ├─ 扣费（economy.costs.<type> 或调用方提供的 Payment）
   ├─ 动画（playInstant / playPrepare）
   ├─ 传送（Folia: teleportAsync；其他: teleport）
   ├─ 失败（第三方插件取消）→ 退回已扣金钱、提示、中止
   ├─ 记录传送日志 + 登记冷却
   └─ 玩家所在区域执行后处理（失明效果、尾随粒子、脚本钩子、回调）
```

**扣费发生在所有校验之后、传送之前**：倒计时被取消、目标不存在、领地拒绝、锚点缺失都不会扣钱。
但「传送之前」不等于「传送一定发生」——第三方插件仍可取消传送事件，此时**退回刚扣的金钱**
（只退金钱：`Payment` 抽象拿不到经验/物品成本）。冷却与日志只在确认传送成功后登记。

跨服分支（不执行本地传送）由命令层通过 `TeleportGates.passes` 先做战斗/冷却判断，
再用 `CostModel.checkAndCharge` 扣费 —— 不走 `TeleportUtil`，因此也不享受传送失败退款。

---

## 🏗️ 主类与依赖关系 | Main class

`StarTeleport` 负责按顺序构造并持有各管理器（顺序有依赖：调度器 → 语言/菜单 → 存储 → 各子系统）：

```java
public class StarTeleport extends JavaPlugin implements Listener, CommandExecutor {
    private SchedulerWrapper scheduler;              // FoliaScheduler
    private LanguageManager lang;
    private JavaMenuConfig javaMenus;
    private TeleportLogManager teleportLogManager;
    private DataStore dataStore;
    private ScriptingManager scriptingManager;
    private AnimationManager animationManager;
    private PortalManager portalManager;
    private RtpPoolManager rtpPoolManager;
    private ScrollManager scrollManager;
    private CrossServerService crossServerService;
    private OfflineTeleportManager offlineTeleportManager;
    private SteleManager steleManager;
    private DeathManager deathManager;
    private GuildManager guildManager;
    private GuildWarpManager guildWarpManager;
    private TownyTeleportManager townyTeleportManager;
    private TollWarpManager tollWarpManager;
    private PartyManager partyManager;
    private PartyAdapterManager partyAdapterManager;
    private CombatManager combatManager;             // 战斗标签 / 受伤打断（默认关闭）
    private CooldownManager cooldownManager;         // 按类型冷却（默认关闭）
    // ... 对应的 getter
}
```

对外可用的入口：`getScheduler()`、`getLang()`、`getDataStore()`、`getJavaMenus()`、
`getRegionManager()`、`getTeleportLogManager()`、`getGuildManager()`、`getGuildWarpManager()`、
`getTownyTeleportManager()`、`getTollWarpManager()`、`getPartyManager()`、`getPartyAdapterManager()`、
`getRtpPoolManager()`、`getAnimationManager()`、`getScriptingManager()`、`getCrossServerService()`、
`getSteleManager()`、`getOfflineTeleportManager()`、`getCombatManager()`、`getCooldownManager()`。

另有几个管理器只有内部字段、没有 getter（`PortalManager`、`ScrollManager`、`DeathManager`、
`JavaMenuConfig` 之外的菜单相关对象），插件外部无法直接取用。

---

## 🔐 权限 | Permissions

命令权限在 `plugin.yml` 声明；`/gtp`、`/towntp`、`/tollwarp`、`/stele` 因为子命令权限不同，
不在命令级声明权限，而是在代码中按子命令检查。家数量上限通过
`novateleport.home.limit.<n>` 动态权限计算（显式否定的节点不计入）。

---

## 🧪 测试 | Testing

仓库目前**没有自动化测试**（没有 `src/test`，也没有测试依赖）；发布流程只做 `mvn package`。
任何改动都应以实际服务端验证为准。

---

## ⚠️ 已知限制 | Known limitations

- `Sqlit-Lib`（nova-storage）当前未被主插件使用，也不打进发布包。
- `scripts/teleport.js` 只在服务器存在 JavaScript 引擎时生效（Java 15+ 已移除内置 Nashorn）。
- 代理侧 `BungeeCore` / `Velocity` 是占位插件；跨服切换依赖代理自身处理 `BungeeCord` 通道
  （Velocity 的 `bungee-plugin-message-channel` 默认为 `true`，无需改动）。
- 仅支持 1.20+ 服务端：针对 spigot-api 1.20.1 编译，`api-version` 声明为 `'1.20'`，
  同一个 jar 可在 1.20.x / 1.21.x / 26.x（26.1 / 26.1.2 / 26.2 …）加载。
  插件为 Java 17 字节码（1.20.0–1.20.4 的服务端只要求 Java 17），
  1.20.5+ / 1.21.x 需 Java 21、26.x 需 Java 25，均能向上兼容加载。
  粒子常量在 1.20.5 被改名，统一走 `util/ParticleCompat` 按名字解析新旧两种写法。
- 可点击聊天消息（`Player#spigot().sendMessage(BaseComponent...)`）依赖 `bungeecord-chat`
  （Paper 26.2 仍随服务端提供，但已标记 deprecated），统一走 `util/ChatCompat` 并在不可用时降级为纯文本。
