package com.novamclabs.storage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 数据存储层（本地YAML + 可扩展跨服字段）
 * Data store (local YAML with optional cross-server fields)
 */
public class DataStore {
    private final File dataFolder;
    private final File homesFile;
    private final File warpsFile;
    private final File playersDir;
    private final File configFile;

    private volatile String serverName = "local";
    /** config.yml 的最后修改时间，用来在 /stp reload 之后重新读取 network.server_name */
    private volatile long serverNameStamp = -1L;

    private final YamlConfiguration homesCfg = new YamlConfiguration();
    private final YamlConfiguration warpsCfg = new YamlConfiguration();

    private final Object homesLock = new Object();
    private final Object warpsLock = new Object();

    /** 内存中的修改是否需要写回磁盘 | whether the in-memory config has unsaved changes */
    private volatile boolean homesDirty;
    private volatile boolean warpsDirty;
    /** 是否已有一个合并写盘任务在排队，避免一次突发排 N 个任务 | one queued flush at a time */
    private final AtomicBoolean flushQueued = new AtomicBoolean(false);

    /**
     * 家/传送点写盘的合并延迟（毫秒）：/sethome 之类只改内存，1 秒内的突发合并成一次整文件写。
     * 用自有守护线程而不是 Bukkit 调度器：插件被禁用时任务会被取消，那时这次修改就只留在内存里直接丢了。
     */
    private static final long FLUSH_DELAY_MS = 1000L;

    private static final ScheduledExecutorService FLUSHER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "NovaTeleport-DataStore");
        t.setDaemon(true);
        return t;
    });
    /** 有未落盘修改的实例；关服钩子据此同步兜底 | instances with unsaved changes */
    private static final Set<DataStore> PENDING = ConcurrentHashMap.newKeySet();

    /**
     * 按数据目录归类的写入锁：同一份文件在同一时刻只能有一个「序列化 + 写临时文件 + 替换」的事务在执行。
     * 少了它的话，两个线程会同时写同一个 .tmp：A 写完 tmp、B 覆盖 tmp 后先 move，A 再 move 就已经没有 tmp 了，
     * 结果是 IOException（本次修改直接丢失），甚至 move 到半个文件。
     * 正常路径上同目录同时只有一个实例（插件重载那一下除外），所以这把锁基本无争用。
     */
    private static final Map<String, Object> DIR_LOCKS = new ConcurrentHashMap<>();

    private static Object dirLock(File dataFolder) {
        String key = dataFolder.getAbsolutePath();
        return DIR_LOCKS.computeIfAbsent(key, k -> new Object());
    }

    static {
        // onDisable 不经过 DataStore，这里注册关服钩子保证内存里的修改最终落盘
        Runtime.getRuntime().addShutdownHook(new Thread(DataStore::flushAllPending, "NovaTeleport-DataStore-Shutdown"));
    }

    /**
     * 每个玩家的写锁。用固定数量的分段锁按 UUID 散列取用，而不是 Map&lt;UUID, Object&gt;：
     * 后者会为每个见过的 UUID 永久保留一个条目（只增不减），这里条目数恒定。
     * 不同 UUID 偶尔共用一把锁只是轻微多等一会，不损失正确性。
     */
    private static final int LOCK_STRIPES = 64;
    private final Object[] playerLocks = new Object[LOCK_STRIPES];

    {
        for (int i = 0; i < LOCK_STRIPES; i++) playerLocks[i] = new Object();
    }

    public DataStore(File pluginDataFolder) {
        this.dataFolder = new File(pluginDataFolder, "data");
        if (!dataFolder.exists()) dataFolder.mkdirs();
        this.playersDir = new File(dataFolder, "players");
        if (!playersDir.exists()) playersDir.mkdirs();
        this.homesFile = new File(dataFolder, "homes.yml");
        this.warpsFile = new File(dataFolder, "warps.yml");
        this.configFile = new File(pluginDataFolder, "config.yml");
        this.serverNameStamp = configFile.lastModified();
        // 插件重载（/reload 或 enable-disable）会新建一个 DataStore：先把同目录里尚未落盘的旧实例写下去，
        // 否则本实例读到旧内容，之后整文件写回时会把旧实例内存里那些修改覆盖掉
        for (DataStore other : new HashSet<>(PENDING)) {
            if (other != this && dataFolder.equals(other.dataFolder)) {
                try {
                    other.flushNow();
                } catch (Throwable ignored) {
                }
            }
        }
        try {
            if (!homesFile.exists()) homesFile.createNewFile();
            if (!warpsFile.exists()) warpsFile.createNewFile();
            homesCfg.load(homesFile);
            warpsCfg.load(warpsFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
    }

    // 新构造：允许设置当前服务器名 | New ctor with server name
    public DataStore(File pluginDataFolder, String serverName) {
        this(pluginDataFolder);
        if (serverName != null && !serverName.isEmpty()) this.serverName = serverName;
    }

    /**
     * 当前服务器名。config.yml 被改动（例如 /stp reload）后自动重新读取，
     * 否则已存储的 home/warp 仍带着旧标记，而 {@code TeleportCommandHandler.isRemoteServer}
     * 读的是实时配置，两边不一致会把本服的家误判成跨服目标。
     */
    public String getServerName() {
        if (configFile.lastModified() != serverNameStamp) reloadServerName();
        return serverName;
    }

    /** 强制重新读取 network.server_name | re-read network.server_name from config.yml */
    public synchronized void reloadServerName() {
        serverNameStamp = configFile.lastModified();
        if (!configFile.isFile()) return;
        String name = YamlConfiguration.loadConfiguration(configFile).getString("network.server_name", "local");
        if (name != null && !name.isEmpty()) serverName = name;
    }

    /**
     * 校验家/传送点名称：不允许为空、过长或包含 YAML 路径分隔符（'.'）。
     * Names are user input and are used as YAML paths, so they must not contain '.'.
     * @return 规范化后的小写名称，非法时返回 null
     */
    public static String normalizeName(String raw) {
        if (raw == null) return null;
        String name = raw.trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty() || name.length() > 32) return null;
        if (name.indexOf('.') >= 0) return null;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
            if (!ok) return null;
        }
        return name;
    }

    /**
     * 原子写入 YAML：先写临时文件再替换，避免写入过程中崩溃导致数据文件损坏。
     * Atomic YAML save: write to a temp file, then move it into place.
     */
    public static void atomicSave(YamlConfiguration cfg, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        // 临时名必须每次唯一：固定名会让并发写的两个线程互相顶掉对方写了一半的 .tmp，
        // 后 move 的那个就把半截内容替换成了正式文件（calls here have no dirLock）。
        File tmp = new File(target.getPath() + "." + UUID.randomUUID() + ".tmp");
        cfg.save(tmp);
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // 通用位置序列化 | serialize location
    public static Map<String, Object> serializeLocation(Location loc) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", loc.getWorld().getName());
        map.put("x", loc.getX());
        map.put("y", loc.getY());
        map.put("z", loc.getZ());
        map.put("yaw", loc.getYaw());
        map.put("pitch", loc.getPitch());
        return map;
    }

    public static Location deserializeLocation(Map<String, Object> map) {
        if (map == null) return null;
        String world = Objects.toString(map.get("world"), null);
        if (world == null) return null;
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        Object rawX = map.get("x"), rawY = map.get("y"), rawZ = map.get("z");
        if (!(rawX instanceof Number) || !(rawY instanceof Number) || !(rawZ instanceof Number)) return null;
        double x = ((Number) rawX).doubleValue();
        double y = ((Number) rawY).doubleValue();
        double z = ((Number) rawZ).doubleValue();
        float yaw = map.get("yaw") instanceof Number ? ((Number) map.get("yaw")).floatValue() : 0f;
        float pitch = map.get("pitch") instanceof Number ? ((Number) map.get("pitch")).floatValue() : 0f;
        return new Location(w, x, y, z, yaw, pitch);
    }

    /** 从配置节读取位置（兼容 ConfigurationSection 与 Map 两种存储形态）| read a location from a section */
    public static Location readLocation(ConfigurationSection section) {
        if (section == null) return null;
        return deserializeLocation(section.getValues(false));
    }

    // 写盘合并 | write coalescing
    // =====
    // 内存态永远是权威，读走内存；磁盘写延后并合并，突发指令只写一次整文件。

    /** 标记需要写盘，并在延迟窗口结束后合并落盘 | mark dirty and coalesce the disk write */
    private void markDirty() {
        PENDING.add(this);
        if (flushQueued.compareAndSet(false, true)) {
            try {
                FLUSHER.schedule(this::flushTask, FLUSH_DELAY_MS, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException ignored) {
                // 已经在关服：保留在 PENDING 里，交给关服钩子同步落盘
                flushQueued.set(false);
            }
        }
    }

    private void flushTask() {
        flushNow();
        // 摘 PENDING 必须在「放开闸门」之前，否则关服钩子可能漏掉这一条；
        // 而放开闸门必须早于这次复查，这样落盘期间发生的修改只有两种归宿：
        // CAS 成功就自己排了队，CAS 失败就说明它早于这里的 set(false)，本次复查必定看到它的脏标记。
        PENDING.remove(this);
        flushQueued.set(false);
        if (isDirty()) markDirty();
    }

    /** 是否有未落盘的修改。两个字段各自被自己的锁保护，必须分别在锁内读，不能裸读。 */
    private boolean isDirty() {
        synchronized (homesLock) {
            if (homesDirty) return true;
        }
        synchronized (warpsLock) {
            return warpsDirty;
        }
    }

    /**
     * 把标记为脏的配置序列化并原子写回。
     * 序列化在各自的配置锁内（此时清脏标志也在锁内），文件 IO 在配置锁外 —— 这样磁盘慢也不会卡住读家的线程。
     * 但整段序列化+落盘必须放进「每数据目录一把」的 dirLock：
     *  - 同一个实例上并发触发（关服钩子 + 延迟任务 + 插件重载时的构造器预落盘）不会互相覆盖 .tmp；
     *  - 也不会出现「A 已写出新内容、B 随后把旧快照盖回去」的写入丢失。
     * 读路径（getHome/listHomes/...）只拿配置锁，不碰 dirLock，所以不会和写盘互等。
     */
    private void flushNow() {
        synchronized (dirLock(dataFolder)) {
            String homes = null;
            String warps = null;
            synchronized (homesLock) {
                if (homesDirty) {
                    homes = homesCfg.saveToString();
                    homesDirty = false;
                }
            }
            synchronized (warpsLock) {
                if (warpsDirty) {
                    warps = warpsCfg.saveToString();
                    warpsDirty = false;
                }
            }
            // 清脏标记在前、落盘在后：写失败就必须把标记放回去，否则这次修改内存和磁盘一起丢
            if (!writeAtomically(homesFile, homes)) {
                synchronized (homesLock) {
                    homesDirty = true;
                }
            }
            if (!writeAtomically(warpsFile, warps)) {
                synchronized (warpsLock) {
                    warpsDirty = true;
                }
            }
        }
    }

    /** @return 磁盘写入是否成功；false 时调用方必须把对应的脏标记放回 | false means the dirty flag must be restored */
    private boolean writeAtomically(File target, String dump) {
        if (dump == null) return true;
        try {
            File tmp = new File(target.getPath() + ".tmp");
            Files.writeString(tmp.toPath(), dump);
            try {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            Bukkit.getLogger().warning("[DataStore] Failed to save " + target.getName() + ": " + e.getMessage());
            return false;
        }
    }

    private static void flushAllPending() {
        for (DataStore store : new HashSet<>(PENDING)) {
            try {
                store.flushNow();
            } catch (Throwable ignored) {
            }
        }
    }

    // 目标封装：支持跨服 | destination wrapper supports cross-server
    public static class Destination {
        public final String server; public final Location location;
        public Destination(String server, Location location) { this.server = server; this.location = location; }
    }

    // 家系统 | homes
    public void setHome(UUID uuid, String name, Location loc) throws IOException {
        String key = normalizeName(name);
        if (key == null) throw new IllegalArgumentException("invalid home name");
        synchronized (homesLock) {
            Map<String, Object> data = serializeLocation(loc);
            data.put("server", getServerName());
            homesCfg.set(uuid.toString() + "." + key, data);
            homesDirty = true;
        }
        markDirty();
    }

    public void delHome(UUID uuid, String name) throws IOException {
        String key = normalizeName(name);
        if (key == null) return;
        synchronized (homesLock) {
            homesCfg.set(uuid.toString() + "." + key, null);
            if (homesCfg.getConfigurationSection(uuid.toString()) != null
                    && homesCfg.getConfigurationSection(uuid.toString()).getKeys(false).isEmpty()) {
                homesCfg.set(uuid.toString(), null);
            }
            homesDirty = true;
        }
        markDirty();
    }

    public Location getHome(UUID uuid, String name) {
        Destination d = getHomeDest(uuid, name);
        return d != null ? d.location : null;
    }

    public Destination getHomeDest(UUID uuid, String name) {
        String key = normalizeName(name);
        if (key == null) return null;
        synchronized (homesLock) {
            return readDestination(homesCfg, uuid.toString() + "." + key);
        }
    }

    public List<String> listHomes(UUID uuid) {
        synchronized (homesLock) {
            ConfigurationSection sec = homesCfg.getConfigurationSection(uuid.toString());
            if (sec == null) return Collections.emptyList();
            return new ArrayList<>(sec.getKeys(false));
        }
    }

    // 传送点 | warps
    public void setWarp(String name, Location loc) throws IOException {
        String key = normalizeName(name);
        if (key == null) throw new IllegalArgumentException("invalid warp name");
        synchronized (warpsLock) {
            Map<String, Object> data = serializeLocation(loc);
            data.put("server", getServerName());
            warpsCfg.set(key, data);
            warpsDirty = true;
        }
        markDirty();
    }

    public void delWarp(String name) throws IOException {
        String key = normalizeName(name);
        if (key == null) return;
        synchronized (warpsLock) {
            warpsCfg.set(key, null);
            warpsDirty = true;
        }
        markDirty();
    }

    public Location getWarp(String name) {
        Destination d = getWarpDest(name);
        return d != null ? d.location : null;
    }

    public Destination getWarpDest(String name) {
        String key = normalizeName(name);
        if (key == null) return null;
        synchronized (warpsLock) {
            return readDestination(warpsCfg, key);
        }
    }

    public List<String> listWarps() {
        synchronized (warpsLock) {
            return new ArrayList<>(warpsCfg.getKeys(false));
        }
    }

    private Destination readDestination(YamlConfiguration cfg, String path) {
        ConfigurationSection sec = cfg.getConfigurationSection(path);
        if (sec == null) return null;
        Map<String, Object> map = sec.getValues(false);
        Object stored = map.get("server");
        String server = stored != null ? stored.toString() : getServerName();
        return new Destination(server, deserializeLocation(map));
    }

    // ===== 玩家数据 | per-player data =====
    // 所有针对 data/players/<uuid>.yml 的读写集中在此，用 per-UUID 锁串行化，
    // 避免 DataStore/DeathManager/SteleManager/AnimationManager 各自读改写造成数据丢失。
    // All access to a player file is funnelled through here and serialized per UUID.

    public File playerFile(UUID uuid) {
        return new File(playersDir, uuid.toString() + ".yml");
    }

    private Object lockFor(UUID uuid) {
        return playerLocks[(uuid.hashCode() & 0x7fffffff) % LOCK_STRIPES];
    }

    /** 读改写玩家数据文件 | read-modify-write a player file under its lock */
    public void updatePlayer(UUID uuid, Consumer<YamlConfiguration> mutator) {
        if (uuid == null || mutator == null) return;
        synchronized (lockFor(uuid)) {
            File f = playerFile(uuid);
            YamlConfiguration cfg = new YamlConfiguration();
            if (f.exists()) {
                try {
                    cfg.load(f);
                } catch (Exception e) {
                    // 读取失败说明文件损坏或被占用；继续写回会用只含本次修改的配置覆盖它，
                    // 顺带抹掉 back/death/animation/steles 等全部数据。宁可放弃这次写入。
                    Bukkit.getLogger().warning("[DataStore] Failed to read " + f.getPath()
                            + ", write skipped to avoid data loss: " + e.getMessage());
                    return;
                }
            }
            mutator.accept(cfg);
            try { atomicSave(cfg, f); } catch (IOException ignored) {}
        }
    }

    /** 读取玩家数据文件（只读快照）| read a snapshot of a player file */
    public YamlConfiguration readPlayer(UUID uuid) {
        YamlConfiguration cfg = new YamlConfiguration();
        if (uuid == null) return cfg;
        synchronized (lockFor(uuid)) {
            File f = playerFile(uuid);
            if (f.exists()) {
                try {
                    cfg.load(f);
                } catch (Exception e) {
                    Bukkit.getLogger().warning("[DataStore] Failed to read " + f.getPath()
                            + ", treating as empty: " + e.getMessage());
                }
            }
        }
        return cfg;
    }

    public void setPlayerValue(UUID uuid, String path, Object value) {
        updatePlayer(uuid, cfg -> cfg.set(path, value));
    }

    public String getPlayerString(UUID uuid, String path) {
        return readPlayer(uuid).getString(path);
    }

    // /back 玩家上一个位置 | back location
    public void setBack(UUID uuid, Location loc) throws IOException {
        setPlayerValue(uuid, "back", serializeLocation(loc));
    }

    public Location getBack(UUID uuid) {
        return readLocation(readPlayer(uuid).getConfigurationSection("back"));
    }
}
