package com.novamclabs.novastorage.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

/**
 * 轻量级 Redis 发布/订阅封装
 */
public class RedisBus {
    private final String host; private final int port; private final String pass; private final String channel;

    public interface Listener { void onMessage(String channel, String message); }

    public RedisBus(String host, int port, String pass, String channel) {
        this.host = host; this.port = port; this.pass = pass; this.channel = channel;
    }

    public boolean start(Listener listener) {
        try {
            new Thread(() -> {
                try (Jedis jedis = new Jedis(host, port)) {
                    if (pass != null && !pass.isEmpty()) jedis.auth(pass);
                    jedis.subscribe(new JedisPubSub() {
                        @Override
                        public void onMessage(String ch, String msg) {
                            listener.onMessage(ch, msg);
                        }
                    }, channel);
                } catch (Throwable ignored) {}
            }, "RedisBus-Sub").start();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public void publish(String message) {
        try (Jedis jedis = new Jedis(host, port)) {
            if (pass != null && !pass.isEmpty()) jedis.auth(pass);
            jedis.publish(channel, message);
        } catch (Throwable ignored) {}
    }
}
