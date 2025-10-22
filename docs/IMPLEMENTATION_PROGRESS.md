# NovaTeleport 大规模重构与功能扩展 - 实施进度

## ✅ 已完成的工作

### 1. Maven 依赖管理（已完成）
- ✅ 父 POM 添加所有必需的仓库
  - JitPack (Vault, Towny, BetterTeams)
  - EngineHub (WorldGuard)
  - CodeMC (PlotSquared, Lands)
  - FoliaLib repository
  - GriefDefender repository
  - OpenCollab (Floodgate/Cumulus)
  - Maven Central (Jedis)
  
- ✅ Bukkit 模块 POM 添加 compileOnly 依赖
  - Vault API 1.7
  - FoliaLib 0.3.1 (compile scope with relocation)
  - WorldGuard 7.0.9
  - PlotSquared 7.3.8
  - Residence (system scope)
  - GriefDefender 2.1.0-SNAPSHOT
  - Lands 6.30.14
  - Towny 0.100.2.0
  - BetterTeams 5.1.4
  - Guilds 3.6.4.2
  - SimpleClans (system scope)
  - FactionsUUID (system scope)
  - Floodgate 2.2.3-SNAPSHOT
  - Cumulus 1.1.2
  - Jedis 5.1.0 (compile scope with relocation)
  - McMMO (system scope)
  - AureliumSkills 2.0.8
  - MythicMobs (system scope)
  - MMOCore (system scope)

- ✅ Maven Shade Plugin 配置重定位
  - FoliaLib -> com.novamclabs.lib.folialib
  - Jedis -> com.novamclabs.lib.jedis

### 2. 经济系统（已完成）
- ✅ 重写 EconomyUtil.java 使用 Vault API 依赖
- ✅ 移除反射代码
- ✅ 添加 deposit() 和 transfer() 方法
- ✅ 改进错误处理和日志记录

### 3. 领地插件集成（已完成）
- ✅ 创建新的领地适配器（使用依赖而非反射）：
  - WorldGuardAdapter.java
  - PlotSquaredAdapter.java
  - ResidenceAdapter.java
  - GriefDefenderAdapter.java
  - LandsAdapter.java
  - TownyAdapter.java
  
- ✅ 重写 RegionAdapterManager.java
  - 自动检测并注册所有可用适配器
  - 提供统一的 canEnter() 接口
  - 支持动态适配器查询

- ✅ 删除旧代码
  - ReflectionRegionAdapter.java (已删除)
  - RegionGuardUtil.java (已删除)

### 4. Towny 城镇传送（已完成）
- ✅ TownyTeleportManager.java
  - 传送到自己的城镇 spawn
  - 传送到指定城镇（公共/权限检查）
  - 检查是否同一城镇
  
- ✅ TownyCommand.java
  - /towntp 命令处理
  - 权限检查
  - Tab 补全支持（框架）

### 5. 工会系统基础（部分完成）
- ✅ GuildAdapter.java 接口
- ✅ GuildsPluginAdapter.java 实现
- ⏳ 需要完成：
  - SimpleClans 适配器
  - FactionsUUID 适配器
  - GuildManager 管理器
  - 工会传送点系统
  - 工会据点配置

---

## 🚧 待完成的工作

### 6. Folia 调度器集成（高优先级）
需要创建：
- [ ] Common 模块：SchedulerWrapper 接口
- [ ] Bukkit 模块：FoliaLibScheduler 实现
- [ ] 改造 StarTeleport 主类使用统一调度器
- [ ] 改造所有倒计时/任务为 entity-aware scheduling

### 7. 组队系统重构
需要修改现有的 party 包：
- [ ] 保留 BetterTeamsAdapter 但改为使用依赖
- [ ] 移动 PartiesAdapter（可能保留在 party）
- [ ] 创建 BetterTeams 依赖版本的适配器

### 8. 工会系统完成
需要创建：
- [ ] SimpleClansAdapter (使用依赖)
- [ ] FactionsUUIDAdapter (使用依赖)
- [ ] GuildManager 管理器
- [ ] GuildWarpManager (工会传送点)
- [ ] GuildHQManager (工会据点)
- [ ] GuildCommand 命令处理器
- [ ] 配置文件：guild_config.yml

### 9. 可配置菜单系统
需要创建：
- [ ] menus 包
- [ ] ConfigurableMenu 类（从 YAML 加载）
- [ ] MenuManager 管理器
- [ ] 配置文件：menus/*.yml
- [ ] 重构现有的硬编码菜单

### 10. 付费传送点系统
需要创建：
- [ ] toll 包
- [ ] TollWarpManager
- [ ] PublicHomeManager
- [ ] TollConfig.yml
- [ ] 命令：/home setpublic <name> [price]
- [ ] GUI 显示收费信息

### 11. 时空领航员系统（复杂）
需要创建：
- [ ] navigator 包
- [ ] NavigatorManager
- [ ] NavigatorSession (会话管理)
- [ ] BeaconStabilizer (信标稳定机制)
- [ ] WormholePortal (虫洞传送)
- [ ] NavigatorCommand
- [ ] 配置文件：astra_navigator.yml
- [ ] 粒子效果系统
- [ ] 任务锁定机制

### 12. 维度裂隙系统
需要创建：
- [ ] rift 包
- [ ] DimensionalRift 类
- [ ] RiftManager (随机生成裂隙)
- [ ] RiftEventHandler (传送劫持)
- [ ] PocketDimension (口袋维度)
- [ ] 配置文件：dimensional_rifts.yml

### 13. 传送技能系统
需要创建：
- [ ] skills 包
- [ ] TeleportSkillManager
- [ ] SkillTreeConfig
- [ ] McMMOIntegration
- [ ] AureliumSkillsIntegration
- [ ] 配置文件：teleport_skills.yml
- [ ] 技能等级系统
- [ ] 经验值计算

### 14. 法力/能量系统
需要创建：
- [ ] mana 包
- [ ] ManaManager
- [ ] MythicMobsIntegration
- [ ] MMOCoreIntegration
- [ ] BossBar/ActionBar 显示
- [ ] 配置文件：mana_system.yml
- [ ] 法力恢复任务

### 15. 传送日志与回溯
需要创建：
- [ ] log 包
- [ ] TeleportLogger
- [ ] TeleportLogEntry
- [ ] LogCommand (/tplog)
- [ ] RewindManager
- [ ] GUI 显示历史记录

### 16. 传送地图集成（附属插件）
需要创建独立模块：
- [ ] NovaTeleport-MapAddon
- [ ] DynmapIntegration
- [ ] BluemapIntegration
- [ ] WebAPI 接口

---

## 📝 配置文件需要创建

1. towny_config.yml
2. guild_config.yml
3. menus/home_menu.yml
4. menus/warp_menu.yml
5. menus/public_homes_menu.yml
6. menus/guild_menu.yml
7. toll_warps_config.yml
8. astra_navigator.yml
9. dimensional_rifts.yml
10. teleport_skills.yml
11. mana_system.yml
12. teleport_logs.yml

---

## 🔄 需要更新的现有文件

1. StarTeleport.java - 添加新管理器初始化
2. plugin.yml - 添加新命令和权限
3. config.yml - 添加新功能开关和配置
4. 语言文件 - 添加新消息键

---

## ⚠️ 注意事项

1. **libs 目录**：需要手动放置以下 JAR 文件
   - Residence.jar
   - SimpleClans.jar
   - Factions.jar (FactionsUUID)
   - mcMMO.jar
   - MythicMobs.jar
   - MMOCore.jar

2. **编译时依赖**：使用 compileOnly 的插件必须在运行时存在

3. **Folia 兼容性**：所有任务调度必须通过 FoliaLib 包装

4. **配置可选性**：所有新功能默认关闭，需在配置中启用

5. **向后兼容**：保持现有功能不受影响

---

## 📊 完成度估算

- Maven 依赖: 100%
- 反射替换: 80% (经济、领地完成，组队/工会待完成)
- Folia 集成: 0%
- 新功能: 5% (工会基础完成)
- 配置系统: 0%
- 文档更新: 0%

**总体完成度: ~20%**

---

## 🚀 下一步优先级

1. **高优先级**：Folia 调度器集成（影响所有异步操作）
2. **中优先级**：完成工会系统、BetterTeams 集成
3. **中优先级**：可配置菜单系统
4. **低优先级**：新功能模块（付费传送点、技能系统等）

建议分阶段提交，每完成一个模块进行一次测试和提交。
