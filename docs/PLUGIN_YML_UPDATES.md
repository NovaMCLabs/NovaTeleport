# plugin.yml 更新指南
# plugin.yml Update Guide

## 需要添加的命令

```yaml
commands:
  # ... 现有命令 ...
  
  # Towny 城镇传送
  towntp:
    description: "传送到城镇出生点"
    usage: "/<command> [城镇名称]"
    aliases: [town, ttown]
    permission: novateleport.towny.home
  
  # 工会传送
  gtp:
    description: "工会传送命令"
    usage: "/<command> <warp|sethome|delhome|list>"
    aliases: [guild, guildtp]
    permission: novateleport.guild.use
  
  # 付费传送点
  tollwarp:
    description: "付费传送点管理"
    usage: "/<command> <create|delete|list|setprice> [参数...]"
    aliases: [toll, publicwarp]
    permission: novateleport.toll.use
  
  # 时空领航员
  navigate:
    description: "请求领航服务"
    usage: "/<command> <玩家> <酬金>"
    aliases: [nav, navigator]
    permission: novateleport.navigator.request
  
  navaccept:
    description: "接受领航任务"
    usage: "/<command>"
    permission: novateleport.navigator.accept
  
  navdeny:
    description: "拒绝领航任务"
    usage: "/<command>"
    permission: novateleport.navigator.deny
  
  # 传送技能
  tpskill:
    description: "查看传送技能信息"
    usage: "/<command> [玩家]"
    aliases: [tpsk, teleportskill]
    permission: novateleport.skills.view
  
  # 法力系统
  mana:
    description: "查看法力值"
    usage: "/<command> [玩家]"
    permission: novateleport.mana.view
  
  # 传送日志
  tplog:
    description: "查看传送日志"
    usage: "/<command> <玩家> [页码]"
    aliases: [teleportlog, tphistory]
    permission: novateleport.log.view
  
  tprewind:
    description: "回溯玩家传送"
    usage: "/<command> <玩家> <记录ID>"
    aliases: [tpback, tpundo]
    permission: novateleport.admin.rewind
```

## 需要添加的权限

```yaml
permissions:
  # ... 现有权限 ...
  
  # Towny 权限
  novateleport.towny.*:
    description: "所有 Towny 传送权限"
    children:
      novateleport.towny.home: true
      novateleport.towny.other: true
  
  novateleport.towny.home:
    description: "传送到自己的城镇"
    default: true
  
  novateleport.towny.other:
    description: "传送到其他城镇"
    default: op
  
  # 工会权限
  novateleport.guild.*:
    description: "所有工会权限"
    children:
      novateleport.guild.use: true
      novateleport.guild.warp: true
      novateleport.guild.admin: true
  
  novateleport.guild.use:
    description: "使用工会传送"
    default: true
  
  novateleport.guild.warp:
    description: "使用工会传送点"
    default: true
  
  novateleport.guild.admin:
    description: "管理工会传送点"
    default: false
  
  # 付费传送点权限
  novateleport.toll.*:
    description: "所有付费传送点权限"
    children:
      novateleport.toll.use: true
      novateleport.toll.create: true
      novateleport.toll.delete: true
  
  novateleport.toll.use:
    description: "使用付费传送点"
    default: true
  
  novateleport.toll.create:
    description: "创建付费传送点"
    default: true
  
  novateleport.toll.delete:
    description: "删除付费传送点"
    default: true
  
  novateleport.toll.bypass:
    description: "免费使用所有付费传送点"
    default: op
  
  # 时空领航员权限
  novateleport.navigator.*:
    description: "所有领航员权限"
    children:
      novateleport.navigator.request: true
      novateleport.navigator.accept: true
      novateleport.navigator.deny: true
  
  novateleport.navigator.request:
    description: "请求领航服务"
    default: true
  
  novateleport.navigator.accept:
    description: "接受领航任务"
    default: true
  
  novateleport.navigator.deny:
    description: "拒绝领航任务"
    default: true
  
  # 传送技能权限
  novateleport.skills.*:
    description: "所有技能权限"
    children:
      novateleport.skills.view: true
      novateleport.skills.use: true
  
  novateleport.skills.view:
    description: "查看技能信息"
    default: true
  
  novateleport.skills.use:
    description: "使用技能系统"
    default: true
  
  # 法力系统权限
  novateleport.mana.*:
    description: "所有法力权限"
    children:
      novateleport.mana.view: true
      novateleport.mana.use: true
  
  novateleport.mana.view:
    description: "查看法力值"
    default: true
  
  novateleport.mana.use:
    description: "使用法力系统"
    default: true
  
  # 传送日志权限
  novateleport.log.*:
    description: "所有日志权限"
    children:
      novateleport.log.view: true
      novateleport.log.self: true
  
  novateleport.log.view:
    description: "查看他人传送日志"
    default: op
  
  novateleport.log.self:
    description: "查看自己的传送日志"
    default: true
  
  novateleport.admin.rewind:
    description: "回溯玩家传送"
    default: op
  
  # 维度裂隙权限
  novateleport.rift.immune:
    description: "免疫裂隙劫持"
    default: op
```

## 需要添加的软依赖（softdepend）

```yaml
softdepend:
  - Vault
  - WorldGuard
  - PlotSquared
  - Residence
  - GriefDefender
  - Lands
  - Towny
  - BetterTeams
  - Guilds
  - SimpleClans
  - Factions
  - Floodgate
  - mcMMO
  - AureliumSkills
  - MythicMobs
  - MMOCore
```

## 需要更新的 folia-supported（如果使用 Folia）

```yaml
folia-supported: true
```
