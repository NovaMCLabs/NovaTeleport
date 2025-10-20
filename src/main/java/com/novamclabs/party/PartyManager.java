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

    public PartyManager(StarTeleport plugin) {
        this.plugin = plugin;
    }

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
        if (p.members.size() >= plugin.getConfig().getInt("party.max_members", 4)) return false;
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
