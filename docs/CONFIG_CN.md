# NovaTeleport 配置指南（CN）

文件列表：
- config.yml — 核心功能、动画、经济、世界规则
- portals.yml — 自定义传送门
- rtp.yml — 随机传送预生成
- scrolls.yml — 传送卷轴
- langs/zh_CN.yml, en_US.yml — 多语言

要点：
- 根命令：/novateleport（别名 /ntp, /novatp）
- 调试：/ntp debug on|off（或在 config.yml 中 general.debug）

config.yml（关键配置）
- general.language：en_US | zh_CN
- general.debug：true/false
- auto_world_teleport：{ delay_seconds, threshold_y, worlds: [...] }
- commands.teleport_delay_seconds：传送前延迟
- features.animation_enabled / post_effect_enabled：粒子与后处理
- animations.default_style：magic | tech | natural
- economy.enabled / bypass_permission / costs.*：经济开关与价格

portals.yml
- 在 portals: 下定义多个传送门：
  - frame_block：框架（Material 或 itemsadder:<id>）
  - activation_item：激活物品（Material 或 itemsadder:/mmoitems:TYPE:ID）
  - portal_block：内部方块（当前实现为1格入口）
  - destination：{ world, x, y, z }（可用 SAME_AS_ENTRY 保持入口坐标）

rtp.yml
- pregen_pool_size：每世界异步坐标池大小
- worlds.<name>.
  - enabled, center_x, center_z, min_radius, max_radius
  - biome_blacklist：生物群系黑名单
- unsafe_landing_blocks：禁止落地方块列表

scrolls.yml
- 可定制未绑定/已绑定卷轴的材质与名称

消息与多语言
- 支持颜色代码与占位符（{name}/{seconds} 等）

经济
- 可选安装 Vault。启用且存在经济提供者时，将按 costs 扣费，拥有 bypass 权限将免单。

基岩版兼容
- 若安装 Floodgate，将自动识别基岩玩家，GUI 不可用时回退为聊天提示。
