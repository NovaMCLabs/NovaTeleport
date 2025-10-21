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

    private void startGeneratorTask() {
        // 异步持续补充
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<String, WorldConfig> e : worldConfigs.entrySet()) {
                    String worldName = e.getKey();
                    WorldConfig conf = e.getValue();
                    if (!conf.enabled) continue;
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) continue;
                    Queue<Location> q = pools.computeIfAbsent(worldName, k -> new ConcurrentLinkedQueue<>());
                    while (q.size() < poolSize) {
                        Location loc = generateOne(world, conf);
                        if (loc != null) q.offer(loc);
                        else break; // 避免死循环
                    }
                }
            }
        }.runTaskTimerAsynchronously(plugin, 20L, 20L * 10); // 每10秒尝试补充
    }

    private Location generateOne(World world, WorldConfig conf) {
        Random rnd = new Random();
        for (int i = 0; i < 50; i++) {
            double angle = rnd.nextDouble() * Math.PI * 2.0;
            double dist = conf.minRadius + (rnd.nextDouble() * (conf.maxRadius - conf.minRadius));
            double rx = conf.centerX + Math.cos(angle) * dist;
            double rz = conf.centerZ + Math.sin(angle) * dist;
            int bx = (int) Math.floor(rx);
            int bz = (int) Math.floor(rz);
            // 生物群系黑名单
            String biome = world.getBiome(bx, world.getHighestBlockYAt(bx, bz), bz).name();
            if (conf.biomeBlacklist.contains(biome)) continue;
            // 从上往下寻找落点
            int top = Math.min(world.getMaxHeight() - 2, 319);
            int bottom = Math.max(world.getMinHeight() + 1, -64);
            for (int y = top; y >= bottom; y--) {
                org.bukkit.block.Block below = world.getBlockAt(bx, y - 1, bz);
                org.bukkit.block.Block feet = world.getBlockAt(bx, y, bz);
                org.bukkit.block.Block head = world.getBlockAt(bx, y + 1, bz);
                if (!feet.getType().isAir() || !head.getType().isAir()) continue;
                if (!below.getType().isSolid()) continue;
                if (conf.unsafeBlocks.contains(below.getType())) continue;
                return new Location(world, bx + 0.5, y, bz + 0.5);
            }
        }
        return null;
    }

    public Location poll(World world) {
        Queue<Location> q = pools.get(world.getName());
        if (q == null) return null;
        return q.poll();
    }
}
