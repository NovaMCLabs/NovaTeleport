# FAQ (NovaTeleport)

## Q1: Which server versions are supported?

The plugin is compiled against the **oldest** supported API — **spigot-api 1.20.1** — and declares
`api-version: '1.20'`. It only uses Bukkit API that already existed in 1.20.1 and is still present
today, so the **same jar** loads on **1.20.x, 1.21.x and the 26.x line** (26.1, 26.1.2, 26.2) across
Spigot, Paper and Folia.

Java: the plugin's classes are **Java 17** bytecode. That is deliberate — 1.20.0–1.20.4 servers only
require Java 17, and many still run on it; Java 21 bytecode would fail there with
`UnsupportedClassVersionError`. 1.20.5+/1.21.x (Java 21) and 26.x (Java 25) can all load Java 17
bytecode upwards. A few optional integrations (WorldGuard 7.0.17, BetterTeams 5.1.4, WorldEdit 7.4)
are themselves Java 21, so those adapters simply never activate on a Java 17 server.

Not supported: 1.19 and older (the plugin uses APIs added in 1.20.1).

Particle constants were renamed wholesale in 1.20.5 (`VILLAGER_HAPPY` → `HAPPY_VILLAGER`,
`REDSTONE` → `DUST`, …). The plugin resolves them at runtime through `ParticleCompat`, which tries
the modern name first and falls back to the legacy one, so animations work on both sides of that
rename.

## Q2: Why is the Folia support real now?

All scheduling goes through `plugin.getScheduler()` (a FoliaLib wrapper), and teleports use
`teleportAsync` on Folia. Nothing in the plugin calls `Bukkit.getScheduler()` any more, which is
what used to throw `UnsupportedOperationException` on Folia.

## Q3: I was charged but the teleport did not happen.

That should no longer be possible in 2.0: costs are charged at the moment the teleport actually
executes, after region/anchor checks, and a cancelled countdown costs nothing.
The only exception is the cross-server branch (homes/warps on another server), where the proxy
switch is charged up front because no local teleport happens.

## Q4: Vault economy costs do not work.

- Set `economy.enabled: true` in `config.yml`.
- Install **Vault** plus an economy provider plugin.
- Check the player does not have `novateleport.economy.bypass`.
- Some features have their own cost settings (`guild_config.yml`, `features_config.yml`,
  `death.yml`, `steles.yml`, `toll_warps_config.yml`), which take precedence for those teleports.

## Q5: Region plugins block teleports.

NovaTeleport asks every detected region adapter; if any of them denies entry the teleport is
cancelled with a "no permission" message. Review the region rules in WorldGuard / PlotSquared /
Towny / etc. WorldGuard checks only the `ENTRY` flag (not `BUILD`); Residence checks its `tp` flag
(there is no `enter` flag — an unregistered flag would silently pass).

At startup the console lists what was detected, e.g. `[RegionAdapter] Registered: WorldGuard`.
If a region plugin is installed but missing from that list, the adapter could not be initialised —
check for a `[RegionAdapter]` warning right after it.

## Q6: Cross-server teleports do not work.

- Cross-server *switching* uses the server-side `BungeeCord` plugin message channel. No plugin is
  needed on the proxy, but the proxy must forward the channel:
  - BungeeCord: works out of the box
  - Velocity: nothing to do — `bungee-plugin-message-channel` in `velocity.toml` defaults to `true`
- `network.server_name` in each backend must match the server names known to the proxy.
- Cross-server **TPA** additionally needs `network.redis.enabled: true` and a reachable Redis.
  If Redis is not active, `/tpa <player>` reports the player as offline instead of pretending the
  request was sent.
- A cross-server `/tpa` works as follows: the request is forwarded over Redis; if the target accepts,
  the asking player is moved to the target's server with `Connect` and is then teleported next to the
  target. The proxy itself can only deliver a player to a server's join point, which is why the last
  step happens server-side after the join.

## Q7: `/rtp` is slow or fails.

- RTP scans for safe ground; it fails when the configured world/radius/biome blacklist is too strict.
- Check `config.yml` (`rtp.*`) and `rtp.yml` (`worlds.*`, `unsafe_landing_blocks`).
- `/rtp <radius>` is clamped to the configured maximum radius for the current world.

## Q8: Portals stopped working after a restart.

Portals in 2.0 persist their activated blocks to `data/portals_state.yml`, so they keep working.
Delete that file (or the portal's `frame` blocks) if you need to reset them.

## Q9: My chat says scripting is disabled.

`scripts/teleport.js` only loads when a JavaScript engine is available on the server. Java 15+
removed the bundled Nashorn, so on a stock server the startup log prints
`[Scripting] No JavaScript engine available …` and scripting is skipped. Install a Nashorn or
GraalJS engine provider if you want it.

## Q10: Which permissions do guild/toll/stele sub-commands need?

See [PERMISSIONS.md](PERMISSIONS.md). These commands check permissions per sub-command in code
rather than on the command itself.

## Q11: How do I use NovaTeleport placeholders?

Install [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/); the expansion
registers itself under the identifier `novateleport`. Available placeholders:

`%novateleport_homes%`, `%novateleport_homes_max%`, `%novateleport_warps%`,
`%novateleport_teleporting%`, `%novateleport_can_back%`, `%novateleport_death_back%`.

Without PlaceholderAPI the plugin loads normally and skips them entirely.
