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

    /** 每个玩家的写锁，避免 Folia 多区域线程同时读改写同一文件 | per-player write lock */
    private final Map<UUID, Object> playerLocks = new ConcurrentHashMap<>();

    private final Object homesLock = new Object();
    private final Object warpsLock = new Object();

    public DataStore(File pluginDataFolder) {
        this.dataFolder = new File(pluginDataFolder, "data");
        if (!dataFolder.exists()) dataFolder.mkdirs();
        this.playersDir = new File(dataFolder, "players");
        if (!playersDir.exists()) playersDir.mkdirs();
        this.homesFile = new File(dataFolder, "homes.yml");
        this.warpsFile = new File(dataFolder, "warps.yml");
        this.configFile = new File(pluginDataFolder, "config.yml");
        this.serverNameStamp = configFile.lastModified();
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
        File tmp = new File(target.getPath() + ".tmp");
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
            atomicSave(homesCfg, homesFile);
        }
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
            atomicSave(homesCfg, homesFile);
        }
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
            atomicSave(warpsCfg, warpsFile);
        }
    }

    public void delWarp(String name) throws IOException {
        String key = normalizeName(name);
        if (key == null) return;
        synchronized (warpsLock) {
            warpsCfg.set(key, null);
            atomicSave(warpsCfg, warpsFile);
        }
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
        return playerLocks.computeIfAbsent(uuid, k -> new Object());
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
