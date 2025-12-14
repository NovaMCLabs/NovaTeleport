package com.novamclabs.log;

import com.novamclabs.StarTeleport;
import com.novamclabs.storage.DataStore;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Teleport log storage + retention.
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

    private final Map<UUID, Deque<TeleportLogEntry>> cache = new ConcurrentHashMap<>();

    public TeleportLogManager(StarTeleport plugin) {
        this.plugin = plugin;
        reload();
        loadData();
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

        for (String uuidStr : data.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                List<Map<?, ?>> entries = data.getMapList(uuidStr);
                Deque<TeleportLogEntry> deque = new ArrayDeque<>();
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
                cache.put(uuid, deque);
            } catch (Exception ignored) {
            }
        }

        trimAll();
        flush();
    }

    private void flush() {
        try {
            data.save(dataFile);
        } catch (Exception ignored) {
        }
    }

    private void trimAll() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        for (UUID uuid : new HashSet<>(cache.keySet())) {
            Deque<TeleportLogEntry> deque = cache.get(uuid);
            if (deque == null) continue;
            deque.removeIf(e -> Instant.ofEpochMilli(e.timeMillis()).isBefore(cutoff));
            writePlayer(uuid, deque);
        }
    }

    public void record(UUID playerId, String type, Location from, Location to) {
        if (!enabled) return;
        if (playerId == null || type == null || from == null || to == null) return;
        String t = type.toLowerCase(Locale.ROOT);
        if (!logTypes.contains(t)) return;

        TeleportLogEntry entry = new TeleportLogEntry(System.currentTimeMillis(), t, from.clone(), to.clone());
        Deque<TeleportLogEntry> deque = cache.computeIfAbsent(playerId, k -> new ArrayDeque<>());
        deque.addFirst(entry);

        // cap in-memory list to avoid unbounded growth; retention still applies.
        while (deque.size() > 200) {
            deque.removeLast();
        }

        trimPlayer(playerId, deque);
        writePlayer(playerId, deque);
        flush();
    }

    private void trimPlayer(UUID playerId, Deque<TeleportLogEntry> deque) {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        deque.removeIf(e -> Instant.ofEpochMilli(e.timeMillis()).isBefore(cutoff));
    }

    private void writePlayer(UUID playerId, Deque<TeleportLogEntry> deque) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (TeleportLogEntry e : deque) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("time", e.timeMillis());
            map.put("type", e.type());
            map.put("from", DataStore.serializeLocation(e.from()));
            map.put("to", DataStore.serializeLocation(e.to()));
            list.add(map);
        }
        data.set(playerId.toString(), list);
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
