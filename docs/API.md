# API Notes (NovaTeleport)

NovaTeleport is primarily a server plugin. A stable public API is **not formally versioned** in this repository, but some internal entry points are commonly used by addons.

## Bukkit main plugin

- Main class: `com.novamclabs.StarTeleport`
- Get instance:

```java
StarTeleport plugin = (StarTeleport) org.bukkit.Bukkit.getPluginManager().getPlugin("NovaTeleport");
```

## Useful entry points

- `plugin.getLang()` — language manager
- `plugin.getDataStore()` — YAML datastore for homes/warps/back
- `com.novamclabs.util.TeleportUtil` — delayed teleport + animation helper
- `com.novamclabs.util.RegionGuardUtil` — region protection checks (WorldGuard/PlotSquared/...)

## Notes

- Internal APIs may change between snapshots.
- For cross-server transfers, the current implementation uses the `BungeeCord` plugin message channel via `ProxyMessenger.connect(...)`.

