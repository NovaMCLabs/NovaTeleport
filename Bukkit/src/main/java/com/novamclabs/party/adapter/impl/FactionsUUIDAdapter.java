package com.novamclabs.party.adapter.impl;

import com.novamclabs.party.adapter.PartyAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class FactionsUUIDAdapter implements PartyAdapter {
    @Override public String name() { return "FactionsUUID"; }
    @Override public boolean isPresent() { return Bukkit.getPluginManager().getPlugin("Factions") != null; }

    @Override
    public PartyInfo getParty(Player player) {
        try {
            // FactionsUUID 兼容：FPlayers.getInstance().getByPlayer(player).getFaction()
            Class<?> fplayers = Class.forName("com.massivecraft.factions.FPlayers");
            Object fps = fplayers.getMethod("getInstance").invoke(null);
            Object fp = fplayers.getMethod("getByPlayer", Player.class).invoke(fps, player);
            Object faction = fp.getClass().getMethod("getFaction").invoke(fp);
            if (faction == null) return null;
            java.util.Collection<?> membersCol = faction.getClass().getMethod("getFPlayers").invoke(faction) instanceof java.util.Collection ? (java.util.Collection<?>) faction.getClass().getMethod("getFPlayers").invoke(faction) : java.util.Collections.emptyList();
            Set<UUID> members = new HashSet<>();
            for (Object m : membersCol) {
                Object u = m.getClass().getMethod("getUuid").invoke(m);
                if (u instanceof UUID) members.add((UUID) u);
            }
            UUID leader = player.getUniqueId();
            try {
                Object leaderObj = faction.getClass().getMethod("getLeader").invoke(faction);
                Object u = leaderObj.getClass().getMethod("getUuid").invoke(leaderObj);
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
