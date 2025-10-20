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
        public final Material frameBlock;
        public final Material activationItem;
        public final Material portalBlock;
        public final String world;
        public final String x;
        public final String y;
        public final String z;
        public PortalDef(String name, Material frameBlock, Material activationItem, Material portalBlock,
                         String world, String x, String y, String z) {
            this.name = name; this.frameBlock = frameBlock; this.activationItem = activationItem; this.portalBlock = portalBlock;
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
            Material frame = Material.matchMaterial(Objects.toString(s.getString("frame_block", "OBSIDIAN")));
            Material act = Material.matchMaterial(Objects.toString(s.getString("activation_item", "FLINT_AND_STEEL")));
            Material portal = Material.matchMaterial(Objects.toString(s.getString("portal_block", "NETHER_PORTAL")));
            String world = s.getString("destination.world", "world");
            String x = Objects.toString(s.get("destination.x", "SAME_AS_ENTRY"));
            String y = Objects.toString(s.get("destination.y", "SAME_AS_ENTRY"));
            String z = Objects.toString(s.get("destination.z", "SAME_AS_ENTRY"));
            if (frame == null || act == null || portal == null) continue;
            map.put(key, new PortalDef(name, frame, act, portal, world, x, y, z));
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
            if (e.getItem().getType() == def.activationItem && e.getClickedBlock().getType() == def.frameBlock) {
                // 简化：在框架方块上方生成一个 portal_block 作为入口
                Location place = clicked.clone().add(0, 1, 0);
                if (place.getBlock().getType().isAir()) {
                    place.getBlock().setType(def.portalBlock);
                    activePortals.put(place.getBlock().getLocation(), def);
                    e.getPlayer().sendMessage(plugin.getLang().tr("portal.activated", "name", def.name));
                }
                return;
            }
        }
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
