package com.novamclabs.cross;

import com.novamclabs.StarTeleport;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 跨服服务：基于 Redis 发布/订阅转发 TPA 请求与应答。
 * Cross-server service: forwards TPA requests/replies over Redis pub/sub.
 *
 * 说明 | Note: 代理侧无需安装插件，跨服切换由服务端通过 BungeeCord 插件消息通道完成。
 * 使用需在 config.yml 中开启 network.redis.enabled。
 */
public class CrossServerService {
    /** 接收跨服消息（已在主线程）| receives cross-server messages on the main thread */
    public interface MessageHandler {
        void onMessage(String type, Map<String, String> data);
    }

    private final StarTeleport plugin;
    private static final String DEFAULT_SERVER_NAME = "local";
    /** 这些字段可被 {@link #reload()} 改写，因此不能是 final */
    private volatile String serverName;
    private volatile boolean redisEnabled;
    private volatile String channel;
    /** Redis 是否真的可用；由发布结果与周期探活维护 | real reachability, not just pool state */
    private volatile boolean healthy;
    /** 代际号：每次重连自增，旧订阅线程据此退出，避免同一个连接上出现两个订阅者 */
    private volatile int generation;

    private JedisPool pool;
    private JedisPubSub subscription;
    private Thread subscriberThread;
    private volatile MessageHandler handler;
    private com.novamclabs.common.scheduler.SchedulerWrapper.ScheduledTask healthTask;

    public CrossServerService(StarTeleport plugin) {
        this.plugin = plugin;
        readConfig();
        if (redisEnabled) {
            if (DEFAULT_SERVER_NAME.equalsIgnoreCase(serverName)) {
                plugin.getLogger().warning("[CrossServer] network.server_name is still the default \"" + DEFAULT_SERVER_NAME
                        + "\": every server needs a unique name, otherwise cross-server messages are silently dropped.");
            }
            initRedis();
        }
    }

    private void readConfig() {
        this.serverName = plugin.getConfig().getString("network.server_name", DEFAULT_SERVER_NAME);
        this.redisEnabled = plugin.getConfig().getBoolean("network.redis.enabled", false);
        this.channel = plugin.getConfig().getString("network.redis.channel", "novateleport");
    }

    /**
     * 重新读取 network.* 并重连。订阅回调（handler）在重载后必须保留。
     * Reloads network.* and reconnects; the injected message handler must survive.
     */
    public synchronized void reload() {
        close();
        readConfig();
        if (redisEnabled) initRedis();
    }

    private void initRedis() {
        String host = plugin.getConfig().getString("network.redis.host", "127.0.0.1");
        int port = plugin.getConfig().getInt("network.redis.port", 6379);
        String pass = plugin.getConfig().getString("network.redis.password", "");
        try {
            redis.clients.jedis.JedisPoolConfig poolConfig = new redis.clients.jedis.JedisPoolConfig();
            poolConfig.setMaxTotal(4);
            poolConfig.setMaxIdle(2);
            if (pass == null || pass.isEmpty()) {
                this.pool = new JedisPool(poolConfig, host, port);
            } else {
                this.pool = new JedisPool(poolConfig, host, port, 2000, pass);
            }
            // 立即验证连接，避免后续 publish 静默失败
            try (Jedis jedis = pool.getResource()) {
                jedis.ping();
            }
            healthy = true;
            startSubscriber();
            startHealthCheck();
            plugin.getLogger().info("[CrossServer] Redis enabled on " + host + ":" + port + " channel=" + channel);
        } catch (Throwable t) {
            plugin.getLogger().warning("[CrossServer] Redis unavailable (" + t.getMessage() + "), cross-server features disabled.");
            healthy = false;
            closePool();
        }
    }

    /** 周期探活：publish 只在真正发送时才知道 Redis 状态，空闲期间也要能发现掉线 */
    private void startHealthCheck() {
        if (healthTask != null) healthTask.cancel();
        healthTask = plugin.getScheduler().runTimerAsync(this::pingRedis, 30L, 30L, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void pingRedis() {
        JedisPool p = this.pool;
        if (p == null || p.isClosed()) return;
        try (Jedis jedis = p.getResource()) {
            jedis.ping();
            setHealthy(true, null);
        } catch (Throwable t) {
            setHealthy(false, t.getMessage());
        }
    }

    /** 只在状态翻转时输出一行日志，避免刷屏 */
    private void setHealthy(boolean value, String reason) {
        if (this.healthy == value) return;
        this.healthy = value;
        if (value) {
            plugin.getLogger().info("[CrossServer] Redis reachable again, cross-server features enabled.");
        } else {
            plugin.getLogger().warning("[CrossServer] Redis unreachable (" + reason + "), cross-server features disabled until it recovers.");
        }
    }

    private void startSubscriber() {
        final int gen = ++generation;
        Thread thread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                if (gen != generation) return;
                if (pool == null || pool.isClosed()) return;
                JedisPubSub sub = new JedisPubSub() {
                    @Override
                    public void onMessage(String ch, String message) {
                        dispatch(message);
                    }
                };
                this.subscription = sub;
                try (Jedis jedis = pool.getResource()) {
                    jedis.subscribe(sub, channel);
                } catch (Throwable t) {
                    if (gen != generation || pool == null || pool.isClosed()) return;
                    plugin.getLogger().warning("[CrossServer] Subscribe interrupted: " + t.getMessage());
                    try {
                        Thread.sleep(5000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }, "NovaTeleport-RedisSub");
        thread.setDaemon(true);
        this.subscriberThread = thread;
        thread.start();
    }

    private void dispatch(String message) {
        Map<String, String> data = parseJson(message);
        if (data.isEmpty()) return;
        String sender = data.get("server");
        if (sender != null && sender.equalsIgnoreCase(serverName)) return; // 自己发的消息忽略

        handlerLocal(data);
    }

    private void handlerLocal(Map<String, String> data) {
        final MessageHandler h = this.handler;
        if (h == null) return;
        final String type = data.get("type");
        if (type == null) return;
        // 回调可能来自 Redis 订阅线程，必须切回主线程再触碰 Bukkit API
        plugin.getScheduler().runNextTick(() -> {
            try {
                h.onMessage(type, data);
            } catch (Throwable t) {
                plugin.getLogger().warning("[CrossServer] Handler error: " + t.getMessage());
            }
        });
    }

    public void setHandler(MessageHandler handler) {
        this.handler = handler;
    }

    /** Redis 是否可用 | whether Redis transport is up. pool 存在并不代表连接还活着，故还要看探活结果 */
    public boolean isActive() {
        return redisEnabled && healthy && pool != null && !pool.isClosed();
    }

    public String getServerName() {
        return serverName;
    }

    /**
     * 发布一条跨服消息（异步，不阻塞主线程）。
     * @return 是否已提交发送
     */
    public boolean publish(Map<String, String> data) {
        if (!isActive()) return false;
        data.put("server", serverName);
        String payload = toJson(data);
        final String ch = channel;
        plugin.getScheduler().runAsync(() -> {
            JedisPool p = this.pool;
            if (p == null || p.isClosed()) return;
            try (Jedis jedis = p.getResource()) {
                jedis.publish(ch, payload);
                setHealthy(true, null);
            } catch (Throwable t) {
                setHealthy(false, t.getMessage());
            }
        });
        return true;
    }

    public void close() {
        stopSubscriber();
        closePool();
        healthy = false;
        if (healthTask != null) {
            healthTask.cancel();
            healthTask = null;
        }
    }

    /** 停止订阅线程并作废旧代际 | stops the subscriber so a stale thread cannot re-subscribe */
    private void stopSubscriber() {
        generation++;
        try {
            if (subscription != null && subscription.isSubscribed()) subscription.unsubscribe();
        } catch (Throwable ignored) {
        }
        subscription = null;
        Thread t = subscriberThread;
        subscriberThread = null;
        if (t != null) t.interrupt();
    }

    private void closePool() {
        try {
            if (pool != null) pool.close();
        } catch (Throwable ignored) {
        }
        pool = null;
    }

    // ===== 极简 JSON（扁平字符串对象）| minimal flat JSON object codec =====

    static String toJson(Map<String, String> data) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : data.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":\"").append(escape(e.getValue())).append('"');
        }
        return sb.append('}').toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    static Map<String, String> parseJson(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        if (json == null) return out;
        int i = json.indexOf('{');
        if (i < 0) return out;
        i++;
        int n = json.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= n || json.charAt(i) == '}') break;
            if (json.charAt(i) != '"') break;
            StringBuilder key = new StringBuilder();
            i = readString(json, i, key);
            if (i < 0) break;
            while (i < n && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ':')) i++;
            if (i >= n) break;
            String value;
            if (json.charAt(i) == '"') {
                StringBuilder val = new StringBuilder();
                i = readString(json, i, val);
                if (i < 0) break;
                value = val.toString();
            } else {
                int start = i;
                while (i < n && json.charAt(i) != ',' && json.charAt(i) != '}') i++;
                value = json.substring(start, i).trim();
            }
            out.put(key.toString(), value);
            while (i < n && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ',')) i++;
        }
        return out;
    }

    /** 读取一个 JSON 字符串字面量，返回结束引号之后的索引 | returns index after the closing quote */
    private static int readString(String json, int start, StringBuilder out) {
        int i = start + 1;
        int n = json.length();
        while (i < n) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < n) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    default: out.append(next);
                }
                i += 2;
                continue;
            }
            if (c == '"') return i + 1;
            out.append(c);
            i++;
        }
        return -1;
    }

    /** 兼容入口：向目标玩家所在服务器请求转发 TPA | entry point used by the /tpa command */
    public boolean publishTpaRequest(String targetName, String requesterName, boolean here) {
        if (!isActive()) return false;
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", "tpa");
        data.put("target", targetName);
        data.put("requester", requesterName);
        data.put("here", Boolean.toString(here));
        return publish(data);
    }
}
