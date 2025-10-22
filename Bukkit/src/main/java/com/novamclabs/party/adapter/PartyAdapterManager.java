package com.novamclabs.party.adapter;

import com.novamclabs.party.adapter.impl.PartiesAdapter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class PartyAdapterManager {
    private PartyAdapter active;

    public void detectAndRegister(JavaPlugin plugin, Runnable refreshCallback) {
        // 仅在检测到目标插件时懒加载对应适配器，避免可选依赖的类加载问题
        if (Bukkit.getPluginManager().getPlugin("Parties") != null) {
            try {
                PartiesAdapter adapter = new PartiesAdapter();
                if (adapter.isPresent()) {
                    this.active = adapter;
                    adapter.register(plugin, refreshCallback);
                    plugin.getLogger().info("Party adapter loaded: " + adapter.name());
                }
            } catch (Throwable ignored) {}
        }
        if (active == null) plugin.getLogger().info("No external party plugin detected, fallback to built-in party.");
    }

    public PartyAdapter getActive() { return active; }
}
