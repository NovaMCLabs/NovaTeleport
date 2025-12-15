package com.novamclabs.cross;

import com.novamclabs.StarTeleport;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

            if (!subscriberClz.isInterface()) {
                plugin.getLogger().warning("Redis subscribe disabled: JedisPubSub is not an interface; current implementation cannot create a subscriber without a compiled Jedis dependency.");
                return;
            }

            Object sub = java.lang.reflect.Proxy.newProxyInstance(subscriberClz.getClassLoader(), new Class[]{subscriberClz}, (proxy, method, args) -> {
                if (method.getName().equals("onMessage") && args != null && args.length == 2) {
                    String msg = String.valueOf(args[1]);
                    handleMessage(msg);
                }
                return null;
            });

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                Object jedis = null;
                try {
                    jedis = jedisClz.cast(jedisPool.getClass().getMethod("getResource").invoke(jedisPool));
                    for (java.lang.reflect.Method m : jedisClz.getMethods()) {
                        if (!m.getName().equals("subscribe")) continue;
                        if (m.getParameterCount() != 2) continue;
                        m.invoke(jedis, sub, new Object[]{new String[]{channel}});
                        break;
                    }
                } catch (Throwable ignored) {
                } finally {
                    closeQuietly(jedis);
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private void handleMessage(String json) {
        // Simple protocol: {"type":"tpa","target":"<name>","requester":"<name>","here":true/false,"server":"<name>"}
        try {
            if (json == null || json.isBlank()) return;
            if (!json.contains("\"type\":\"tpa\"")) return;

            String server = extractString(json, "server");
            if (server != null && !server.isBlank() && serverName.equalsIgnoreCase(server)) {
                return;
            }

            String target = extractString(json, "target");
            if (target == null || target.isBlank()) return;

            String requester = extractString(json, "requester");
            boolean here = extractBoolean(json, "here", false);

            Player tpTarget = Bukkit.getPlayerExact(target);
            if (tpTarget == null) return;

            // 重用本地 TPA 展示逻辑
            Bukkit.getScheduler().runTask(plugin, () -> {
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
                    tpTarget.sendMessage(plugin.getLang().tr(here ? "tpa.prompt.to_here" : "tpa.prompt.to_you", "requester", requester));
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static String extractString(String json, String key) {
        Pattern p = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : "";
    }

    private static boolean extractBoolean(String json, String key, boolean def) {
        Pattern p = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(true|false)");
        Matcher m = p.matcher(json);
        if (!m.find()) return def;
        return Boolean.parseBoolean(m.group(1));
    }

    private static void closeQuietly(Object obj) {
        if (obj == null) return;
        try {
            if (obj instanceof AutoCloseable) {
                ((AutoCloseable) obj).close();
                return;
            }
            java.lang.reflect.Method close = obj.getClass().getMethod("close");
            close.invoke(obj);
        } catch (Throwable ignored) {
        }
    }

    public void publishTpaRequest(String targetName, String requesterName, boolean here) {
        if (!redisEnabled || jedisPool == null) return;
        String channel = plugin.getConfig().getString("network.redis.channel", "novateleport");
        String payload = String.format("{\"type\":\"tpa\",\"target\":\"%s\",\"requester\":\"%s\",\"here\":%s,\"server\":\"%s\"}",
                targetName, requesterName, here ? "true" : "false", serverName);

        Object jedis = null;
        try {
            Class<?> jedisClz = Class.forName("redis.clients.jedis.Jedis");
            jedis = jedisClz.cast(jedisPool.getClass().getMethod("getResource").invoke(jedisPool));
            jedisClz.getMethod("publish", String.class, String.class).invoke(jedis, channel, payload);
        } catch (Throwable ignored) {
        } finally {
            closeQuietly(jedis);
        }
    }

    // 监听玩家加入以处理待办 | handle pending on join (reserved for future)
    private class JoinListener implements org.bukkit.event.Listener {
        @org.bukkit.event.EventHandler
        public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
            // 可在此查询 Redis 是否有待处理动作 | check pending actions on redis
        }
    }
}
