package com.novamclabs.party.adapter;

import com.novamclabs.party.adapter.impl.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 组队适配器管理器（已移除工会插件，工会功能移至独立的 guild 包）
 * Party adapter manager (guild plugins removed, guild features moved to separate guild package)
 */
public class PartyAdapterManager {
    private final List<PartyAdapter> candidates = new ArrayList<>();
    private PartyAdapter active;

    public PartyAdapterManager() {
        // 只保留纯组队插件，工会插件已移至 guild 包
        // Only keep pure party plugins, guild plugins moved to guild package
        candidates.addAll(Arrays.asList(
                new PartiesAdapter(),
                new BetterTeamsAdapter()
        ));
    }

    public void detectAndRegister(JavaPlugin plugin, Runnable refreshCallback) {
        for (PartyAdapter adapter : candidates) {
            if (adapter.isPresent()) {
                this.active = adapter;
                adapter.register(plugin, refreshCallback);
                plugin.getLogger().info("[Party] Adapter loaded: " + adapter.name());
                break;
            }
        }
        if (active == null) {
            plugin.getLogger().info("[Party] No external party plugin detected, fallback to built-in party.");
        }
    }

    public PartyAdapter getActive() { 
        return active; 
    }
}
