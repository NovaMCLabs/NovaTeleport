# 更新日志 | Changelog

所有 NovaTeleport 的重要更改都将记录在此文件中。

All notable changes to NovaTeleport will be documented in this file.

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

---

## [2.0-SNAPSHOT] - 未发布 | Unreleased

### 平台升级 | Platform
- **一个 jar 同时支持 1.20.x / 1.21.x / 26.x**：编译基线由 spigot-api 26.2 **下调到
  1.20.1-R0.1-SNAPSHOT**（取最短的那一端），`plugin.yml` 的 `api-version` 改为 `'1.20'`。
  只引用 1.20.1 就已存在的 API，之后新增的 API 一律不直接调用，因此旧服务端不会再
  抛 `NoSuchMethodError`；而服务端只拒绝比自身新的 `api-version`，声明 `1.20` 在 1.20.1
  与 26.2 上都不会被拒。
  Minecraft 已在 2025-12 改用「年.版本.修订」命名（26.1 / 26.1.2 / 26.2 …），26.x 在
  同一基线之上，无需额外处理。
- **字节码目标降到 Java 17**（原为 21）。这是 1.20.x 支持的硬性前提：**1.20.0–1.20.4
  的服务端只要求 Java 17**，很多这类服仍跑在 17 上，Java 21 字节码会直接
  `UnsupportedClassVersionError`。而 1.20.5+ / 1.21.x（Java 21）与 26.x（Java 25）
  都能向上兼容加载 17 字节码，因此 17 是覆盖面最大的选择。
  注意**构建**仍需 JDK 25：`Velocity` 代理模块依赖的 `velocity-api 4.x` 是 Java 25 字节码，
  低版本 javac 读不了（构建用 JDK ≠ 运行用 JRE）。其余模块可在 JDK 21 上构建。
- **新增 `ParticleCompat` 兼容层**：Minecraft 1.20.5 起 Bukkit 把 `Particle` 的枚举常量改成
  与注册表一致的新名字，旧名字随之消失（`VILLAGER_HAPPY`→`HAPPY_VILLAGER`、
  `REDSTONE`→`DUST`、`ENCHANTMENT_TABLE`→`ENCHANT`、`EXPLOSION_NORMAL`→`POOF` …）。
  常量名无法同时兼容两侧，两个名字写在字节码里必有一侧抛 `NoSuchFieldError`，
  因此改为运行期用 `Particle.valueOf` 按名字解析并回退到旧名（结果缓存）。
  插件自用的 9 个粒子逐一对着 spigot-api 1.20.1 与 26.2 的真实枚举核对过。
- **显式声明 `net.md-5:bungeecord-chat`**：较新的 spigot-api 不再传递依赖它，
  而 `Player#spigot().sendMessage(BaseComponent...)` 仍在 API 中。版本对齐 Paper 内置的
  `1.21-R0.2`（该库在 Paper 中已标记 deprecated，但仍随服务端提供）。
- **新增 `ChatCompat` 兼容层**：可点击消息与 ActionBar 集中到一处，
  并在组件库不可用时自动降级为普通文本，不再直接抛 `NoClassDefFoundError`。
- **Adventure 仅作编译期依赖**：Towny / PlotSquared 的 API 签名引用 Adventure（Paper 运行期自带 5.2.0），
  插件自身不调用它，以保持 Spigot 兼容。

### 代理 | Proxies
- **BungeeCord**：`bungeecord-api` 升到 **26.1-R0.1-SNAPSHOT**（构件已从 oss.sonatype.org
  迁到 `repo.papermc.io`，旧地址 404）。BungeeCord 的 API 不含版本相关的 NMS，
  向前向后都兼容，同一个代理插件在旧版 BungeeCord 上同样能加载。
  `bungee.yml` 补上 `author`（原先代理启动日志会显示 `by null`）。
- **Velocity**：`velocity-api` 升到 **4.1.1**（最新发布版）。Velocity 4 的 API 类是
  Java 25 字节码，但用 release 17 编译是可行的（`release` 只约束输出字节码与平台 API，
  不影响读取更高版本的依赖），因此代理插件本身仍是 **Java 17 字节码**。
  同时显式关闭注解处理（`<proc>none</proc>`）：`velocity-api` 自带的
  `PluginAnnotationProcessor` 同属 Java 25 字节码，且我们本来就手写
  `velocity-plugin.json`（Velocity 官方支持的写法，位于 jar 根目录），不需要处理器介入。
- **修正代理配置说明**：Velocity 的 `velocity.toml` **没有 `bungeecord` 这个键**，
  新版本里叫 `bungee-plugin-message-channel`（默认 `true`）。原先文档里的
  `bungeecord = true` 是静默无效的写法，已全部更正；因为默认就是开启的，
  正常情况下无需修改任何配置。
- 三个发布产物（Bukkit / Bungee / Velocity）现在统一为 Java 17 字节码。

#### 实测验证 | Live verification

- **Paper 1.20.1（build 196，**Java 17**）**：插件加载无异常、无 `UnsupportedClassVersionError`，
  `[Scheduler] Folia=false`，`/stp reload`、`/warps`、`/stele list`、`/ntp help` 均正常响应
- **Paper 1.21.4（Java 21）**：同上，全部正常
- **Paper 26.2（build 123，Java 25）**：`api-version: '1.20'` 未被拒绝；
  `[RegionAdapter] Registered: WorldGuard`、`Registered: Towny`、
  `[Party] Adapter loaded: Parties`、`[Guild] Registered adapter: SimpleClans`、
  PlaceholderAPI 扩展 `novateleport` 注册成功
- **Folia 26.2（build 7，Java 25）**：`[Scheduler] Folia=true`，区域线程模型下无
  `UnsupportedOperationException`
- **粒子兼容层**：把 `ParticleCompat` 的解析逻辑单独跑在 spigot-api 1.20.1 与 26.2 上，
  9 个粒子在两侧都解析成功（1.20.1 走旧名回退，26.2 走新名）
- **代理**：BungeeCord 26.1 与 Velocity 4.1.2 均能加载本项目的代理插件并打印启用日志

### 依赖更新 | Dependencies
| 依赖 | 旧 | 新 |
|---|---|---|
| spigot-api | 1.21.4-R0.1-SNAPSHOT | **1.20.1-R0.1-SNAPSHOT**（编译基线取最老的一端） |
| VaultAPI | 1.7 | 1.7.1 |
| FoliaLib | v0.4.3 | 0.4.4 |
| worldguard-bukkit | 7.0.9 | 7.0.17 |
| plotsquared-core / -bukkit | 7.3.8 | 7.6.0 |
| GriefDefender api | 2.1.0-SNAPSHOT | 2.1.1-SNAPSHOT |
| Towny | 0.100.3.3 | 0.103.2.6 |
| BetterTeams | 4.15.2 | 5.1.4 |
| Guilds | 3.5.2 | 3.5.3.9 |
| Jedis | 5.1.0 | 6.0.0 |
| velocity-api | 3.3.0-SNAPSHOT | 4.1.1 |
| bungeecord-api | 1.19-R0.1-SNAPSHOT | 26.1-R0.1-SNAPSHOT（构件已迁至 repo.papermc.io） |
| PlaceholderAPI | — | 2.12.3（新增，provided） |

- **Lands 改用官方 API 构件**：Lands 已把坐标从 `me.angeschossen` 迁到
  `com.incredibleplugins:lands-api`，原先的桩声明的 `role.enums.RoleSetting` **在真实 API 中
  根本不存在**（进入标志是 `flags.type.Flags.LAND_ENTER`，类型 `RoleFlag`），已改为真实依赖。
- **SimpleClans 改用官方 API 构件**（CodeMC `2.16.0`，与运行期的 2.19.x 签名一致），
  不再是手写桩。
- **Parties 改用官方 API 构件**（`com.alessiodp.parties:parties-api:3.2.17`），
  原先的反射实现入口类名与方法名全错，实际不可用。
- **BetterTeams 改用类所在的子模块坐标** `com.github.booksaw.BetterTeams:betterteams`
  （`com.github.booksaw:BetterTeams` 只是 312 字节的聚合 POM，靠传递依赖才能编译）。
- Velocity 4 起 `javax.inject` 不再随 API 提供，代理占位插件改用 `com.google.inject.Inject`
  （PaperMC 官方文档的写法）。
- 保持发布产物不膨胀：`error_prone_annotations`、`jetbrains:annotations` 已从 shade 中排除。

### 新增 | Added
- **Folia 支持（真正接入）** — 全部调度统一走 `FoliaScheduler`（FoliaLib），
  传送倒计时按实体区域调度，传送使用 `teleportAsync`
- **6 个领地插件适配** — WorldGuard、PlotSquared、Residence、GriefDefender、Lands、Towny（编译期依赖，软依赖可选）
- **Towny 城镇传送** — `/towntp`、`/towntp <城镇>`，含费用与权限
- **工会传送** — Guilds / SimpleClans / FactionsUUID，`/gtp` 全套子命令
- **付费传送点** — 创建/改价/删除/使用，费用按 `owner_fee_percentage` 分成
- **传送日志与回溯** — `/tplog`，保留期可配置
- **死亡回溯** — `/deathback`，冷却与费用可配置
- **传送石碑 / 传送门 / 传送卷轴 / RTP 池 / 离线传送 / 空间锚点**
- **跨服 TPA** — 通过 Redis 转发请求与应答（可选），`/tpaccept` 会触发代理切服
- **可配置的传送取消** — `commands.cancel_on_move`、`cancel_move_distance`、
  `move_cancel_exempt_types`、`block_interactions`
- **统一菜单配置** — `java_menus.yml`
- **PlaceholderAPI 扩展** — `%novateleport_homes%`、`_homes_max`、`_warps`、`_teleporting`、
  `_can_back`、`_death_back`；未安装 PlaceholderAPI 时完全跳过

### 玩法限制（默认关闭）| Gameplay restrictions (off by default)

新增两节配置，默认全部关闭，升级后传送行为与之前完全一致。

- **战斗标签 `combat_tag`** — 战斗中禁止发起传送。判定是**双向**的：玩家造成或受到伤害
  都算进入战斗（含玩家↔生物、投掷物与 TNT 都能正确解包出动手方），
  因此既能阻止「被追着打时传送逃跑」，也能阻止「打一下就跑」。
  时长、是否受击刷新、豁免的传送类型、bypass 权限都可配置。
- **受伤打断倒计时 `damage_interrupt`** — 受伤时打断进行中的传送。
  **即使 `combat_tag` 关闭，这一节也独立生效**（两者是独立开关）。
- **按类型的传送冷却 `teleport_cooldowns`** — home / warp / spawn / back / rtp / tpa / …
  各自独立的冷却秒数。检查在发起时、**登记在传送真正执行之后**，
  因此**倒计时被移动或受伤取消、校验或扣费失败，都不会消耗冷却**
  （与「费用在真正执行时才扣」是同一套设计）。
- 跨服传送不经过 `TeleportUtil`，改为统一走 `TeleportGates` 在 `connect` 前做同一套检查、
  发出后登记冷却，覆盖 `/home`、`/warp` 的跨服分支、`/city` 的 proxy 模式，
  以及跨服 `/tpa`（请求方在自己所在服务器收到 `tpa_accept` 时校验）与
  `/tpahere`（目标就在本服，直接校验）——否则跨服会成为绕过口子。
- 两节默认关闭是有意的：纯便利型 / 建筑服通常不希望传送被战斗状态或冷却限制。

### 费用体系统一 | Unified cost model

原先「花钱传送」在 4 个 YAML、5 种键名下各实现了一遍，且行为不一致。现在收敛到
`util/CostModel`，核心不变量是**先校验全部、再统一扣减**——任何一项不足都不会产生部分扣减。

- **YAML 位置与键名全部不变**，只统一代码路径；现有配置升级后行为一致。
- 覆盖三类成本：金钱、经验等级、物品；付费传送点的所有者分成也走同一处
  （保留「服务器部分扣除失败则退回已付给所有者的金额」的回滚）。
- `EconomyUtil` 新增 `canCharge` + `MoneyStatus`：原来的 `charge` 把「余额不足」和
  「经济不可用」都归结为一次失败/放行，无法支撑先校验后扣减。
- **新增可选全局默认值层** `economy.global_fallback.enabled`（**默认关闭**）：
  某功能自己的配置里**没写**费用键时，回退到 `economy.costs.<类型>`。
  默认关闭是因为各功能的默认配置都写了 cost 键，回退本来就不生效；
  开启后真正的效果是「服主删掉特征键时改用全局值」，属于静默改变语义，必须主动开启。
  同时让 `economy.costs.{stele,guild,towny,deathback}` 这几个**从未被读取**的死键有了意义。
- **启动/重载时提示经济不可用**：配置了金钱费用却用不了经济时打印一条 WARNING 说明
  这些费用不会生效（原有的静默降级会让服主以为在收费、实际全免费）。
  **不阻止传送**，行为与之前一致。
- 传送门保持免费（`portals` 是有意不收费的），未接上 `economy.costs.portal`。

### 修复 | Fixed
- **石碑激活会白扣物品** —— `SteleManager#tryActivate` 先把背包里的同类物品扣掉一部分，
  再判断数量是否足够，不够就直接返回失败：玩家被扣了东西却没能激活石碑。
  改用统一的 `CostModel`「先校验全部再扣减」后不再可能发生
- **领地适配器集体注册失败** —— `List.of(new WorldGuardAdapter(), new PlotSquaredAdapter(), …)`
  会在**任意一个**领地插件缺席时整体抛 `NoClassDefFoundError`（适配器字节码直接引用对应插件的
  类型），异常被 `RegionGuardUtil.init` 静默吞掉，表现为「装了 WorldGuard 却完全没有领地保护」。
  现改为按类名逐个反射构造，并把初始化失败改为打印警告。`GuildManager`、`PartyAdapterManager`
  同样问题一并修复
- **Residence 适配器永远注册不上（旧版）／不被兼容（现代版）** —— Residence 有两支血脉且
  API 形状相反：现代版（Zrips 维护，6.x）有静态 `getInstance()`、`getResidenceManager()` 是
  **实例**方法；旧版（bekvon 2.6.x）没有 `getInstance()`、`getResidenceManager()` 是**静态**方法。
  两者类名完全相同，编译期选定一种就会在另一种上抛 `IncompatibleClassChangeError`，
  现改为反射调用并解析一次后缓存，两支都能用。同时修掉三处签名/语义错误：
  `ResidencePermissions#playerHas` 返回基本类型 `boolean`（原桩写成 `Boolean`，描述符不匹配）；
  `"enter"` 并非 Residence 注册的标志（未注册标志直接返回默认值，检查恒为通过），
  改用官方注册的 `"tp"` 标志（6.x 用 `Flags.tp` 枚举重载，旧版回退到名字符串重载）
- **Parties 适配器恒返回 null** —— 反射入口写成了 `com.alessiodp.parties.api.PartiesAPI`
  （真实位置是 `api.interfaces.PartiesAPI`），且 `PartyPlayer#getParty()`、
  `Party#getMembersUUID()` 两个方法名都不存在；异常被静默吞掉，队伍名/成员信息完全失效。
  改用官方 API 构件 `com.alessiodp.parties:parties-api` 编译，由编译器保证签名
- **队伍适配器注册失败会拖垮整个插件** —— `PartyAdapterManager` 未捕获 `register()` 的异常，
  一旦适配器注册失败（例如为基类 `Event` 注册 `@EventHandler`，Bukkit 会抛
  `IllegalPluginAccessException`），插件的 `onEnable` 直接中断、整个插件被禁用。
  现改为捕获并回退到内置组队
- **PlotSquared 适配器绕过 DENY_TELEPORT** —— `if (plot.isAdded(uuid)) return true;`
  这个短路让地皮主人设置的 `deny-teleport` 完全失效（设成 `TRUSTED`/`NONOWNERS` 时，
  本应被挡在外面的玩家仍可传送进入）。改为只交给 `DenyTeleportFlag.allowsTeleport` 判断
- **GriefDefender 适配器使用了不存在的权限节点** —— `griefdefender.admin.claim.enter`
  并非 GD 的权限，真实的管理员绕过入口是
  `Core#getUser(uuid)#getPlayerData()#canIgnoreClaim(claim)`
- **Guilds 适配器在新版上失去副会长权限** —— `GuildRole#isChangeHome()/isPromote()/isKick()`
  在 Guilds 3.5.7+ 已被 `hasPerm(GuildRolePerm)` 取代，直接调用会抛 `NoSuchMethodError`
  并被吞掉。现改为反射探测新旧两种形态；两者都认不出时只认会长（宁可少授权）
- **FactionsUUID 适配器跨分支不兼容** —— SaberFactions 的 `Faction#getFPlayers()` 返回 `Set`、
  `FPlayer#getRole()` 返回 `struct.Role`（含 `LEADER`）；上游 FactionsUUID 返回 `List`、
  角色枚举移到 `perms.Role`（`LEADER` 改名 `ADMIN`）。两者 plugin.yml 同名 `Factions`，
  无法同时编译匹配，现按 `Collection` / 枚举名反射取值，两个分支都能工作
- **FactionsUUID 适配器 `getFPlayers()` 描述符不匹配** —— 真实 API 返回 `Set<FPlayer>`，
  桩声明为 `List<FPlayer>`，调用会抛 `NoSuchMethodError`
- **Lands 适配器引用不存在的类** —— `me.angeschossen.lands.api.role.enums.RoleSetting`
  在真实 API 中不存在（`getstatic` → `NoSuchFieldError`），现改用 `Flags.LAND_ENTER`
- **`ChatCompat` 命名颜色无效** —— `ChatColor.of("GREEN")` 只接受 `#rrggbb` 形式的十六进制串，
  传颜色名会抛异常并静默降级为无点击效果的纯文本；现按「`#` 前缀走 `of`，否则走枚举 `valueOf`」解析
- **删除仓库根目录误提交的 `plugin.yml`**（内容是 BetterTeams 的清单，与本体无关）
- **版本声明修正** — `api-version: 1.16` 与代码实际使用的 1.17+/1.20.5+ API 不符，
  现为 `'1.20'`（同时兼容 1.20.x / 1.21.x / 26.x），文档同步更新版本要求
- **粒子特效在 1.20.5+ 上全部失效** — 常量名在 1.20.5 被整体改名，
  原先直接引用 `Particle.HAPPY_VILLAGER` 等新名字，在 1.20.0–1.20.4 上会抛
  `NoSuchFieldError`（被 `try/catch` 吞掉后表现为「动画没有特效」）；
  脚本 `ctx.particle("VILLAGER_HAPPY")` 同理。现统一走 `ParticleCompat` 解析新旧两种名字
- **Folia 支持不再只是声明** — 之前 `FoliaScheduler` 从未被实例化，23 处仍在用
  `Bukkit.getScheduler()`（在 Folia 上抛 `UnsupportedOperationException`）
- **跨服/Redis 功能可用** — `Class.forName("redis.clients.jedis.*")` 与 shade 重定位
  （`com.novamclabs.lib.jedis`）冲突、且 `JedisPubSub` 无法用动态代理实现，
  原实现 100% 不可用；现改为编译期依赖 + `JedisPubSub` 子类，发布改为异步
- **不再谎报跨服 TPA 成功** — 目标不在线且 Redis 不可用时如实提示玩家离线
- **跨服 home/warp 修复** — 原先先判断 `location == null` 导致跨服分支永远不可达
- **扣费时机修正** — 费用改为在传送真正执行时扣除；取消倒计时、领地拒绝、锚点缺失、
  玩家离线都不再扣钱。付费传送点的分成也移到传送时，并补上服务器手续费失败时的回滚
- **移动取消传送对所有命令生效** — 之前只对世界阈值传送生效，其他传送的倒计时无法取消
- **`GriefDefenderAdapter.canEnter()` 恒返回 true** 的 bug（领地保护形同虚设）
- **WorldGuard 适配器不再检查 BUILD 标志** — 之前会把主城/公共区域一并拦下
- **Towny 适配器允许进入公共城镇** — 之前对所有城镇套用 BUILD 权限
- **传送门刷屏与任务风暴** — 站在传送门里每个移动事件都会发消息并新建定时任务；
  现在只在"跨入方块"时触发并带冷却；激活状态持久化到 `data/portals_state.yml`，
  重启后仍然可用
- **石碑激活白扣物品** — 经验不足时物品已被扣掉；现在先校验经验再扣物品
- **`/rtp <半径>` 缺少上限** — 可传入超大半径强制生成远距离区块；现按世界配置上限截断
- **玩家数据竞争** — 家/warp/`/back`/死亡点/石碑/动画风格原先各自对同一个
  `data/players/<uuid>.yml` 读改写；现统一走 `DataStore.updatePlayer`（按 UUID 加锁）
- **YAML 写入原子化** — 写临时文件再替换，避免写一半崩溃损坏数据
- **家/传送点名称校验** — 禁止 `.` 等会破坏 YAML 路径的字符
- **家数量上限忽略否定权限** — `-novateleport.home.limit.100` 曾可反向解锁上限
- **传送日志不再每次传送重写整个文件** — 改为内存累积 + 每 10 秒异步合并落盘，关闭时同步保存
- **RTP 池生成线程安全** — 区块访问通过 `runAtLocation` 调度到对应区域（Folia 必需）
- **`preloadTargetChunk` 不再退化为同步区块加载**（非 Paper 平台直接跳过）
- **基岩版表单修复** — `validResultHandler` 重载选择错误、RTP 滑块读取用错方法，
  表单不可用时会记录一次警告而不是静默失败
- **`/ntp debug on` 不再重写 config.yml**（会抹掉全部注释），改为仅本次运行有效
- **队伍前缀闪烁** — 改为增量更新计分板队伍，不再每 10 秒重建
- **缺失的语言键** — 补齐 `debug.trigger_teleport` 及跨服 TPA、名称校验、冷却等键，
  `zh_CN` 与 `en_US` 键集合保持一致
- **代理侧模块说明** — `BungeeCore` / `Velocity` 为占位插件，跨服切换依赖
  `BungeeCord` 插件消息（Velocity 的 `velocity.toml` 中 `bungee-plugin-message-channel` 默认为 `true`）
- **CI 修复** — 发布不再在每次 push 到 main/master 时覆盖同一个 release（仅 tag 触发），
  发布任务复用构建产物而不是重复构建

### 修复（第二轮审计）| Fixed (second audit round)
- **付费传送点会凭空造币** —— `CostModel#chargeMoney` 在「先 `transfer` 给所有者、再 `charge`
  服务器部分」的两段式扣费中，第二段失败时调用的是 `deposit(收款人, ownerFee)`：此时钱**已经**
  转给收款人了，这一句等于给收款人再发一遍，而付款人一分没退。改为对付款人**单次全额扣款**
  （不存在「扣一半」的中间态，失败即净变化为 0），成功后再支付分成；分成支付失败只记日志、
  保留为服务器收入，不做多步回滚（回滚本身也可能失败）。三个场景的资金流已在 CHANGELOG
  下方「费用」一节列出
- **战斗中仍可在倒计时里逃走** —— 战斗标签只在发起时检查一次，而打断倒计时的 `damage_interrupt`
  默认关闭，于是「先发起传送、倒计时中被击中、照常完成传送」这条路绕过了整个功能。
  现在 `execute()` 在扣费之前再复查一次战斗标签
- **传送被第三方插件拦截时仍然扣费并谎报成功** —— `Entity#teleport` 的返回值被丢弃，
  第三方插件取消 `PlayerTeleportEvent` 时玩家**没有移动**，却已经被扣钱、被记冷却、被写进
  传送日志，还收到「传送完成」。现在传送步骤会返回结果，失败时退款、不记冷却与日志、
  不触发完成回调，并明确告知玩家（Folia 走 `teleportAsync` 的 future，非 Folia 走
  `Entity#teleport` 的布尔值）
- **`/tplog` 回溯扣错人的钱** —— 管理员执行 `/tplog <玩家>` 并点击日志条目时，被扣
  `economy.costs.rewind` 的是**被回溯的玩家**而不是管理员本人。现改为管理员计费，
  管理员付不起时明确提示并中止
- **跨服传送整体绕过战斗标签、冷却与费用** —— 跨服分支不走 `TeleportUtil`，而重写的门禁
  只用在了跨服 home/warp 两条路径上。`/city`（代理模式）、跨服 `/tpa`、跨服 `/tpahere`
  因此都是免费且无冷却的脱战手段。现抽出 `util/TeleportGates` 作为唯一实现，并在
  `/city`（注意 `novateleport.command.spawn` 默认人人可用）、`/tpahere`（换服的是接受方）、
  跨服 `/tpa`（换服的是请求方，在收到 `tpa_accept` 的那一侧处理）三处补齐门禁与扣费，
  顺序统一为「门禁 → 扣费 → 通知 → 切服 → 记冷却」
- **跨服 `tpa_arriving` 可被伪造** —— 接收方对该消息**没有任何校验**，任何能往 Redis 频道
  发消息的人都可以指定任意玩家名，让该玩家在下次上线时被传送到攻击者指定的人身边
  （并顺手覆盖其 `/back`）。现在要求本服存在一条 `meet` 自己发起的、跨服的、
  `here=true` 的待处理请求，且处理后立即消费该请求
- **跨服到达依赖 20 tick 的睡眠** —— 原实现「先发通知、睡 1 秒、再切服」，
  Redis 延迟超过 1 秒时玩家会在对方登记之前进服，于是**永远不会**被送到请求者身边，
  而那条迟到的通知会让他在**下次登录时**突然被传送。现在取消这个延时，
  接收方在「到达者已经在线」时直接完成会合，结果不再取决于通知与进服的先后顺序
- **Redis 掉线后跨服功能静默失效** —— `isActive()` 只表示「连接池没关」，Redis 中途挂掉后
  仍返回 true，`publish` 异步发送失败只写一行日志，于是 `/tpa` 对玩家谎报「请求已发送」
  并留下一条 60 秒后才过期的死请求。现在 `isActive()` 反映真实可达性（发布结果 +
  30 秒一次异步 ping），掉线/恢复各只打印一次
- **`network.*` 改不了、默认 `server_name` 静默失效** —— 该服务的字段在构造时一次读取且为
  `final`，`/stp reload` 也从不重建它；而 `server_name` 默认值 `local` 会让每个同名服务器
  **丢弃彼此的全部消息**且不留任何日志。现在字段可重载、新增 `reload()` 并接入
  `/stp reload`，启动时若开着 Redis 却仍是默认名会打印警告
- **基岩版支持整体不可用** —— `BedrockUtil` 与 `BedrockFormsUtil` 都把 Floodgate API 写成
  `floodgate.api.FloodgateApi`，真实包名是 `org.geysermc.floodgate.api.FloodgateApi`；
  `Class.forName` 必抛并被 `catch` 吞掉，于是**装了 Floodgate 也恒判定为非基岩版**，
  所有表单入口从未进入过。同时修掉：RTP 滑块按 `int`/`double` 反射而 Cumulus 1.1.2
  只有 `float` 重载；列表表单把本地化显示名（「我的家」）当命令参数传给 `/ntp`，
  中文下主菜单四个按钮全废；`warnOnce` 用单个全局静态标志，首次失败后所有后续诊断都被吞掉
- **动画风格与 Lore 配置静默失效** —— `/tpanimation select magick` 会把风格悄悄重置为默认值
  并回复「设置成功」；菜单物品只配 `lore` 不配 `name` 时 lore 被整段丢弃
- **`/tollwarp create a.b 10` 会让该传送点在重启后消失** —— 传送点名称直接作为 YAML
  路径键写入，含 `.` 的名称被写成嵌套结构，读回时 `UUID.fromString(null)` 抛异常，
  条目被丢弃且数据无法再被访问。现在入库前统一走 `DataStore.normalizeName`
- **`/stp reload` 会丢传送日志** —— `reloadAll()` 直接 `cache.clear()`，
  尚未落盘的记录被静默丢弃，而该功能的意义正是事后审计与回溯。现在先同步落盘再重载
- **`death.yml` 无法重载、冷却表泄漏** —— 配置在构造时一次性读入 `final` 字段且从不重建，
  `/stp reload` 后所有费用/冷却/延时仍是旧值直到重启；`cooldowns` 也从不在玩家退出时清理
- **传送门的两张表存在 Folia 数据竞争** —— `PortalManager` 的 `activePortals` 与 `defs` 是普通
  `HashMap`，而插件声明 `folia-supported: true`：不同区域的两个玩家同时触发传送门会在
  不同区域线程上写同一张表，轻则丢条目、重则 `HashMap.resize` 死循环挂住区域线程。
  现改 `ConcurrentHashMap`（`defs` 另加 `volatile`）——注意 `loadDefinitions()` 原先也是
  新建普通 `HashMap` 再整体赋值，只改字段初始化会被它覆盖，一并修正
- **脚本绕过动画总开关** —— `features.animation_enabled: false` 时脚本仍会发粒子/标题/音效
- **倒计时会多显示一个 `0`** —— 终局判断在显示之后，默认 3 秒会看到 `3`、`2`、`1`、`0`
- **倒计时每秒重读一次玩家 YAML** —— 未存过动画风格的玩家每次都重新解析文件（阻塞主线程），
  现缓存「回退到默认值」这一结果；`styles` 表也补上退出清理（原先只增不减）

### 修复（第三轮：真机验证与深挖）| Fixed (third round: real-server verification)

这一轮的关键条目来自**真实服务端复现**，不是代码阅读推断。

- **RTP 预生成会在主线程同步生成冷区块，直接把服务器卡死** —— 这是本轮唯一在真机上复现的
  严重缺陷。在 **Paper 1.20.1 / Java 17** 上启动插件后，服务器**连续三次**触发 Paper 的
  「20 秒无响应」watchdog 并产生线程转储，栈全部指向同一条路：
  `CraftWorld.getHighestBlockYAt → Level.getChunk → ServerChunkCache.getChunk → waitForTasks`
  ← `RtpPoolManager.generateAt` ← `FoliaScheduler.runAtLocation` ← `SpigotImplementation.runNextTick`
  ← `CraftScheduler.mainThreadHeartbeat`。转储里的 chunk holder 是
  `entityChunkFromDisk=false / currentChunkStatus=INACCESSIBLE / structure_starts`，即正在从零生成。
  根因是原注释**写反了**：`runAtLocation` 是 Folia 概念，在 Spigot/Paper 上 FoliaLib 把它映射为
  `runNextTick`，也就是**主线程**；而 `getHighestBlockYAt` 会同步加载并生成该坐标的区块。
  默认 `rtp.yml`（`min_radius: 500 / max_radius: 5000`）的采样点几乎必然落在未生成区域，
  每次采样都是一次冷生成，三次连在一起就超过阈值。现改为**只采样已加载区块**
  （`isChunkLoaded` 只是查表，不加载），未加载则重抽（最多 8 次）或放弃本轮；
  回调里再次确认，防止检查与回调之间区块被卸载。批量、200 tick 周期、池大小语义均未改。
  **注意这是 Spigot/Paper 上的缺陷，不是 Folia 专属**，此前只在 26.2 上验证过，
  而 26.2 区块生成快得多，所以一直没暴露
- **同一个冷区块阻塞问题还在 `RTPUtil.findSafeLocation`** —— `/rtp <半径>` 会**先**走它，
  `/rtp now` 在池子为空时也回退到它，`/deathback` 的自动 RTP 同样。它直接在主线程做
  最多 30 次 × 约 384 格的方块扫描。同一类根因，只是玩家触发而非定时器触发
- **RTP 的生物群系黑名单在真实路径上从未生效** —— `rtp.yml` 里的
  `biome_blacklist: [OCEAN, DEEP_OCEAN]` 只有池子生成会查，而 `/rtp` 的默认路径走
  `RTPUtil.findSafeLocation`，那里完全没有 biome 判断，所以默认配置下玩家照样落进海洋。
  现已补上（列表为空时零开销，受 `tries` 上限约束不会死循环）
- **RTP 的两套半径配置互不知晓** —— 池子按 `rtp.yml` 的 `min_radius/max_radius`（500/5000）生成，
  而 `/rtp` 的截断用 `config.yml` 的 `rtp.radius`（2000）：`/rtp 5000` 被截到 2000，
  `/rtp now` 却可能取到 5000 处的池坐标。现统一以 `rtp.yml` 的 `worlds.<world>` 为权威来源
- **一个读失败的玩家文件会被一次无关写入清空** —— `DataStore.updatePlayer` 里 `cfg.load(f)`
  抛异常被 `catch (Exception ignored)` 吞掉后仍继续写入，于是只含本次改动键的空白配置
  覆盖整个 `data/players/<uuid>.yml`，`back` / 死亡点 / 动画风格 / 石碑解锁全部丢失。
  现改为读失败即记警告并**放弃本次写入**（宁可少写一次，不能丢文件）
- **四处落盘未走原子写入** —— `GuildWarpManager` / `TollWarpManager` / `OfflineTeleportManager`
  用的是 `cfg.save(file)`，写一半崩溃即损坏文件，再被上一条放大成完全丢失。现统一走
  `DataStore.atomicSave`（临时文件 + 替换）
- **`server_name` 改名后必须重启，否则 `/home` 直接不可用** —— `DataStore` 只在构造时读一次，
  而 `TeleportCommandHandler.isRemoteServer` 实时读配置：改名 + `/stp reload` 后，
  所有已存的家/传送点仍带旧服名，被判定为跨服目标，玩家被送去不存在的服务器而遭踢出。
  现 `serverName` 改为可重载（`/stp reload` 时同步刷新）
- **`/tplog` 的传送日志会在关服时丢失** —— 异步落盘从不等待，关服时的同步 flush 会被
  在途的旧落盘覆盖。现引入写入序号，最后写入者胜出；同时给 `data`/缓存的读写加锁，
  消除「全局线程 flush vs 区域线程 reload」的数据竞争（后者在 Folia 上是真实竞态）
- **`/back` 在**发起**传送时就被覆盖** —— 这是权限/玩法限制生效前提下的一个隐性数据损失：
  玩家 `/home` 后移动 3 格（`cancel_on_move` 默认开启）或战斗/冷却/余额不足导致传送中止，
  **旧 back 已经没了**，`/back` 指向的正是刚才站着的位置。现 `/back` 只在传送**成功后**
  由 `TeleportUtil.finish()` 用已捕获的 `from` 统一写入，且 `type == "back"` 时跳过
  （避免 `/back` 覆盖自己的目的地）
- **传送被第三方插件拦截时仍会消耗传送卷轴** —— 卷轴的物品扣除在扣费回调里（传送前），
  而退款只退金钱，卷轴白丢；旁边注释与实现相反。现改为成功后（`onComplete`）才消耗，
  并补上右键 `setCancelled`（此前右键箱子会**同时**开箱并触发传送）
- **GUI / 方块入口绕过命令权限** —— `plugin.yml` 只拦命令，而 `/tpmenu` 默认人人可用，
  其菜单项直接调用共用处理器（内部无 `hasPermission`）。于是默认组被禁 `command.warp` 的服，
  玩家仍能经 GUI 与基岩表单到达 warp/home/back/rtp。现校验下沉到共用处理器
- **石碑「传送」不要求解锁** —— `/stele travel`、菜单、基岩表单只付传送费即可使用从未激活过的石碑，
  `isUnlocked` 只在右键路径检查。现校验收敛到 `travelTo` 这一唯一汇聚点
- **拆掉传送门门框后传送门仍然有效** —— 走向已消失的门框位置照样被传送（隐形传送陷阱）。
  现移动时校验脚下的方块是否仍是该门的方块，不是则懒删除该条目（单次 `getType()`，Folia 安全）
- **`/tollwarp` 无参数泄露全部付费传送点** —— 无参路径没有权限校验（`list` 子命令有），
  任何玩家可看到所有传送点的名称/所有者/价格/坐标，Tab 补全也同样泄露。现已与 `list` 同闸
- **付费传送点使用次数少计** —— 所有者分支不计次，其余分支计次
- **`/forcetp` 对在线玩家同步跨区域传送**（Folia）
- **组队成员退出后不清理** —— `PartyManager` 未实现 `Listener`，队伍与离线队长常驻至重启
- **基岩玩家无法按名字被找到** —— Floodgate 默认给基岩玩家名加 `.` 前缀（`.Steve`），
  `getPlayerExact("Steve")` 找不到，`/tpa Steve`、`/forcetp Steve` 全部报「无在线玩家」。
  现先试原名、再试 `.`+原名
- **基岩版界面缺入口与兜底** —— `/tollwarp list`、`/tplog`、`/gtp warp`、`/towntp`、`/party`、
  `/scroll` 与动画风格选择补上表单；若干表单失败时会**既无表单也无聊天提示**（死路），现已补兜底
- **基岩表单泄漏格式码与占位符** —— 按钮/标题未剥 `§`（按钮上会显示字面量 `§e`），
  RTP 滑块标签显示字面量 `{radius}`；列表表单无按钮上限
- **`showModalConfirm` 在拿不到回调时仍发送表单** —— 玩家看到确认框但点「是」什么都不发生
- **菜单展示物可被拖进背包** —— 全部菜单只取消 `InventoryClickEvent`，
  全项目无 `InventoryDragEvent` 处理器。现新增统一的拖拽拦截器
- **组队前缀对基岩玩家不可见** —— Geyser 不渲染计分板 Team 前缀，整个组队显示静默失效
- **`animations.default_style` 改动对在线玩家不生效** —— 默认值被一并缓存；现把「该玩家没有存过风格」
  这一事实与解析结果分开缓存，默认值每次实时求值，同时保持每玩家只读一次 YAML
- **单个菜单物品只配 `lore` 不配 `name` 时 lore 被整段丢弃**
- **`/tplog` 回溯扣的是被回溯玩家的钱**（而非点击的管理员）
- **`/city` 的代理分支、跨服 `/tpa`、跨服 `/tpahere` 会绕过战斗标签与冷却** ——
  跨服路径不由 `TeleportUtil` 把关，原先只在跨服 home/warp 补了检查
- **跨服 `tpa_arriving` 消息可被伪造** —— 接收方原无任何校验，任何能发 Redis 消息的人
  都能让指定玩家在下次上线时被传送到攻击者指定的人身边
- **传送门的两张表在 Folia 上存在数据竞争**（普通 `HashMap`）——本轮继续收敛 `TollWarpManager`、
  `GuildWarpManager` 的同类问题（`ConcurrentHashMap` + 写盘串行化）
- **`/stp reload` 会丢传送日志**（未落盘的记录被 `cache.clear()` 丢弃）
- **`death.yml` 与 `network.*` 无法重载**，需要重启
- **脚本绕过动画总开关**；**倒计时多显示一个 `0`**；**倒计时每秒重读一次玩家 YAML**
- **文档中一批与实际不符的说法** —— 例如 Maven 坐标写成了仓库里并不存在的 `NovaTeleport`、
  「编译期依赖」与实际的反射加载相反、把 WorldEdit 列为集成项、以及一段「已在真实服务端验证」
  却列出与仓库矛盾版本号的内容（现改为如实说明：CI 只跑构建，集成插件仅保证按锁定版本编译通过）
- **6 处硬编码的玩家可见文本**（`/gtp` 帮助、`/tplog` 用法、组队侧栏标题、动画风格按钮、
  `java_menus.yml` 的英文标题与 lore 等）未走语言文件，在英文服上会显示中文（或反之）
- **升级后新增的语言键会显示成原始键名** —— 这也是真机复现的：`ensureDefaults` 只在语言文件
  **不存在**时才从插件内复制，从不补齐新版本新增的键。服务器上已有的 `langs/zh_CN.yml`
  （205 键）不会被更新，而本轮新增了 48 个键，于是 `/stele list` 直接回显 `stele.list`。
  现改为查找时按「服主文件 → 插件内置同语言 → 默认语言（en_US）→ 原样返回键名」四级回退，
  **不修改服主的文件**；缺失键每个只记录一次 INFO。同时修掉语言文件解析失败时被静默换成
  空配置的问题（现在会打印警告，否则一处格式错误会让整个插件的文本变成键名且无任何提示）
- **管理器构造函数拿到的是未加载的语言** —— 语言只在 `loadConfig()` 里加载，而多个管理器在此
  之前构造，构造函数里取语言文本作为默认值时会拿到原始键名（启动日志可见
  `Missing language key '…' in null`）。现把语言加载提前到初始化管理器之前

#### 真机验证 | Live verification

同一个 jar 在四台真实服务端上启动并执行命令，均**零 `com.novamclabs` 栈帧**：

- **Paper 1.20.1（Java 17）** —— RTP 卡死的复现场景。修复前同一时间窗内触发 3 次 watchdog 线程转储；
  修复后 **0 次**，`/stp reload`、`/stele list`、`/warps`、`/ntp help` 正常，启动 18.6s
- **Paper 1.21.4（Java 21）** —— 同上，命令全部正常；语言升级回退路径在此单独复测
- **Paper 26.2（Java 25）** —— WorldGuard + Towny 适配器注册，命令逐个执行无异常，
  新配置键正确生成；watchdog 0 次
- **Folia 26.2（Java 25）** —— `[Scheduler] Folia=true`，区域线程模型下
  `UnsupportedOperationException` **0 次**，`/stp reload` 干净重载

其中「升级后新增语言键显示为原始键名」一条是在 **1.20.1 上保留旧 `langs/` 目录**
（模拟真实升级）复现的：修复前 `/stele list` 回显 `stele.list`，修复后正确显示
`传送石碑:`，且服主的语言文件**未被改写**（仍为 205 键）。

> 关于「粒子/Biome 在 1.21.4+ 抛 `IncompatibleClassChangeError`」的说法：这是**静态分析的假阳性**。
> 用最小插件对着 spigot-api 1.20.4 编译后放进真机，`Biome.name()` 与 `Sound.valueOf()` 在
> 1.21.4 与 26.2 上都正常（Paper 运行期类型继承 `org.oldenum.OldEnum`，提供 `name()`）。
> 记录于此以免后人重复踩坑


- 补充新增配置键的说明：`features.animation_particles`、`features.animation_sounds`、
  `features.animation_styles.<magic|tech|natural>`、`features.animation_effect_interval_ticks`
  （默认 20，范围 1..200，**只影响特效刷新频率**，不改变倒计时与传送时刻）、
  `features.bedrock_particle_multiplier`（默认 0.5，仅缩放基岩玩家粒子数量，截断到 1..100）、
  `bedrock.forms.enabled`（默认 true）
- 明确 `network.server_name` **每台服务器必须唯一**：重名时双方静默丢弃对方的跨服消息
  （启动时会针对仍是默认值 `local` 的情况告警）；Redis 可用性不再假定，而是启动验证 +
  每 30 秒探活 + 每次发布时复查，状态翻转才输出日志；`network.*` 改动在 `/stp reload` 生效
  （重建连接池与订阅线程）
- 明确退款**只退金钱**：`Payment` 抽象拿不到经验/物品成本，含 XP 或卷轴的传送点在
  被第三方插件拦下时不会回滚这些消耗；跨服分支更是完全不退
- 明确战斗标签在**发起时与真正执行时各查一次**（倒计时期间可能刚被打上标签），
  `damage_interrupt` 只打断「倒计时中」的传送；跨服传送经 `TeleportGates` 走同一套判断，
  覆盖 `/home`、`/warp` 的跨服分支、`/city` 的 proxy 模式与跨服 `/tpa` / `/tpahere`
- 基岩版说明改为如实描述：Floodgate 是表单的前置，检测本身另有 Floodgate UUID 形状兜底；
  表单发不出去时回退聊天文本；基岩版上 `tech` 风格的青色 `DUST` 会换成 `CRIT`
  （Geyser 无法传递自定义粉尘颜色）；**未经真机端到端验证**
- 修正文档中的过时描述：`tollwarp` 的价格来自 `toll_warps.yml` 的每传送点数据而非
  `toll_warps_config.yml`；「三节玩法限制」此前被写成「两节」；传送失败退款此前被描述为
  「不会再发生」；`ARCHITECTURE.md` 中的跨服扣费路径由 `EconomyUtil.charge` 更正为
  `CostModel.checkAndCharge`，并补上 `CombatManager` / `CooldownManager`
- `docs/FAQ.md` 新增 Q15–Q17：配置了战斗标签仍能逃脱、跨服传送静默失效、
  基岩版表单不出现；`Q3` 改为如实描述「被拦下后退钱（只退金钱）」

### 移除 | Removed
- 仓库根目录的 `plugin.yml`（BetterTeams 的清单）
- `lang_extensions_*.yml`（从未被加载，且键与主语言文件重复、格式不一致）
- `Bukkit/src/main/java/com/novamclabs/novastorage/**`（与 nova-storage 模块重复且无人引用）
- `Sqlit-Lib` 的 `RedisBus`（反射实现无法创建 `JedisPubSub` 子类，实际不可用）
- 仓库中的 `.serena/`、空的 `Bukkit/libs/` 与 `artifacts/` 占位
- 未使用依赖：`nova-storage`（会把 4 个 JDBC 驱动打进插件包）、AureliumSkills
- `ExternalPluginStubs` 中的 Lands、SimpleClans 桩（已改用官方 API 构件）；
  Residence 桩（改为纯反射，因为两支血脉的 API 形状相反）
- 保留 Factions/SaberFactions 的桩（两个分支的 plugin.yml 同名 `Factions` 但 API 不同，
  且都无匹配的公开构件），桩内容已按 SaberFactions 4.1.9 的真实 jar 逐成员核对；
  适配器中真正跨分支分歧的调用（`getFPlayers()` 返回类型、角色枚举名）改用反射
- 未实现的配置段：`features_config.yml` 中的 `economy` / `guild` / `toll_warps` /
  `astra_navigator` / `dimensional_rifts` / `teleport_skills` / `mana_system` / `scheduler`
  （前三个与 `config.yml`、`guild_config.yml`、`toll_warps_config.yml` 重复且默认值冲突，
  其余从未有代码读取）
- `death.yml` 中无实现落的 `move_cancel` / `restrict_interact` / `use_bossbar`
  （前两项由 `config.yml` 的 `commands.*` 统一承担）
- `toll_warps_config.yml` 的 `expiration_days`、`scrolls.yml` 的 `unbound`、`steles.yml` 的
  `natural_spawning` / `player_crafting`（均无实现）
- 文档 `docs/LANGUAGE_KEYS.md`、`docs/PLUGIN_YML_UPDATES.md`（描述的键/命令已存在或属于未实现系统）

### 文档 | Docs
- README / README_CN / CONFIGURATION / CONFIG / CONFIG_CN / COMMANDS / PERMISSIONS /
  ARCHITECTURE / API / FAQ 全面校正：版本要求（Java 21+、1.21.x 与 26.x）、真实的配置归属表、
  真实的权限节点、真实可编译的 API 示例
- `ARCHITECTURE.md` 不再声称存在冷却检查、权限缓存、SQL/MySQL 存储、单元测试等未实现内容；
  统一说明当前没有自动化测试

---

## [1.3] 及更早 | 1.3 and earlier

- 基础传送功能（TPA/Home/Warp/RTP/Spawn/Back）
- 传送动画、传送石碑、RTP 池
- 跨服传送与经济系统集成

---

## 计划中 | Planned

以下特性尚未实现，相关配置项也已从配置文件移除，避免出现"配置了但不生效"的误导：

- 时空领航员系统（动态传送服务）
- 维度裂隙系统
- 传送技能系统（mcMMO / AureliumSkills 集成）
- 法力/能量系统（MythicMobs / MMOCore 集成）
- Web 管理面板、传送地图（Dynmap/BlueMap）集成

---

## 版本说明 | Version Notes

- **主版本号** (X.0.0) - 不兼容的 API 变更
- **次版本号** (0.X.0) - 新增功能，向后兼容
- **修订号** (0.0.X) - 问题修复，向后兼容
- **-SNAPSHOT** - 开发版本，不稳定
- **-beta** - 测试版本
- **-alpha** - 内部测试版本

---

Made with ❤️ by NovaMC Labs
