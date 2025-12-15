# Commands Reference (NovaTeleport)

This is a quick reference. The authoritative list is `Bukkit/src/main/resources/plugin.yml`.

## Administration

- `/stp reload` — reload main config and feature configs
  - Permission: `novateleport.command.reload`

- `/novateleport debug on|off` (aliases: `/ntp`, `/novatp`) — toggle `general.debug`
  - Permission: `novateleport.admin`

## Core teleport

| Command | Description | Permission |
|---|---|---|
| `/tpa <player>` | Request to teleport to a player | `novateleport.command.tpa` |
| `/tpahere <player>` | Request a player to teleport to you | `novateleport.command.tpahere` |
| `/tpaccept` | Accept request | `novateleport.command.tpaccept` |
| `/tpdeny` | Deny request | `novateleport.command.tpdeny` |
| `/tpcancel` | Cancel outgoing request | `novateleport.command.tpcancel` |
| `/sethome [name]` | Set a home | `novateleport.command.home` |
| `/home [name]` | Teleport to a home (no args opens menu) | `novateleport.command.home` |
| `/delhome [name]` | Delete a home | `novateleport.command.home` |
| `/homes` | List/select homes | `novateleport.command.home` |
| `/setwarp <name>` | Create warp | `novateleport.command.setwarp` |
| `/warp [name]` | Teleport to warp (no args opens menu) | `novateleport.command.warp` |
| `/delwarp <name>` | Delete warp | `novateleport.command.setwarp` |
| `/warps` | List/select warps | `novateleport.command.warp` |
| `/spawn` | Teleport to spawn | `novateleport.command.spawn` |
| `/back` | Teleport to last location | `novateleport.command.back` |
| `/deathback` | Teleport to last death location | `novateleport.command.back` |
| `/rtp [now|start|radius]` | Random teleport | `novateleport.command.rtp` |
| `/rtpgui` | Open RTP GUI | `novateleport.command.rtp` |
| `/tpmenu` | Open teleport menu | `novateleport.command.tpmenu` |
| `/tpanimation select <magic|tech|natural>` | Select animation style | `novateleport.animation.select` |
| `/scroll bind <home|warp> <name>` | Bind a teleport scroll | `novateleport.scroll.bind` |
| `/city` (alias: `/hub`) | City/hub teleport (local or proxy) | `novateleport.command.spawn` |

## Party / Guild / Towny / Toll

- `/party ...` — built-in party system
  - Permission: `novateleport.command.party`
- `/gtp ...` — guild teleports
  - Permission: `novateleport.guild.home`
- `/towntp [town]` — Towny teleports
  - Permission: `novateleport.towny.home`
- `/tollwarp ...` — paid/public warps
  - Permission: `novateleport.toll.create`

## Offline admin

- `/forcetp <player> <world> <x> <y> <z>` — queue teleport for offline player
  - Permission: `novateleport.admin`

