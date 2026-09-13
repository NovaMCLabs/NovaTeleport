# NovaTeleport 2.0

[![Build](https://img.shields.io/github/actions/workflow/status/novamclabs/NovaTeleport/build.yml?branch=main)](https://github.com/novamclabs/NovaTeleport/actions)
[![Version](https://img.shields.io/github/v/release/novamclabs/NovaTeleport)](https://github.com/novamclabs/NovaTeleport/releases)
[![License](https://img.shields.io/github/license/novamclabs/NovaTeleport)](LICENSE)

一个功能强大、高度可配置的 Minecraft 传送插件，支持 Spigot / Paper / Folia 服务器。

**A powerful and highly configurable Minecraft teleportation plugin for Spigot / Paper / Folia servers.**

---

## ✨ 主要特性 | Key Features

### 🚀 核心功能 | Core Features
- **多种传送方式**: TPA、Home、Warp、RTP、Spawn、Back 等
- **跨服传送**: BungeeCord / Velocity（代理侧无需安装插件）
- **Folia 兼容**: 全部调度走 FoliaLib，在 Folia 上按区域线程执行
- **经济系统**: Vault 集成，费用在传送真正执行时才扣除
- **变量支持**: PlaceholderAPI 扩展 `%novateleport_*%`
- **基岩版支持**: Floodgate/Geyser 玩家使用表单界面
- **传送可取消**: 倒计时期间移动超过配置距离自动取消（不扣费）

### 🏰 领地集成 | Region Protection
使用编译期依赖替代反射，适配器全部为可选软依赖（按类名逐个反射加载，任一插件缺席不会影响其他适配器）：
- WorldGuard 7.x（检查 ENTRY 标志）
- PlotSquared 7.x
- Residence（`tp` 标志）
- GriefDefender
- Lands（`LAND_ENTER` 标志）
- Towny

### 🎭 组队与工会 | Party & Guild
- **内置组队系统**，以及 BetterTeams、Parties 适配
- **工会系统**: 支持 Guilds、SimpleClans、FactionsUUID
  - 工会据点传送、工会传送点、成员信息

### 🏙️ Towny 城镇传送 | Towny Integration
- 传送到自己的城镇 / 指定城镇，含费用与权限控制

### 💰 付费传送点 | Toll Warps
- 玩家创建收费或免费的公共传送点
- 费用按比例分给所有者、其余作为服务器收入
- 支持配置价格上下限、每人数量上限、仅个人可用模式

### 🎨 高级功能 | Advanced Features
- **传送动画**: magic / tech / natural 三种风格
- **传送石碑**: 结构识别、激活消耗、传送费用
- **传送卷轴**: 绑定 home/warp，使用时才消耗
- **传送门**: 自定义框架与激活物品，激活状态持久化
- **RTP 池系统**: 后台预生成随机传送坐标
- **离线传送**: 玩家上线后自动执行排队的传送
- **死亡回溯**: `/deathback` 回到死亡点，支持冷却与费用
- **传送日志 / 回溯**: 管理员可查看并回滚他人传送
- **跨服 TPA**: 通过 Redis 转发请求/应答（可选）

---

## 📦 安装 | Installation

### 前置要求 | Requirements
- **服务端 Java 版本**：1.20.0–1.20.4 需 Java 17；1.20.5+ / 1.21.x 需 Java 21；26.x 需 Java 25
- **Spigot / Paper / Folia 1.20+**，含 **1.20.x、1.21.x 与当前版本线 26.x**（26.1 / 26.1.2 / 26.2 …）
- **（可选）Vault** — 经济系统
- **（可选）领地/工会/城镇插件** — 见下方兼容性表
- **（可选）Redis** — 跨服 TPA 转发（`network.redis.enabled`）
- **（可选）Floodgate + Cumulus** — 基岩版表单界面

> 版本说明：插件针对 **spigot-api 1.20.1** 编译（`plugin.yml` 声明 `api-version: '1.20'`），
> 只使用 1.20.1 就已存在、且至今仍然稳定的 Bukkit API，因此**同一个 jar** 可在
> 1.20.x / 1.21.x / 26.x 上加载。
> 插件本体是 **Java 17 字节码**：1.20.0–1.20.4 服务端只需 Java 17，
> 1.20.5+ / 1.21.x（Java 21）与 26.x（Java 25）都能向上兼容加载。
>
> 粒子常量在 1.20.5 被整体改名，插件通过 `ParticleCompat` 在运行期解析新旧两种名字。

### 已验证的第三方插件版本 | Verified integration versions

以下版本是编译期依赖并已通过构建验证；更老的版本通常也能工作（适配器都做了存在性检测）。

| 插件 | 版本 | 集成内容 |
|------|------|----------|
| [Vault](https://github.com/MilkBowl/VaultAPI) | 1.7.1 | 经济 |
| [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI) | 2.12.3 | `%novateleport_*%` 占位符 |
| [WorldGuard](https://github.com/EngineHub/WorldGuard) | 7.0.17 | 领地 ENTRY 标志 |
| [PlotSquared](https://github.com/IntellectualSites/PlotSquared) | 7.6.0 | 地皮进出权限 |
| Residence | 2.6.x | 领地 `tp` 标志 |
| GriefDefender | 2.1.1-SNAPSHOT | Claim 信任等级 |
| Lands | API 8.0.0 | 领地 `LAND_ENTER` 标志 |
| [Towny](https://github.com/TownyAdvanced/Towny) | 0.103.2.6 | 领地 + 城镇传送 |
| Guilds | 3.5.3.9 | 工会据点/传送点 |
| SimpleClans | 2.16.0+ | 工会（宗族） |
| FactionsUUID / SaberFactions | 4.1.9 | 工会（势力） |
| BetterTeams | 5.1.4 | 组队 |
| Parties | API 3.2.17 | 组队（队伍名/成员同步） |
| Floodgate | 2.2.x | 基岩版识别 |
| Cumulus | 1.1.2 | 基岩版表单 |
| [FoliaLib](https://github.com/TechnicallyCoded/FoliaLib) | 0.4.4 | Folia 调度（已内嵌） |
| Jedis | 6.0.0 | Redis 跨服（已内嵌） |

未安装的插件会被自动跳过（`softdepend` + 运行时存在性检测），不会报错。

#### 实测环境 | Tested against

已在真实服务端启动验证（插件加载 + 适配器注册 + 命令响应均无异常）：

- **Paper 1.20.1（build 196，Java 17）** —— 验证 Java 17 字节码在 1.20.x 线可用
- **Paper 1.21.4（build，Java 21）**
- **Paper 26.2（build 123，Java 25）** + WorldGuard 7.0.17、WorldEdit 7.4.5、Towny 0.103.2.0、
  SimpleClans 2.19.2、BetterTeams 5.1.4、Parties 3.2.14、VaultUnlocked 2.20.2、Floodgate 2.2.5、
  PlaceholderAPI 2.12.3
- **Folia 26.2（build 7，Java 25）** —— 启动日志确认 `[Scheduler] Folia=true`，
  所有命令与定时任务在区域线程模型下均无 `UnsupportedOperationException`
- **粒子兼容层** —— 解析逻辑单独跑在 spigot-api 1.20.1 与 26.2 上，9 个粒子在两侧都解析成功

Residence / Lands / FactionsUUID / GriefDefender / PlotSquared / Guilds 的适配器已按各自官方
API 签名逐一核对后编译（Residence 的 `getInstance()`、Lands 的 `RoleSetting`、Factions 的
`getFPlayers()` 返回类型等历史误用已修复），但这些插件的发行包只在其官网 / SpigotMC 分发，
无法在本仓库的验证环境中自动下载实测。

Residence 与 Factions 各有**两支同名血脉且 API 形状相反**（Residence 现代版 6.x vs 旧版
2.6.x；SaberFactions vs 上游 FactionsUUID）。编译期只能匹配其中一支，因此这些调用改为反射，
运行时按实际安装的插件解析，两支都能工作。

### 安装步骤 | Installation Steps

1. **下载插件** — 从 [Releases](https://github.com/novamclabs/NovaTeleport/releases) 下载 `NovaTeleport-Bukkit.jar`
2. **安装插件**
   ```bash
   cp NovaTeleport-Bukkit.jar /path/to/server/plugins/
   ```
3. **启动服务器** — 首次启动会生成 `plugins/NovaTeleport/` 下的默认配置
4. **配置插件** — 编辑 `config.yml` 等文件，重载配置: `/stp reload`

#### 可选：代理与基岩版

- **代理（跨服）**：只需把 `NovaTeleport-Bukkit.jar` 放到各子服；
  - BungeeCord：开箱即用
  - Velocity：无需改配置（`velocity.toml` 的 `bungee-plugin-message-channel` 默认为 `true`）
  - `network.server_name` 必须与各子服名称一致；跨服 TPA 还需启用 Redis
  - `NovaTeleport-Bungee.jar` / `NovaTeleport-Velocity.jar` 是**占位插件**（仅打印启动日志），
    跨服切换靠子服直接发送 `BungeeCord` 插件消息，代理侧无需安装
- **基岩版**：安装 Floodgate（+ Cumulus）后自动启用表单界面；表单 API 不可用时回退为聊天菜单
- **Folia**：`folia-supported: true`，所有调度走区域线程；无需额外安装插件

---

## ⚙️ 配置 | Configuration

配置文件位于 `plugins/NovaTeleport/`，按功能拆分：

| 文件 | 内容 |
|------|------|
| `config.yml` | 核心设置：语言、调试、世界阈值传送、命令延迟、经济、RTP 基础、动画/特效、空间锚点 |
| `features_config.yml` | Towny 传送、传送日志与回溯 |
| `guild_config.yml` | 工会系统（据点、传送点、费用） |
| `toll_warps_config.yml` | 付费传送点 |
| `party.yml` | 内置组队 |
| `death.yml` | 死亡回溯（冷却、费用、提示） |
| `steles.yml` | 传送石碑（激活/传送消耗、结构定义） |
| `portals.yml` | 自定义传送门 |
| `rtp.yml` | RTP 预生成池（分世界半径、生物群系黑名单） |
| `scrolls.yml` | 传送卷轴物品 |
| `java_menus.yml` | Java 版 GUI 菜单布局 |
| `langs/zh_CN.yml`、`langs/en_US.yml` | 语言文件 |

`config.yml` 关键片段：

```yaml
general:
  language: zh_CN
  debug: false

network:
  server_name: local
  redis:
    enabled: false
    host: 127.0.0.1
    port: 6379
    channel: novateleport

commands:
  teleport_delay_seconds: 3
  cancel_on_move: true          # 移动则取消倒计时
  cancel_move_distance: 2.0
  move_cancel_exempt_types: [portal]
  block_interactions: false

economy:
  enabled: false
  costs:
    home: 0
    warp: 0
    rtp: 0
    tpa: 0
    # 键名与传送类型一致，费用在传送执行时扣除
```

详细说明见 [配置文档](docs/CONFIGURATION.md)。

---

## 📖 命令与权限 | Commands & Permissions

以 `Bukkit/src/main/resources/plugin.yml` 为准。

### 基础传送命令 | Basic Commands

| 命令 | 说明 | 权限 |
|------|------|------|
| `/tpa <玩家>` | 请求传送到玩家 | `novateleport.command.tpa` |
| `/tpahere <玩家>` | 请求玩家传送到你 | `novateleport.command.tpahere` |
| `/tpaccept` / `/tpdeny` / `/tpcancel` | 接受 / 拒绝 / 取消请求 | `novateleport.command.tpaccept` 等 |
| `/home [名称]` | 传送到家（无参数打开菜单） | `novateleport.command.home` |
| `/sethome [名称]` / `/delhome [名称]` / `/homes` | 设置 / 删除 / 列出家 | `novateleport.command.home` |
| `/warp [名称]` / `/warps` | 公共传送点（无参数打开菜单） | `novateleport.command.warp` |
| `/setwarp <名称>` / `/delwarp <名称>` | 管理公共传送点 | `novateleport.command.setwarp` |
| `/spawn` | 传送到出生点 | `novateleport.command.spawn` |
| `/back` | 返回上一个位置 | `novateleport.command.back` |
| `/deathback` | 返回死亡地点 | `novateleport.command.back` |
| `/rtp [now\|半径]` / `/rtpgui` | 随机传送 | `novateleport.command.rtp` |
| `/tpmenu` | 打开传送菜单 | `novateleport.command.tpmenu` |
| `/city`（别名 `/hub`） | 回城（本服或跨服） | `novateleport.command.spawn` |
| `/tpanimation select <magic\|tech\|natural>` | 选择动画风格 | `novateleport.animation.select` |
| `/scroll bind <home\|warp> <名称>` | 生成传送卷轴 | `novateleport.scroll.bind` |
| `/party ...` | 内置组队 | `novateleport.command.party` |

### 集成命令 | Integration Commands

| 命令 | 说明 | 权限 |
|------|------|------|
| `/towntp` | 传送到自己的城镇 | `novateleport.towny.home` |
| `/towntp <城镇>` | 传送到指定城镇 | `novateleport.towny.other` |
| `/gtp home` / `/gtp sethome` | 工会据点传送 / 设置据点 | `novateleport.guild.home` / `novateleport.guild.admin` |
| `/gtp warp <名称>` | 工会传送点 | `novateleport.guild.warp` |
| `/gtp setwarp` / `/gtp delwarp <名称>` | 管理工会传送点 | `novateleport.guild.admin` |
| `/gtp list` / `/gtp info` | 列表 / 工会信息 | `novateleport.guild.use` |
| `/tollwarp list` / `/mywarps` / `/tp <名称>` | 使用付费传送点 | `novateleport.toll.use` |
| `/tollwarp create <名称> [价格]` / `setprice` | 创建 / 改价 | `novateleport.toll.create` |
| `/tollwarp delete <名称>` | 删除（删除他人需 `novateleport.toll.delete.others`） | `novateleport.toll.delete` |
| `/stele list\|locate\|travel` | 传送石碑 | `novateleport.stele.use` |
| `/stele create\|remove\|activatefor` | 管理石碑 | `novateleport.admin` |
| `/tplog <玩家>` | 查看并回滚传送记录 | `novateleport.admin.rewind` |
| `/forcetp <玩家> <世界> <x> <y> <z>` | 强制传送（离线排队） | `novateleport.admin` |
| `/stp reload` | 重载配置 | `novateleport.command.reload` |
| `/novateleport debug on\|off`（别名 `/ntp`） | 开关调试日志（仅本次运行有效） | `novateleport.admin` |

完整列表见 [命令文档](docs/COMMANDS.md) 与 [权限文档](docs/PERMISSIONS.md)。

---

## 🔌 API 使用 | API Usage

### Maven 依赖 | Maven Dependency

```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>

<dependency>
    <groupId>com.github.novamclabs</groupId>
    <artifactId>NovaTeleport</artifactId>
    <version>2.0-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

### 示例代码 | Example Code

```java
StarTeleport plugin = (StarTeleport) Bukkit.getPluginManager().getPlugin("NovaTeleport");

// 调度器（Bukkit / Folia 统一）
SchedulerWrapper scheduler = plugin.getScheduler();
scheduler.runAtEntity(player, () -> { /* 在玩家所属区域执行 */ });

// 领地检查
RegionAdapterManager regionManager = plugin.getRegionManager();
boolean canTeleport = regionManager == null || regionManager.canEnter(player, targetLocation);

// 数据存储
DataStore store = plugin.getDataStore();
Location home = store.getHome(player.getUniqueId(), "home");

// 带倒计时 + 动画 + 扣费的传送
TeleportUtil.delayedTeleportWithAnimation(plugin, player, targetLocation, 3, "home",
        () -> player.sendMessage("arrived"));
```

详细说明见 [API 文档](docs/API.md) 与 [架构文档](docs/ARCHITECTURE.md)。

---

## 🏗️ 从源码构建 | Building from Source

需要 **JDK 25**。输出字节码是 Java 17（`--release 17`），但构建期要读 Java 25 字节码的依赖：
`Velocity` 代理模块依赖的 `velocity-api 4.x` 本身就是 Java 25 字节码。
若不需要代理模块，`mvn -pl Bukkit -am package` 可在 **JDK 21** 上完成
（Bukkit 模块的 Java 21 集成依赖 WorldGuard / BetterTeams / Lands 需要 JDK 21+ 的 javac）。

```bash
git clone https://github.com/novamclabs/NovaTeleport.git
cd NovaTeleport
mvn -B clean package -DskipTests
```

产物 | Output:

```
Bukkit/target/NovaTeleport-Bukkit-2.0-SNAPSHOT.jar      # 服务端插件（唯一必需）
BungeeCore/target/NovaTeleport-Bungee-2.0-SNAPSHOT.jar  # 代理侧占位插件（可选）
Velocity/target/NovaTeleport-Velocity-2.0-SNAPSHOT.jar  # 代理侧占位插件（可选）
target/dist/                                            # 以上三个 jar 的汇总输出（Dist 模块）
```

> `BungeeCore` 与 `Velocity` 目前只是占位插件（仅打印启动日志）：跨服切换依赖服务端直接发送
> `BungeeCord` 插件消息，代理侧无需任何代码。它们被保留是为了后续需要代理侧逻辑时可直接扩展。

---

## 🤝 贡献 | Contributing

见 [CONTRIBUTING.md](CONTRIBUTING.md)。

---

## 📄 许可证 | License

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

---

## 🙏 致谢 | Credits

- [Vault](https://github.com/MilkBowl/VaultAPI) — 经济系统
- [FoliaLib](https://github.com/TechnicallyCoded/FoliaLib) — Folia 调度器
- [WorldGuard](https://github.com/EngineHub/WorldGuard)、[PlotSquared](https://github.com/IntellectualSites/PlotSquared)、[Towny](https://github.com/TownyAdvanced/Towny) 等

---

## 📞 支持 | Support

- **文档**: [docs/](docs/)
- **Issues**: [GitHub Issues](https://github.com/novamclabs/NovaTeleport/issues)

---

## 🔄 更新日志 | Changelog

见 [CHANGELOG.md](CHANGELOG.md)。

---

Made with ❤️ by NovaMC Labs
