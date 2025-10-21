package com.novamclabs.proxy;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import org.slf4j.Logger;
import javax.inject.Inject;

@Plugin(id = "novateleportproxy", name = "NovaTeleportProxy", version = "1.0.0")
public class NovaTeleportVelocity {
    private final Logger logger;

    @Inject
    public NovaTeleportVelocity(Logger logger) {
        this.logger = logger;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent e) {
        logger.info("NovaTeleportProxy-Velocity enabled.");
    }
}
