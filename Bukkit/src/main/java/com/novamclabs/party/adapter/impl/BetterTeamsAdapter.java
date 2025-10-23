package com.novamclabs.party.adapter.impl;

import com.booksaw.betterTeams.Team;
import com.booksaw.betterTeams.TeamManager;
import com.booksaw.betterTeams.TeamPlayer;
import com.novamclabs.party.adapter.PartyAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * BetterTeams 适配器（使用编译期依赖，替代反射）
 * BetterTeams adapter (using compile-time dependency instead of reflection)
 */
public class BetterTeamsAdapter implements PartyAdapter {
    
    @Override 
    public String name() { 
        return "BetterTeams"; 
    }
    
    @Override 
    public boolean isPresent() { 
        try {
            return Bukkit.getPluginManager().getPlugin("BetterTeams") != null
                && TeamManager.class != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public PartyInfo getParty(Player player) {
        if (!isPresent()) return null;
        
        try {
            Team team = TeamManager.getTeam(player);
            if (team == null) return null;
            
            // 获取队伍成员
            Set<UUID> members = new HashSet<>();
            for (TeamPlayer tp : team.getMembers()) {
                members.add(tp.getUUID());
            }
            
            // 获取队长
            UUID leader = team.getOwner();
            if (leader == null) {
                leader = player.getUniqueId();
            }
            
            return new PartyInfo(leader, members);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public void register(JavaPlugin plugin, Runnable refresh) {
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler 
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) { 
                refresh.run();
            }
            
            @org.bukkit.event.EventHandler 
            public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) { 
                refresh.run();
            }
        }, plugin);
    }
}
