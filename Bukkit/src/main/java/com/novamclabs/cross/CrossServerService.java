package com.novamclabs.cross;

import com.novamclabs.StarTeleport;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跨服服务整合（Redis 可选，用于 TPA/待处理任务广播）
 * Cross-server service (optional Redis for TPA/pending actions)
 */
public class CrossServerService {
    private final StarTeleport plugin;
    private final String serverName;
    private final boolean redisEnabled;

    public CrossServerService(StarTeleport plugin) {
        this.plugin = plugin;
        this.serverName = plugin.getConfig().getString("network.server_name", "local");
        this.redisEnabled = plugin.getConfig().getBoolean("network.redis.enabled", false);
        if (redisEnabled) initRedis();
        Bukkit.getPluginManager().registerEvents(new JoinListener(), plugin);
    }

    private Object jedisPool; // 通过反射持有 | hold via reflection

    private void initRedis() {
        try {
            Class<?> jedisPoolClz = Class.forName("redis.clients.jedis.JedisPool");
            String host = plugin.getConfig().getString("network.redis.host", "127.0.0.1");
            int port = plugin.getConfig().getInt("network.redis.port", 6379);
            String pass = plugin.getConfig().getString("network.redis.password", "");
            if (pass == null || pass.isEmpty()) {
                jedisPool = jedisPoolClz.getConstructor(String.class, int.class).newInstance(host, port);
            } else {
                jedisPool = jedisPoolClz.getConstructor(String.class, int.class, String.class).newInstance(host, port, pass);
            }
            startSubscriber();
        } catch (Throwable t) {
            plugin.getLogger().warning("Redis unavailable, cross-server features limited");
        }
    }

    private void startSubscriber() {
        try {
            final String channel = plugin.getConfig().getString("network.redis.channel", "novateleport");
            Class<?> jedisClz = Class.forName("redis.clients.jedis.Jedis");
            Class<?> subscriberClz = Class.forName("redis.clients.jedis.JedisPubSub");
            Object sub = java.lang.reflect.Proxy.newProxyInstance(subscriberClz.getClassLoader(), new Class[]{subscriberClz}, (proxy, method, args) -> {
                if (method.getName().equals("onMessage") && args.length == 2) {
                    String msg = (String) args[1];
                    handleMessage(msg);
                }
                return null;
            });
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    Object jedis = jedisClz.cast(jedisPool.getClass().getMethod("getResource").invoke(jedisPool));
                    jedisClz.getMethod("subscribe", subscriberClz, String[].class).invoke(jedis, sub, new Object[]{new String[]{channel}});
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    private void handleMessage(String json) {
        // 兼容简单协议：type:tpa,target:<name>,requester:<name>,here:true/false
        try {
            if (!json.contains("\"type\":\"tpa\"")) return;
            String target = extract(json, "target");
            String requester = extract(json, "requester");
            boolean here = Boolean.parseBoolean(extract(json, "here"));
            Player tpTarget = Bukkit.getPlayerExact(target);
            if (tpTarget == null) return;
            // 重用本地 TPA 展示逻辑
            Bukkit.getScheduler().runTask(plugin, () -> {
                com.novamclabs.util.BedrockUtil.isBedrock(tpTarget);
                boolean sent = com.novamclabs.util.BedrockFormsUtil.showTpaRequestForm(plugin, tpTarget, requester, here);
                if (!sent) {
                    net.md_5.bungee.api.chat.TextComponent yes = new net.md_5.bungee.api.chat.TextComponent(plugin.getLang().t("tpa.click.accept"));
                    yes.setColor(net.md_5.bungee.api.ChatColor.GREEN);
                    yes.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/tpaccept"));
                    net.md_5.bungee.api.chat.TextComponent no = new net.md_5.bungee.api.chat.TextComponent(plugin.getLang().t("tpa.click.deny"));
                    no.setColor(net.md_5.bungee.api.ChatColor.RED);
                    no.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/tpdeny"));
                    net.md_5.bungee.api.chat.TextComponent spacer = new net.md_5.bungee.api.chat.TextComponent(" ");
                    tpTarget.spigot().sendMessage(yes, spacer, no);
                    tpTarget.sendMessage(plugin.getLang().tr(here?"tpa.prompt.to_here":"tpa.prompt.to_you", "requester", requester));
                }
            });
        } catch (Throwable ignored) {}
    }

    private String extract(String json, String key) {
        int i = json.indexOf('"' + key + '"');
        if (i < 0) return "";
        int colon = json.indexOf(':', i);
        int start = json.indexOf('"', colon + 1) + 1;
        int end = json.indexOf('"', start);
        if (start < 1 || end < 0) return "";
        return json.substring(start, end);
    }

    public void publishTpaRequest(String targetName, String requesterName, boolean here) {
        if (!redisEnabled || jedisPool == null) return;
        String channel = plugin.getConfig().getString("network.redis.channel", "novateleport");
        String payload = String.format("{\"type\":\"tpa\",\"target\":\"%s\",\"requester\":\"%s\",\"here\":%s,\"server\":\"%s\"}",
                targetName, requesterName, here?"true":"false", serverName);
        try {
            Class<?> jedisClz = Class.forName("redis.clients.jedis.Jedis");
            Object jedis = jedisClz.cast(jedisPool.getClass().getMethod("getResource").invoke(jedisPool));
            jedisClz.getMethod("publish", String.class, String.class).invoke(jedis, channel, payload);
        } catch (Throwable ignored) {}
    }

    // 监听玩家加入以处理待办 | handle pending on join (reserved for future)
    private class JoinListener implements org.bukkit.event.Listener {
        @org.bukkit.event.EventHandler
        public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
            // 可在此查询 Redis 是否有待处理动作 | check pending actions on redis
        }
    }
}
