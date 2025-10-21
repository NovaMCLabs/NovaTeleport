package com.novamclabs.party.adapter.impl;

import com.novamclabs.party.adapter.PartyAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SimpleClansAdapter implements PartyAdapter {
    @Override public String name() { return "SimpleClans"; }
    @Override public boolean isPresent() { return Bukkit.getPluginManager().getPlugin("SimpleClans") != null; }

    @Override
    public PartyInfo getParty(Player player) {
        try {
            Class<?> scClz = Class.forName("net.sacredlabyrinth.phaed.simpleclans.managers.ClanManager");
            Object api = Class.forName("net.sacredlabyrinth.phaed.simpleclans.SimpleClans").getMethod("getInstance").invoke(null);
            Object clanMgr = api.getClass().getMethod("getClanManager").invoke(api);
            Object scPlayer = clanMgr.getClass().getMethod("getClanPlayer", Player.class).invoke(clanMgr, player);
            if (scPlayer == null) return null;
            Object clan = scPlayer.getClass().getMethod("getClan").invoke(scPlayer);
            if (clan == null) return null;
            java.util.Collection<?> membersCol = clan.getClass().getMethod("getMembers").invoke(clan);
            Set<UUID> members = new HashSet<>();
            for (Object m : membersCol) {
                Object u = m.getClass().getMethod("getUniqueId").invoke(m);
                if (u instanceof UUID) members.add((UUID) u);
            }
            // leader: owner or first leader
            UUID leader = player.getUniqueId();
            try {
                Object leaderObj = clan.getClass().getMethod("getLeader").invoke(clan);
                Object u = leaderObj.getClass().getMethod("getUniqueId").invoke(leaderObj);
                if (u instanceof UUID) leader = (UUID) u;
            } catch (Throwable ignored) {}
            return new PartyInfo(leader, members);
        } catch (Throwable ignored) { return null; }
    }

    @Override
    public void register(JavaPlugin plugin, Runnable refresh) {
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler public void onJoin(org.bukkit.event.player.PlayerJoinEvent e){ refresh.run();}
            @org.bukkit.event.EventHandler public void onQuit(org.bukkit.event.player.PlayerQuitEvent e){ refresh.run();}
        }, plugin);
    }
}
