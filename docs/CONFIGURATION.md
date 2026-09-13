# Configuration Guide (NovaTeleport)

All files live under `plugins/NovaTeleport/`.

> `config.yml` is the only file loaded through Bukkit's `saveDefaultConfig()`.
> Every other file is created/loaded by its own manager on first run.

## 1. File list

| File | Loaded by | Contents |
|---|---|---|
| `config.yml` | `StarTeleport` | language, debug, world-threshold teleports, command delays, economy, RTP defaults, animation/effects, spatial anchors, network/Redis |
| `features_config.yml` | `TownyTeleportManager`, `TeleportLogManager` | Towny teleport, teleport log & rewind |
| `guild_config.yml` | `GuildManager` | guild HQ / warps / costs / which guild plugins to use |
| `toll_warps_config.yml` | `TollWarpManager` | toll warp mode, limits, price range, owner fee share |
| `party.yml` | `PartyManager` | built-in party limits and name prefixes |
| `death.yml` | `DeathManager` | death-back cooldown, cost, prompts, auto random respawn |
| `steles.yml` | `SteleManager` | stele activation cost, travel cost, structure definitions |
| `portals.yml` | `PortalManager` | custom portal definitions |
| `rtp.yml` | `RtpPoolManager` | RTP pre-generation pool (per-world radius/blacklist/unsafe blocks) |
| `scrolls.yml` | `ScrollManager` | bound scroll item material/name |
| `java_menus.yml` | `JavaMenuConfig` | Java-edition GUI layouts |
| `langs/en_US.yml`, `langs/zh_CN.yml` | `LanguageManager` | messages |

Generated data (not meant for manual editing): `data/homes.yml`, `data/warps.yml`,
`data/players/<uuid>.yml`, `data/teleport_logs.yml`, `data/offline.yml`,
`data/portals_state.yml`, `data/steles_index.yml`, `guild_warps.yml`, `toll_warps.yml`.

## 2. `config.yml`

### General / network

```yaml
general:
  language: zh_CN      # zh_CN | en_US
  debug: false

network:
  server_name: local   # must match the server name known to your proxy
  redis:
    enabled: false
    host: 127.0.0.1
    port: 6379
    password: ""
    channel: novateleport
```

`network.server_name` decides whether a stored home/warp is treated as *remote*
(cross-server) or local.

### Command delays and cancellation

```yaml
commands:
  teleport_delay_seconds: 3      # countdown before command teleports
  cancel_on_move: true           # cancel the countdown if the player walks away
  cancel_move_distance: 2.0      # blocks
  move_cancel_exempt_types:      # teleport types that ignore cancel_on_move
    - portal
  block_interactions: false      # block interact/break/place during the countdown
```

Cancelling a countdown never charges the player: **costs are only taken at the moment the
teleport actually executes** (see Economy below).

### Auto world-threshold teleport

```yaml
auto_world_teleport:
  delay_seconds: 5
  threshold_y: -62        # negative: trigger below; positive: trigger above
  worlds:
    tp1:
      world_from: world
      world_to: cave
      threshold_y: -64
```

Requires the `novateleport.pass` permission on the player.

### Economy (Vault)

```yaml
economy:
  enabled: false
  bypass_permission: novateleport.economy.bypass
  costs:
    auto_world_teleport: 0
    home: 0
    warp: 0
    spawn: 0
    back: 0
    rtp: 0
    tpa: 0
    tpahere: 0
    deathback: 0
    city: 0
    party: 0
    scroll: 0
    portal: 0
    stele: 0
    guild: 0
    towny: 0
    tollwarp: 0
    rewind: 0
```

The key is the teleport *type*, which matches `teleport_log.log_types`.
`deathback`, `stele`, `guild`, `towny` and `tollwarp` have their own cost settings in
`death.yml`, `steles.yml`, `guild_config.yml`, `features_config.yml` and
`toll_warps_config.yml` respectively; the values above apply only when no dedicated cost
is configured.

### Visuals / performance

```yaml
display_settings:
  default_mode: TITLE        # CHAT | ACTION_BAR | TITLE
features:
  animation_enabled: true
  post_effect_enabled: true
  carry_boat_with_passengers: false
animations:
  default_style: magic       # magic | tech | natural
post_teleport_effect:
  enabled: true
  effect: BLINDNESS
  duration: 3
  amplifier: 0
performance:
  preload_target_chunk: true # Paper only: pre-load the target chunk asynchronously
```

### Spatial anchors

```yaml
spatial_anchors:
  enabled: false
  required_types: [guild, towny]   # teleport types that need an anchor
  center_block: OBSIDIAN           # the block the player stands on
  edge_block: END_ROD              # N/S/E/W
  corner_block: GOLD_BLOCK         # diagonals
```

### City / hub

```yaml
city:
  mode: local                # local | proxy
  local:
    world: world
    x: 0.5
    y: 80
    z: 0.5
  proxy:
    server: hub
```

## 3. Other files

- `features_config.yml`
  - `towny.{enabled, home_delay, other_delay, home_cost, other_cost}`
  - `teleport_log.{enabled, retention_days, log_types, rewind_permission}`
- `guild_config.yml`
  - `enabled`, `plugins` (allowed adapters: `Guilds`, `SimpleClans`, `FactionsUUID`)
  - `warps.{enabled, max_per_guild, admin_only, delay, cost}`
  - `headquarters.{enabled, protection_radius, delay, cost}`
- `toll_warps_config.yml`
  - `enabled`, `mode` (`toll` | `personal_free`), `max_per_player`,
    `min_price`, `max_price`, `owner_fee_percentage`, `allow_free`, `teleport_delay_seconds`
  - `owner_fee_percentage` is the share paid to the warp owner; the remainder is the server's cut.
- `rtp.yml`
  - `pregen_pool_size`
  - `worlds.<name>.{enabled, center_x, center_z, min_radius, max_radius, biome_blacklist}`
  - `unsafe_landing_blocks`
- `portals.yml`
  - `frame_block`: `Material` or `itemsadder:<id>`
  - `activation_item`: `Material`, `itemsadder:<id>` or `mmoitems:TYPE:ID`
  - `portal_block`: material used to fill the frame interior
  - `destination`: `{ world, x, y, z }` (supports `SAME_AS_ENTRY`)
  - Activated portals are persisted to `data/portals_state.yml`, so they survive restarts.
- `scrolls.yml`: `bound.{material, name}` for the item produced by `/scroll bind`.
- `death.yml`: `cooldown_seconds`, `teleport_delay_seconds`, `cost.{vault, xp_levels}`,
  `auto_prompt.{bedrock, java}`, `auto_random.{enabled, world}`.
- `steles.yml`: `activation.{item_required, item_amount, xp_level_cost}`,
  `teleport_cost.{xp_level_cost, vault_cost}`, `structures.<key>.{core_block, frame}`.

## 4. Scripting (optional)

`scripts/teleport.js` is loaded only if a JavaScript engine is available on the server
(GraalJS shipped by the platform, or a Nashorn/GraalJS provider plugin). Java 15+ removed the
bundled Nashorn engine, so on a stock server this log line appears and scripting stays disabled:

```
[Scripting] No JavaScript engine available on this server — scripts/teleport.js is disabled.
```

## 5. Reloading

`/stp reload` reloads `config.yml`, language files, economy, menus, region adapters and the
feature managers (stele, portal, RTP pool, scrolls, guild, towny, toll, teleport log).
`/novateleport debug on|off` only affects the current run; set `general.debug` in
`config.yml` for a persistent change (the plugin deliberately does not rewrite `config.yml`,
which would strip its comments).

If you want the Chinese version, see `CONFIG_CN.md`.
