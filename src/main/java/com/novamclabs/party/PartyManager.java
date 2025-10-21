package com.novamclabs.party;

import com.novamclabs.StarTeleport;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量级内置组队系统（可选启用）
 * Lightweight built-in party system (optional)
 */
public class PartyManager {
    public static class Party {
        public UUID leader;
        public final Set<UUID> members = new HashSet<>();
        public long createdAt = System.currentTimeMillis();
    }

    private final StarTeleport plugin;
    private final Map<UUID, Party> byMember = new ConcurrentHashMap<>();
    private int maxMembers = 4;
    private int inviteExpireSeconds = 120;
    private int teleportDelay = 5;

    public PartyManager(StarTeleport plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        java.io.File f = new java.io.File(plugin.getDataFolder(), "party.yml");
        if (!f.exists()) {
            try {
                f.getParentFile().mkdirs();
                java.nio.file.Files.write(f.toPath(), (
                        "# Party settings | 组队设置\n" +
                        "max_members: 4\n" +
                        "invite_expire_seconds: 120\n" +
                        "teleport_delay: 5\n"
                ).getBytes());
            } catch (Exception ignored) {}
        }
        org.bukkit.configuration.file.YamlConfiguration cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
        maxMembers = cfg.getInt("max_members", 4);
        inviteExpireSeconds = cfg.getInt("invite_expire_seconds", 120);
        teleportDelay = cfg.getInt("teleport_delay", 5);
    }

    public int getMaxMembers() { return maxMembers; }
    public int getInviteExpireSeconds() { return inviteExpireSeconds; }
    public int getTeleportDelay() { return teleportDelay; }

    public boolean hasParty(UUID uuid) { return byMember.containsKey(uuid); }
    public Party getParty(UUID uuid) { return byMember.get(uuid); }

    public Party createParty(Player leader) {
        Party p = new Party();
        p.leader = leader.getUniqueId();
        p.members.add(leader.getUniqueId());
        byMember.put(leader.getUniqueId(), p);
        return p;
    }

    public void disband(Party p) {
        for (UUID u : new HashSet<>(p.members)) {
            byMember.remove(u);
        }
    }

    public boolean addMember(Party p, Player player) {
        if (p.members.size() >= getMaxMembers()) return false;
        p.members.add(player.getUniqueId());
        byMember.put(player.getUniqueId(), p);
        return true;
    }

    public boolean isLeader(UUID uuid) {
        Party p = getParty(uuid);
        return p != null && p.leader.equals(uuid);
    }

    public void remove(Player player) {
        Party p = getParty(player.getUniqueId());
        if (p == null) return;
        p.members.remove(player.getUniqueId());
        byMember.remove(player.getUniqueId());
        if (p.members.isEmpty() || p.leader.equals(player.getUniqueId())) {
            // 领队离开则解散 | disband on leader leave
            disband(p);
        }
    }
}
