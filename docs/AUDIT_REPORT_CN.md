# NovaTeleport 代码与文档审计日志（内部检查）

> 审计范围：仅基于仓库内 **静态代码/配置/文档** 阅读，不包含真实服务器环境运行测试。
>
> 审计时间：2025-12-15

---

## 0. 结论摘要（Executive Summary）

- **核心传送功能总体齐全**：TPA/Home/Warp/Spawn/Back/RTP/菜单/动画/卷轴/传送门/石碑/死亡回溯/离线传送/工会/Towny/付费传送点/传送日志等在 Bukkit 模块中均可找到对应实现，并在 `plugin.yml` 中注册了命令与权限。
- **文档完整性不足**：`docs/README.md` 与根 `README.md` 指向的部分文档文件此前缺失，且部分架构/示例 API 文档与当前代码实现存在偏差（例如“Folia 调度器抽象、RegionManager 注入、SQL/Redis 存储”等描述与实际 wiring 不一致）。
- **存在影响稳定性的代码风险（已修复部分）**：
  - RTP 预生成池之前在异步线程中访问 `World#getBlockAt/getBiome/...`，在 Paper/Spigot 上属于高风险并发访问（可能引发异常或数据竞争）。**已改为主线程生成，并做了生成批量限制与算法简化**（见下方“已做的修复”）。
  - `StarTeleport` 中使用 `Map<Player, ...>` 作为 key 存储任务与位置，玩家下线后可能产生对象引用滞留/内存泄漏。**已改用 UUID key，并在 Quit 时清理**。
- **跨服与代理模块偏“占位/简化”**：Bungee/Velocity 模块目前仅输出启动日志；跨服主要依赖 Bukkit 侧通过 `BungeeCord` plugin message 发送 `Connect`，Redis 也只是用于跨服 TPA 请求广播的简化实现（且使用反射方式接 Jedis），与 README/ARCHITECTURE 中“完整跨服/存储”描述存在落差。
- **Folia 兼容性声明与实现仍有差距**：`plugin.yml` 标记 `folia-supported: true`，但仍存在大量直接使用 BukkitScheduler/直接 teleport/world 访问的实现点；仓库中提供了 `FoliaScheduler`（FoliaLib 包装）但未贯穿到所有关键路径。建议在后续迭代中统一接入调度/传送抽象。

---

## 1. 项目结构与模块概览

根 POM（`pom.xml`）为多模块 Maven：

- `Bukkit/`：主插件（Spigot/Paper/Folia 目标）
- `Common/`：共享代码（含调度器接口）
- `Sqlit-Lib/`：NovaStorage（JDBC/Redis 总线）
- `ExternalPluginStubs/`：外部插件 API stub（用于编译期依赖）
- `BungeeCore/`、`Velocity/`：代理侧模块（当前实现非常轻量）
- `Folia/`：说明性目录（README + pom，占位/文档）

---

## 2. 文档检查（Docs Audit）

### 2.1 发现的问题

1. `docs/README.md` 原先引用了 `CONFIGURATION.md / COMMANDS.md / PERMISSIONS.md / API.md / FAQ.md` 等文件，但仓库中实际只有 `CONFIG.md / CONFIG_CN.md / ARCHITECTURE.md / LANGUAGE_KEYS.md` 等。
2. 根 `README.md` 中也存在对缺失文档的链接（例如 `docs/CONFIGURATION.md`）。
3. `docs/ARCHITECTURE.md` 叙述了“StarTeleport 注入 scheduler/regionManager/SQL/Redis 存储”等，但当前代码中：
   - Region 入口为 `RegionGuardUtil.init(...)`（内部创建 `RegionAdapterManager`），而非 `plugin.getRegionManager()` 对外暴露。
   - Folia 调度器抽象存在（`FoliaScheduler` + `SchedulerWrapper`），但业务逻辑并未统一使用。
   - 数据存储实际使用 `storage/DataStore` 的本地 YAML（homes/warps/back）；SQL/Redis 存储能力更多是“存在于库/接口层”，尚未集成到主业务。

### 2.2 已做的修复（文档补齐）

为避免文档索引失效，已新增以下文档文件（内容为简版说明，并指向 `plugin.yml`/源码作为权威来源）：

- `docs/CONFIGURATION.md`
- `docs/COMMANDS.md`
- `docs/PERMISSIONS.md`
- `docs/API.md`
- `docs/FAQ.md`

> 说明：这些文件主要用于修复断链与提供“最低可用”文档骨架，后续可进一步扩写。

---

## 3. 功能完整性核对（按功能域）

### 3.1 核心命令

在 `Bukkit` 模块中可见：
- `/tpa /tpahere /tpaccept /tpdeny /tpcancel`
- `/home /sethome /delhome /homes`
- `/warp /setwarp /delwarp /warps`
- `/spawn /back /deathback`
- `/rtp /rtpgui`（含 GUI 与半径选择）
- `/tpmenu`
- `/novateleport debug on|off`（通过 `BaseCommandRouter`）

结论：核心功能“可用实现”较完整。

### 3.2 高级系统

- 动画：`animations/` + `TeleportUtil` 内的粒子与音效逻辑
- 传送门：`portals/PortalManager`（框架检测 + 填充 portal block + 进入触发）
- RTP 预生成池：`rtp/RtpPoolManager`（见“风险与修复”）
- 卷轴：`scrolls/`
- 组队：`party/`（并支持外部 Parties/BetterTeams 适配）
- 工会：`guild/`（Guilds/SimpleClans/FactionsUUID 适配）
- Towny：`towny/`
- 付费传送点：`toll/`
- 传送日志：`log/`（含回溯能力）
- 离线传送：`offline/OfflineTeleportManager`

结论：高级功能点基本都有对应实现与配置文件。

### 3.3 跨服/代理

- Bukkit 侧：
  - `ProxyMessenger.connect(...)` 通过 `BungeeCord` plugin message 发送 `Connect` 指令
  - `CrossServerService` 可选用 Redis（反射 Jedis）做跨服 TPA 请求广播
- 代理侧：
  - `BungeeCore` 与 `Velocity` 模块目前仅输出启用日志，无实际桥接逻辑

结论：仓库中“跨服能力”偏向 **最小实现**；与 README/ARCHITECTURE 中“完整跨服与代理桥接”表述不完全一致。

---

## 4. 关键代码风险清单（Issues & Risks）

### 4.1 [已修复] RTP 预生成池异步访问 World（高风险）

- 文件：`Bukkit/src/main/java/com/novamclabs/rtp/RtpPoolManager.java`
- 原问题：在 `runTaskTimerAsynchronously` 内调用 `world.getBiome(...) / world.getHighestBlockYAt(...) / world.getBlockAt(...)`。
  - 在 Spigot/Paper 上属于典型的“异步访问世界数据”风险点，可能导致并发问题或直接抛出异常。
- 修复：
  - 改为主线程 `runTaskTimer`。
  - 每次运行对每个世界生成数量做上限（默认每轮最多 5 个），避免一次性大量扫描导致卡顿。
  - 生成算法由“从上到下全高度扫描”简化为“基于 `getHighestBlockYAt` 的表面落点检查”，大幅降低单次计算成本。

### 4.2 [已修复] 使用 Player 对象作为 Map key 导致引用滞留（中风险）

- 文件：`Bukkit/src/main/java/com/novamclabs/StarTeleport.java`
- 原问题：`taskMap/canTriggerMap/originalLocations` 使用 `Map<Player,...>`。
  - 玩家下线后 Player 对象会被 map 强引用，可能造成内存泄漏/状态残留。
- 修复：
  - 改为使用 `UUID` 作为 key。
  - 增加 `PlayerQuitEvent` 清理。
  - 同时补充 `PlayerMoveEvent#getTo()` 判空，避免边界情况下 NPE。

### 4.3 Folia 兼容性风险（高风险，未完全修复）

- 现状：仓库中存在 `FoliaScheduler`（FoliaLib 包装）与 `SchedulerWrapper` 接口，但大量逻辑仍使用：
  - BukkitScheduler（`runTaskTimer`）
  - 直接 `player.teleport(...)`
  - 直接世界/方块访问
- 风险：在 Folia 的区域线程模型下，很多操作需要在实体/位置调度器中执行，否则可能触发线程安全限制。
- 建议：
  1) 在 `StarTeleport` 中统一暴露 scheduler，并将核心传送、RTP、Portal、Boat 携带等路径切换为 `runAtEntity/teleportAsync`。
  2) 对涉及 `World#getBlockAt` 的逻辑，引入“按位置调度”或使用 Paper/Folia 推荐的安全 API。

### 4.4 Portal 活跃缓存可能残留（低-中风险）

- 文件：`portals/PortalManager`
- 现状：`activePortals` 仅在创建时写入，未监听方块破坏/世界卸载等事件进行清理。
- 影响：方块被破坏后缓存仍可能保留，造成错误触发或内存小幅累积。
- 建议：监听 `BlockBreakEvent`/`BlockPhysicsEvent` 或定期校验。

### 4.5 过多 `catch (Throwable ignored)` 影响可维护性（中风险）

- 多处代码为了“容错”，直接吞掉异常；这会让配置错误/兼容问题难以定位。
- 建议：至少在 debug 模式下输出堆栈，或按功能域记录 warning。

---

## 5. 功能/实现与文档一致性（Gap Analysis）

| 文档/宣传点 | 代码现状 | 结论 |
|---|---|---|
| “Folia 完整支持（调度抽象贯穿）” | 有 FoliaLib 包装，但业务代码未统一接入 | 存在差距 |
| “SQLite/MySQL/Redis 存储” | 主业务仍是 YAML DataStore；SQL/Redis 能力存在于库/接口但未 wiring | 存在差距 |
| “Bungee/Velocity 跨服代理支持” | 代理模块偏占位；Bukkit 侧仅做 Connect + 可选 Redis TPA 广播 | 部分实现 |
| “API 示例 plugin.getScheduler/getRegionManager” | 当前 `StarTeleport` 未暴露对应方法 | 文档过时 |

---

## 6. 后续建议（Prioritized TODO）

1. **统一调度与传送抽象（Folia 方向）**：将 Teleport/RTP/Portal 等关键路径切换到 `SchedulerWrapper`。
2. **跨服落地方案明确化**：如果目标要支持 Velocity，建议补齐 Velocity 侧 channel/forwarding 或明确依赖（如兼容插件）。
3. **存储层整合**：如果计划支持 SQL/Redis，建议用配置选择 `DataStore(YAML)` vs `NovaStorage(SQL)`，并提供迁移工具。
4. **异常日志策略**：debug 模式下输出 stacktrace；生产模式 warning + 关键上下文。
5. **Portal 缓存清理**：监听方块变化事件或增加自检。

---

## 7. 本次提交包含的改动清单（Changelog）

- 修复：`RtpPoolManager` 不再异步访问世界数据；并限制每轮生成数量。
- 修复：`StarTeleport` 使用 UUID 作为任务/位置缓存 key，避免 Player 引用滞留；补充 quit 清理与 to-null 防护。
- 补齐文档：新增 `docs/CONFIGURATION.md / COMMANDS.md / PERMISSIONS.md / API.md / FAQ.md` 以修复 docs 索引断链。

