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

    /**
     * 该传送类型是否**强制要求**目标处存在锚点结构。
     *
     * 注意这里的语义是「必需」而不是「加成」：一旦 {@code spatial_anchors.enabled: true}，
     * {@code required_types} 里列出的类型在目标落点（脚下方块 y-1 为中心）没有搭出正确结构时
     * 会被直接拒绝传送（{@link RegionGuardUtil} 返回 {@code ANCHOR_MISSING}）。
     * 默认值 {@code [guild, towny]} 意味着开启该功能后，公会点/Towny 落点必须**实际搭建**锚点，
     * 否则那条线路的传送会永久失败 —— 不是「没有加成」，而是「不能用」。
     * 因此开启前请先确认这些落点都已按 center/edge/corner 三档方块搭好。
     * Whether the destination *must* carry an anchor structure, not merely benefit from one.
     */
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

    /**
     * 读取目标脚下的方块布局。会读方块，因此在 Folia 上**必须在目标所属区域线程**调用
     * （走 {@link RegionGuardUtil#checkDestination}），否则就是跨区域访问。
     * Reads blocks, so on Folia it must run on the destination's owning region thread.
     */
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
