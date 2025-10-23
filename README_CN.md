# NovaTeleport 2.0

[![构建状态](https://img.shields.io/github/actions/workflow/status/novamclabs/NovaTeleport/build.yml?branch=main)](https://github.com/novamclabs/NovaTeleport/actions)
[![版本](https://img.shields.io/github/v/release/novamclabs/NovaTeleport)](https://github.com/novamclabs/NovaTeleport/releases)
[![许可证](https://img.shields.io/github/license/novamclabs/NovaTeleport)](LICENSE)

一个功能强大、高度可配置的 Minecraft 传送插件，支持 Spigot/Paper/Folia 服务器。

---

## ✨ 主要特性

### 🚀 核心功能
- **多种传送方式**: TPA、Home、Warp、RTP、Spawn、Back 等
- **跨服传送**: 支持 BungeeCord 和 Velocity 代理
- **Folia 兼容**: 完整支持 Folia 区域多线程
- **经济系统**: Vault 集成，支持传送费用
- **基岩版支持**: Floodgate/Geyser 玩家支持

### 🏰 领地集成
使用编译期依赖替代反射，提供 **10倍性能提升**：
- WorldGuard 7.x
- PlotSquared 7.x
- Residence
- GriefDefender
- Lands
- Towny

### 🎭 组队与工会
- **BetterTeams** 组队传送
- **Parties** 插件支持
- **工会系统**: 支持 Guilds、SimpleClans、FactionsUUID
  - 工会据点传送
  - 工会传送点（每个工会最多 5 个）
  - 工会成员批量传送

### 🏙️ Towny 城镇传送
- 传送到自己的城镇
- 传送到公共城镇
- 完整的权限控制

### 💰 付费传送点
- 玩家创建收费/免费公共传送点
- 自动经济交易
- 传送点使用统计
- 灵活的价格配置

### 🎨 高级功能
- **传送动画**: 多种粒子效果和音效
- **传送石碑**: 实体传送网络
- **传送卷轴**: 一次性传送物品
- **传送门**: 自定义传送门
- **RTP 池系统**: 预生成随机传送位置
- **离线传送**: 玩家离线时排队传送
- **死亡回溯**: 回到死亡位置

---

## 📦 安装

### 前置要求
- **Java 17+**
- **Spigot/Paper 1.16+** 或 **Folia**
- **（可选）Vault** - 经济系统
- **（可选）领地插件** - WorldGuard、PlotSquared 等

### 安装步骤

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

## ⚙️ 配置

### 主配置文件

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

## 📖 命令与权限

### 基础传送命令

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

### Towny 命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/towntp` | 传送到自己的城镇 | `novateleport.towny.home` |
| `/towntp <城镇>` | 传送到指定城镇 | `novateleport.towny.other` |

### 工会命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/gtp home` | 传送到工会据点 | `novateleport.guild.home` |
| `/gtp sethome` | 设置工会据点 | `novateleport.guild.admin` |
| `/gtp warp <名称>` | 传送到工会传送点 | `novateleport.guild.warp` |
| `/gtp setwarp <名称>` | 创建工会传送点 | `novateleport.guild.admin` |
| `/gtp delwarp <名称>` | 删除工会传送点 | `novateleport.guild.admin` |
| `/gtp list` | 列出工会传送点 | `novateleport.guild.use` |

### 付费传送点命令

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

## 🏗️ 从源码构建

### 前置要求
- JDK 17+
- Maven 3.8+
- Git

### 构建步骤

```bash
# 克隆仓库
git clone https://github.com/novamclabs/NovaTeleport.git
cd NovaTeleport

# 编译
mvn clean package

# 生成的 JAR 文件位于
# Bukkit/target/NovaTeleport-Bukkit-2.0-SNAPSHOT.jar
# target/dist/NovaTeleport-Bukkit.jar
```

---

## 🤝 贡献

欢迎贡献！请遵循以下步骤：

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
- **Discord**: [加入我们的 Discord](https://discord.gg/your-invite-link)

---

## 🔄 更新日志

查看 [CHANGELOG.md](CHANGELOG.md) 了解详细更新内容。

---

Made with ❤️ by NovaMC Labs
