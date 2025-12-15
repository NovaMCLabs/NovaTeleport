# FAQ (NovaTeleport)

## Q1: Why does `/rtp` feel slow or fail sometimes?

- RTP may need safe ground checks and can fail when the configured world/radius/biome blacklist is too strict.
- Check `config.yml` (`rtp.*`) and `rtp.yml` (`worlds.*`, `unsafe_landing_blocks`).

## Q2: Vault economy costs do not work

- Ensure `economy.enabled: true` in `config.yml`.
- Install **Vault** and an economy provider plugin.
- Check that the player does not have the bypass permission `novateleport.economy.bypass`.

## Q3: Region plugins block teleports

- NovaTeleport checks all detected region adapters. If any adapter denies entry, the teleport is cancelled.
- Review the rules in your region plugins (WorldGuard/PlotSquared/Residence/etc.).

## Q4: Cross-server teleports do not work

- The current implementation uses the `BungeeCord` plugin message `Connect` command.
- Ensure your proxy supports and forwards the `BungeeCord` channel and that server names match `network.server_name`.

