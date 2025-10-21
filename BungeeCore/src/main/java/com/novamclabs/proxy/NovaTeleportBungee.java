package com.novamclabs.proxy;

import net.md_5.bungee.api.plugin.Plugin;

/**
 * NovaTeleport 代理侧（Bungee）占位与 Redis 桥接（可选）
 * Bungee-side helper for NovaTeleport (optional Redis bridge)
 */
public class NovaTeleportBungee extends Plugin {
    @Override
    public void onEnable() {
        getLogger().info("NovaTeleportProxy-Bungee enabled.");
        // 若需要可在此加载 Redis 总线并转发跨服消息 | Hook Redis bus here if needed
    }

    @Override
    public void onDisable() {
        getLogger().info("NovaTeleportProxy-Bungee disabled.");
    }
}
