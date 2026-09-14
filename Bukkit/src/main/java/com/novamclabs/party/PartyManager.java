package com.novamclabs.party;

import com.novamclabs.StarTeleport;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量级内置组队系统（可选启用）
 * Lightweight built-in party system (optional)
 */
public class PartyManager implements Listener {
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
    /** 语言文件缺失该键时的最终回退（与 PartyNameDisplay 共用同一份，避免两处漂移） */
    static final String FALLBACK_LEADER_PREFIX = "§6[队长] ";
    static final String FALLBACK_MEMBER_PREFIX = "§a[队友] ";
    private String leaderPrefix = FALLBACK_LEADER_PREFIX;
    private String memberPrefix = FALLBACK_MEMBER_PREFIX;

    public PartyManager(StarTeleport plugin) {
        this.plugin = plugin;
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** 下线即退出队伍，否则离队成员的记录会一直留在内存里（领队下线同样解散队伍） */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        remove(event.getPlayer());
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
        leaderPrefix = cfg.getString("display.leader_prefix", plugin.getLang().t("party.prefix.leader"));
        memberPrefix = cfg.getString("display.member_prefix", plugin.getLang().t("party.prefix.member"));
    }

    public int getMaxMembers() { return maxMembers; }
    public int getInviteExpireSeconds() { return inviteExpireSeconds; }
    public int getTeleportDelay() { return teleportDelay; }
    public String getLeaderPrefix() { return leaderPrefix; }
    public String getMemberPrefix() { return memberPrefix; }

    public boolean hasParty(UUID uuid) { return byMember.containsKey(uuid); }
    public Party getParty(UUID uuid) { return byMember.get(uuid); }

    public Party createParty(Player leader) {
        Party p = new Party();
        p.leader = leader.getUniqueId();
        p.members.add(leader.getUniqueId());
        byMember.put(leader.getUniqueId(), p);
        updateNameTags(p);
        return p;
    }

    public void disband(Party p) {
        for (UUID u : new HashSet<>(p.members)) {
            byMember.remove(u);
        }
        clearNameTags(p);
    }

    public boolean addMember(Party p, Player player) {
        if (p.members.size() >= getMaxMembers()) return false;
        p.members.add(player.getUniqueId());
        byMember.put(player.getUniqueId(), p);
        updateNameTags(p);
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
        } else {
            updateNameTags(p);
        }
    }

    // 名字显示系统（Scoreboard）| Name display via Scoreboard
    private void updateNameTags(Party p) {
        org.bukkit.scoreboard.Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        String base = ("NTP_" + p.leader.toString().substring(0, 8)).toUpperCase();
        org.bukkit.scoreboard.Team tl = sb.getTeam(base + "_L");
        if (tl == null) tl = sb.registerNewTeam(base + "_L");
        tl.setPrefix(leaderPrefix);
        org.bukkit.scoreboard.Team tm = sb.getTeam(base + "_M");
        if (tm == null) tm = sb.registerNewTeam(base + "_M");
        tm.setPrefix(memberPrefix);
        // 清空并重新分配 | reset entries and re-add
        for (String e : new java.util.HashSet<>(tl.getEntries())) tl.removeEntry(e);
        for (String e : new java.util.HashSet<>(tm.getEntries())) tm.removeEntry(e);
        for (UUID u : p.members) {
            Player pl = Bukkit.getPlayer(u);
            if (pl == null) continue;
            if (u.equals(p.leader)) tl.addEntry(pl.getName()); else tm.addEntry(pl.getName());
            // Player#setScoreboard 会触碰玩家状态，Folia 上必须在该玩家所属区域执行
            if (pl.getScoreboard() != sb) plugin.getScheduler().runAtEntity(pl, () -> {
                if (pl.isOnline() && pl.getScoreboard() != sb) pl.setScoreboard(sb);
            });
        }
    }

    private void clearNameTags(Party p) {
        org.bukkit.scoreboard.Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        String base = ("NTP_" + p.leader.toString().substring(0, 8)).toUpperCase();
        org.bukkit.scoreboard.Team tl = sb.getTeam(base + "_L");
        org.bukkit.scoreboard.Team tm = sb.getTeam(base + "_M");
        if (tl != null) tl.unregister();
        if (tm != null) tm.unregister();
    }
}
