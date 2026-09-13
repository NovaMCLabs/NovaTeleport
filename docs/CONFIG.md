# NovaTeleport Configuration Guide (EN)

See [CONFIGURATION.md](CONFIGURATION.md) for the full reference. This page is a quick summary.

Files (all under `plugins/NovaTeleport/`):
- `config.yml` — language, debug, world-threshold teleports, command delays, economy, RTP defaults, animation/effects, spatial anchors, network/Redis
- `features_config.yml` — Towny teleport, teleport log & rewind
- `guild_config.yml`, `toll_warps_config.yml`, `party.yml`, `death.yml`
- `steles.yml`, `portals.yml`, `rtp.yml`, `scrolls.yml`
- `java_menus.yml` — Java-edition GUI layout
- `langs/en_US.yml`, `langs/zh_CN.yml` — messages

Highlights
- Root command: `/novateleport` (aliases: `/ntp`, `/novatp`)
- Debug: `/ntp debug on|off` (session only) or `general.debug` in `config.yml` (persistent)

`config.yml`
- `general.language`: `en_US` | `zh_CN`
- `general.debug`: true/false
- `network.server_name`: this server's name as known to the proxy; used to detect cross-server homes/warps
- `network.redis.*`: optional Redis for forwarding cross-server TPA requests
- `auto_world_teleport.*`: threshold-based teleport (needs `novateleport.pass`)
- `commands.teleport_delay_seconds`: countdown length
- `commands.cancel_on_move` / `cancel_move_distance` / `move_cancel_exempt_types`: countdown cancellation
- `commands.block_interactions`: block interact/break/place during the countdown
- `economy.enabled`, `economy.costs.<type>`: costs are charged only when the teleport actually executes
- `features.animation_enabled`, `features.post_effect_enabled`, `animations.default_style`
- `spatial_anchors.*`: require a 3x3 anchor structure at the destination for the listed types

`portals.yml`
- Define portals under `portals:`:
  - `frame_block`: `Material` or `itemsadder:<id>`
  - `activation_item`: `Material`, `itemsadder:<id>` or `mmoitems:TYPE:ID`
  - `portal_block`: fills the interior of the rectangular frame
  - `destination`: `{ world, x, y, z }` (`SAME_AS_ENTRY` keeps the entry coordinates)
- Activated portal blocks are saved, so they still work after a restart.

`rtp.yml`
- `pregen_pool_size`: coordinates pre-generated per world
- `worlds.<name>.{enabled, center_x, center_z, min_radius, max_radius, biome_blacklist}`
- `unsafe_landing_blocks`: blocks that are never used as landing ground

`scrolls.yml`
- `bound.material` / `bound.name` for the item created by `/scroll bind`.
  The scroll is consumed only when the teleport executes.

Messages
- Colours and `{placeholder}` substitutions; `zh_CN.yml` and `en_US.yml` must define the same keys.

Economy
- Vault optional. When enabled and a provider is present, costs are charged unless the player has
  `novateleport.economy.bypass`. Charging happens at teleport time, so cancelled countdowns are free.

Bedrock compatibility
- With Floodgate installed, Bedrock players get Cumulus form menus; if the form API cannot be used a
  warning is logged once and the plugin falls back to chat menus.
