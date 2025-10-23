package com.novamclabs.guild.impl;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;
import com.novamclabs.guild.GuildAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * FactionsUUID 适配器（使用编译期依赖）
 * FactionsUUID adapter (using compile-time dependency)
 */
public class FactionsUUIDAdapter implements GuildAdapter {
    
    @Override
    public String name() {
        return "FactionsUUID";
    }

    @Override
    public boolean isPresent() {
        try {
            return Bukkit.getPluginManager().getPlugin("Factions") != null
                && Factions.getInstance() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public String getGuildId(Player player) {
        if (!isPresent()) return null;
        try {
            FPlayer fp = FPlayers.getInstance().getByPlayer(player);
            if (fp == null || !fp.hasFaction()) return null;
            return fp.getFaction().getId();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public boolean isSameGuild(Player p1, Player p2) {
        if (!isPresent()) return false;
        try {
            FPlayer fp1 = FPlayers.getInstance().getByPlayer(p1);
            FPlayer fp2 = FPlayers.getInstance().getByPlayer(p2);
            if (fp1 == null || fp2 == null) return false;
            if (!fp1.hasFaction() || !fp2.hasFaction()) return false;
            return fp1.getFaction().equals(fp2.getFaction());
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public List<UUID> getGuildMembers(String guildId) {
        if (!isPresent()) return new ArrayList<>();
        try {
            Faction faction = Factions.getInstance().getFactionById(guildId);
            if (faction == null) return new ArrayList<>();
            return faction.getFPlayers().stream()
                .map(fp -> UUID.fromString(fp.getId()))
                .collect(Collectors.toList());
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    @Override
    public String getGuildName(String guildId) {
        if (!isPresent()) return null;
        try {
            Faction faction = Factions.getInstance().getFactionById(guildId);
            return faction != null ? faction.getTag() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public Location getGuildHome(String guildId) {
        if (!isPresent()) return null;
        try {
            Faction faction = Factions.getInstance().getFactionById(guildId);
            return faction != null ? faction.getHome() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public boolean setGuildHome(String guildId, Location location) {
        if (!isPresent()) return false;
        try {
            Faction faction = Factions.getInstance().getFactionById(guildId);
            if (faction == null) return false;
            faction.setHome(location);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean isGuildAdmin(Player player) {
        if (!isPresent()) return false;
        try {
            FPlayer fp = FPlayers.getInstance().getByPlayer(player);
            if (fp == null || !fp.hasFaction()) return false;
            return fp.getRole().isAtLeast(com.massivecraft.factions.struct.Role.COLEADER);
        } catch (Throwable t) {
            return false;
        }
    }
}
