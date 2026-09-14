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

### General / Bedrock / network

```yaml
general:
  language: zh_CN      # zh_CN | en_US
  debug: false

bedrock:
  forms:
    enabled: true      # false = Bedrock players always get the chat fallback

network:
  server_name: local   # MUST be unique per server — see below
  redis:
    enabled: false
    host: 127.0.0.1
    port: 6379
    password: ""
    channel: novateleport
```

`network.server_name` decides whether a stored home/warp is treated as *remote*
(cross-server) or local, and is also the sender id stamped on every published message.

**It must be unique across your network.** An incoming message whose `server` field
equals the local `server_name` is treated as its own echo and dropped; two servers sharing
a name therefore discard each other's traffic silently — nothing is logged and nothing
fails, the request just never arrives. The plugin warns once at startup if the value is
still the shipped default `local`, which is a placeholder rather than a valid setting for
a multi-server network.

`bedrock.forms.enabled` is the master switch for the Cumulus forms sent to Bedrock players.
Setting it to `false` makes every form call return immediately, so Bedrock players fall back
to the chat/text menus (see the Bedrock section below).

Note that `network.*` (including `server_name`, host, port and channel) is read at startup
and re-read on `/stp reload`, which tears down and rebuilds the Redis pool and subscriber.

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

Cancelling a countdown never charges the player: costs are only taken inside `execute()`, after
the anchor/region checks and immediately before the teleport is attempted (see Economy below for
what happens when a third-party plugin blocks the teleport after that point).

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

`deathback`, `stele`, `guild` and `towny` have their own cost settings in `death.yml`,
`steles.yml`, `guild_config.yml` and `features_config.yml` respectively.

`tollwarp` is different again: the price is **per-warp data** stored in `toll_warps.yml`, set
with `/tollwarp create <name> <price>` or `setprice` and bounded by `toll_warps_config.yml`'s
`min_price` / `max_price`. `scroll` consumes the bound scroll *item*, not money.

**Global fallback (opt-in, off by default):**

```yaml
economy:
  global_fallback:
    enabled: false
```

When enabled, a feature that does **not** define its own cost key falls back to
`economy.costs.<type>`. It is off by default because every shipped feature config already
writes its own cost key (`0` counts as written), so the fallback resolves to nothing anyway —
turning it on only matters if you *delete* a feature key, and then the global value silently
takes over. `tollwarp` is exempt (its price is per-warp data).

**Costs are charged money, XP levels or items.** All of them are validated together first and
only applied if every component passes, so a partial deduction (e.g. items consumed because
the XP check came second) cannot happen. If money costs are configured but the economy is
unusable, the plugin logs a warning at startup and on `/stp reload` — the teleport still
proceeds for free, matching the previous behaviour.

**A teleport blocked by another plugin is refunded — money only.** Charging happens after the
region/anchor checks but before the actual teleport, because a third-party plugin can still
cancel the teleport event at that point (`PlayerTeleportEvent`, Folia's async result). When
that happens the amount that was just withdrawn is deposited back and `teleport.cancelled.title`
is shown. The refund is limited to **money**: the payment hook only exposes "charge once", so
the plugin cannot know how many XP levels or items were taken and does not attempt to restore
them. Teleports whose cost includes XP levels or a bound scroll therefore lose those on a
blocked teleport. A failed refund (economy provider error) is written to the log as a warning.

The cross-server branch is the other exception: it does not run a local teleport, so the fee is
taken up front and is **not** refunded if the proxy ignores the switch request.

### Cross-server (Redis)

Cross-server *switching* (homes/warps stored on another server, `/city` in `proxy` mode) uses the
server-side `BungeeCord` plugin message channel and needs nothing from Redis. Cross-server **TPA**
additionally uses Redis pub/sub to forward requests and replies between backends.

Redis reachability is reported, not assumed. The pool is validated with a `PING` when it is
created, re-checked every 30 seconds, and re-checked on every publish; the state only flips on
the transition, so you get one line when Redis goes away and one when it comes back. While Redis
is unreachable, `network.redis.enabled` being `true` is not enough — the transport reports itself
inactive and `/tpa <player>` reports the player as offline rather than pretending the request was
sent. Messages are matched by the channel name, and each carries the sender's `server_name`; a
message whose sender equals the local `server_name` is dropped as an echo.

Activating a cross-server home/warp is the one path where the cost is charged before the transfer
(no local teleport happens), and it is not refunded if the proxy does not act on the request.

### Gameplay restrictions

All three sections are **off by default** (`teleport_cooldowns.enabled: false`,
`combat_tag.enabled: false`, `damage_interrupt.enabled: false`); an upgrading server keeps the
exact same teleport behaviour until you turn them on.

Each section's `bypass_permission` renames its permission node; the nodes shown below are the
shipped defaults.

```yaml
# Cannot start a teleport while in combat.
# Bidirectional: dealing OR taking damage tags you (players and mobs alike),
# so it blocks both "teleport away while being chased" and "hit and run".
combat_tag:
  enabled: false
  duration_seconds: 15
  refresh_on_damage: true      # further hits restart the timer; false = fixed window
  exempt_types: []             # teleport types exempt, same syntax as commands.move_cancel_exempt_types
  bypass_permission: novateleport.combat.bypass

# Taking damage cancels a teleport that is counting down.
# Independent of combat_tag: works even when combat_tag is disabled.
damage_interrupt:
  enabled: false
  exempt_types: [portal]
  bypass_permission: novateleport.damage.bypass

# Per-teleport-type cooldown in seconds (0 = no cooldown for that type)
teleport_cooldowns:
  enabled: false
  bypass_permission: novateleport.cooldown.bypass
  types:
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
    stele: 0
    guild: 0
    towny: 0
    tollwarp: 0
    auto_world_teleport: 0
```

Cooldowns are checked when the teleport starts but recorded only **after it actually
executes**, so a countdown cancelled by movement or damage — or a teleport that fails its
region/anchor/payment checks — never consumes a cooldown.

The combat tag is checked twice: when the teleport is requested (so the player is not shown a
countdown that is doomed) and again when `execute()` runs, because a player can be tagged *during*
the countdown by taking a hit that `damage_interrupt` did not cancel. `damage_interrupt` cancels
only a teleport that is already counting down — an instant teleport has nothing to interrupt.

Cross-server teleports do not go through the normal teleport pipeline, so the same combat-tag and
cooldown checks are applied to them explicitly through `TeleportGates` before the proxy `Connect`
message is sent — they cannot be used to escape a tag. This covers the cross-server branches of
`/home` and `/warp`, `/city` in `proxy` mode, and cross-server `/tpa` / `/tpahere` (the `/tpa`
check happens on the requester's server when it receives the accept, the `/tpahere` check on the
target's server, which is the player actually travelling).

### Visuals / performance

```yaml
display_settings:
  default_mode: TITLE        # CHAT | ACTION_BAR | TITLE
features:
  animation_enabled: true            # master switch; false disables everything below
  animation_particles: true          # particles only
  animation_sounds: true             # sounds only
  animation_effect_interval_ticks: 20 # 1..200, particle/sound refresh interval
  bedrock_particle_multiplier: 0.5   # particle count scaling for Bedrock players
  animation_styles:
    magic: true                      # per-style switch, same keys as animations.default_style
    tech: true
    natural: true
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

`features.animation_enabled` is the master switch: with it `false` nothing animates at all
(including from scripts), and the countdown still works. `animation_particles` and
`animation_sounds` independently mute one channel while keeping the other. The per-style
switches under `animation_styles` are looked up using the player's style, so a player on `tech`
is only affected by `animation_styles.tech`.

`features.animation_effect_interval_ticks` (default `20`, clamped to `1..200`) sets how often the
particles/sounds are emitted during the countdown. It is **only** a visual cadence: the countdown
still ticks every second and the teleport still fires at exactly `commands.teleport_delay_seconds`.
Raising it reduces the effect load (and the number of packets Bedrock clients receive); lowering it
below 20 makes the effects smoother at the cost of more particle packets.

`features.bedrock_particle_multiplier` (default `0.5`) scales the particle count for Bedrock
players only, and the result is clamped to `1..100`. Geyser does not forward one Java particle
packet as one packet: it emits one upstream packet per particle and silently truncates above 100.
The multiplier plus the explicit clamp keeps the truncation intentional rather than accidental.
Set it to `0` to send no particles to Bedrock players at all.

### Bedrock players

Floodgate is required for the Bedrock-form path. Detection (`BedrockUtil`) asks Floodgate through
reflection when the plugin is present; if that is unavailable it falls back to the Floodgate UUID
shape — Floodgate UUIDs have a zero high 64-bit half, which a Java account cannot produce. So a
Bedrock player is still recognised as such without Floodgate, but with less certainty.

Forms require **Floodgate** (the API is reached through it) and are gated by
`bedrock.forms.enabled`. Forms are used for the terse interactions: the `/tpa` accept/deny prompt,
list selections (`/homes`, `/warps`, `/tpanimation`), confirmation modals, and the `/rtp` radius
slider. Every call returns `false` instead of throwing when the form cannot be sent (switch off,
Floodgate absent, a Cumulus/Floodgate API change), and the caller falls back to a chat message —
that is why a Bedrock player may see plain text instead of a form.

Known particle degradations on Bedrock: the `tech` style's aqua `DUST` particles are replaced with
`CRIT` on Bedrock clients. Geyser's Java→Bedrock `DUST` translation reads the colour integers as a
block runtime id, so custom colours do not survive the trip and Bedrock players would see the wrong
particle. Other styles are sent unchanged and rely on Geyser's own mappings.

Bedrock support has **not** been verified end-to-end against a real Bedrock client; the individual
code paths (detection, form dispatch, fallback) have been reviewed but not exercised on a device.

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
  - `permissions.{require_permission, base_permission}`
- `party.yml`
  - `max_members`, `invite_expire_seconds`, `teleport_delay`,
    `display.{leader_prefix, member_prefix}` — the built-in party system, used when no
    Parties/BetterTeams plugin is detected.
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

`/stp reload` reloads `config.yml`, language files, economy, menus, region adapters, the
feature managers (stele, portal, RTP pool, scrolls, guild, towny, toll, teleport log), the
gameplay-restriction sections (`combat_tag`, `damage_interrupt`, `teleport_cooldowns`) and the
`network.*` settings. The Redis connection is torn down and rebuilt, so a changed
`network.server_name`, host, port, password or channel takes effect without a restart; the
injected cross-server message handler survives the reload.
`/novateleport debug on|off` only affects the current run; set `general.debug` in
`config.yml` for a persistent change (the plugin deliberately does not rewrite `config.yml`,
which would strip its comments).

If you want the Chinese version, see `CONFIG_CN.md`.
