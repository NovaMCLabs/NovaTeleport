# NovaTeleport Configuration Guide (EN)

Files:
- config.yml — core features, animations, economy, world rules
- portals.yml — custom portals
- rtp.yml — random teleport pregen
- scrolls.yml — teleport scrolls
- langs/en_US.yml, zh_CN.yml — messages

Highlights
- Root command: /novateleport (aliases: /ntp, /novatp)
- Debug: /ntp debug on|off (or general.debug in config.yml)

config.yml (key sections)
- general.language: en_US | zh_CN
- general.debug: true/false
- auto_world_teleport: { delay_seconds, threshold_y, worlds: [...] }
- commands.teleport_delay_seconds: int
- features.animation_enabled, post_effect_enabled: boolean
- animations.default_style: magic|tech|natural
- economy.enabled: true/false; economy.bypass_permission; economy.costs.* per action

portals.yml
- Define many portals under portals:
  - frame_block: Material or itemsadder:<id>
  - activation_item: Material or itemsadder:/mmoitems:TYPE:ID
  - portal_block: Material to fill interior of rectangular frame
  - destination: { world, x, y, z } (use SAME_AS_ENTRY to keep entry coordinates)

rtp.yml
- pregen_pool_size: size of async queue per world
- worlds.<name>.
  - enabled, center_x, center_z, min_radius, max_radius
  - biome_blacklist: list of biome names
- unsafe_landing_blocks: will be treated as invalid ground

scrolls.yml
- unbound/bound item skins (Material or custom name) and display names.

Messages
- Customize texts and colors, placeholders in braces: {name}/{seconds}...

Economy
- Vault optional. If enabled and provider present, costs will be charged unless bypass permission is granted.

Bedrock compatibility
- If Floodgate is present, Bedrock detection is enabled and chat fallbacks will be used for GUIs.
