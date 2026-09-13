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
- `network.server_name`：本服在代理中的名称，用于判断跨服家/传送点
- `network.redis.*`：可选的 Redis，用于转发跨服 TPA 请求
- `auto_world_teleport.*`：阈值自动传送（需要 `novateleport.pass` 权限）
- `commands.teleport_delay_seconds`：倒计时长度
- `commands.cancel_on_move` / `cancel_move_distance` / `move_cancel_exempt_types`：倒计时取消规则
- `commands.block_interactions`：倒计时期间禁止交互/破坏/放置
- `economy.enabled`、`economy.costs.<类型>`：费用只在传送真正执行时扣除
- `features.animation_enabled`、`features.post_effect_enabled`、`animations.default_style`
- `spatial_anchors.*`：为指定传送类型要求落点存在 3x3 锚点结构

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
- 安装 Floodgate 后基岩玩家使用 Cumulus 表单；若表单 API 不可用会记录一次警告并回退为聊天菜单。
