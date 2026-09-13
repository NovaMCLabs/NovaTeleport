# Permissions (NovaTeleport)

The authoritative list is `Bukkit/src/main/resources/plugin.yml`. Commands also enforce
permissions in code where the check depends on a subcommand.

## Common nodes

- `novateleport.admin` — admin features (`/stele create|remove|activatefor`, `/forcetp`, `/ntp debug`)
- `novateleport.pass` — allows triggering **auto world threshold teleport**
- `novateleport.command.reload` — `/stp reload`

## Core commands

- `novateleport.command.tpa`
- `novateleport.command.tpahere`
- `novateleport.command.tpaccept`
- `novateleport.command.tpdeny`
- `novateleport.command.tpcancel`
- `novateleport.command.home` — `/home`, `/sethome`, `/delhome`, `/homes`
- `novateleport.command.warp` — `/warp`, `/warps`
- `novateleport.command.setwarp` — `/setwarp`, `/delwarp`
- `novateleport.command.spawn` — `/spawn`, `/city`
- `novateleport.command.back` — `/back`, `/deathback`
- `novateleport.command.rtp` — `/rtp`, `/rtpgui`
- `novateleport.command.tpmenu`
- `novateleport.command.party`

## Home limit

- `novateleport.home.limit.<n>` — the highest granted value wins (default from
  `config.yml` → `homes.default_limit`). Explicitly negated nodes are ignored.

## Economy

- `novateleport.economy.bypass` — bypass all teleport costs

## Animation

- `novateleport.animation.select`
- `novateleport.animation.tech`
- `novateleport.animation.natural`

## Items

- `novateleport.scroll.bind`

## Integrations

- Guild: `novateleport.guild.use` (`list`/`info`), `novateleport.guild.home` (`/gtp home`),
  `novateleport.guild.warp` (`/gtp warp`), `novateleport.guild.admin` (`setwarp`/`delwarp`/`sethome`)
- Towny: `novateleport.towny.home`, `novateleport.towny.other`
- Toll warps: `novateleport.toll.use`, `novateleport.toll.create`, `novateleport.toll.delete`,
  `novateleport.toll.delete.others`, `novateleport.toll.bypass`
- Steles: `novateleport.stele.use`
- Teleport log rewind: `novateleport.admin.rewind`
