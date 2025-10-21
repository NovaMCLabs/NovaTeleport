[**简体中文 - CN**] | [[**English - EN**](README.md)]

### NovaTeleport - 星之传送

全功能传送解决方案（Spigot/Paper，Java 17）。

核心特性：
- 阈值触发跨世界传送
- 三种传送动画：魔法(Magic) | 科技(Tech) | 自然(Natural)，玩家可自选
- Vault 经济支持（可选，支持免单权限）
- 高性能随机传送RTP：异步预生成坐标池（rtp.yml）
- 自定义传送门：方块框架 + 物品激活（portals.yml）
- 传送卷轴：可绑定家/传送点（scrolls.yml）
- 家/传送点/返回/出生点/TPA/GUI（Java物品栏 + 基岩版聊天回退）
- 主命令路由：/novateleport（别名 /ntp, /novatp）
- 回城：/city（或 /hub）支持本服或跨服
- 轻量内置组队系统：/party

---

快速开始
1) 放入 plugins/ 并启动一次生成文件
2) 配置：
   - config.yml（核心、动画、功能、经济）
   - portals.yml（自定义传送门）
   - rtp.yml（RTP 预生成池与世界规则）
   - scrolls.yml（卷轴外观）
   - langs/en_US.yml, zh_CN.yml（多语言）
3) 权限见 plugin.yml。经济可装 Vault；基岩版识别可装 Floodgate。

---

常用指令（建议通过路由 /novateleport | /ntp 使用）
- /stp reload — 重载配置
- /tpanimation select <magic|tech|natural> — 设置个人动画风格
- /scroll bind <home|warp> <名称> — 生成绑定卷轴
- /city （/hub）— 前往主城（本服或跨服）
- /party <create|invite <玩家>|accept|leave|tp> — 内置组队
- 仍然支持：/tpa /tpahere /tpaccept /tpdeny /tpcancel /sethome /home /delhome /homes /setwarp /warp /delwarp /warps /spawn /back /rtp /tpmenu

---

配置文件说明
- config.yml
  - general.language, general.debug
  - auto_world_teleport: delay_seconds, threshold_y, worlds
  - commands.teleport_delay_seconds
  - features.animation_enabled, features.post_effect_enabled
  - animations.default_style
  - economy.enabled, economy.bypass_permission, economy.costs.*
- portals.yml：定义传送门框架、激活物品（支持原版/ItemsAdder/MMOItems）、内部方块和目标
- rtp.yml：异步预生成池大小、世界半径/生物群系黑名单/不安全落地点
- scrolls.yml：卷轴材质与名称

---

开发者提示
- 入口类：com.novamclabs.StarTeleport
- 动画管理：com.novamclabs.animations.AnimationManager
- RTP池：com.novamclabs.rtp.RtpPoolManager
- 自定义传送门：com.novamclabs.portals.PortalManager
- 经济：com.novamclabs.util.EconomyUtil
