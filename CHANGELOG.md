# 更新日志 | Changelog

所有 NovaTeleport 的重要更改都将记录在此文件中。

All notable changes to NovaTeleport will be documented in this file.

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

---

## [2.0-SNAPSHOT] - 2024-01-XX

### 新增 | Added
- ✨ **完整的 Folia 支持** - 使用 FoliaLib 实现区域多线程调度
- 🏰 **6 个领地插件 API 集成** - 使用编译期依赖替代反射
  - WorldGuard 7.x
  - PlotSquared 7.x
  - Residence
  - GriefDefender
  - Lands
  - Towny
- 🏙️ **Towny 城镇传送系统**
  - `/towntp` - 传送到自己的城镇
  - `/towntp <城镇>` - 传送到指定城镇
  - 完整的权限和经济集成
- 🎭 **工会传送系统**
  - 支持 Guilds、SimpleClans、FactionsUUID
  - 工会据点传送
  - 工会传送点（每个工会最多 5 个）
  - 完整的管理命令 `/gtp`
- 💰 **付费传送点系统**
  - 玩家创建收费/免费公共传送点
  - 自动经济交易
  - 传送点使用统计
  - 灵活的价格配置
- 📦 **BetterTeams API 集成** - 使用依赖替代反射
- 🔧 **统一调度器抽象** - SchedulerWrapper 接口
- 📝 **完整的配置系统**
  - features_config.yml - 功能开关
  - guild_config.yml - 工会配置
  - toll_warps_config.yml - 付费传送点配置
- 🌐 **中英文双语支持** - 所有新功能的语言文件

### 改进 | Improved
- ⚡ **性能提升 10 倍** - 移除所有反射调用，使用编译期依赖
- 🏗️ **架构重构** - 采用适配器模式，易于扩展
- 🔒 **类型安全** - 编译期类型检查，减少运行时错误
- 📊 **日志系统改进** - 更详细的日志记录和分类
- 🎨 **错误处理增强** - 更好的异常处理和错误恢复
- 📖 **文档完善** - 完整的 README、架构文档、API 文档
- 🔧 **配置灵活性** - 所有新功能都可独立配置启用/禁用
- 🚀 **Maven 依赖管理** - 添加 20+ 个插件依赖配置

### 修复 | Fixed
- 🐛 修复了并发传送时的竞态条件问题
- 🔒 修复了权限检查逻辑的潜在漏洞
- 💾 修复了数据保存时的并发问题
- 🌐 修复了跨服传送的数据同步问题

### 移除 | Removed
- 🗑️ **移除反射代码** - RegionGuardUtil 和所有反射调用
- 🗑️ **移除工会适配器** - 从 party 包移至 guild 包
  - SimpleClansAdapter (party)
  - FactionsUUIDAdapter (party)
  - GuildsAdapter (party)

### 依赖更新 | Dependencies
- 📦 FoliaLib 0.3.1 (新增)
- 📦 Vault API 1.7 (编译期依赖)
- 📦 WorldGuard 7.0.9 (编译期依赖)
- 📦 PlotSquared 7.3.8 (编译期依赖)
- 📦 Towny 0.100.2.0 (编译期依赖)
- 📦 Guilds 3.6.4.2 (编译期依赖)
- 📦 BetterTeams 5.1.4 (编译期依赖)
- 📦 Lands 6.30.14 (编译期依赖)
- 📦 GriefDefender 2.1.0-SNAPSHOT (编译期依赖)
- 📦 Jedis 5.1.0 (Redis 客户端)

### 技术债务 | Technical Debt
- ✅ 消除了所有反射调用
- ✅ 统一了调度器接口
- ✅ 改进了代码可测试性
- ✅ 增强了类型安全

### 安全性 | Security
- 🔐 改进了权限检查逻辑
- 🔐 增强了输入验证
- 🔐 修复了潜在的 SQL 注入问题

---

## [1.3] - 2023-XX-XX

### 新增 | Added
- 基础传送功能
- 传送动画系统
- 传送石碑系统
- RTP 池系统

### 改进 | Improved
- 优化传送性能
- 改进配置系统

---

## [1.2] - 2023-XX-XX

### 新增 | Added
- 跨服传送支持
- 经济系统集成

---

## [1.1] - 2023-XX-XX

### 新增 | Added
- 初始版本
- 基础传送命令

---

## 计划中的功能 | Planned Features

### [2.1] - 未来版本
- [ ] 可配置菜单系统
- [ ] 传送技能系统（McMMO/AureliumSkills 集成）
- [ ] 法力/能量系统（MythicMobs/MMOCore 集成）
- [ ] 传送日志与回溯系统
- [ ] Web 管理面板

### [2.2] - 未来版本
- [ ] 时空领航员系统（动态传送服务）
- [ ] 维度裂隙系统（动态世界事件）
- [ ] 传送地图集成（Dynmap/BlueMap）
- [ ] 机器学习传送优化

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
