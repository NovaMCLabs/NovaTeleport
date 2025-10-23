# NovaTeleport 架构文档
# NovaTeleport Architecture Documentation

本文档详细说明 NovaTeleport 2.0 的架构设计。

This document details the architecture design of NovaTeleport 2.0.

---

## 📐 总体架构 | Overall Architecture

### 模块化设计 | Modular Design

NovaTeleport 采用多模块 Maven 项目结构：

```
NovaTeleport-Parent/
├── Common/          # 共享代码和接口
├── Bukkit/          # Bukkit/Spigot/Paper/Folia 实现
├── BungeeCore/      # BungeeCord 代理支持
├── Velocity/        # Velocity 代理支持
├── Folia/           # Folia 特定功能
└── Sqlit-Lib/       # 数据存储库
```

---

## 🔌 适配器模式 | Adapter Pattern

### 领地插件适配 | Region Plugin Integration

所有第三方插件集成都使用适配器模式，实现松耦合：

```java
// 接口定义
public interface RegionAdapter {
    String name();
    boolean isPresent();
    boolean canEnter(Player player, Location destination);
}

// 具体实现
public class WorldGuardAdapter implements RegionAdapter {
    // WorldGuard API 实现
}

public class PlotSquaredAdapter implements RegionAdapter {
    // PlotSquared API 实现
}
```

**优势**:
- ✅ 易于添加新插件支持
- ✅ 插件间互不干扰
- ✅ 运行时动态检测可用插件
- ✅ 编译期依赖，性能优秀

### 工会插件适配 | Guild Plugin Integration

类似的适配器模式用于工会插件：

```
guild/
├── GuildAdapter.java          # 接口
├── GuildManager.java          # 管理器
└── impl/
    ├── GuildsPluginAdapter.java
    ├── SimpleClansAdapter.java
    └── FactionsUUIDAdapter.java
```

---

## ⚙️ 调度器抽象 | Scheduler Abstraction

### 统一调度器接口

为了兼容 Bukkit 和 Folia，实现了统一的调度器抽象：

```java
// Common 模块接口
public interface SchedulerWrapper {
    void runAsync(Runnable task);
    void runAtEntity(Object entity, Runnable task);
    CompletableFuture<Boolean> teleportAsync(Object entity, Object location);
    // ...
}

// Bukkit 模块实现
public class FoliaScheduler implements SchedulerWrapper {
    private final FoliaLib foliaLib;
    // FoliaLib 实现
}
```

**自动检测**:
- 在 Folia 服务器上使用 FoliaLib
- 在 Bukkit/Paper 服务器上降级到传统调度器
- API 保持一致，无需修改业务代码

---

## 💾 数据管理 | Data Management

### 数据存储层

```
DataStore
├── Home 数据
├── Warp 数据
├── Guild Warp 数据
├── Toll Warp 数据
└── Player 数据
```

**存储方式**:
- SQLite（本地）
- MySQL（可选，跨服数据）
- Redis（可选，实时数据同步）

---

## 🔄 事件系统 | Event System

### 传送事件流程

```
Player Action
    ↓
Command Handler
    ↓
Permission Check
    ↓
Economy Check (Vault)
    ↓
Region Check (Adapters)
    ↓
Cooldown Check
    ↓
Teleport Countdown
    ↓
Movement Check
    ↓
Execute Teleport (Scheduler)
    ↓
Animation & Effects
```

---

## 🏗️ 依赖注入 | Dependency Injection

### 管理器初始化

主插件类负责初始化所有管理器：

```java
public class StarTeleport extends JavaPlugin {
    // 调度器
    private FoliaScheduler scheduler;
    
    // 功能管理器
    private RegionAdapterManager regionManager;
    private GuildManager guildManager;
    private TownyTeleportManager townyManager;
    private TollWarpManager tollWarpManager;
    
    @Override
    public void onEnable() {
        // 初始化顺序很重要
        this.scheduler = new FoliaScheduler(this);
        this.regionManager = new RegionAdapterManager(this);
        this.guildManager = new GuildManager(this);
        // ...
    }
}
```

---

## 🔐 权限系统 | Permission System

### 层次化权限

```
novateleport.*
├── novateleport.command.*
│   ├── tpa
│   ├── home
│   └── warp
├── novateleport.guild.*
│   ├── use
│   ├── home
│   └── admin
├── novateleport.towny.*
│   ├── home
│   └── other
└── novateleport.toll.*
    ├── use
    ├── create
    └── bypass
```

---

## 📦 包结构 | Package Structure

```
com.novamclabs/
├── commands/          # 命令处理器
├── util/              # 工具类
├── region/            # 领地集成
│   ├── RegionAdapter.java
│   ├── RegionAdapterManager.java
│   └── impl/          # 各领地插件适配器
├── guild/             # 工会系统
│   ├── GuildAdapter.java
│   ├── GuildManager.java
│   ├── GuildWarpManager.java
│   └── impl/          # 各工会插件适配器
├── towny/             # Towny 集成
│   ├── TownyTeleportManager.java
│   └── TownyCommand.java
├── toll/              # 付费传送点
│   ├── TollWarp.java
│   ├── TollWarpManager.java
│   └── TollWarpCommand.java
├── scheduler/         # 调度器
│   └── FoliaScheduler.java
├── party/             # 组队系统
├── animations/        # 传送动画
├── portals/           # 传送门
├── stele/             # 传送石碑
└── rtp/               # 随机传送
```

---

## 🔧 配置系统 | Configuration System

### 多层次配置

```
config.yml              # 主配置
├── features_config.yml # 功能开关
├── guild_config.yml    # 工会配置
├── toll_warps_config.yml # 付费传送点配置
└── lang/               # 语言文件
    ├── zh_CN.yml
    └── en_US.yml
```

---

## 🚀 性能优化 | Performance Optimization

### 1. 移除反射
- 使用编译期依赖替代运行时反射
- 性能提升 10 倍

### 2. 异步处理
- 数据库操作异步化
- 网络请求异步化

### 3. 缓存机制
- 权限检查结果缓存
- 配置数据缓存
- 领地查询缓存

### 4. Folia 优化
- 实体操作在实体调度器执行
- 区域操作在区域调度器执行
- 避免跨线程访问

---

## 🔄 扩展性 | Extensibility

### 添加新领地插件支持

1. 创建适配器类实现 `RegionAdapter`
2. 在 `RegionAdapterManager` 中注册
3. 添加 Maven 依赖
4. 完成！

```java
public class NewRegionAdapter implements RegionAdapter {
    @Override
    public String name() {
        return "NewRegion";
    }
    
    @Override
    public boolean isPresent() {
        return Bukkit.getPluginManager().getPlugin("NewRegion") != null;
    }
    
    @Override
    public boolean canEnter(Player p, Location dest) {
        // 实现检查逻辑
        return true;
    }
}
```

---

## 🧪 测试策略 | Testing Strategy

### 单元测试
- 核心逻辑单元测试
- 工具类测试
- 配置解析测试

### 集成测试
- 插件加载测试
- 命令执行测试
- 权限检查测试

### 性能测试
- 并发传送测试
- 大量玩家测试
- 内存泄漏检查

---

## 📊 监控与日志 | Monitoring & Logging

### 日志级别

```
INFO    - 正常运行信息
WARNING - 警告信息（不影响运行）
SEVERE  - 严重错误
DEBUG   - 调试信息（需开启）
```

### 关键日志点

- 插件启动/关闭
- 适配器注册
- 传送执行
- 经济交易
- 错误异常

---

## 🔐 安全性 | Security

### 权限检查
- 命令执行前权限验证
- 传送前权限验证
- 经济操作权限验证

### 数据验证
- 输入参数验证
- 坐标合法性检查
- 金额范围检查

### 防御性编程
- Try-Catch 保护关键代码
- Null 检查
- 类型检查

---

## 🎯 设计原则 | Design Principles

1. **单一职责** - 每个类只负责一个功能
2. **开闭原则** - 对扩展开放，对修改关闭
3. **里氏替换** - 适配器可互换
4. **接口隔离** - 接口粒度合理
5. **依赖倒置** - 依赖抽象而非实现

---

## 🔮 未来规划 | Future Plans

- [ ] 更多领地插件支持
- [ ] Web 控制面板
- [ ] 传送地图可视化
- [ ] 机器学习传送优化
- [ ] 微服务架构支持

---

Made with ❤️ by NovaMC Labs
