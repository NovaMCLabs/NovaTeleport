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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class PortalManager implements Listener {
    public static class PortalDef {
        public final String name;
        public final String frameBlockSpec; // 支持 ItemsAdder 自定义方块 ID | Support IA custom block id
        public final String activationSpec; // 支持 ItemsAdder/MMOItems 物品 | Support IA/MMOItems
        public final Material portalBlock;
        public final String world;
        public final String x;
        public final String y;
        public final String z;
        public PortalDef(String name, String frameBlockSpec, String activationSpec, Material portalBlock,
                         String world, String x, String y, String z) {
            this.name = name; this.frameBlockSpec = frameBlockSpec; this.activationSpec = activationSpec; this.portalBlock = portalBlock;
            this.world = world; this.x = x; this.y = y; this.z = z;
        }
    }

    private final StarTeleport plugin;
    private final Map<Location, PortalDef> activePortals = new HashMap<>();
    private Map<String, PortalDef> defs = new HashMap<>();

    public PortalManager(StarTeleport plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadDefinitions();
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
        if (sec == null) return;
        Map<String, PortalDef> map = new HashMap<>();
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
            map.put(key, new PortalDef(name, frameSpec, actSpec, portal, world, x, y, z));
        }
        this.defs = map;
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
        // 采用外扩一圈后的边界作为矩形周长
        int minY = y0, maxY = y1, minV = Math.min(v0, v1), maxV = Math.max(v0, v1);
        if (maxY - minY < 2 || maxV - minV < 2) return false; // 至少 3x3 框架 | need at least 3x3
        // 校验外圈都是框架方块 | verify perimeter is frame
        for (int y = minY; y <= maxY; y++) {
            for (int v = minV; v <= maxV; v++) {
                boolean edge = (y == minY || y == maxY || v == minV || v == maxV);
                org.bukkit.block.Block b = xConstant ? origin.getWorld().getBlockAt(fixed, y, v)
                                                     : origin.getWorld().getBlockAt(v, y, fixed);
                if (edge) {
                    if (!isFrame(def, b)) return false;
                }
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
                    activePortals.put(b.getLocation(), def);
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
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Location feet = e.getTo().getBlock().getLocation();
        PortalDef def = activePortals.get(feet);
        if (def == null) return;
        // 进入传送门
        Player p = e.getPlayer();
        p.sendMessage(plugin.getLang().tr("portal.teleporting", "name", def.name));
        // 计算目标
        org.bukkit.World w = Bukkit.getWorld(def.world);
        if (w == null) return;
        Location dest = new Location(w, resolveCoord(def.x, feet.getX()), resolveCoord(def.y, feet.getY()), resolveCoord(def.z, feet.getZ()));
        int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
        com.novamclabs.util.TeleportUtil.delayedTeleportWithAnimation(plugin, p, dest, delay, null);
    }

    private double resolveCoord(String v, double fallback) {
        if (v == null) return fallback;
        if ("SAME_AS_ENTRY".equalsIgnoreCase(v)) return fallback;
        try { return Double.parseDouble(v);} catch (Exception ignored) {}
        return fallback;
    }
}
