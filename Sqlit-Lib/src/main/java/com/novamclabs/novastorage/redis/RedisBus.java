package com.novamclabs.novastorage.redis;

/**
 * 轻量级 Redis 发布/订阅封装（反射加载 Jedis）
 * Lightweight Redis pub/sub wrapper using reflection (Jedis)
 */
public class RedisBus {
    private final String host; private final int port; private final String pass; private final String channel;
    private Object jedisPool;

    public interface Listener { void onMessage(String channel, String message); }

    public RedisBus(String host, int port, String pass, String channel) {
        this.host = host; this.port = port; this.pass = pass; this.channel = channel;
    }

    public boolean start(Listener listener) {
        try {
            Class<?> jedisPoolClz = Class.forName("redis.clients.jedis.JedisPool");
            if (pass == null || pass.isEmpty()) jedisPool = jedisPoolClz.getConstructor(String.class, int.class).newInstance(host, port);
            else jedisPool = jedisPoolClz.getConstructor(String.class, int.class, String.class).newInstance(host, port, pass);
            final Class<?> jedisClz = Class.forName("redis.clients.jedis.Jedis");
            final Class<?> subClz = Class.forName("redis.clients.jedis.JedisPubSub");
            Object sub = java.lang.reflect.Proxy.newProxyInstance(subClz.getClassLoader(), new Class[]{subClz}, (proxy, method, args) -> {
                if (method.getName().equals("onMessage") && args.length == 2) {
                    String ch = (String) args[0]; String msg = (String) args[1]; listener.onMessage(ch, msg);
                }
                return null;
            });
            new Thread(() -> {
                try {
                    Object jedis = jedisClz.cast(jedisPoolClz.getMethod("getResource").invoke(jedisPool));
                    jedisClz.getMethod("subscribe", subClz, String[].class).invoke(jedis, sub, new Object[]{new String[]{channel}});
                } catch (Throwable ignored) {}
            }, "RedisBus-Sub").start();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public void publish(String message) {
        try {
            Class<?> jedisPoolClz = jedisPool.getClass();
            Class<?> jedisClz = Class.forName("redis.clients.jedis.Jedis");
            Object jedis = jedisClz.cast(jedisPoolClz.getMethod("getResource").invoke(jedisPool));
            jedisClz.getMethod("publish", String.class, String.class).invoke(jedis, channel, message);
        } catch (Throwable ignored) {}
    }
}
