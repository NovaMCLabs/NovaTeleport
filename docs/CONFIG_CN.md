# NovaTeleport 配置指南（CN）

完整参考见 [CONFIGURATION.md](CONFIGURATION.md)。本页为速查。

文件（均位于 `plugins/NovaTeleport/`）：
- `config.yml` — 语言、调试、世界阈值传送、命令延迟与取消、经济、RTP 基础、动画/特效、空间锚点、网络/Redis
- `features_config.yml` — Towny 传送、传送日志与回溯
- `guild_config.yml`、`toll_warps_config.yml`、`party.yml`、`death.yml`
- `steles.yml`、`portals.yml`、`rtp.yml`、`scrolls.yml`
- `java_menus.yml` — Java 版 GUI 布局
- `langs/zh_CN.yml`、`langs/en_US.yml` — 语言文件

要点
- 根命令：`/novateleport`（别名 `/ntp`、`/novatp`）
- 调试：`/ntp debug on|off`（仅本次运行）或 `config.yml` 的 `general.debug`（永久生效）

`config.yml`
- `general.language`：`zh_CN` | `en_US`
- `general.debug`：true/false
- `network.server_name`：本服在代理中的名称，用于判断跨服家/传送点。**每个服务器必须唯一**——
  两台服务器重名时，双方会静默丢弃对方的跨服消息（仍是默认值 `local` 时启动日志会给出警告）
- `network.redis.*`：可选的 Redis，用于转发跨服 TPA 请求。可用性在启动时验证、之后每 30 秒
  与每次发布时复查，因此 `enabled: true` 并不等于通道可用
- `bedrock.forms.enabled`（默认 `true`）：基岩版 Form 表单总开关；关闭后基岩玩家一律走聊天文本菜单
- `auto_world_teleport.*`：阈值自动传送（需要 `novateleport.pass` 权限）
- `commands.teleport_delay_seconds`：倒计时长度
- `commands.cancel_on_move` / `cancel_move_distance` / `move_cancel_exempt_types`：倒计时取消规则
- `commands.block_interactions`：倒计时期间禁止交互/破坏/放置
- `economy.enabled`、`economy.costs.<类型>`：费用只在传送真正执行时扣除；
  金钱、经验、物品三类成本会**先一起校验、全部通过才扣**，不会出现扣了一半的情况。
  若扣费之后传送被第三方插件拦下，会**退回金钱**——只退金钱，**不退经验等级、不退物品**
- `economy.global_fallback.enabled`（**默认关闭**）：某功能自己没写费用键时，
  回退到 `economy.costs.<类型>`。默认关闭是因为各功能的默认配置都写了 cost 键（写 0 也算写了），
  开启后只有在**主动删掉**特征键时全局值才会接管
- `features.animation_enabled`（**总开关**）、`features.animation_particles`、
  `features.animation_sounds`、`features.animation_styles.{magic,tech,natural}`、
  `features.animation_effect_interval_ticks`（默认 `20`，范围 `1..200`，**只影响特效刷新频率**，
  不改变倒计时长度与传送时刻）、`features.bedrock_particle_multiplier`（默认 `0.5`，
  仅缩放基岩玩家的粒子数量，结果截断到 `1..100`）
- `features.post_effect_enabled`、`animations.default_style`
- `spatial_anchors.*`：为指定传送类型要求落点存在 3x3 锚点结构
- `combat_tag.*`（**默认关闭**）：战斗中禁止发起传送。判定是双向的——造成或受到伤害
  都算进入战斗（含与生物的战斗），可配时长、是否受击刷新、豁免类型、bypass 权限
- `damage_interrupt.*`（**默认关闭**）：受伤打断进行中的倒计时。
  **独立于 `combat_tag`**，即使战斗标签关着也生效
- `teleport_cooldowns.*`（**默认关闭**）：按传送类型分别配置的冷却秒数。
  冷却在传送**真正执行后**才登记，倒计时被取消或校验/扣费失败都不消耗冷却

> 战斗标签、受伤打断、冷却三节默认全部关闭，升级后行为与之前完全一致。

`portals.yml`
- 在 `portals:` 下定义传送门：
  - `frame_block`：`Material` 或 `itemsadder:<id>`
  - `activation_item`：`Material`、`itemsadder:<id>` 或 `mmoitems:TYPE:ID`
  - `portal_block`：填充矩形框架内部
  - `destination`：`{ world, x, y, z }`（`SAME_AS_ENTRY` 表示沿用进入坐标）
- 已激活的传送门方块会被保存，重启后依然可用。

`rtp.yml`
- `pregen_pool_size`：每个世界预生成的坐标数量
- `worlds.<name>.{enabled, center_x, center_z, min_radius, max_radius, biome_blacklist}`
- `unsafe_landing_blocks`：不允许作为落脚点的方块

`scrolls.yml`
- `bound.material` / `bound.name`：`/scroll bind` 生成的卷轴物品。
  卷轴只在传送真正执行时才消耗。

消息与多语言
- 支持颜色代码与 `{占位符}`；`zh_CN.yml` 与 `en_US.yml` 必须定义相同的键。

经济
- 可选安装 Vault。启用且存在经济提供者时按 `costs` 扣费，拥有
  `novateleport.economy.bypass` 权限的玩家免单。费用在传送执行时扣除，取消倒计时不扣钱。

基岩版兼容
- 检测优先走 Floodgate 的 API；未安装时回退为 Floodgate UUID 形状判断（高 64 位恒为 0），
  因此没有 Floodgate 也能认出大部分基岩玩家
- 表单（`/tpa` 接受/拒绝、列表菜单、确认弹窗、`/rtp` 半径滑块）需要 **Floodgate**，
  并受 `bedrock.forms.enabled` 控制。表单发不出去时每个阶段只告警一次，调用方回退为聊天提示
- 基岩版上 `tech` 风格的青色 `DUST` 粒子会换成 `CRIT`：Geyser 无法正确传递自定义颜色
- **尚未在真机基岩客户端上做过端到端验证**
