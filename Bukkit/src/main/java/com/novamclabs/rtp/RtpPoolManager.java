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
        loadConfig();
        startGeneratorTask();
    }

    public void loadConfig() {
        File out = new File(plugin.getDataFolder(), "rtp.yml");
        if (!out.exists()) {
            try { plugin.saveResource("rtp.yml", false);} catch (IllegalArgumentException ignored) {}
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(out);
        this.poolSize = cfg.getInt("pregen_pool_size", 50);
        // 默认不安全方块
        List<String> defaults = Arrays.asList("LAVA","WATER","FIRE","CACTUS","MAGMA_BLOCK","AIR","VOID_AIR","CAVE_AIR","SWEET_BERRY_BUSH","WITHER_ROSE");
        Set<Material> defaultUnsafe = new HashSet<>();
        for (String s : defaults) { Material m = Material.matchMaterial(s); if (m!=null) defaultUnsafe.add(m);} 
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
        // 注意：World/Chunk 访问必须在主线程执行（异步访问会导致并发问题）
        // Important: World/chunk access must run on the server thread.
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                final int maxGeneratePerWorldPerRun = 5;
                for (Map.Entry<String, WorldConfig> e : worldConfigs.entrySet()) {
                    String worldName = e.getKey();
                    WorldConfig conf = e.getValue();
                    if (!conf.enabled) continue;
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) continue;

                    Queue<Location> q = pools.computeIfAbsent(worldName, k -> new ConcurrentLinkedQueue<>());
                    int generated = 0;
                    while (q.size() < poolSize && generated < maxGeneratePerWorldPerRun) {
                        Location loc = generateOne(world, conf);
                        if (loc == null) break;
                        q.offer(loc);
                        generated++;
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L * 10); // 每10秒尝试补充 | try refill every 10 seconds
    }

    private Location generateOne(World world, WorldConfig conf) {
        for (int i = 0; i < 50; i++) {
            double angle = rnd.nextDouble() * Math.PI * 2.0;
            double dist = conf.minRadius + (rnd.nextDouble() * (conf.maxRadius - conf.minRadius));
            double rx = conf.centerX + Math.cos(angle) * dist;
            double rz = conf.centerZ + Math.sin(angle) * dist;
            int bx = (int) Math.floor(rx);
            int bz = (int) Math.floor(rz);

            int highest = world.getHighestBlockYAt(bx, bz);
            int feetY = highest + 1;
            if (feetY < world.getMinHeight() + 1 || feetY > world.getMaxHeight() - 2) continue;

            String biome = world.getBiome(bx, highest, bz).name();
            if (conf.biomeBlacklist.contains(biome)) continue;

            org.bukkit.block.Block below = world.getBlockAt(bx, highest, bz);
            org.bukkit.block.Block feet = world.getBlockAt(bx, feetY, bz);
            org.bukkit.block.Block head = world.getBlockAt(bx, feetY + 1, bz);

            if (!feet.getType().isAir() || !head.getType().isAir()) continue;
            if (!below.getType().isSolid()) continue;
            if (conf.unsafeBlocks.contains(below.getType())) continue;

            return new Location(world, bx + 0.5, feetY, bz + 0.5);
        }
        return null;
    }

    public Location poll(World world) {
        Queue<Location> q = pools.get(world.getName());
        if (q == null) return null;
        return q.poll();
    }
}
