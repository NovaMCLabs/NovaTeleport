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

            // SaberFactions 的 getFPlayers() 返回 Set，上游 FactionsUUID 返回 List —— 两个分支
            // 的 plugin.yml 都叫 "Factions"，无法同时编译匹配。按 Collection 反射取值，
            // 避免在其中一个分支上抛 NoSuchMethodError 导致成员列表恒为空。
            Object raw = faction.getClass().getMethod("getFPlayers").invoke(faction);
            if (!(raw instanceof java.util.Collection<?> col)) return new ArrayList<>();

            List<UUID> out = new ArrayList<>(col.size());
            for (Object fp : col) {
                try {
                    Object id = fp.getClass().getMethod("getId").invoke(fp);
                    if (id != null) out.add(UUID.fromString(id.toString()));
                } catch (Throwable ignored) {
                    // 单个成员取不到就跳过，不影响其余成员
                }
            }
            return out;
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

            // 角色的枚举类型在两个分支里不同（SaberFactions: struct.Role，含 LEADER；
            // 上游 FactionsUUID: perms.Role，LEADER 改名 ADMIN），按枚举名反射比较更稳。
            Object role = fp.getClass().getMethod("getRole").invoke(fp);
            if (!(role instanceof Enum<?> e)) return false;
            String n = e.name();
            return n.equals("LEADER") || n.equals("ADMIN") || n.equals("COLEADER");
        } catch (Throwable t) {
            return false;
        }
    }
}
