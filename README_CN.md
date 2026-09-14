# NovaTeleport 2.0

[![构建状态](https://img.shields.io/github/actions/workflow/status/novamclabs/NovaTeleport/build.yml?branch=main)](https://github.com/novamclabs/NovaTeleport/actions)
[![版本](https://img.shields.io/github/v/release/novamclabs/NovaTeleport)](https://github.com/novamclabs/NovaTeleport/releases)
[![许可证](https://img.shields.io/github/license/novamclabs/NovaTeleport)](LICENSE)

一个功能强大、高度可配置的 Minecraft 传送插件，支持 Spigot / Paper / Folia 服务器。

English: [README.md](README.md)

---

## ✨ 主要特性

### 🚀 核心功能
- **多种传送方式**: TPA、Home、Warp、RTP、Spawn、Back 等
- **跨服传送**: BungeeCord / Velocity（代理侧无需安装插件）
- **Folia 兼容**: 所有调度统一走 FoliaLib，在 Folia 上按区域线程执行
- **经济系统**: Vault 集成，费用在传送真正执行时才扣除；被第三方插件拦下时退回已扣的**金钱**（不退经验/物品）
- **变量支持**: PlaceholderAPI 扩展 `%novateleport_*%`
- **基岩版支持**: Floodgate 检测 + Form 表单界面，表单不可用时回退聊天文本
- **传送可取消**: 倒计时期间移动超过配置距离自动取消（不扣费、不消耗冷却）
- **玩法限制（可选，默认关闭）**: 战斗中禁止传送（判定双向，执行时复查）、受伤打断倒计时、按类型配置的传送冷却

### 🏰 领地集成
适配器尽量使用编译期依赖（各领地插件的 API），但按类名逐个反射加载，任一插件缺席不会影响其他适配器：
- WorldGuard 7.x（只检查 ENTRY 标志）
- PlotSquared 7.x
- Residence（`tp` 标志）
- GriefDefender
- Lands（`LAND_ENTER` 标志）
- Towny

### 🎭 组队与工会
- **内置组队系统**，以及 BetterTeams、Parties 适配
- **工会系统**: 支持 Guilds、SimpleClans、FactionsUUID（据点传送、传送点、成员信息）

### 🏙️ Towny 城镇传送
- 传送到自己的城镇 / 指定城镇，含费用与权限控制

### 💰 付费传送点
- 玩家创建收费或免费的公共传送点
- 费用按比例分给所有者、其余作为服务器收入
- 价格上下限、每人数量上限、仅个人可用模式

### 🎨 高级功能
- **传送动画**: magic / tech / natural
- **传送石碑**: 结构识别、激活消耗、传送费用
- **传送卷轴**: 绑定 home/warp，使用时才消耗
- **传送门**: 自定义框架与激活物品，激活状态持久化
- **RTP 池系统**: 后台预生成随机传送坐标
- **离线传送**: 玩家上线后自动执行排队传送
- **死亡回溯**: `/deathback`，支持冷却与费用
- **传送日志 / 回溯**: 管理员可查看并回滚他人传送
- **跨服 TPA**: 通过 Redis 转发请求与应答（可选）

---

## 📦 安装

### 前置要求
- **服务端 Java 版本**：1.20.0–1.20.4 需 Java 17；1.20.5+ / 1.21.x 需 Java 21；26.x 需 Java 25
- **Spigot / Paper / Folia 1.20+**，含 **1.20.x、1.21.x 与当前版本线 26.x**（26.1 / 26.1.2 / 26.2 …）
- **（可选）Vault** — 经济系统
- **（可选）领地/工会/城镇插件** — 见下方兼容性表
- **（可选）Redis** — 跨服 TPA 转发（`network.redis.enabled`）
- **（可选）Floodgate / Cumulus** — 基岩版表单

> 插件针对 **spigot-api 1.20.1** 编译（`api-version: '1.20'`），只使用 1.20.1 就已存在且
> 至今仍然稳定的 Bukkit API，因此**同一个 jar** 可在 1.20.x / 1.21.x / 26.x 上加载。
> 插件本体是 **Java 17 字节码**：1.20.0–1.20.4 服务端只需 Java 17，
> 1.20.5+ / 1.21.x 与 26.x 都能向上兼容加载。
>
> 粒子常量在 1.20.5 被整体改名，插件通过 `ParticleCompat` 在运行期解析新旧两种名字。

### 第三方插件版本

下表是插件使用的第三方 API 版本，绝大多数取自 `pom.xml`（`provided` 编译期依赖）；更老的版本通常也能工作（适配器都做了存在性检测）。带 `*` 的两项**不是编译期依赖，不经构建验证**：Residence 没有 Maven 构件、改走反射；Factions 只依赖 `ExternalPluginStubs/` 中的手写桩。

| 插件 | 版本 | 集成内容 |
|------|------|----------|
| Vault | 1.7.1 | 经济 |
| PlaceholderAPI | 2.12.3 | `%novateleport_*%` 占位符 |
| WorldGuard | 7.0.17 | 领地 ENTRY 标志 |
| PlotSquared | 7.6.0 | 地皮进出权限 |
| Residence * | 2.6.x | 领地 `tp` 标志 |
| GriefDefender | 2.1.1-SNAPSHOT | Claim 信任等级 |
| Lands | API 8.0.0 | 领地 `LAND_ENTER` 标志 |
| Towny | 0.103.2.6 | 领地 + 城镇传送 |
| Guilds | 3.5.3.9 | 工会据点/传送点 |
| SimpleClans | 2.16.0+ | 工会（宗族） |
| FactionsUUID / SaberFactions * | 4.1.9 | 工会（势力） |
| BetterTeams | 5.1.4 | 组队 |
| Parties | API 3.2.17 | 组队（队伍名/成员同步） |
| Floodgate | 2.2.x | 基岩版识别 |
| Cumulus | 1.1.2 | 基岩版表单 |
| FoliaLib | 0.4.4 | Folia 调度（内嵌） |
| Jedis | 6.0.0 | Redis 跨服（内嵌） |

未安装的插件会被自动跳过，不会报错。

#### 测试情况 | Testing status

CI（`.github/workflows/build.yml`）只执行 `mvn -B clean package`，**不会启动服务端**，
因此没有任何版本组合经过自动化集成测试。集成插件（WorldGuard、PlotSquared、Towny、
SimpleClans、Parties、BetterTeams、Guilds、Floodgate 等）只保证**按 `pom.xml` 锁定的版本编译通过**；
运行期能否工作取决于服务端实际安装的版本，适配器都做了存在性检测，插件缺失时会被跳过而不报错。

开发期间曾在 Paper 1.20.1 / 1.21.4 / 26.2 与 Folia 26.2 上手动启动做过冒烟验证
（插件加载、适配器注册、命令响应无异常），但这属于人工验证，未纳入 CI，也无法由本仓库复现。

Floodgate / Cumulus 只验证了**插件加载与软依赖识别**，没有连接真实基岩客户端，
因此基岩版的表单交互与粒子表现**未经端到端实测**。

Lands / GriefDefender / PlotSquared / Guilds 的适配器已按各自官方 API 签名核对后编译
（Lands 的 `RoleSetting` 等历史误用已修复），但这些插件的发行包只在其官网 / SpigotMC 分发，
无法在本仓库的验证环境中自动下载实测。

其中 Residence 与 Factions 存在**两支同名血脉、API 形状相反**的情况（Residence 现代版 6.x
vs 旧版 2.6.x；SaberFactions vs 上游 FactionsUUID）。两者都没有编译期依赖：Residence 全走
反射，Factions 只靠 `ExternalPluginStubs/` 中的手写桩编译，运行时按实际安装的插件解析。

### 安装步骤

1. 从 [Releases](https://github.com/novamclabs/NovaTeleport/releases) 下载 `NovaTeleport-Bukkit.jar`
2. 放入子服 `plugins/` 目录
3. 启动服务器，生成 `plugins/NovaTeleport/` 下的默认配置
4. 编辑配置后执行 `/stp reload`

#### 代理与基岩版

- **代理（跨服）**：只需把 `NovaTeleport-Bukkit.jar` 放到各子服
  - BungeeCord：开箱即用；Velocity：无需改配置（`velocity.toml` 的 `bungee-plugin-message-channel` 默认为 `true`）
  - `network.server_name` **每台子服必须唯一**且与代理中的名称一致：重名时双方会静默丢弃对方的
    跨服消息（仍是默认值 `local` 时启动日志会警告）；跨服 TPA 还需 Redis
  - `NovaTeleport-Bungee.jar` / `NovaTeleport-Velocity.jar` 是**占位插件**，代理侧无需安装
- **基岩版**：Floodgate 提供检测与表单 API；未安装时检测回退为 Floodgate UUID 形状判断。
  表单（`/tpa`、列表菜单、确认弹窗、RTP 半径滑块）受 `bedrock.forms.enabled` 控制，
  发不出去时回退聊天文本。**未在真机基岩客户端上做过端到端验证**
- **Folia**：`folia-supported: true`，无需额外插件

---

## ⚙️ 配置

配置文件位于 `plugins/NovaTeleport/`：

| 文件 | 内容 |
|------|------|
| `config.yml` | 核心设置：语言、调试、世界阈值传送、命令延迟、经济、RTP 基础、动画/特效、空间锚点 |
| `features_config.yml` | Towny 传送、传送日志与回溯 |
| `guild_config.yml` | 工会系统 |
| `toll_warps_config.yml` | 付费传送点 |
| `party.yml` | 内置组队 |
| `death.yml` | 死亡回溯 |
| `steles.yml` | 传送石碑 |
| `portals.yml` | 自定义传送门 |
| `rtp.yml` | RTP 预生成池 |
| `scrolls.yml` | 传送卷轴 |
| `java_menus.yml` | Java 版 GUI 菜单 |
| `langs/zh_CN.yml`、`langs/en_US.yml` | 语言文件 |

```yaml
# config.yml 片段
general:
  language: zh_CN
  debug: false

commands:
  teleport_delay_seconds: 3
  cancel_on_move: true          # 移动则取消倒计时
  cancel_move_distance: 2.0
  move_cancel_exempt_types: [portal]
  block_interactions: false

features:
  animation_enabled: true               # 动画总开关
  animation_particles: true             # 只关粒子
  animation_sounds: true                # 只关音效
  animation_effect_interval_ticks: 20   # 粒子/音效刷新间隔（只影响观感，不影响倒计时）
  bedrock_particle_multiplier: 0.5      # 基岩版粒子数量倍率
  animation_styles: { magic: true, tech: true, natural: true }

bedrock:
  forms:
    enabled: true             # 基岩版表单总开关，关闭后一律走聊天文本

economy:
  enabled: false
  costs:
    home: 0
    warp: 0
    rtp: 0
    tpa: 0
```

详细配置说明请查看 [配置文档](docs/CONFIGURATION.md)

---

## 📖 命令与权限

以 `Bukkit/src/main/resources/plugin.yml` 为准。

### 基础传送命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/tpa <玩家>` | 请求传送到玩家 | `novateleport.command.tpa` |
| `/tpahere <玩家>` | 请求玩家传送到你 | `novateleport.command.tpahere` |
| `/tpaccept` / `/tpdeny` / `/tpcancel` | 接受 / 拒绝 / 取消请求 | `novateleport.command.tpaccept` 等 |
| `/home [名称]` | 传送到家（无参数打开菜单） | `novateleport.command.home` |
| `/sethome` / `/delhome` / `/homes` | 设置 / 删除 / 列出家 | `novateleport.command.home` |
| `/warp [名称]` / `/warps` | 公共传送点（无参数打开菜单） | `novateleport.command.warp` |
| `/setwarp` / `/delwarp` | 管理公共传送点 | `novateleport.command.setwarp` |
| `/spawn` | 传送到出生点 | `novateleport.command.spawn` |
| `/back` / `/deathback` | 返回上一个位置 / 死亡地点 | `novateleport.command.back` |
| `/rtp [now\|半径]` / `/rtpgui` | 随机传送 | `novateleport.command.rtp` |
| `/tpmenu` | 打开传送菜单 | `novateleport.command.tpmenu` |
| `/city`（别名 `/hub`） | 回城（本服或跨服） | `novateleport.command.spawn` |
| `/tpanimation select <风格>` | 选择动画风格 | `novateleport.animation.select` |
| `/scroll bind <home\|warp> <名称>` | 生成传送卷轴 | `novateleport.scroll.bind` |
| `/party ...` | 内置组队 | `novateleport.command.party` |

### 集成命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/towntp` | 传送到自己的城镇 | `novateleport.towny.home` |
| `/towntp <城镇>` | 传送到指定城镇 | `novateleport.towny.other` |
| `/gtp home` / `/gtp sethome` | 工会据点传送 / 设置据点 | `novateleport.guild.home` / `novateleport.guild.admin` |
| `/gtp warp <名称>` | 传送到工会传送点 | `novateleport.guild.warp` |
| `/gtp setwarp` / `/gtp delwarp` | 管理工会传送点 | `novateleport.guild.admin` |
| `/gtp list` / `/gtp info` | 列表 / 工会信息 | `novateleport.guild.use` |
| `/tollwarp list` / `/tollwarp mywarps` / `/tollwarp tp <名称>` | 使用付费传送点（`/tollwarp` 别名 `/publichomes`） | `novateleport.toll.use` |
| `/tollwarp create` / `setprice` | 创建 / 改价 | `novateleport.toll.create` |
| `/tollwarp delete <名称>` | 删除（删除他人需 `novateleport.toll.delete.others`） | `novateleport.toll.delete` |
| `/stele list\|locate\|travel` | 传送石碑 | `novateleport.stele.use` |
| `/stele create\|remove\|activatefor` | 管理石碑 | `novateleport.admin` |
| `/tplog <玩家>` | 查看并回滚传送记录 | `novateleport.admin.rewind` |
| `/forcetp <玩家> <世界> <x> <y> <z>` | 强制传送（离线排队） | `novateleport.admin` |
| `/stp reload` | 重载配置 | `novateleport.command.reload` |
| `/novateleport debug on\|off`（别名 `/ntp`） | 开关调试日志（仅本次运行有效） | `novateleport.admin` |
| `/ntp <子命令>` | 根命令同时是所有命令的路由（如 `/ntp home` 等于 `/home`，`/ntp reload` 等于 `/stp reload`） | 按被转发的命令校验 |

完整命令列表请查看 [命令文档](docs/COMMANDS.md) 与 [权限文档](docs/PERMISSIONS.md)

---

## 🔌 API 使用

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

详细说明见 [API 文档](docs/API.md) 与 [架构文档](docs/ARCHITECTURE.md)

---

## 🏗️ 从源码构建

### 前置要求
- JDK 25（输出字节码为 Java 17，但 `Velocity` 模块依赖的 `velocity-api 4.x` 本身是 Java 25 字节码；
  只构建 Bukkit 模块时 JDK 21 即可）
- Maven 3.8+
- Git

```bash
git clone https://github.com/novamclabs/NovaTeleport.git
cd NovaTeleport
mvn -B clean package -DskipTests
```

产物：

```
Bukkit/target/NovaTeleport-Bukkit-2.0-SNAPSHOT.jar      # 服务端插件（唯一必需）
BungeeCore/target/NovaTeleport-Bungee-2.0-SNAPSHOT.jar  # 代理侧占位插件（可选）
Velocity/target/NovaTeleport-Velocity-2.0-SNAPSHOT.jar  # 代理侧占位插件（可选）
target/dist/                                            # 以上三个 jar 的汇总输出（Dist 模块）
```

> `BungeeCore` 与 `Velocity` 目前只是占位插件（仅打印启动日志）：跨服切换依赖服务端直接发送
> `BungeeCord` 插件消息，代理侧无需任何代码。保留它们是为了后续需要代理侧逻辑时可直接扩展。

---

## 🤝 贡献

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

详细贡献指南请查看 [CONTRIBUTING.md](CONTRIBUTING.md)

---

## 📄 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

---

## 📞 支持

- **文档**: [docs/](docs/)
- **Issues**: [GitHub Issues](https://github.com/novamclabs/NovaTeleport/issues)

---

## 🔄 更新日志

查看 [CHANGELOG.md](CHANGELOG.md) 了解详细更新内容。

---

Made with ❤️ by NovaMC Labs
