package com.novamclabs.party.adapter.impl;

import com.novamclabs.party.adapter.PartyAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * BetterTeams 适配
 */
public class BetterTeamsAdapter implements PartyAdapter {
    @Override public String name() { return "BetterTeams"; }
    @Override public boolean isPresent() { return Bukkit.getPluginManager().getPlugin("BetterTeams") != null; }

    @Override
    public PartyInfo getParty(Player player) {
        try {
            Class<?> apiClz = Class.forName("com.booksaw.betterTeams.Team");
            // Team.getTeam(Player)
            Object team = Class.forName("com.booksaw.betterTeams.TeamManager").getMethod("getTeam", Player.class).invoke(null, player);
            if (team == null) return null;
            // team.getMembers() 返回 List<TeamPlayer>，取 UUID
            java.util.Collection<?> membersCol = (java.util.Collection<?>) team.getClass().getMethod("getMembers").invoke(team);
            Set<UUID> members = new HashSet<>();
            for (Object tp : membersCol) {
                Object uuid = tp.getClass().getMethod("getUUID").invoke(tp);
                if (uuid instanceof UUID) members.add((UUID) uuid);
            }
            // leader
            Object leaderObj = team.getClass().getMethod("getOwner").invoke(team);
            UUID leader = leaderObj instanceof UUID ? (UUID) leaderObj : player.getUniqueId();
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
