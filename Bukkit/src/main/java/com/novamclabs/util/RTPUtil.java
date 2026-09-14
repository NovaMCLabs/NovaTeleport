package com.novamclabs.util;

import com.novamclabs.StarTeleport;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class RTPUtil {
    public static class RtpSettings {
        public int radius;
        public int tries;
        public double centerX;
        public double centerZ;
        public int minY;
        public int maxY;
        public Set<String> biomeBlacklist = new HashSet<>();
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
        // 半径/中心/生物群系黑名单以 rtp.yml 的世界配置为权威来源（与 RtpPoolManager 同源）：
        // 坐标池按 worlds.<world>.min_radius/max_radius/center_x/center_z 生成坐标，
        // 这里若仍读 config.yml 的 rtp.radius(默认 2000)，同一条 /rtp 的两种用法
        // （/rtp <半径> 与 /rtp now 取池）就会遵守不同上限。
        // rtp.yml 未配置该世界时才退回 config.yml 的旧默认值。
        //
        // Radius/center/biome blacklist are authoritative in rtp.yml (the same parsed config the
        // coordinate pool uses) so both consumers share one source; falls back to the old
        // config.yml defaults when rtp.yml has no entry for this world.
        com.novamclabs.rtp.RtpPoolManager pool = plugin.getRtpPoolManager();
        com.novamclabs.rtp.RtpPoolManager.WorldConfig wc = pool == null ? null : pool.getWorldConfig(world.getName());
        if (wc != null) {
            s.radius = wc.maxRadius;
            s.centerX = wc.centerX;
            s.centerZ = wc.centerZ;
            s.biomeBlacklist = new HashSet<>(wc.biomeBlacklist);
        } else {
            s.radius = plugin.getConfig().getInt(wbase + ".radius", plugin.getConfig().getInt(base + ".radius", 2000));
            s.centerX = plugin.getConfig().getDouble(wbase + ".center.x", plugin.getConfig().getDouble(base + ".center.x", 0));
            s.centerZ = plugin.getConfig().getDouble(wbase + ".center.z", plugin.getConfig().getDouble(base + ".center.z", 0));
        }
        s.tries = plugin.getConfig().getInt(wbase + ".tries", plugin.getConfig().getInt(base + ".tries", 30));
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

            // 冷区块护栏（非显而易见的原因）：
            // getBiome / getBlockAt 会同步加载并生成目标区块（ServerChunkCache.getChunk 的
            // 阻塞式冷生成）。本方法在 Spigot/Paper 上由主线程调用、在 Folia 上由 region 线程
            // 调用，也就是「调用者线程」；一个落在未生成区块上的候选点就足以卡死 tick 并触发
            // 看门狗——这正是 RtpPoolManager 预生成侧修复过的同一条路径。
            // 因此未加载的候选直接跳过，绝不请求区块生成。跳过同样消耗 s.tries 计数，
            // 全都不加载时循环照常结束并返回 null（调用方回 rtp.no_safe），不会死循环。
            //
            // Cold-chunk guard: getBiome/getBlockAt force a synchronous chunk load+generate and
            // this runs on the caller's tick thread (main on Spigot/Paper, region on Folia), so a
            // candidate whose chunk is not loaded is skipped rather than allowed to block the tick.
            // Skipping consumes one of the existing s.tries attempts, so the scan still terminates.
            if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
                continue;
            }

            // 从上往下扫描，寻找安全落点
            int top = Math.min(s.maxY, world.getMaxHeight() - 2);
            int bottom = Math.max(s.minY, world.getMinHeight() + 1);

            // 生物群系黑名单（与坐标池 generateAt 相同的判断）。
            // 默认 rtp.yml 黑名单为 OCEAN/DEEP_OCEAN，而本节是 /rtp 的默认路径，
            // 缺了这段玩家就会落在海里。列表为空时整段跳过，不产生额外开销。
            // `continue` 交给外层 s.tries 计数，命中全部黑名单时正常退出返回 null，不会死循环。
            //
            // Biome blacklist, same check as RtpPoolManager.generateAt. Empty list = no overhead.
            // `continue` is bounded by the existing s.tries loop, so an all-blacklisted scan
            // still terminates and returns null.
            if (!s.biomeBlacklist.isEmpty() && s.biomeBlacklist.contains(world.getBiome(bx, top, bz).name())) {
                continue;
            }

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
