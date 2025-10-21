package com.novamclabs.party.adapter.impl;

import com.novamclabs.party.adapter.PartyAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class GuildsAdapter implements PartyAdapter {
    @Override public String name() { return "Guilds"; }
    @Override public boolean isPresent() { return Bukkit.getPluginManager().getPlugin("Guilds") != null; }

    @Override
    public PartyInfo getParty(Player player) {
        try {
            // me.glaremasters.guilds.guild.GuildsAPI.getGuild(player)
            Class<?> apiClz = Class.forName("me.glaremasters.guilds.api.GuildsAPI");
            Object api = apiClz.getMethod("getGuildsAPI").invoke(null);
            Object guild = apiClz.getMethod("getGuild", Player.class).invoke(api, player);
            if (guild == null) return null;
            java.util.Collection<?> membersCol = (java.util.Collection<?>) guild.getClass().getMethod("getMembers").invoke(guild);
            Set<UUID> members = new HashSet<>();
            for (Object m : membersCol) {
                Object u = m.getClass().getMethod("getUniqueId").invoke(m);
                if (u instanceof UUID) members.add((UUID) u);
            }
            UUID leader = player.getUniqueId();
            try {
                Object leaderObj = guild.getClass().getMethod("getGuildMaster").invoke(guild);
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
