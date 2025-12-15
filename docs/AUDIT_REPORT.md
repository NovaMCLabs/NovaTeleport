# NovaTeleport Code/Docs Audit Report

> Static audit only (code/config/docs reading). No live server runtime verification.
> Date: 2025-12-15

## Summary

- Bukkit module contains implementations for the main advertised teleport features (TPA/Home/Warp/Spawn/Back/RTP/menus/animations/scrolls/portals/stele/deathback/offline teleport/guild/Towny/toll warps/teleport logs).
- Documentation had broken references and some architectural/API descriptions that do not match current wiring.
- Two stability issues were fixed in this branch:
  1) RTP pool generation no longer accesses world data asynchronously.
  2) `StarTeleport` no longer stores `Player` objects as map keys; it uses UUID keys and cleans up on quit.

See `AUDIT_REPORT_CN.md` for the full detailed Chinese report.
