package com.novamclabs.storage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 数据存储层（本地YAML + 可扩展跨服字段）
 * Data store (local YAML with optional cross-server fields)
 */
public class DataStore {
    private final File dataFolder;
    private final File homesFile;
    private final File warpsFile;
    private final File playersDir;
    private String serverName = "local";

    private final YamlConfiguration homesCfg = new YamlConfiguration();
    private final YamlConfiguration warpsCfg = new YamlConfiguration();

    // 兼容旧构造（仅通过数据目录）| Legacy constructor
    public DataStore(File pluginDataFolder) {
        this.dataFolder = new File(pluginDataFolder, "data");
        if (!dataFolder.exists()) dataFolder.mkdirs();
        this.playersDir = new File(dataFolder, "players");
        if (!playersDir.exists()) playersDir.mkdirs();
        this.homesFile = new File(dataFolder, "homes.yml");
        this.warpsFile = new File(dataFolder, "warps.yml");
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

    public String getServerName() { return serverName; }

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
        String world = (String) map.get("world");
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        double x = ((Number) map.get("x")).doubleValue();
        double y = ((Number) map.get("y")).doubleValue();
        double z = ((Number) map.get("z")).doubleValue();
        float yaw = ((Number) map.getOrDefault("yaw", 0)).floatValue();
        float pitch = ((Number) map.getOrDefault("pitch", 0)).floatValue();
        return new Location(w, x, y, z, yaw, pitch);
    }

    // 目标封装：支持跨服 | destination wrapper supports cross-server
    public static class Destination {
        public final String server; public final Location location;
        public Destination(String server, Location location) { this.server = server; this.location = location; }
    }

    // 家系统 | homes
    public void setHome(UUID uuid, String name, Location loc) throws IOException {
        String path = uuid.toString() + "." + name.toLowerCase(Locale.ROOT);
        Map<String, Object> data = serializeLocation(loc);
        data.put("server", serverName);
        homesCfg.set(path, data);
        homesCfg.save(homesFile);
    }

    public void delHome(UUID uuid, String name) throws IOException {
        String path = uuid.toString() + "." + name.toLowerCase(Locale.ROOT);
        homesCfg.set(path, null);
        homesCfg.save(homesFile);
    }

    public Location getHome(UUID uuid, String name) {
        Destination d = getHomeDest(uuid, name);
        return d != null ? d.location : null;
    }

    public Destination getHomeDest(UUID uuid, String name) {
        String path = uuid.toString() + "." + name.toLowerCase(Locale.ROOT);
        if (!homesCfg.contains(path)) return null;
        Map<String, Object> map = (Map<String, Object>) homesCfg.getConfigurationSection(path).getValues(false);
        String server = Objects.toString(map.getOrDefault("server", serverName));
        Location loc = deserializeLocation(map);
        return new Destination(server, loc);
    }

    public List<String> listHomes(UUID uuid) {
        if (!homesCfg.contains(uuid.toString())) return Collections.emptyList();
        return new ArrayList<>(Objects.requireNonNull(homesCfg.getConfigurationSection(uuid.toString())).getKeys(false));
    }

    // 传送点 | warps
    public void setWarp(String name, Location loc) throws IOException {
        String path = name.toLowerCase(Locale.ROOT);
        Map<String, Object> data = serializeLocation(loc);
        data.put("server", serverName);
        warpsCfg.set(path, data);
        warpsCfg.save(warpsFile);
    }

    public void delWarp(String name) throws IOException {
        String path = name.toLowerCase(Locale.ROOT);
        warpsCfg.set(path, null);
        warpsCfg.save(warpsFile);
    }

    public Location getWarp(String name) {
        Destination d = getWarpDest(name);
        return d != null ? d.location : null;
    }

    public Destination getWarpDest(String name) {
        String path = name.toLowerCase(Locale.ROOT);
        if (!warpsCfg.contains(path)) return null;
        Map<String, Object> map = (Map<String, Object>) Objects.requireNonNull(warpsCfg.getConfigurationSection(path)).getValues(false);
        String server = Objects.toString(map.getOrDefault("server", serverName));
        Location loc = deserializeLocation(map);
        return new Destination(server, loc);
    }

    public List<String> listWarps() {
        return new ArrayList<>(warpsCfg.getKeys(false));
    }

    // /back 玩家上一个位置 | back location
    public void setBack(UUID uuid, Location loc) throws IOException {
        File f = new File(playersDir, uuid.toString() + ".yml");
        YamlConfiguration cfg = new YamlConfiguration();
        if (f.exists()) try {cfg.load(f);} catch (Exception ignored) {}
        cfg.set("back", serializeLocation(loc));
        cfg.save(f);
    }

    public Location getBack(UUID uuid) {
        File f = new File(playersDir, uuid.toString() + ".yml");
        if (!f.exists()) return null;
        YamlConfiguration cfg = new YamlConfiguration();
        try { cfg.load(f);} catch (Exception e) {return null;}
        if (!cfg.contains("back")) return null;
        Map<String, Object> map = (Map<String, Object>) Objects.requireNonNull(cfg.getConfigurationSection("back")).getValues(false);
        return deserializeLocation(map);
    }
}
