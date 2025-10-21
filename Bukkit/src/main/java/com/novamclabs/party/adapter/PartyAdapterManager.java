package com.novamclabs.party.adapter;

import com.novamclabs.party.adapter.impl.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PartyAdapterManager {
    private final List<PartyAdapter> candidates = new ArrayList<>();
    private PartyAdapter active;

    public PartyAdapterManager() {
        candidates.addAll(Arrays.asList(
                new PartiesAdapter(),
                new BetterTeamsAdapter(),
                new SimpleClansAdapter(),
                new FactionsUUIDAdapter(),
                new GuildsAdapter()
        ));
    }

    public void detectAndRegister(JavaPlugin plugin, Runnable refreshCallback) {
        for (PartyAdapter adapter : candidates) {
            if (adapter.isPresent()) {
                this.active = adapter;
                adapter.register(plugin, refreshCallback);
                plugin.getLogger().info("Party adapter loaded: " + adapter.name());
                break;
            }
        }
        if (active == null) plugin.getLogger().info("No external party plugin detected, fallback to built-in party.");
    }

    public PartyAdapter getActive() { return active; }
}
