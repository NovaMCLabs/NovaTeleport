# NovaTeleport 2.0

[![Build](https://img.shields.io/github/actions/workflow/status/novamclabs/NovaTeleport/build.yml?branch=main)](https://github.com/novamclabs/NovaTeleport/actions)
[![Version](https://img.shields.io/github/v/release/novamclabs/NovaTeleport)](https://github.com/novamclabs/NovaTeleport/releases)
[![License](https://img.shields.io/github/license/novamclabs/NovaTeleport)](LICENSE)

一个功能强大、高度可配置的 Minecraft 传送插件，支持 Spigot/Paper/Folia 服务器。

**A powerful and highly configurable Minecraft teleportation plugin for Spigot/Paper/Folia servers.**

---

## ✨ 主要特性 | Key Features

### 🚀 核心功能 | Core Features
- **多种传送方式**: TPA、Home、Warp、RTP、Spawn、Back 等
- **跨服传送**: 支持 BungeeCord 和 Velocity 代理
- **Folia 兼容**: 完整支持 Folia 区域多线程
- **经济系统**: Vault 集成，支持传送费用
- **基岩版支持**: Floodgate/Geyser 玩家支持

### 🏰 领地集成 | Region Protection
使用编译期依赖替代反射，提供 **10倍性能提升**：
- WorldGuard 7.x
- PlotSquared 7.x
- Residence
- GriefDefender
- Lands
- Towny

### 🎭 组队与工会 | Party & Guild
- **BetterTeams** 组队传送
- **Parties** 插件支持
- **工会系统**: 支持 Guilds、SimpleClans、FactionsUUID
  - 工会据点传送
  - 工会传送点（每个工会最多 5 个）
  - 工会成员批量传送

### 🏙️ Towny 城镇传送 | Towny Integration
- 传送到自己的城镇
- 传送到公共城镇
- 完整的权限控制

### 💰 付费传送点 | Toll Warps
- 玩家创建收费/免费公共传送点
- 自动经济交易
- 传送点使用统计
- 灵活的价格配置

### 🎨 高级功能 | Advanced Features
- **传送动画**: 多种粒子效果和音效
- **传送石碑**: 实体传送网络
- **传送卷轴**: 一次性传送物品
- **传送门**: 自定义传送门
- **RTP 池系统**: 预生成随机传送位置
- **离线传送**: 玩家离线时排队传送
- **死亡回溯**: 回到死亡位置

---

## 📦 安装 | Installation

### 前置要求 | Requirements
- **Java 17+**
- **Spigot/Paper 1.16+** 或 **Folia**
- **（可选）Vault** - 经济系统
- **（可选）领地插件** - WorldGuard、PlotSquared 等

### 安装步骤 | Installation Steps

1. **下载插件**
   - 从 [Releases](https://github.com/novamclabs/NovaTeleport/releases) 下载最新版本
   - 选择 `NovaTeleport-Bukkit.jar`

2. **安装插件**
   ```bash
   # 将 JAR 文件放入服务器 plugins 目录
   cp NovaTeleport-Bukkit.jar /path/to/server/plugins/
   ```

3. **启动服务器**
   - 首次启动将生成默认配置文件
   - 配置文件位于 `plugins/NovaTeleport/`

4. **配置插件**
   - 编辑 `config.yml` 自定义功能
   - 编辑 `features_config.yml` 启用高级功能
   - 重载配置: `/stp reload`

---

## ⚙️ 配置 | Configuration

### 主配置文件 | Main Configuration

**config.yml** - 基础传送设置
```yaml
# 传送延迟（秒）
delay_seconds: 3

# 经济系统
economy:
  enabled: true
  costs:
    tpa: 10.0
    home: 5.0
    warp: 5.0
```

**features_config.yml** - 高级功能
```yaml
# Towny 城镇传送
towny:
  enabled: true
  home_delay: 3

# 工会系统
guild:
  enabled: true
  warps:
    max_per_guild: 5

# 付费传送点
toll_warps:
  enabled: true
  max_per_player: 3
```

详细配置说明请查看 [配置文档](docs/CONFIGURATION.md)

---

## 📖 命令与权限 | Commands & Permissions

### 基础传送命令 | Basic Commands

| 命令 | 说明 | 权限 |
|------|------|------|
| `/tpa <玩家>` | 请求传送到玩家 | `novateleport.command.tpa` |
| `/tpahere <玩家>` | 请求玩家传送到你 | `novateleport.command.tpahere` |
| `/tpaccept` | 接受传送请求 | `novateleport.command.tpaccept` |
| `/tpdeny` | 拒绝传送请求 | `novateleport.command.tpdeny` |
| `/home [名称]` | 传送到家 | `novateleport.command.home` |
| `/sethome [名称]` | 设置家 | `novateleport.command.home` |
| `/warp [名称]` | 传送到公共传送点 | `novateleport.command.warp` |
| `/spawn` | 传送到出生点 | `novateleport.command.spawn` |
| `/back` | 返回上一个位置 | `novateleport.command.back` |
| `/rtp` | 随机传送 | `novateleport.command.rtp` |

### Towny 命令 | Towny Commands

| 命令 | 说明 | 权限 |
|------|------|------|
| `/towntp` | 传送到自己的城镇 | `novateleport.towny.home` |
| `/towntp <城镇>` | 传送到指定城镇 | `novateleport.towny.other` |

### 工会命令 | Guild Commands

| 命令 | 说明 | 权限 |
|------|------|------|
| `/gtp home` | 传送到工会据点 | `novateleport.guild.home` |
| `/gtp sethome` | 设置工会据点 | `novateleport.guild.admin` |
| `/gtp warp <名称>` | 传送到工会传送点 | `novateleport.guild.warp` |
| `/gtp setwarp <名称>` | 创建工会传送点 | `novateleport.guild.admin` |
| `/gtp delwarp <名称>` | 删除工会传送点 | `novateleport.guild.admin` |
| `/gtp list` | 列出工会传送点 | `novateleport.guild.use` |

### 付费传送点命令 | Toll Warp Commands

| 命令 | 说明 | 权限 |
|------|------|------|
| `/tollwarp create <名称> <价格>` | 创建付费传送点 | `novateleport.toll.create` |
| `/tollwarp tp <名称>` | 使用付费传送点 | `novateleport.toll.use` |
| `/tollwarp list` | 列出所有传送点 | `novateleport.toll.use` |
| `/tollwarp mywarps` | 查看自己的传送点 | `novateleport.toll.use` |
| `/tollwarp setprice <名称> <价格>` | 修改价格 | `novateleport.toll.create` |
| `/tollwarp delete <名称>` | 删除传送点 | `novateleport.toll.delete` |

完整命令列表请查看 [命令文档](docs/COMMANDS.md)

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
// 获取插件实例
StarTeleport plugin = (StarTeleport) Bukkit.getPluginManager().getPlugin("NovaTeleport");

// 检查玩家是否可以传送到某个位置
RegionAdapterManager regionManager = plugin.getRegionManager();
boolean canTeleport = regionManager.canEnter(player, targetLocation);

// 使用 FoliaLib 调度器
FoliaScheduler scheduler = plugin.getScheduler();
scheduler.runAtEntity(player, () -> {
    // 在玩家实体区域执行任务
});

// 工会管理
GuildManager guildManager = plugin.getGuildManager();
String guildId = guildManager.getGuildId(player);
boolean sameGuild = guildManager.isSameGuild(player1, player2);
```

详细 API 文档请查看 [API 文档](docs/API.md)

---

## 🏗️ 从源码构建 | Building from Source

### 前置要求 | Prerequisites
- JDK 17+
- Maven 3.8+
- Git

### 构建步骤 | Build Steps

```bash
# 克隆仓库
git clone https://github.com/novamclabs/NovaTeleport.git
cd NovaTeleport

# 编译
mvn clean package

# 生成的 JAR 文件位于
# Generated JAR files in:
# Bukkit/target/NovaTeleport-Bukkit-2.0-SNAPSHOT.jar
# target/dist/NovaTeleport-Bukkit.jar
```

---

## 🤝 贡献 | Contributing

欢迎贡献！请遵循以下步骤：

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

详细贡献指南请查看 [CONTRIBUTING.md](CONTRIBUTING.md)

---

## 📄 许可证 | License

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

---

## 🙏 致谢 | Credits

### 依赖项 | Dependencies
- [Vault](https://github.com/MilkBowl/VaultAPI) - 经济系统
- [FoliaLib](https://github.com/TechnicallyCoded/FoliaLib) - Folia 调度器
- [WorldGuard](https://github.com/EngineHub/WorldGuard) - 领地保护
- [PlotSquared](https://github.com/IntellectualSites/PlotSquared) - 地皮系统
- [Towny](https://github.com/TownyAdvanced/Towny) - 城镇系统
- 以及其他优秀的开源项目

### 贡献者 | Contributors
感谢所有为本项目做出贡献的开发者！

---

## 📞 支持 | Support

- **文档**: [docs/](docs/)
- **Issues**: [GitHub Issues](https://github.com/novamclabs/NovaTeleport/issues)
- **Discord**: [加入我们的 Discord](https://discord.gg/your-invite-link)

---

## 🔄 更新日志 | Changelog

### [2.0-SNAPSHOT] - 2024
#### 新增 | Added
- ✨ 完整的 Folia 支持（FoliaLib 集成）
- 🏰 6 个领地插件集成（使用 API 替代反射）
- 🏙️ Towny 城镇传送系统
- 🎭 工会传送系统（Guilds、SimpleClans、FactionsUUID）
- 💰 付费传送点系统
- ⚡ 性能提升 10 倍（移除反射调用）

#### 改进 | Improved
- 🔧 完全重构的架构（适配器模式）
- 📦 使用编译期依赖提高稳定性
- 📝 完整的中英文文档
- 🎨 改进的错误处理和日志

#### 修复 | Fixed
- 🐛 修复了多个并发问题
- 🔒 改进了权限检查逻辑

查看完整更新日志：[CHANGELOG.md](CHANGELOG.md)

---

Made with ❤️ by NovaMC Labs
