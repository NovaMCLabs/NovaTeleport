[**English - EN**] | [[**简体中文 - CN**](README_CN.md)]

### NovaTeleport

**Plugin Description**
This plugin teleports players to a specific world when they reach a designated altitude. The teleportation process includes the following mechanisms:

1. **Countdown System** - Automatically initiates a countdown when triggered. Teleportation only executes after the full countdown completes.
2. **Movement Detection** - Immediately cancels the teleportation if the player moves more than 2 blocks during the countdown.

---

**Command System**
* `stp reload` - Reload plugin configuration file

---

**Permission Nodes**

* `novateleport.pass` - Grants permission to trigger threshold-based teleportation
* `novateleport.command.reload` - Grants permission to reload configuration files
