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

    /** 每轮每个世界最多尝试的数量：主线程按区块代价高，控制在很小的批量 */
    private static final int MAX_GENERATE_PER_WORLD_PER_RUN = 3;

    /** 抢占到一个未加载区块时的重采样次数 | resample attempts when the sampled chunk is not loaded */
    private static final int MAX_CANDIDATE_ATTEMPTS = 8;

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
        // 周期补充坐标池。
        //
        // 为什么只采样「已加载」的区块（非显而易见，曾经的注释在此处给出了错误结论）：
        // 1) runAtLocation 是 Folia 的概念；FoliaLib 在 Spigot/Paper 上把它映射为 runNextTick，
        //    也就是主线程执行（见 SchedulerWrapper#runAtLocation 的注释与
        //    SpigotImplementation.runNextTick）。所以回调里的任何开销都是 main-thread 开销。
        // 2) getHighestBlockYAt 会同步加载并生成目标区块（Level.getChunk ->
        //    ServerChunkCache.getChunk，未生成时阻塞式冷生成）。采样半径一旦超出玩家已探索
        //    的范围（曾经的默认值 min_radius=500/max_radius=5000 就是如此），采样点就会
        //    几乎总是落在未生成区块上，一次采样就足以长时间阻塞主线程并触发看门狗。
        // 因此这里只取已加载区块中的坐标，未加载就重采样/放弃本轮，绝不请求区块生成。
        //
        // NOTE: runAtLocation is a Folia concept; FoliaLib maps it to runNextTick (main thread)
        // on Spigot/Paper, and getHighestBlockYAt forces a synchronous chunk load+generate.
        // Therefore pre-generation only ever samples chunks that are ALREADY loaded and
        // skips the rest instead of blocking the tick thread.
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
                    int[] candidate = pickLoadedCandidate(world, conf);
                    if (candidate == null) break; // 附近没有已加载区块，本轮跳过该世界
                    int bx = candidate[0];
                    int bz = candidate[1];
                    plugin.getScheduler().runAtLocation(world, bx, 0, bz, () -> {
                        if (q.size() >= poolSize) return;
                        // 检查与回调之间区块可能已被卸载：再确认一次，
                        // 否则 generateAt 里的 getHighestBlockYAt 会重新同步生成它。
                        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) return;
                        Location loc = generateAt(world, conf, bx, bz);
                        if (loc != null) q.offer(loc);
                    });
                }
            }
        }, 20L, 200L);
    }

    /**
     * 只在已加载的区块中取点，最多尝试 MAX_CANDIDATE_ATTEMPTS 次；都不行返回 null。
     * Only returns coordinates whose chunk is already loaded; null if none found.
     *
     * 主线程安全：isChunkLoaded 只查区块表，不会加载或生成区块。
     * Main-thread safe: isChunkLoaded is a lookup and never loads/generates a chunk.
     */
    private int[] pickLoadedCandidate(World world, WorldConfig conf) {
        for (int i = 0; i < MAX_CANDIDATE_ATTEMPTS; i++) {
            double[] candidate = pickCandidate(conf);
            int bx = (int) Math.floor(candidate[0]);
            int bz = (int) Math.floor(candidate[1]);
            if (world.isChunkLoaded(bx >> 4, bz >> 4)) return new int[]{bx, bz};
        }
        return null;
    }

    private double[] pickCandidate(WorldConfig conf) {
        double angle = rnd.nextDouble() * Math.PI * 2.0;
        double dist = conf.minRadius + (rnd.nextDouble() * (conf.maxRadius - conf.minRadius));
        return new double[]{ conf.centerX + Math.cos(angle) * dist, conf.centerZ + Math.sin(angle) * dist };
    }

    /**
     * 必须在 owning region 线程执行 | must run on the region owning (bx,bz)
     *
     * 前置条件：调用者必须先确认 (bx,bz) 所在区块已加载。本方法里的
     * getHighestBlockYAt / getBlockAt / getBiome 都会同步加载并生成区块，
     * 对未加载区块调用会在 tick 线程上造成一次冷生成。
     * Precondition: the chunk at (bx,bz) MUST already be loaded — these calls force a
     * synchronous chunk load + generation otherwise.
     */
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

    /**
     * rtp.yml 中该世界的配置；未配置该世界时返回 null。
     * 供其它路径（如 RTPUtil）复用同一份已解析的 rtp.yml，避免重复读盘并保证单一声明来源。
     * World config parsed from rtp.yml, or null if the world has no entry. Lets other
     * paths read the same parsed config instead of re-reading rtp.yml.
     */
    public WorldConfig getWorldConfig(String worldName) {
        if (worldName == null) return null;
        return worldConfigs.get(worldName);
    }
}
