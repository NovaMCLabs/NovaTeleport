package com.novamclabs.util;

import com.novamclabs.StarTeleport;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;

public final class SpatialAnchorUtil {

    private SpatialAnchorUtil() {
    }

    public static boolean isRequired(StarTeleport plugin, String type) {
        if (type == null || type.isBlank()) {
            return false;
        }
        if (!plugin.getConfig().getBoolean("spatial_anchors.enabled", false)) {
            return false;
        }
        List<String> required = plugin.getConfig().getStringList("spatial_anchors.required_types");
        if (required == null || required.isEmpty()) {
            return false;
        }
        for (String t : required) {
            if (t != null && t.equalsIgnoreCase(type)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasAnchor(StarTeleport plugin, Location destination) {
        if (destination == null || destination.getWorld() == null) {
            return false;
        }

        Material center = readMaterial(plugin, "spatial_anchors.center_block", Material.OBSIDIAN);
        Material edge = readMaterial(plugin, "spatial_anchors.edge_block", Material.END_ROD);
        Material corner = readMaterial(plugin, "spatial_anchors.corner_block", Material.GOLD_BLOCK);

        Location ground = destination.clone().subtract(0, 1, 0);
        World world = ground.getWorld();
        int x = ground.getBlockX();
        int y = ground.getBlockY();
        int z = ground.getBlockZ();

        if (!matches(world.getBlockAt(x, y, z), center)) return false;

        // edges (N/S/E/W)
        if (!matches(world.getBlockAt(x + 1, y, z), edge)) return false;
        if (!matches(world.getBlockAt(x - 1, y, z), edge)) return false;
        if (!matches(world.getBlockAt(x, y, z + 1), edge)) return false;
        if (!matches(world.getBlockAt(x, y, z - 1), edge)) return false;

        // corners
        if (!matches(world.getBlockAt(x + 1, y, z + 1), corner)) return false;
        if (!matches(world.getBlockAt(x + 1, y, z - 1), corner)) return false;
        if (!matches(world.getBlockAt(x - 1, y, z + 1), corner)) return false;
        if (!matches(world.getBlockAt(x - 1, y, z - 1), corner)) return false;

        return true;
    }

    private static boolean matches(Block block, Material expected) {
        return block != null && block.getType() == expected;
    }

    private static Material readMaterial(StarTeleport plugin, String path, Material def) {
        String raw = plugin.getConfig().getString(path);
        if (raw == null || raw.isBlank()) {
            return def;
        }
        Material m = Material.matchMaterial(raw.trim().toUpperCase());
        return m != null ? m : def;
    }
}
