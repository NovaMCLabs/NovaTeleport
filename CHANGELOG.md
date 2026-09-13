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

### 修复 | Fixed
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
