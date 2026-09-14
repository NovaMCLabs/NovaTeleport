# FAQ (NovaTeleport)

## Q1: Which server versions are supported?

The plugin is compiled against the **oldest** supported API — **spigot-api 1.20.1** — and declares
`api-version: '1.20'`. It only uses Bukkit API that already existed in 1.20.1 and is still present
today, so the **same jar** loads on **1.20.x, 1.21.x and the 26.x line** (26.1, 26.1.2, 26.2) across
Spigot, Paper and Folia.

Java: the plugin's classes are **Java 17** bytecode. That is deliberate — 1.20.0–1.20.4 servers only
require Java 17, and many still run on it; Java 21 bytecode would fail there with
`UnsupportedClassVersionError`. 1.20.5+/1.21.x (Java 21) and 26.x (Java 25) can all load Java 17
bytecode upwards. A few optional integrations (WorldGuard 7.0.17, BetterTeams 5.1.4)
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

Charging happens at the last possible moment — after the region/anchor checks, immediately before the
teleport is attempted — and a cancelled countdown costs nothing. But "immediately before" is still
before: another plugin can cancel the teleport event (`PlayerTeleportEvent`), and Folia reports the
same thing through the async result. When that happens the amount is **deposited back** and you see
`teleport.cancelled.title`.

The refund covers **money only**. The payment hook is a single "charge once" call, so the plugin
cannot know how many XP levels or items were taken and does not try to restore them. A teleport
whose price includes XP levels or a consumed scroll therefore loses those on a blocked teleport.

The cross-server branch is the other exception: it performs no local teleport, so the fee is taken up
front and is **not** refunded if the proxy ignores the `Connect` request.

## Q4: Vault economy costs do not work.

- Set `economy.enabled: true` in `config.yml`.
- Install **Vault** plus an economy provider plugin.
- Check the player does not have `novateleport.economy.bypass`.
- Some features have their own cost settings (`guild_config.yml`, `features_config.yml`,
  `death.yml`, `steles.yml`), which take precedence for those teleports. Toll warp prices are
  per-warp data in `toll_warps.yml`, bounded by `toll_warps_config.yml`'s `min_price`/`max_price`.

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
- `network.server_name` in each backend must match the server names known to the proxy **and be
  unique per server** — duplicates silently discard each other's messages (see Q16).
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

## Q12: Can I stop players from teleporting away from a fight?

Not by default — `combat_tag` and `damage_interrupt` are off out of the box, so teleporting is
never restricted by combat. Turn them on in `config.yml`:

- `combat_tag.enabled: true` — cannot *start* a teleport while tagged. Tagging is bidirectional,
  so dealing damage tags you too (this blocks "hit and run" as well as escaping).
- `damage_interrupt.enabled: true` — taking damage cancels a countdown already in progress.
  This is independent of `combat_tag`; either can be on without the other.

`novateleport.combat.bypass` / `novateleport.damage.bypass` exempt staff. If you enabled both and
players are still escaping, see Q15.

## Q13: Do cancelled teleports use up the cooldown?

No. Cooldowns (`teleport_cooldowns`, off by default) are checked when the teleport starts but
recorded only after it actually executes. A countdown cancelled by movement or damage — or a
teleport rejected by a region plugin, a missing anchor, or an unaffordable cost — never
consumes one. Costs follow the same principle: they are only charged inside the execution step,
after those checks, so nothing is taken for a teleport that never gets that far.

## Q14: I configured costs but nobody is being charged.

Check the startup log. If money costs are configured while the economy is unusable you will see:

```
[Economy] N money cost(s) are configured but will have NO effect (economy.enabled is false).
```

That means `economy.enabled` is `false`, or Vault is present without an economy plugin. The
plugin keeps the teleport free in that situation rather than blocking it, so the server stays
usable — but the costs are inert. Install Vault plus an economy plugin and set
`economy.enabled: true`.

Note that `deathback`, `stele`, `guild` and `towny` read their prices from their *own* config
files (`death.yml`, `steles.yml`, `guild_config.yml`, `features_config.yml`). The matching
`economy.costs.*` keys only apply if you enable `economy.global_fallback.enabled` **and** delete
the feature's own key.

## Q15: I turned on `combat_tag` but players still escape a fight.

Two things are usually missing:

- **`combat_tag` only blocks *starting* a teleport.** A countdown that is already running keeps
  running unless you also enable `damage_interrupt.enabled: true`, which cancels the countdown when
  the player is hit. The two switches are independent on purpose; enable both if you want a full
  lockdown, and make sure `damage_interrupt.exempt_types` does not list the type you care about
  (it ships with `portal`).
- **The tag expires.** `duration_seconds` (default 15) is the window, and with
  `refresh_on_damage: true` (default) every further hit restarts it. With `refresh_on_damage: false`
  the window is fixed from the first hit, so a player who is hit once and then kites for 15 seconds
  is free again even while still under attack.

Remember the tag is bidirectional — dealing damage tags you too, so a player who hits a mob and then
tries to `/home` is also blocked. `exempt_types` is the escape hatch for teleport types that should
always work, and `novateleport.combat.bypass` (configurable) exempts staff. If a player is tagged and
still gets away, check that the teleport type is not in `exempt_types` and that the player does not
have the bypass node.

## Q16: My cross-server teleports silently do nothing.

"Silently" is the signature of a duplicate `network.server_name`. An incoming Redis message whose
`server` field equals the local `server_name` is discarded as an echo, so two backends configured
with the same name drop each other's requests: no error, no log line, the request simply never
arrives. The server also refuses to trigger its own messages.

- Give every backend a unique `network.server_name` that matches the name the proxy knows it by.
- The shipped default is `local`; the plugin logs `network.server_name is still the default "local"`
  at startup when Redis is enabled. That warning means the value is a placeholder, not a valid setup.
- Changing it takes effect on `/stp reload` — the Redis pool and subscriber are rebuilt, no restart
  needed.
- Cross-server TPA also needs a reachable Redis. `network.redis.enabled: true` is not enough: the
  connection is verified at startup and re-checked every 30s, and while it is down the transport
  reports itself inactive and `/tpa` tells the sender the player is offline rather than pretending
  the request went out. Watch for `[CrossServer] Redis unreachable` / `Redis reachable again`.

## Q17: The Bedrock forms do not appear.

Forms need three things at once:

- **Floodgate installed.** The API is reached through Floodgate; without it every form call returns
  `false` immediately. The player is still detected as Bedrock (the fallback checks the Floodgate UUID
  shape), so they get the chat fallback rather than forms.
- **`bedrock.forms.enabled: true`** (the default). Setting it to `false` is a deliberate way to force
  everyone onto chat menus.
- **A stable Cumulus API.** A failure is logged once per stage as
  `[Bedrock] Form API call failed at <stage> (...)`, and the interaction falls back to a chat message.
  If you see that line, the form did not reach the client; the text alternative did.

Note that only the terse interactions use forms: the `/tpa` accept/deny prompt, list selections
(`/homes`, `/warps`, `/tpanimation`), confirmation modals and the `/rtp` radius slider. Everything
else is chat/text for both editions. Bedrock support has not been verified end-to-end on a real
Bedrock client.
