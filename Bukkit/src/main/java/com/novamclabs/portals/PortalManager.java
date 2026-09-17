package com.novamclabs.portals;

import com.novamclabs.StarTeleport;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PortalManager implements Listener {
    public static class PortalDef {
        public final String key;
        public final String name;
        public final String frameBlockSpec; // 支持 ItemsAdder 自定义方块 ID | Support IA custom block id
        public final String activationSpec; // 支持 ItemsAdder/MMOItems 物品 | Support IA/MMOItems
        public final Material portalBlock;
        public final String world;
        public final String x;
        public final String y;
        public final String z;
        public PortalDef(String key, String name, String frameBlockSpec, String activationSpec, Material portalBlock,
                         String world, String x, String y, String z) {
            this.key = key;
            this.name = name; this.frameBlockSpec = frameBlockSpec; this.activationSpec = activationSpec; this.portalBlock = portalBlock;
            this.world = world; this.x = x; this.y = y; this.z = z;
        }
    }

    /** 同一玩家两次触发之间的最小间隔（毫秒），避免站在传送门里被反复传送 */
    private static final long REUSE_COOLDOWN_MS = 3000L;

    private final StarTeleport plugin;
    // 传送门方块由各区域线程读写（Folia 下同服不同区域并行），必须是并发 Map
    private final Map<Location, PortalDef> activePortals = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTrigger = new ConcurrentHashMap<>();
    private volatile Map<String, PortalDef> defs = new ConcurrentHashMap<>();
    private File stateFile;

    public PortalManager(StarTeleport plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "data/portals_state.yml");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        reload();
    }

    /** 重新载入定义并恢复已激活的传送门方块 | reload definitions and restore activated portals */
    public synchronized void reload() {
        loadDefinitions();
        restoreActivePortals();
    }

    private void loadDefinitions() {
        File out = new File(plugin.getDataFolder(), "portals.yml");
        if (!out.exists()) {
            out.getParentFile().mkdirs();
            try {
                plugin.saveResource("portals.yml", false);
            } catch (IllegalArgumentException ignored) {}
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(out);
        ConfigurationSection sec = cfg.getConfigurationSection("portals");
        Map<String, PortalDef> map = new ConcurrentHashMap<>();
        if (sec == null) {
            this.defs = map;
            return;
        }
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) continue;
            String name = s.getString("name", key);
            String frameSpec = Objects.toString(s.getString("frame_block", "OBSIDIAN"));
            String actSpec = Objects.toString(s.getString("activation_item", "FLINT_AND_STEEL"));
            Material portal = Material.matchMaterial(Objects.toString(s.getString("portal_block", "NETHER_PORTAL")));
            String world = s.getString("destination.world", "world");
            String x = Objects.toString(s.get("destination.x", "SAME_AS_ENTRY"));
            String y = Objects.toString(s.get("destination.y", "SAME_AS_ENTRY"));
            String z = Objects.toString(s.get("destination.z", "SAME_AS_ENTRY"));
            if (portal == null) continue;
            map.put(key, new PortalDef(key, name, frameSpec, actSpec, portal, world, x, y, z));
        }
        this.defs = map;
    }

    // ===== 传送门状态持久化 | persist activated portals =====

    private void restoreActivePortals() {
        activePortals.clear();
        if (stateFile == null || !stateFile.exists()) return;
        YamlConfiguration state = YamlConfiguration.loadConfiguration(stateFile);
        for (String raw : state.getStringList("active")) {
            String[] parts = raw.split(";");
            if (parts.length != 5) continue;
            PortalDef def = defs.get(parts[4]);
            if (def == null) continue;
            try {
                org.bukkit.World w = Bukkit.getWorld(parts[0]);
                if (w == null) continue;
                Location loc = new Location(w, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                activePortals.put(loc.getBlock().getLocation(), def);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void saveActivePortals() {
        YamlConfiguration state = new YamlConfiguration();
        java.util.List<String> list = new java.util.ArrayList<>();
        for (Map.Entry<Location, PortalDef> e : activePortals.entrySet()) {
            Location l = e.getKey();
            if (l.getWorld() == null) continue;
            list.add(l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ() + ";" + e.getValue().key);
        }
        state.set("active", list);
        try {
            com.novamclabs.storage.DataStore.atomicSave(state, stateFile);
        } catch (IOException ignored) {
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        ItemStack it = e.getItem();
        if (it == null) return;
        Location clicked = e.getClickedBlock() != null ? e.getClickedBlock().getLocation() : null;
        if (clicked == null) return;
        for (PortalDef def : defs.values()) {
            if (com.novamclabs.util.ItemResolver.matches(def.activationSpec, it)
                && com.novamclabs.util.ItemResolver.blockMatchesFrame(def.frameBlockSpec, e.getClickedBlock())) {
                // 尝试检测垂直矩形框架并填充 | Try detect vertical rectangular frame and fill interior
                if (tryBuildPortalRegion(e.getPlayer(), clicked, def)) {
                    saveActivePortals();
                    e.getPlayer().sendMessage(plugin.getLang().tr("portal.activated", "name", def.name));
                }
                return;
            }
        }
    }

    // 检测矩形框架并填充内部为传送方块（支持 X-常量平面 或 Z-常量平面）
    // Detect rectangular frame in vertical plane (X-constant or Z-constant), fill interior with portal blocks
    private boolean tryBuildPortalRegion(Player actor, Location origin, PortalDef def) {
        // 尝试两种平面：x 固定(y,z 平面) 与 z 固定(y,x 平面)
        if (buildInPlane(origin, def, true)) return true;
        return buildInPlane(origin, def, false);
    }

    private boolean buildInPlane(Location origin, PortalDef def, boolean xConstant) {
        org.bukkit.block.Block start = origin.getBlock();
        int fixed = xConstant ? start.getX() : start.getZ();
        // 找到边界：沿着两个轴向扩展，直到非框架方块为止 | expand along axes until non-frame
        int y0 = start.getY(), y1 = start.getY();
        int v0 = xConstant ? start.getZ() : start.getX();
        int v1 = v0;
        // 向上
        while (isFrame(def, xConstant ? origin.getWorld().getBlockAt(fixed, y0 - 1, v0)
                                      : origin.getWorld().getBlockAt(v0, y0 - 1, fixed))) y0--;
        // 向下
        while (isFrame(def, xConstant ? origin.getWorld().getBlockAt(fixed, y1 + 1, v0)
                                      : origin.getWorld().getBlockAt(v0, y1 + 1, fixed))) y1++;
        // 向负方向（z 或 x）
        while (isFrame(def, xConstant ? origin.getWorld().getBlockAt(fixed, start.getY(), v0 - 1)
                                      : origin.getWorld().getBlockAt(v0 - 1, start.getY(), fixed))) v0--;
        // 向正方向（z 或 x）
        while (isFrame(def, xConstant ? origin.getWorld().getBlockAt(fixed, start.getY(), v1 + 1)
                                      : origin.getWorld().getBlockAt(v1 + 1, start.getY(), fixed))) v1++;
        int minY = y0, maxY = y1, minV = Math.min(v0, v1), maxV = Math.max(v0, v1);
        if (maxY - minY < 2 || maxV - minV < 2) return false; // 至少 3x3 框架 | need at least 3x3
        // 校验外圈都是框架方块 | verify perimeter is frame
        for (int y = minY; y <= maxY; y++) {
            for (int v = minV; v <= maxV; v++) {
                boolean edge = (y == minY || y == maxY || v == minV || v == maxV);
                if (!edge) continue;
                org.bukkit.block.Block b = xConstant ? origin.getWorld().getBlockAt(fixed, y, v)
                                                     : origin.getWorld().getBlockAt(v, y, fixed);
                if (!isFrame(def, b)) return false;
            }
        }
        // 填充内部为空气才填充传送方块 | fill only if interior is air
        boolean placed = false;
        for (int y = minY + 1; y <= maxY - 1; y++) {
            for (int v = minV + 1; v <= maxV - 1; v++) {
                org.bukkit.block.Block b = xConstant ? origin.getWorld().getBlockAt(fixed, y, v)
                                                     : origin.getWorld().getBlockAt(v, y, fixed);
                if (b.getType().isAir()) {
                    b.setType(def.portalBlock);
                    activePortals.put(b.getLocation().getBlock().getLocation(), def);
                    placed = true;
                }
            }
        }
        return placed;
    }

    private boolean isFrame(PortalDef def, org.bukkit.block.Block block) {
        if (block == null) return false;
        return com.novamclabs.util.ItemResolver.blockMatchesFrame(def.frameBlockSpec, block);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastTrigger.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (to == null) return;

        // 只取一次方块对象：读取的正是玩家所在的方块，属于该玩家的区域线程，Folia 下也安全
        org.bukkit.block.Block toB = to.getBlock();
        Location toBlock = toB.getLocation();
        PortalDef def = activePortals.get(toBlock);
        if (def == null) return;

        // 框架被破坏后传送方块会消失（原版机制），此时地图条目已失效：
        // 懒清理该条目，避免出现「空气里还能传送」的隐形陷阱
        if (toB.getType() != def.portalBlock) {
            activePortals.remove(toBlock, def);
            return;
        }

        // 只有“刚进入”该方块时才触发；站着不动/原地转头不会重复触发
        Location fromBlock = e.getFrom().getBlock().getLocation();
        if (fromBlock.equals(toBlock)) return;

        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastTrigger.get(uuid);
        if (last != null && now - last < REUSE_COOLDOWN_MS) return;
        lastTrigger.put(uuid, now);

        p.sendMessage(plugin.getLang().tr("portal.teleporting", "name", def.name));
        org.bukkit.World w = Bukkit.getWorld(def.world);
        if (w == null) {
            plugin.getLogger().warning("[Portal] Destination world not loaded: " + def.world);
            return;
        }
        Location dest = new Location(w, resolveCoord(def.x, toBlock.getX()), resolveCoord(def.y, toBlock.getY()), resolveCoord(def.z, toBlock.getZ()));
        int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
        // 传送门不做经济扣费，也不因移动取消（进入即传送）
        com.novamclabs.util.TeleportUtil.delayedTeleportWithAnimation(plugin, p, dest, delay, "portal",
                (player, charged) -> true, null);
    }

    private double resolveCoord(String v, double fallback) {
        if (v == null) return fallback;
        if ("SAME_AS_ENTRY".equalsIgnoreCase(v)) return fallback;
        try { return Double.parseDouble(v);} catch (Exception ignored) {}
        return fallback;
    }
}
