# API Notes (NovaTeleport)

NovaTeleport is a server plugin. The API below is stable within the 2.0 line but is not
semantically versioned; internal classes may change between snapshots.

## 获取实例 | Getting the instance

```java
StarTeleport plugin = (StarTeleport) Bukkit.getPluginManager().getPlugin("NovaTeleport");
if (plugin == null) return; // not installed / not enabled
```

## 可用入口 | Available entry points

| 方法 | 返回 | 说明 |
|---|---|---|
| `plugin.getScheduler()` | `SchedulerWrapper` | 统一调度器（Bukkit/Folia），实际实现是 `FoliaScheduler` |
| `plugin.getLang()` | `LanguageManager` | 语言查询：`t(key)`、`tr(key, k, v, ...)` |
| `plugin.getDataStore()` | `DataStore` | 家/传送点/`/back`/玩家数据 |
| `plugin.getJavaMenus()` | `JavaMenuConfig` | `java_menus.yml` 菜单构建 |
| `plugin.getRegionManager()` | `RegionAdapterManager`（可能为 `null`） | 领地检查 |
| `plugin.getTeleportLogManager()` | `TeleportLogManager` | 传送日志记录 |
| `plugin.getGuildManager()` | `GuildManager` | 工会适配 |
| `plugin.getTownyTeleportManager()` | `TownyTeleportManager` | Towny 传送 |
| `plugin.getTollWarpManager()` | `TollWarpManager` | 付费传送点 |
| `plugin.getPartyManager()` | `PartyManager` | 内置组队 |
| `plugin.getRtpPoolManager()` | `RtpPoolManager` | RTP 坐标池 |
| `plugin.getAnimationManager()` | `AnimationManager` | 动画风格 |
| `plugin.getCrossServerService()` | `CrossServerService` | 跨服 TPA 转发 |
| `plugin.isDebug()` / `plugin.setDebug(boolean)` | `boolean` / `void` | 调试开关（仅本次运行） |

> 注意：`getGuildManager()`、`getTownyTeleportManager()`、`getTollWarpManager()`、`getPartyManager()`
> 在 `onEnable` 中较晚赋值，且部分只在对应命令注册成功时才创建，插件外部调用前请判空。

## 传送工具 | Teleport helper

```java
import com.novamclabs.util.TeleportUtil;

// 使用 economy.costs.<type> 的扣费方式
TeleportUtil.delayedTeleportWithAnimation(plugin, player, target, 3, "home", onComplete);

// 自定义费用（类型无对应经济配置时使用）
TeleportUtil.Payment payment = p -> {
    if (p.getLevel() < 5) return false;
    p.setLevel(p.getLevel() - 5);
    return true;
};
TeleportUtil.delayedTeleportWithAnimation(plugin, player, target, 3, "stele", payment, onComplete);
```

- `delaySeconds <= 0` 时立即传送（Folia 上会自动调度到玩家所属区域）。
- 费用回调在传送真正执行前调用，返回 `false` 会中止传送（回调自身负责提示玩家）。
- 返回 `SchedulerWrapper.ScheduledTask`（倒计时任务），可 `cancel()`；立即传送时返回 `null`。

## 领地检查 | Region checks

```java
RegionAdapterManager regions = plugin.getRegionManager();
boolean ok = regions == null || regions.canEnter(player, destination);
```

## 事件与扩展

插件没有对外事件 API。如需在传送前后插入逻辑，可用：

- `scripts/teleport.js` 的 `onPreTeleport(ctx)` / `onPostTeleport(ctx)`（需要服务器提供 JS 引擎）
- 或直接监听 Bukkit 的 `PlayerTeleportEvent`

## PlaceholderAPI 占位符 | Placeholders

服务端安装 PlaceholderAPI 时会自动注册标识为 `novateleport` 的扩展（未安装则完全跳过，不影响
插件加载）。可用占位符：

| 占位符 | 说明 |
|---|---|
| `%novateleport_homes%` | 玩家已设置的家数量 |
| `%novateleport_homes_max%` | 玩家的家数量上限（按 `novateleport.home.limit.X` 权限解析；离线玩家取配置默认值） |
| `%novateleport_warps%` | 全局传送点数量 |
| `%novateleport_teleporting%` | 是否正在传送倒计时中（`true`/`false`） |
| `%novateleport_can_back%` | `/back` 是否有可用目标 |
| `%novateleport_death_back%` | `/deathback` 是否有可用目标 |

未定义的 key 返回 `null`，PlaceholderAPI 会原样保留 `%novateleport_xxx%` 文本。

## 注意事项

- 内部 API 可能在快照间变化。
- 数据文件是 YAML，跨服位置通过 `DataStore.Destination#server` 记录归属服务器。
