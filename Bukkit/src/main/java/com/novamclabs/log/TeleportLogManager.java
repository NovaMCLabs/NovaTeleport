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
    /**
     * data（YamlConfiguration 内部是非并发 Map）与 cache 的整体重置只能单线程执行：
     * flush 在全局线程、flushSync/loadData 在区域线程、record 在玩家区域线程，
     * 三者交叉会丢记录甚至抛 ConcurrentModificationException。
     */
    private final Object stateLock = new Object();
    /** 落盘串行化并用序号保证「最后一次写入胜出」，关服时的同步落盘不会被在途的旧 dump 覆盖 */
    private final Object writeLock = new Object();
    private long dumpSeq = 0;
    private long persistedSeq = -1;
    private SchedulerWrapper.ScheduledTask flushTask;

    /** 每个玩家保留的最大条数 | max entries kept in memory per player */
    private static final int MAX_ENTRIES_PER_PLAYER = 200;
    /**
     * 内存里最多保留多少个玩家的日志条目。只是缓存上限，磁盘上的记录不受影响
     * （只在 cache 超限时逐出，record 走的路径不会因为达到这个数就把记录丢掉）。
     * | memory-only cap; an evicted player's on-disk entries are left untouched
     */
    private static final int MAX_CACHED_PLAYERS = 2000;

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
        // 先同步落盘，否则 loadData() 里的 cache.clear() 会静默丢弃尚未写出的记录
        flushSync();
        loadData();
    }

    /** 同步落盘所有脏数据，不经过异步通道（关服 / 重载前调用）| synchronously persist dirty entries */
    private void flushSync() {
        final String dump;
        final long seq;
        synchronized (stateLock) {
            if (data == null) return;
            for (UUID uuid : new HashSet<>(dirty)) {
                Deque<TeleportLogEntry> deque = cache.get(uuid);
                if (deque == null || deque.isEmpty()) data.set(uuid.toString(), null);
                else data.set(uuid.toString(), toRawList(deque));
            }
            dirty.clear();
            dump = data.saveToString();
            seq = ++dumpSeq;
        }
        // 同步写：序号更大，在途的旧 dump 会让位
        persist(dump, seq);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getRewindPermission() {
        return rewindPermission;
    }

    private void loadData() {
        synchronized (stateLock) {
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
    }

    /**
     * 把脏数据合并进内存配置并异步落盘 | merge dirty entries and persist asynchronously
     */
    private void flush() {
        final String dump;
        final long seq;
        synchronized (stateLock) {
            if (data == null) return;
            Set<UUID> batch = new HashSet<>(dirty);
            if (batch.isEmpty()) return;
            dirty.removeAll(batch);

            for (UUID uuid : batch) {
                Deque<TeleportLogEntry> deque = cache.get(uuid);
                if (deque == null || deque.isEmpty()) {
                    data.set(uuid.toString(), null);
                } else {
                    data.set(uuid.toString(), toRawList(deque));
                }
            }

            // 序列化必须在持锁时完成，否则别的线程会一边改 data 一边被序列化
            dump = data.saveToString();
            seq = ++dumpSeq;
        }

        // 文件 IO 交给异步线程
        plugin.getScheduler().runAsync(() -> persist(dump, seq));
    }

    /** 写盘入口：串行化并丢弃比已落盘版本更旧的 dump | serialise writes, stale dumps lose */
    private void persist(String dump, long seq) {
        synchronized (writeLock) {
            if (seq < persistedSeq) return;
            persistedSeq = seq;
            writeAtomically(dump);
        }
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
        synchronized (stateLock) {
            Deque<TeleportLogEntry> deque = cache.computeIfAbsent(playerId, k -> new ConcurrentLinkedDeque<>());
            deque.addFirst(entry);

            Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
            deque.removeIf(e -> Instant.ofEpochMilli(e.timeMillis()).isBefore(cutoff));
            while (deque.size() > MAX_ENTRIES_PER_PLAYER) {
                deque.removeLast();
            }
            dirty.add(playerId);
            // 记录本身永远不删（/tplog rewind 要靠它回溯），但内存缓存必须限容：
            // cache 只对最近见过的 UUID 保留条目，否则一个从不再回服的玩家会把整份记录一直压在堆里。
            // 逐出后磁盘上的内容不变，retention 仍由 flush 时的时间裁剪负责。
            evictIfOverCapacity(playerId);
        }
    }

    /**
     * cache 达到上限时逐出「缓存条目最多」的玩家（当前玩家除外）。
     * 调用方已持有 stateLock，所以与 flush/loadData 互斥：不会出现「刚把 cache 项置空、flush 又把它写回磁盘」的竞争。
     */
    private void evictIfOverCapacity(UUID current) {
        if (cache.size() <= MAX_CACHED_PLAYERS) return;
        int evicted = 0;
        while (cache.size() > MAX_CACHED_PLAYERS) {
            UUID victim = null;
            int worst = -1;
            for (Map.Entry<UUID, Deque<TeleportLogEntry>> e : cache.entrySet()) {
                if (e.getKey().equals(current)) continue;
                int n = e.getValue().size();
                if (n > worst) { worst = n; victim = e.getKey(); }
            }
            if (victim == null) break;
            cache.remove(victim);
            // 不能再改动 dirty：这里碰 data 会触发 YamlConfiguration 的惰性序列化，而此刻可能正持有 dirLock，
            // 有死锁风险。逐出只丢内存副本，磁盘内容与下次 flush 都保持原样。
            evicted++;
            if (evicted >= 64) break; // 单次逐出上限，避免 O(n^2) 卡住调用线程
        }
    }

    /** 插件关闭时同步落盘 | flush synchronously on shutdown */
    public void shutdown() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        flushSync();
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
