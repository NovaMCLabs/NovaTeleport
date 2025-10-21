package com.novamclabs.util;

import com.novamclabs.StarTeleport;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;

import java.util.Random;

public class RTPUtil {
    public static class RtpSettings {
        public int radius;
        public int tries;
        public double centerX;
        public double centerZ;
        public int minY;
        public int maxY;
        public boolean avoidWater;
        public boolean avoidLava;
        public boolean avoidLeaves;
        public boolean avoidCactus;
        public boolean avoidFire;
        public boolean avoidCampfire;
        public boolean avoidMagma;
        public boolean avoidPowderSnow;
    }

    public static RtpSettings loadSettings(StarTeleport plugin, World world) {
        RtpSettings s = new RtpSettings();
        String base = "rtp";
        String wbase = base + ".worlds." + world.getName();
        s.radius = plugin.getConfig().getInt(wbase + ".radius", plugin.getConfig().getInt(base + ".radius", 2000));
        s.tries = plugin.getConfig().getInt(wbase + ".tries", plugin.getConfig().getInt(base + ".tries", 30));
        s.centerX = plugin.getConfig().getDouble(wbase + ".center.x", plugin.getConfig().getDouble(base + ".center.x", 0));
        s.centerZ = plugin.getConfig().getDouble(wbase + ".center.z", plugin.getConfig().getDouble(base + ".center.z", 0));
        int worldMin = world.getMinHeight();
        int worldMax = world.getMaxHeight();
        s.minY = Math.max(plugin.getConfig().getInt(wbase + ".min_y", plugin.getConfig().getInt(base + ".min_y", worldMin + 1)), worldMin + 1);
        s.maxY = Math.min(plugin.getConfig().getInt(wbase + ".max_y", plugin.getConfig().getInt(base + ".max_y", worldMax - 2)), worldMax - 2);
        String avoid = base + ".avoid";
        String avoidW = wbase + ".avoid";
        s.avoidWater = plugin.getConfig().getBoolean(avoidW + ".water", plugin.getConfig().getBoolean(avoid + ".water", true));
        s.avoidLava = plugin.getConfig().getBoolean(avoidW + ".lava", plugin.getConfig().getBoolean(avoid + ".lava", true));
        s.avoidLeaves = plugin.getConfig().getBoolean(avoidW + ".leaves", plugin.getConfig().getBoolean(avoid + ".leaves", true));
        s.avoidCactus = plugin.getConfig().getBoolean(avoidW + ".cactus", plugin.getConfig().getBoolean(avoid + ".cactus", true));
        s.avoidFire = plugin.getConfig().getBoolean(avoidW + ".fire", plugin.getConfig().getBoolean(avoid + ".fire", true));
        s.avoidCampfire = plugin.getConfig().getBoolean(avoidW + ".campfire", plugin.getConfig().getBoolean(avoid + ".campfire", true));
        s.avoidMagma = plugin.getConfig().getBoolean(avoidW + ".magma", plugin.getConfig().getBoolean(avoid + ".magma", true));
        s.avoidPowderSnow = plugin.getConfig().getBoolean(avoidW + ".powder_snow", plugin.getConfig().getBoolean(avoid + ".powder_snow", true));
        return s;
    }

    public static Location findSafeLocation(StarTeleport plugin, World world, Random rnd) {
        return findSafeLocation(plugin, world, rnd, null);
    }

    public static Location findSafeLocation(StarTeleport plugin, World world, Random rnd, Integer overrideRadius) {
        RtpSettings s = loadSettings(plugin, world);
        if (overrideRadius != null && overrideRadius > 0) s.radius = overrideRadius;
        for (int i = 0; i < s.tries; i++) {
            // 均匀分布选点：半径使用 sqrt 随机
            double angle = rnd.nextDouble() * Math.PI * 2.0;
            double dist = Math.sqrt(rnd.nextDouble()) * s.radius;
            double rx = s.centerX + Math.cos(angle) * dist;
            double rz = s.centerZ + Math.sin(angle) * dist;
            int bx = (int) Math.floor(rx);
            int bz = (int) Math.floor(rz);

            // 从上往下扫描，寻找安全落点
            int top = Math.min(s.maxY, world.getMaxHeight() - 2);
            int bottom = Math.max(s.minY, world.getMinHeight() + 1);
            for (int y = top; y >= bottom; y--) {
                Block below = world.getBlockAt(bx, y - 1, bz);
                Block feet = world.getBlockAt(bx, y, bz);
                Block head = world.getBlockAt(bx, y + 1, bz);

                if (!feet.getType().isAir() || !head.getType().isAir()) {
                    continue; // 需要保证脚部与头部都是空气
                }
                if (isLiquidOrWaterlogged(feet) || isLiquidOrWaterlogged(head)) {
                    continue;
                }
                if (!below.getType().isSolid()) {
                    continue; // 需要站在固体方块上
                }
                if (isUnsafeGround(below.getType(), s)) {
                    continue;
                }
                // 通过了所有检查，返回候选
                return new Location(world, bx + 0.5, y, bz + 0.5);
            }
        }
        return null;
    }

    private static boolean isLiquidOrWaterlogged(Block b) {
        if (b.isLiquid()) return true;
        if (b.getBlockData() instanceof Waterlogged) {
            return ((Waterlogged) b.getBlockData()).isWaterlogged();
        }
        Material t = b.getType();
        return t == Material.WATER || t == Material.LAVA;
    }

    private static boolean isUnsafeGround(Material m, RtpSettings s) {
        if (s.avoidWater && (m == Material.WATER || m == Material.KELP || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS)) return true;
        if (s.avoidLava && m == Material.LAVA) return true;
        if (s.avoidLeaves && org.bukkit.Tag.LEAVES.isTagged(m)) return true;
        if (s.avoidCactus && m == Material.CACTUS) return true;
        if (s.avoidFire && (m == Material.FIRE || m == Material.SOUL_FIRE)) return true;
        if (s.avoidCampfire && (m == Material.CAMPFIRE || m == Material.SOUL_CAMPFIRE)) return true;
        if (s.avoidMagma && m == Material.MAGMA_BLOCK) return true;
        if (s.avoidPowderSnow && m == Material.POWDER_SNOW) return true;
        return false;
    }
}
