# Configuration Guide (NovaTeleport)

This project uses **multiple YAML files** under `plugins/NovaTeleport/`.

> Note: `config.yml` is the only file loaded via Bukkit's `saveDefaultConfig()`. The other files are created/loaded by their respective managers (they call `saveResource(...)` on first run).

## 1. File list

- `config.yml` — core settings (language/debug), command delays, economy costs, auto world threshold teleports, RTP base settings, animation/effects.
- `features_config.yml` — feature toggles and extra settings (Towny, etc.).
- `guild_config.yml` — guild integration settings.
- `toll_warps_config.yml` — public/toll warp settings.
- `party.yml` — built-in party settings.
- `death.yml` — death/back tracking settings.
- `steles.yml` — stele/waypoint network settings.
- `portals.yml` — custom portal definitions.
- `rtp.yml` — RTP pre-generation pool config (world-specific radii/blacklists/unsafe blocks).
- `scrolls.yml` — teleport scroll items and bindings.
- `java_menus.yml` — Java edition GUI menu layout.
- `langs/en_US.yml`, `langs/zh_CN.yml` — language files.
- `lang_extensions_*.yml` — extra language keys for optional features.

## 2. `config.yml` highlights

### General

```yaml
general:
  language: zh_CN
  debug: false
```

### Network / cross-server

```yaml
network:
  server_name: local
  redis:
    enabled: false
    host: 127.0.0.1
    port: 6379
    password: ""
    channel: novateleport
```

### Auto world threshold teleport

```yaml
auto_world_teleport:
  delay_seconds: 5
  threshold_y: -62
  worlds:
    tp1:
      world_from: world
      world_to: cave
      threshold_y: -64
```

### Command delay

```yaml
commands:
  teleport_delay_seconds: 3
```

### Visual features

```yaml
features:
  animation_enabled: true
  post_effect_enabled: true
  carry_boat_with_passengers: false
```

### Economy (Vault)

```yaml
economy:
  enabled: false
  bypass_permission: novateleport.economy.bypass
  costs:
    home: 0
    warp: 0
    rtp: 0
    tpa: 0
```

## 3. Other config files

- `portals.yml`: define portals under `portals:`.
  - `frame_block`: `Material` or `itemsadder:<id>`
  - `activation_item`: `Material` or `itemsadder:/mmoitems:TYPE:ID`
  - `portal_block`: material used to fill the portal interior
  - `destination`: `{ world, x, y, z }` (supports `SAME_AS_ENTRY`)

- `rtp.yml`: RTP pre-generation pool.
  - `pregen_pool_size`
  - `worlds.<name>.{enabled, center_x, center_z, min_radius, max_radius, biome_blacklist}`
  - `unsafe_landing_blocks`

- `scrolls.yml`: item material/name/lore for bound/unbound scrolls.

If you want the Chinese version, see `CONFIG_CN.md`.
