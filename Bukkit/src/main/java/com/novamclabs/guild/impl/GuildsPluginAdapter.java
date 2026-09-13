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

            if (guild.isMaster(player)) {
                return true;
            }

            GuildMember member = guild.getMember(player.getUniqueId());
            if (member == null) return false;

            Object role = member.getRole();
            if (role == null) return false;

            // Guilds 3.5.3.x 的 GuildRole 有 isChangeHome/isPromote/isKick 三个开关；
            // 新版（3.5.7+）把它们换成了 hasPerm(GuildRolePerm)，直接调用会抛 NoSuchMethodError
            // 并被外层 catch 吞掉 → 副会长全部失去权限。因此这里用反射探测，两个形态都兼容。
            Boolean legacy = anyRoleFlag(role, "isChangeHome", "isPromote", "isKick");
            if (legacy != null) return legacy;

            Boolean modern = anyRolePerm(role, "CHANGE_HOME", "PROMOTE", "KICK");
            if (modern != null) return modern;

            // 两种形态都认不出来时只认会长：宁可少授权，不能误授权
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 旧版 GuildRole 的布尔开关，任一为 true 即视为管理员 | any legacy role flag set */
    private Boolean anyRoleFlag(Object role, String... methodNames) {
        boolean found = false;
        for (String m : methodNames) {
            try {
                Object r = role.getClass().getMethod(m).invoke(role);
                found = true;
                if (Boolean.TRUE.equals(r)) return Boolean.TRUE;
            } catch (NoSuchMethodException ignored) {
                // 该方法在这个版本不存在
            } catch (Throwable ignored) {
                return null;
            }
        }
        return found ? Boolean.FALSE : null;
    }

    /** 新版 GuildRole#hasPerm(GuildRolePerm)，按枚举常量名探测 | modern hasPerm(GuildRolePerm) */
    private Boolean anyRolePerm(Object role, String... permNames) {
        Class<?> permEnum;
        try {
            permEnum = Class.forName("me.glaremasters.guilds.guild.GuildRolePerm");
        } catch (Throwable t) {
            return null;
        }
        java.lang.reflect.Method hasPerm;
        try {
            hasPerm = role.getClass().getMethod("hasPerm", permEnum);
        } catch (Throwable t) {
            return null;
        }
        boolean found = false;
        for (String name : permNames) {
            Object constant;
            try {
                constant = Enum.valueOf(permEnum.asSubclass(Enum.class), name);
            } catch (Throwable t) {
                continue;
            }
            found = true;
            try {
                if (Boolean.TRUE.equals(hasPerm.invoke(role, constant))) return Boolean.TRUE;
            } catch (Throwable t) {
                return null;
            }
        }
        return found ? Boolean.FALSE : null;
    }
}
