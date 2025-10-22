# 下一步实施指南
# Next Steps Implementation Guide

## ✅ 当前完成状态总结

### 已完成（~25%）

1. **Maven 依赖配置** ✅
   - 所有仓库已添加到父 POM
   - 所有 compileOnly/compile 依赖已添加到 Bukkit POM
   - FoliaLib 和 Jedis 重定位配置完成

2. **反射代码替换** ✅
   - EconomyUtil.java（Vault API）
   - 6个领地适配器（WorldGuard, PlotSquared, Residence, GriefDefender, Lands, Towny）
   - RegionAdapterManager 重构
   - 删除 ReflectionRegionAdapter 和 RegionGuardUtil

3. **Towny 集成** ✅
   - TownyAdapter（领地检查）
   - TownyTeleportManager（城镇传送）
   - TownyCommand（/towntp 命令）

4. **工会系统基础** ✅
   - GuildAdapter 接口
   - GuildsPluginAdapter 实现

5. **FoliaLib 调度器** ✅
   - SchedulerWrapper 接口（Common 模块）
   - FoliaScheduler 实现（Bukkit 模块）

6. **付费传送点** ✅
   - TollWarp 数据类
   - toll_warps_config.yml 配置

7. **文档** ✅
   - IMPLEMENTATION_PROGRESS.md
   - LANGUAGE_KEYS.md
   - PLUGIN_YML_UPDATES.md
   - features_config.yml

---

## 🚧 待完成的核心任务

### 阶段 1：完成基础设施（高优先级）

#### 1.1 集成 FoliaLib 到主类
**文件**: `StarTeleport.java`

**任务**:
```java
// 在 StarTeleport 类中添加：
private FoliaScheduler scheduler;

// 在 onEnable() 中：
this.scheduler = new FoliaScheduler(this);

// 替换所有 Bukkit.getScheduler() 调用为：
scheduler.runLater(...);
scheduler.runAsync(...);
// 等等
```

**影响**: 所有异步任务、倒计时、定时器

**估时**: 2-3 小时

#### 1.2 更新 plugin.yml
**文件**: `Bukkit/src/main/resources/plugin.yml`

**任务**:
- 添加所有新命令（参考 PLUGIN_YML_UPDATES.md）
- 添加所有新权限
- 添加 softdepend 列表
- 设置 `folia-supported: true`

**估时**: 30 分钟

#### 1.3 创建主配置加载器
**文件**: `ConfigManager.java`

**任务**:
```java
public class ConfigManager {
    public void loadAllConfigs() {
        // 加载 features_config.yml
        // 加载 toll_warps_config.yml
        // 等等
    }
}
```

**估时**: 1 小时

---

### 阶段 2：完成工会系统（中优先级）

#### 2.1 创建工会适配器
**需要创建的文件**:
- `guild/impl/SimpleClansAdapter.java`
- `guild/impl/FactionsUUIDAdapter.java`

**参考**: `GuildsPluginAdapter.java`

**估时**: 3-4 小时

#### 2.2 创建工会管理器
**文件**: `guild/GuildManager.java`

**功能**:
- 检测并注册所有工会适配器
- 提供统一的工会API
- 管理工会传送点和据点

**估时**: 2 小时

#### 2.3 工会传送点系统
**文件**:
- `guild/GuildWarpManager.java`
- `guild/GuildWarp.java`（数据类）
- `guild/GuildCommand.java`

**估时**: 4 小时

---

### 阶段 3：组队系统重构（中优先级）

#### 3.1 BetterTeams 适配器重构
**文件**: `party/adapter/impl/BetterTeamsAdapter.java`

**任务**:
- 移除反射代码
- 使用 BetterTeams API 依赖
- 测试同队检测

**估时**: 2 小时

#### 3.2 更新 PartyAdapterManager
**文件**: `party/adapter/PartyAdapterManager.java`

**任务**:
- 移除 Guilds/SimpleClans/FactionsUUID 的注册
- 只保留 BetterTeams 和 Parties

**估时**: 30 分钟

---

### 阶段 4：可配置菜单系统（中优先级）

#### 4.1 创建菜单框架
**需要创建的文件**:
- `menus/ConfigurableMenu.java`
- `menus/MenuManager.java`
- `menus/MenuItem.java`

**估时**: 5-6 小时

#### 4.2 创建菜单配置
**需要创建的文件**:
- `resources/menus/home_menu.yml`
- `resources/menus/warp_menu.yml`
- `resources/menus/public_homes_menu.yml`
- `resources/menus/guild_menu.yml`
- `resources/menus/toll_warps_menu.yml`

**估时**: 3 小时

#### 4.3 重构现有菜单
**任务**:
找出所有硬编码的 GUI 创建代码，替换为 ConfigurableMenu

**估时**: 4 小时

---

### 阶段 5：新功能模块（低优先级）

#### 5.1 完成付费传送点系统
**需要创建**:
- `toll/TollWarpManager.java`
- `toll/TollCommand.java`
- GUI 集成

**估时**: 6 小时

#### 5.2 时空领航员系统
**需要创建**:
- `navigator/NavigatorManager.java`
- `navigator/NavigatorSession.java`
- `navigator/BeaconStabilizer.java`
- `navigator/WormholePortal.java`
- `navigator/NavigatorCommand.java`
- `resources/astra_navigator.yml`

**估时**: 15-20 小时（最复杂）

#### 5.3 维度裂隙系统
**需要创建**:
- `rift/DimensionalRift.java`
- `rift/RiftManager.java`
- `rift/RiftEventHandler.java`
- `rift/PocketDimension.java`
- `resources/dimensional_rifts.yml`

**估时**: 10-12 小时

#### 5.4 传送技能系统
**需要创建**:
- `skills/TeleportSkillManager.java`
- `skills/SkillLevel.java`
- `skills/McMMOIntegration.java`
- `skills/AureliumSkillsIntegration.java`
- `resources/teleport_skills.yml`

**估时**: 8-10 小时

#### 5.5 法力/能量系统
**需要创建**:
- `mana/ManaManager.java`
- `mana/PlayerManaData.java`
- `mana/MythicMobsIntegration.java`
- `mana/MMOCoreIntegration.java`
- `mana/ManaDisplay.java`（BossBar/ActionBar）
- `resources/mana_system.yml`

**估时**: 8-10 小时

#### 5.6 传送日志与回溯
**需要创建**:
- `log/TeleportLogger.java`
- `log/TeleportLogEntry.java`
- `log/LogCommand.java`
- `log/RewindManager.java`
- GUI 集成

**估时**: 6-8 小时

---

## 📋 实施优先级建议

### 第一周（关键基础设施）
1. 集成 FoliaLib 到主类
2. 更新 plugin.yml
3. 创建 ConfigManager
4. 完成工会系统
5. 重构 BetterTeams 适配器

### 第二周（菜单与配置）
1. 创建可配置菜单系统
2. 重构现有菜单
3. 完成付费传送点系统
4. 测试与修复

### 第三周（新功能 - 第一批）
1. 传送日志与回溯
2. 维度裂隙系统（如果时间充裕）

### 第四周（新功能 - 第二批）
1. 传送技能系统
2. 法力/能量系统
3. 时空领航员系统（可选，最复杂）

---

## 🔧 开发工具和命令

### 编译项目
```bash
cd /home/engine/project
mvn clean package
```

### 只编译 Bukkit 模块
```bash
cd /home/engine/project/Bukkit
mvn clean package
```

### 检查依赖问题
```bash
mvn dependency:tree
```

### 跳过测试编译
```bash
mvn clean package -DskipTests
```

---

## ⚠️ 重要注意事项

1. **libs 目录**: 记得放置 system scope 的 JAR 文件
   - Residence.jar
   - SimpleClans.jar
   - Factions.jar
   - mcMMO.jar
   - MythicMobs.jar
   - MMOCore.jar

2. **测试环境**: 每个阶段完成后在测试服务器上验证

3. **版本兼容性**: 确保所有依赖版本与服务器兼容

4. **性能考虑**: 使用 FoliaLib 时注意线程安全

5. **向后兼容**: 保持现有功能不受影响

---

## 📞 需要帮助时

如果遇到问题，请参考：
- `IMPLEMENTATION_PROGRESS.md` - 进度跟踪
- `LANGUAGE_KEYS.md` - 语言键值
- `PLUGIN_YML_UPDATES.md` - plugin.yml 更新
- 各个插件的官方文档和 API 文档

---

**总估时**: 80-100 小时
**当前完成**: ~25%
**剩余工作**: ~75%

建议采用渐进式开发，每完成一个模块就进行测试和提交。
