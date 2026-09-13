package com.novamclabs.proxy;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

/**
 * Velocity 侧占位插件。
 *
 * 跨服切换由子服通过 BungeeCord 插件消息通道完成（Velocity 需在 velocity.toml 中开启 bungeecord = true），
 * 因此这里不需要任何功能代码，仅保留可扩展的插件骨架：
 * 需要代理侧逻辑（如跨服排队、服务器分发）时，在 {@link #onInit} 中注册事件监听即可。
 *
 * 注意：Velocity 4 使用 com.google.inject.Inject（javax.inject 已不再随 API 提供）。
 */
@Plugin(id = "novateleportproxy", name = "NovaTeleportProxy", version = "1.0.0")
public class NovaTeleportVelocity {
    private final ProxyServer server;
    private final Logger logger;

    @Inject
    public NovaTeleportVelocity(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent e) {
        logger.info("NovaTeleportProxy enabled on Velocity {} (server-side BungeeCord channel is used for cross-server switches).",
                server.getVersion().getVersion());
    }
}
