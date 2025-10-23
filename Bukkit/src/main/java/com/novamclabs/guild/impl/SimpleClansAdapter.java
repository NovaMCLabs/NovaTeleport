package com.novamclabs.guild.impl;

import com.novamclabs.guild.GuildAdapter;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * SimpleClans 适配器（使用编译期依赖）
 * SimpleClans adapter (using compile-time dependency)
 */
public class SimpleClansAdapter implements GuildAdapter {
    private SimpleClans simpleClans;
    
    @Override
    public String name() {
        return "SimpleClans";
    }

    @Override
    public boolean isPresent() {
        try {
            if (Bukkit.getPluginManager().getPlugin("SimpleClans") != null && simpleClans == null) {
                simpleClans = SimpleClans.getInstance();
            }
            return simpleClans != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public String getGuildId(Player player) {
        if (!isPresent()) return null;
        try {
            ClanPlayer cp = simpleClans.getClanManager().getClanPlayer(player);
            if (cp == null || cp.getClan() == null) return null;
            return cp.getClan().getTag();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public boolean isSameGuild(Player p1, Player p2) {
        if (!isPresent()) return false;
        try {
            ClanPlayer cp1 = simpleClans.getClanManager().getClanPlayer(p1);
            ClanPlayer cp2 = simpleClans.getClanManager().getClanPlayer(p2);
            if (cp1 == null || cp2 == null) return false;
            Clan c1 = cp1.getClan();
            Clan c2 = cp2.getClan();
            return c1 != null && c2 != null && c1.equals(c2);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public List<UUID> getGuildMembers(String guildId) {
        if (!isPresent()) return new ArrayList<>();
        try {
            Clan clan = simpleClans.getClanManager().getClan(guildId);
            if (clan == null) return new ArrayList<>();
            return clan.getMembers().stream()
                .map(cp -> cp.getUniqueId())
                .collect(Collectors.toList());
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    @Override
    public String getGuildName(String guildId) {
        if (!isPresent()) return null;
        try {
            Clan clan = simpleClans.getClanManager().getClan(guildId);
            return clan != null ? clan.getName() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public Location getGuildHome(String guildId) {
        if (!isPresent()) return null;
        try {
            Clan clan = simpleClans.getClanManager().getClan(guildId);
            return clan != null ? clan.getHomeLocation() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public boolean setGuildHome(String guildId, Location location) {
        if (!isPresent()) return false;
        try {
            Clan clan = simpleClans.getClanManager().getClan(guildId);
            if (clan == null) return false;
            clan.setHomeLocation(location);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean isGuildAdmin(Player player) {
        if (!isPresent()) return false;
        try {
            ClanPlayer cp = simpleClans.getClanManager().getClanPlayer(player);
            if (cp == null || cp.getClan() == null) return false;
            return cp.isLeader() || cp.getClan().getLeaders().contains(cp);
        } catch (Throwable t) {
            return false;
        }
    }
}
