package com.novamclabs.rtp;

import com.novamclabs.StarTeleport;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RtpPoolManager {
    private final StarTeleport plugin;
    private final Map<String, Queue<Location>> pools = new ConcurrentHashMap<>();
    private int poolSize = 50;
    private final Map<String, WorldConfig> worldConfigs = new ConcurrentHashMap<>();

    /** 每轮每个世界最多生成的数量：主线程按区块代价高，控制在很小的批量 */
    private static final int MAX_GENERATE_PER_WORLD_PER_RUN = 3;

    public static class WorldConfig {
        public boolean enabled = true;
        public double centerX = 0;
        public double centerZ = 0;
        public int minRadius = 500;
        public int maxRadius = 5000;
        public Set<String> biomeBlacklist = new HashSet<>();
        public Set<Material> unsafeBlocks = new HashSet<>();
    }

    public RtpPoolManager(StarTeleport plugin) {
        this.plugin = plugin;
        reload();
        startGeneratorTask();
    }

    public void reload() {
        File out = new File(plugin.getDataFolder(), "rtp.yml");
        if (!out.exists()) {
            try { plugin.saveResource("rtp.yml", false);} catch (IllegalArgumentException ignored) {}
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(out);
        this.poolSize = Math.max(1, cfg.getInt("pregen_pool_size", 50));
        // 默认不安全方块
        List<String> defaults = Arrays.asList("LAVA","WATER","FIRE","CACTUS","MAGMA_BLOCK","AIR","VOID_AIR","CAVE_AIR","SWEET_BERRY_BUSH","WITHER_ROSE");
        Set<Material> defaultUnsafe = new HashSet<>();
        for (String s : defaults) { Material m = Material.matchMaterial(s); if (m!=null) defaultUnsafe.add(m);}

        worldConfigs.clear();
        ConfigurationSection worlds = cfg.getConfigurationSection("worlds");
        if (worlds != null) {
            for (String w : worlds.getKeys(false)) {
                ConfigurationSection ws = worlds.getConfigurationSection(w);
                if (ws == null) continue;
                WorldConfig wc = new WorldConfig();
                wc.enabled = ws.getBoolean("enabled", true);
                wc.centerX = ws.getDouble("center_x", 0);
                wc.centerZ = ws.getDouble("center_z", 0);
                wc.minRadius = ws.getInt("min_radius", 500);
                wc.maxRadius = ws.getInt("max_radius", 5000);
                if (wc.maxRadius < wc.minRadius) wc.maxRadius = wc.minRadius;
                wc.biomeBlacklist = new HashSet<>(ws.getStringList("biome_blacklist"));
                wc.unsafeBlocks = new HashSet<>(defaultUnsafe);
                for (String s : cfg.getStringList("unsafe_landing_blocks")) {
                    Material m = Material.matchMaterial(s);
                    if (m != null) wc.unsafeBlocks.add(m);
                }
                worldConfigs.put(w, wc);
                pools.computeIfAbsent(w, k -> new ConcurrentLinkedQueue<>());
            }
        }
    }

    private final Random rnd = new Random();

    private void startGeneratorTask() {
        // 周期补充坐标池。区块访问通过 runAtLocation 调度到对应区域线程，
        // 保证 Folia 下也在正确线程执行；每轮批量很小，避免主线程卡顿。
        plugin.getScheduler().runTimer(() -> {
            for (Map.Entry<String, WorldConfig> e : worldConfigs.entrySet()) {
                String worldName = e.getKey();
                WorldConfig conf = e.getValue();
                if (!conf.enabled) continue;
                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;

                Queue<Location> q = pools.computeIfAbsent(worldName, k -> new ConcurrentLinkedQueue());
                int missing = poolSize - q.size();
                if (missing <= 0) continue;
                int batch = Math.min(MAX_GENERATE_PER_WORLD_PER_RUN, missing);
                for (int i = 0; i < batch; i++) {
                    double[] candidate = pickCandidate(conf);
                    int bx = (int) Math.floor(candidate[0]);
                    int bz = (int) Math.floor(candidate[1]);
                    plugin.getScheduler().runAtLocation(world, bx, 0, bz, () -> {
                        if (q.size() >= poolSize) return;
                        Location loc = generateAt(world, conf, bx, bz);
                        if (loc != null) q.offer(loc);
                    });
                }
            }
        }, 20L, 200L);
    }

    private double[] pickCandidate(WorldConfig conf) {
        double angle = rnd.nextDouble() * Math.PI * 2.0;
        double dist = conf.minRadius + (rnd.nextDouble() * (conf.maxRadius - conf.minRadius));
        return new double[]{ conf.centerX + Math.cos(angle) * dist, conf.centerZ + Math.sin(angle) * dist };
    }

    /** 必须在 owning region 线程执行 | must run on the region owning (bx,bz) */
    private Location generateAt(World world, WorldConfig conf, int bx, int bz) {
        int highest = world.getHighestBlockYAt(bx, bz);
        int feetY = highest + 1;
        if (feetY < world.getMinHeight() + 1 || feetY > world.getMaxHeight() - 2) return null;

        String biome = world.getBiome(bx, highest, bz).name();
        if (conf.biomeBlacklist.contains(biome)) return null;

        org.bukkit.block.Block below = world.getBlockAt(bx, highest, bz);
        org.bukkit.block.Block feet = world.getBlockAt(bx, feetY, bz);
        org.bukkit.block.Block head = world.getBlockAt(bx, feetY + 1, bz);

        if (!feet.getType().isAir() || !head.getType().isAir()) return null;
        if (!below.getType().isSolid()) return null;
        if (conf.unsafeBlocks.contains(below.getType())) return null;

        return new Location(world, bx + 0.5, feetY, bz + 0.5);
    }

    public Location poll(World world) {
        if (world == null) return null;
        Queue<Location> q = pools.get(world.getName());
        if (q == null) return null;
        return q.poll();
    }
}
