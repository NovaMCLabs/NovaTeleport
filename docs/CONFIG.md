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
- `network.server_name`: this server's name as known to the proxy; used to detect cross-server
  homes/warps. **Must be unique per server** — two servers sharing a name silently drop each
  other's cross-server messages (the startup log warns if it is still the default `local`)
- `network.redis.*`: optional Redis for forwarding cross-server TPA requests. Reachability is
  verified at startup, re-checked every 30s and on every publish, so `enabled: true` alone does
  not mean the transport is up
- `bedrock.forms.enabled` (default `true`): master switch for the Cumulus forms sent to Bedrock
  players; `false` sends every Bedrock player to the chat fallback
- `auto_world_teleport.*`: threshold-based teleport (needs `novateleport.pass`)
- `commands.teleport_delay_seconds`: countdown length
- `commands.cancel_on_move` / `cancel_move_distance` / `move_cancel_exempt_types`: countdown cancellation
- `commands.block_interactions`: block interact/break/place during the countdown
- `economy.enabled`, `economy.costs.<type>`: costs are charged only inside the teleport execution
  step — after the region/anchor checks, immediately before the teleport is attempted — so a
  cancelled countdown is free.
  Money, XP levels and items are validated together first and applied only if all pass, so a partial
  deduction cannot happen. If a third-party plugin blocks the teleport after charging, the money is
  refunded — **money only**, never XP levels or items
- `economy.global_fallback.enabled` (**off by default**): falls back to `economy.costs.<type>` when a
  feature does not define its own cost key. Off by default because every shipped feature config already
  writes its cost key (writing `0` counts) — enabling it only matters if you delete a feature key
- `features.animation_enabled` (**master switch**), `features.animation_particles`,
  `features.animation_sounds`, `features.animation_styles.{magic,tech,natural}`,
  `features.animation_effect_interval_ticks` (default `20`, `1..200` — visual cadence only, it does
  not change the countdown or the teleport moment), `features.bedrock_particle_multiplier` (default
  `0.5`; scales particle counts for Bedrock players, clamped to `1..100`)
- `features.post_effect_enabled`, `animations.default_style`
- `spatial_anchors.*`: require a 3x3 anchor structure at the destination for the listed types
- `combat_tag.*` (**off by default**): cannot start a teleport while in combat. Bidirectional —
  dealing or taking damage tags you, players and mobs alike
- `damage_interrupt.*` (**off by default**): taking damage cancels a running countdown.
  Independent of `combat_tag`
- `teleport_cooldowns.*` (**off by default**): per-teleport-type cooldown. Recorded only after the
  teleport actually executes, so a cancelled countdown never consumes it

> All three gameplay sections are off by default; an upgrading server keeps its current behaviour.

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
- Detection uses Floodgate's API when the plugin is installed, and otherwise falls back to the
  Floodgate UUID shape (zero high 64-bit half), so Bedrock players are still recognised without it.
- Forms (`/tpa` accept/deny, list menus, confirmations, the RTP radius slider) require Floodgate and
  `bedrock.forms.enabled: true`. When a form cannot be sent, a warning is logged once per stage and
  the caller falls back to a chat message.
- On Bedrock the `tech` style's aqua `DUST` particles are replaced with `CRIT`: Geyser misreads
  custom dust colours.
- Not verified end-to-end against a real Bedrock client.
