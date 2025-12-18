package com.novamclabs.guild.impl;

import com.novamclabs.guild.GuildAdapter;
import me.glaremasters.guilds.Guilds;
import me.glaremasters.guilds.api.GuildsAPI;
import me.glaremasters.guilds.guild.Guild;
import me.glaremasters.guilds.guild.GuildMember;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Guilds 插件适配器（使用编译期依赖）
 * Guilds plugin adapter (using compile-time dependency)
 */
public class GuildsPluginAdapter implements GuildAdapter {
    private GuildsAPI api;
    
    @Override
    public String name() {
        return "Guilds";
    }

    @Override
    public boolean isPresent() {
        try {
            if (Bukkit.getPluginManager().getPlugin("Guilds") != null && api == null) {
                api = Guilds.getApi();
            }
            return api != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public String getGuildId(Player player) {
        if (!isPresent()) return null;
        try {
            Guild guild = api.getGuild(player);
            return guild != null ? guild.getId().toString() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public boolean isSameGuild(Player p1, Player p2) {
        if (!isPresent()) return false;
        try {
            Guild g1 = api.getGuild(p1);
            Guild g2 = api.getGuild(p2);
            return g1 != null && g2 != null && g1.getId().equals(g2.getId());
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public List<UUID> getGuildMembers(String guildId) {
        if (!isPresent()) return new ArrayList<>();
        try {
            Guild guild = api.getGuild(UUID.fromString(guildId));
            if (guild == null) return new ArrayList<>();
            
            return guild.getMembers().stream()
                .map(GuildMember::getUuid)
                .collect(Collectors.toList());
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    @Override
    public String getGuildName(String guildId) {
        if (!isPresent()) return null;
        try {
            Guild guild = api.getGuild(UUID.fromString(guildId));
            return guild != null ? guild.getName() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public Location getGuildHome(String guildId) {
        if (!isPresent()) return null;
        try {
            Guild guild = api.getGuild(UUID.fromString(guildId));
            if (guild == null) return null;
            return guild.getHome() != null ? guild.getHome().getAsLocation() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public boolean setGuildHome(String guildId, Location location) {
        if (!isPresent()) return false;
        try {
            Guild guild = api.getGuild(UUID.fromString(guildId));
            if (guild == null) return false;
            
            guild.setHome(new me.glaremasters.guilds.guild.GuildHome(
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
            ));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean isGuildAdmin(Player player) {
        if (!isPresent()) return false;
        try {
            Guild guild = api.getGuild(player);
            if (guild == null) return false;
            
            GuildMember member = guild.getMember(player.getUniqueId());
            if (member == null) return false;
            
            if (guild.isMaster(player)) {
                return true;
            }

            me.glaremasters.guilds.guild.GuildRole role = member.getRole();
            return role != null && (role.isChangeHome() || role.isPromote() || role.isKick());
        } catch (Throwable t) {
            return false;
        }
    }
}
