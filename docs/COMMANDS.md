# Commands Reference (NovaTeleport)

The authoritative list is `Bukkit/src/main/resources/plugin.yml`. Sub-command permissions are
enforced in code for `/gtp`, `/towntp`, `/tollwarp` and `/stele`.

## Administration

- `/stp reload` — reload `config.yml`, language, economy, menus, region adapters and feature managers
  - Permission: `novateleport.command.reload`
- `/novateleport debug on|off` (aliases: `/ntp`, `/novatp`) — toggle debug logging for this run
  - Permission: `novateleport.admin`
  - Add `debug: true` under `general` in `config.yml` to make it persistent; the plugin does not
    rewrite `config.yml` (that would strip its comments).

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
| `/rtp [now\|start\|radius]` | Random teleport (radius is clamped to the configured max) | `novateleport.command.rtp` |
| `/rtpgui` | Open RTP GUI | `novateleport.command.rtp` |
| `/tpmenu` | Open teleport menu | `novateleport.command.tpmenu` |
| `/tpanimation select <magic\|tech\|natural>` | Select animation style | `novateleport.animation.select` |
| `/scroll bind <home\|warp> <name>` | Bind a teleport scroll | `novateleport.scroll.bind` |
| `/city` (alias: `/hub`) | City/hub teleport (local or proxy) | `novateleport.command.spawn` |
| `/party ...` | Built-in party system | `novateleport.command.party` |

Home and warp names may contain letters, digits, `_` and `-` only (max 32 chars); other characters
would break the YAML paths used for storage.

## Towny / Guild / Toll

| Command | Description | Permission |
|---|---|---|
| `/towntp` | Teleport to your own town | `novateleport.towny.home` |
| `/towntp <town>` | Teleport to another town | `novateleport.towny.other` |
| `/gtp home` (`hq`) | Teleport to guild HQ | `novateleport.guild.home` |
| `/gtp sethome` (`sethq`) | Set guild HQ | `novateleport.guild.admin` |
| `/gtp warp <name>` | Teleport to a guild warp | `novateleport.guild.warp` |
| `/gtp setwarp <name>` / `/gtp delwarp <name>` | Manage guild warps | `novateleport.guild.admin` |
| `/gtp list` (`warps`) / `/gtp info` | List warps / show guild info | `novateleport.guild.use` |
| `/tollwarp list` / `mywarps` | List public / own toll warps | `novateleport.toll.use` |
| `/tollwarp tp <name>` or `/tollwarp <name>` | Use a toll warp | `novateleport.toll.use` |
| `/tollwarp create <name> [price]` / `setprice <name> <price>` | Create / re-price | `novateleport.toll.create` |
| `/tollwarp delete <name>` | Delete (others' warps also need `novateleport.toll.delete.others`) | `novateleport.toll.delete` |
| `/stele list` / `locate` / `travel [name]` | Use the stele network | `novateleport.stele.use` |
| `/stele create <name>` / `remove <name>` / `activatefor <p> <key>` | Manage steles | `novateleport.admin` |

## Logs and offline admin

- `/tplog <player>` — open the teleport log GUI and optionally rewind the player
  - Permission: `novateleport.admin.rewind`
- `/forcetp <player> <world> <x> <y> <z>` — teleport now, or queue for the next login
  - Permission: `novateleport.admin`

## Cross-server notes

- `/tpa` to a player on another server is forwarded over Redis
  (`network.redis.enabled: true`). If Redis is not active, the command reports the player as offline
  instead of pretending the request was delivered.
- Accepting a cross-server request moves the player with the proxy's `Connect` message, so the proxy
  must forward the `BungeeCord` channel (Velocity: `bungee-plugin-message-channel` in `velocity.toml`, `true` by default).
  Because the proxy can only drop the player at the destination server's join point, the server
  records the pending arrival and teleports the player to the partner as soon as they connect.
  If the partner went offline in the meantime the arriving player stays at the join point.
- Cross-server homes/warps are stored with the server name they were created on; using them switches
  servers instead of teleporting locally.
- Cost caveat: a cross-server switch is the one case where the fee is charged *before* the transfer,
  because no local teleport happens. If the proxy ignores the `Connect` message (server not
  registered, `bungeecord` disabled, no proxy at all) the fee is not refunded.
