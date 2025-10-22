package com.novamclabs.party.adapter.impl;

import com.alessiodp.parties.api.PartiesAPI;
import com.alessiodp.parties.api.interfaces.Party;
import com.alessiodp.parties.api.interfaces.PartyPlayer;
import com.novamclabs.party.adapter.PartyAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Parties 适配（直接使用官方 API）
 */
public class PartiesAdapter implements PartyAdapter {
    @Override public String name() { return "Parties"; }

    @Override
    public boolean isPresent() {
        return Bukkit.getPluginManager().getPlugin("Parties") != null;
    }

    @Override
    public PartyInfo getParty(Player player) {
        try {
            PartiesAPI api = PartiesAPI.getApi();
            if (api == null) return null;
            PartyPlayer pp = api.getPartyPlayer(player.getUniqueId());
            if (pp == null) return null;
            Party party = pp.getParty();
            if (party == null) return null;
            Set<UUID> members = new HashSet<>(party.getMembersUUID());
            UUID leader = party.getLeader();
            if (leader == null) leader = player.getUniqueId();
            return new PartyInfo(leader, members);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public void register(JavaPlugin plugin, Runnable refresh) {
        // 兜底：玩家加入/退出服务器时刷新
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler public void onJoin(org.bukkit.event.player.PlayerJoinEvent e){ refresh.run();}
            @org.bukkit.event.EventHandler public void onQuit(org.bukkit.event.player.PlayerQuitEvent e){ refresh.run();}
        }, plugin);
    }
}
