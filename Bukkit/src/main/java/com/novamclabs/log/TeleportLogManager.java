package com.novamclabs.log;

import com.novamclabs.StarTeleport;
import com.novamclabs.common.scheduler.SchedulerWrapper;
import com.novamclabs.storage.DataStore;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Teleport log storage + retention.
 *
 * 写入策略：传送时只改内存，每 10 秒把脏数据合并后异步落盘一次，
 * 避免每次传送都在主线程重写整个日志文件。
 */
public class TeleportLogManager {
    private final StarTeleport plugin;

    private FileConfiguration config;
    private boolean enabled;
    private int retentionDays;
    private Set<String> logTypes;
    private String rewindPermission;

    private File dataFile;
    private YamlConfiguration data;

    // 传送记录由玩家所属区域线程写入（Folia），而 flush/getLogs 在全局线程执行，
    // 因此队列必须是并发结构，否则会出现 ConcurrentModificationException / 丢数据。
    private final Map<UUID, Deque<TeleportLogEntry>> cache = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private SchedulerWrapper.ScheduledTask flushTask;

    /** 每个玩家保留的最大条数 | max entries kept in memory per player */
    private static final int MAX_ENTRIES_PER_PLAYER = 200;

    public TeleportLogManager(StarTeleport plugin) {
        this.plugin = plugin;
        reload();
        loadData();
        this.flushTask = plugin.getScheduler().runTimer(this::flush, 200L, 200L);
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "features_config.yml");
        if (!f.exists()) {
            try {
                plugin.saveResource("features_config.yml", false);
            } catch (IllegalArgumentException ignored) {
            }
        }
        this.config = YamlConfiguration.loadConfiguration(f);

        this.enabled = config.getBoolean("teleport_log.enabled", true);
        this.retentionDays = Math.max(1, config.getInt("teleport_log.retention_days", 30));
        this.rewindPermission = config.getString("teleport_log.rewind_permission", "novateleport.admin.rewind");
        List<String> list = config.getStringList("teleport_log.log_types");
        if (list == null || list.isEmpty()) {
            this.logTypes = Set.of("tpa", "tpahere", "home", "warp", "rtp", "back", "portal", "stele");
        } else {
            Set<String> set = new HashSet<>();
            for (String s : list) {
                if (s != null && !s.isBlank()) set.add(s.trim().toLowerCase(Locale.ROOT));
            }
            this.logTypes = set;
        }
    }

    public void reloadAll() {
        reload();
        loadData();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getRewindPermission() {
        return rewindPermission;
    }

    private void loadData() {
        this.dataFile = new File(plugin.getDataFolder(), "data/teleport_logs.yml");
        if (!dataFile.getParentFile().exists()) dataFile.getParentFile().mkdirs();
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (Exception ignored) {
            }
        }
        this.data = YamlConfiguration.loadConfiguration(dataFile);
        this.cache.clear();
        this.dirty.clear();

        for (String uuidStr : data.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                List<Map<?, ?>> entries = data.getMapList(uuidStr);
                Deque<TeleportLogEntry> deque = new ConcurrentLinkedDeque<>();
                for (Map<?, ?> raw : entries) {
                    Object t = raw.get("type");
                    Object time = raw.get("time");
                    Object from = raw.get("from");
                    Object to = raw.get("to");
                    if (!(t instanceof String) || !(time instanceof Number) || !(from instanceof Map) || !(to instanceof Map)) continue;
                    Location fromLoc = DataStore.deserializeLocation(castMap(from));
                    Location toLoc = DataStore.deserializeLocation(castMap(to));
                    if (fromLoc == null || toLoc == null) continue;
                    deque.add(new TeleportLogEntry(((Number) time).longValue(), ((String) t), fromLoc, toLoc));
                }
                if (!deque.isEmpty()) cache.put(uuid, deque);
            } catch (Exception ignored) {
            }
        }

        // 载入后按保留期裁剪一次 | trim once on load
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        for (UUID uuid : new HashSet<>(cache.keySet())) {
            Deque<TeleportLogEntry> deque = cache.get(uuid);
            if (deque == null) continue;
            if (deque.removeIf(e -> Instant.ofEpochMilli(e.timeMillis()).isBefore(cutoff))) {
                dirty.add(uuid);
            }
        }
        if (!dirty.isEmpty()) flush();
    }

    /**
     * 把脏数据合并进内存配置并异步落盘 | merge dirty entries and persist asynchronously
     */
    private void flush() {
        if (data == null) return;
        Set<UUID> batch = new HashSet<>(dirty);
        if (batch.isEmpty()) return;
        dirty.removeAll(batch);

        Iterator<UUID> it = batch.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            Deque<TeleportLogEntry> deque = cache.get(uuid);
            if (deque == null || deque.isEmpty()) {
                data.set(uuid.toString(), null);
            } else {
                data.set(uuid.toString(), toRawList(deque));
            }
        }

        // 序列化在主线程完成，文件 IO 交给异步线程
        final String dump = data.saveToString();
        plugin.getScheduler().runAsync(() -> writeAtomically(dump));
    }

    private static List<Map<String, Object>> toRawList(Deque<TeleportLogEntry> deque) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (TeleportLogEntry e : deque) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("time", e.timeMillis());
            map.put("type", e.type());
            map.put("from", DataStore.serializeLocation(e.from()));
            map.put("to", DataStore.serializeLocation(e.to()));
            list.add(map);
        }
        return list;
    }

    private void writeAtomically(String dump) {
        try {
            File tmp = new File(dataFile.getPath() + ".tmp");
            Files.writeString(tmp.toPath(), dump);
            try {
                Files.move(tmp.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("[TeleportLog] Failed to save logs: " + e.getMessage());
        }
    }

    public void record(UUID playerId, String type, Location from, Location to) {
        if (!enabled) return;
        if (playerId == null || type == null || from == null || to == null) return;
        String t = type.toLowerCase(Locale.ROOT);
        if (!logTypes.contains(t)) return;

        TeleportLogEntry entry = new TeleportLogEntry(System.currentTimeMillis(), t, from.clone(), to.clone());
        Deque<TeleportLogEntry> deque = cache.computeIfAbsent(playerId, k -> new ConcurrentLinkedDeque<>());
        deque.addFirst(entry);

        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        deque.removeIf(e -> Instant.ofEpochMilli(e.timeMillis()).isBefore(cutoff));
        while (deque.size() > MAX_ENTRIES_PER_PLAYER) {
            deque.removeLast();
        }
        dirty.add(playerId);
    }

    /** 插件关闭时同步落盘 | flush synchronously on shutdown */
    public void shutdown() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        if (data == null) return;
        for (UUID uuid : new HashSet<>(dirty)) {
            Deque<TeleportLogEntry> deque = cache.get(uuid);
            if (deque == null || deque.isEmpty()) data.set(uuid.toString(), null);
            else data.set(uuid.toString(), toRawList(deque));
        }
        dirty.clear();
        writeAtomically(data.saveToString());
    }

    public List<TeleportLogEntry> getLogs(UUID playerId) {
        Deque<TeleportLogEntry> deque = cache.get(playerId);
        if (deque == null) return Collections.emptyList();
        return new ArrayList<>(deque);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object obj) {
        return (Map<String, Object>) obj;
    }
}
