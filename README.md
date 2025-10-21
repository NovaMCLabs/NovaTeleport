[**English - EN**] | [[**简体中文 - CN**](README_CN.md)]

### NovaTeleport

An all‑in‑one teleportation solution for Spigot/Paper (Java 17).

Key highlights:
- Auto world-teleport by Y threshold
- Rich animation library (Magic | Tech | Natural) + per‑player preference
- Economy support via Vault (optional, bypass permission supported)
- High‑performance RTP with async pregen pool (rtp.yml)
- Custom portals from blocks/items (portals.yml)
- Teleport scrolls (consumable items) (scrolls.yml)
- Homes/Warps/Back/Spawn/Tpa/GUI (Java inventory + Bedrock fallback)
- Root command router with prefix: /novateleport (aliases: /ntp, /novatp)
- City/hub command: local world or Bungee/Velocity server
- Built‑in lightweight Party system (/party)
- Cross-server TPA menu via Redis bus (Bedrock forms + Java clickable chat)
- Scripting hooks (JS) with MythicMobs/MMOCore reflection calls

Modules (in repo):
- Plugin (this module)
- novastorage-lib: JDBC storage + Redis bus (no compile deps)
- nova-proxy-bungee: Bungee helper plugin
- nova-proxy-velocity: Velocity helper plugin

---

Getting started
1) Drop the jar in plugins/, start server once to generate files.
2) Configure:
   - config.yml (core settings, animations, features, economy)
   - portals.yml (Custom portal definitions)
   - rtp.yml (RTP pregen pool and world rules)
   - scrolls.yml (Scroll items style)
   - langs/en_US.yml, langs/zh_CN.yml (messages)
3) Permissions: see plugin.yml. Vault (optional) for economy; Floodgate (optional) for Bedrock detection.

---

Commands (router /novateleport | /ntp)
- /stp reload — reload configuration
- /tpanimation select <magic|tech|natural> — set own animation style
- /scroll bind <home|warp> <name> — create a bound teleport scroll
- /city (alias /hub) — go to main city (local or proxy)
- /party <create|invite <player>|accept|leave|tp> — built‑in party
- All classic commands remain: /tpa, /tpahere, /tpaccept, /tpdeny, /tpcancel, /sethome, /home, /delhome, /homes, /setwarp, /warp, /delwarp, /warps, /spawn, /back, /rtp, /tpmenu

---

Configuration files
- config.yml
  - general.language, general.debug
  - auto_world_teleport: delay_seconds, threshold_y, worlds: [...]
  - commands.teleport_delay_seconds
  - features.animation_enabled, features.post_effect_enabled
  - animations.default_style
  - economy.enabled, economy.bypass_permission, economy.costs.*
- portals.yml: define any number of portals with frame_block, activation_item (supports vanilla, ItemsAdder, MMOItems), portal_block, destination
- rtp.yml: async pregen_pool_size, per‑world radius/blacklist/unsafe blocks
- scrolls.yml: item skins and names for scrolls

---

Developer notes
- API entry: main class com.novamclabs.StarTeleport
- Animations API: com.novamclabs.animations.AnimationManager
- RTP async pool: com.novamclabs.rtp.RtpPoolManager
- Portals: com.novamclabs.portals.PortalManager (ItemsAdder/MMOItems via reflection)
- Economy: com.novamclabs.util.EconomyUtil (Vault via reflection, optional)

---

License: MIT (if missing, treat as all‑rights‑reserved placeholder)
