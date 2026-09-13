package com.novamclabs.party;

import com.novamclabs.common.scheduler.SchedulerWrapper;
import com.novamclabs.party.adapter.PartyAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * 队伍名前缀显示（基于主计分板队伍）。
 *
 * 刷新时只做增量更新：不再每次 unregister 全部 NTP_ 队伍再重建，
 * 否则每个刷新周期所有队伍成员的前缀都会闪一下。
 */
public class PartyNameDisplay {
    private static final String LEADER_PREFIX = "§6[队长] ";
    private static final String MEMBER_PREFIX = "§a[队友] ";

    public static void refreshAll(PartyAdapter adapter) {
        refreshAll(adapter, null, null);
    }

    public static void refreshAll(PartyAdapter adapter, PartyManager builtIn) {
        refreshAll(adapter, builtIn, null);
    }

    /**
     * 刷新所有队伍名前缀。
     *
     * 若同时存在外部组队适配器与内置组队系统，两者都必须参与计算：
     * 否则每次刷新都会把内置队伍（同样以 NTP_ 开头）当成“过期队伍”删掉。
     *
     * 计分板队伍的增删改在调用线程完成；而给玩家指定计分板（Player#setScoreboard）
     * 会触碰玩家实体状态，在 Folia 上必须调度到该玩家所属区域，因此通过 scheduler 执行。
     */
    public static void refreshAll(PartyAdapter adapter, PartyManager builtIn, SchedulerWrapper scheduler) {
        if (adapter == null && builtIn == null) return;
        org.bukkit.scoreboard.Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();

        String leaderPrefix = builtIn != null ? builtIn.getLeaderPrefix() : LEADER_PREFIX;
        String memberPrefix = builtIn != null ? builtIn.getMemberPrefix() : MEMBER_PREFIX;

        // 期望状态：队伍名 -> (队长 UUID, 成员集合)
        Map<String, Desired> desired = new LinkedHashMap<>();

        if (adapter != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                PartyAdapter.PartyInfo info = adapter.getParty(p);
                if (info == null || info.members == null || info.members.isEmpty()) continue;
                Desired d = desired.computeIfAbsent(teamBase(info.leader), k -> new Desired(info.leader));
                d.members.addAll(info.members);
            }
        }

        if (builtIn != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                PartyManager.Party party = builtIn.getParty(p.getUniqueId());
                if (party == null) continue;
                Desired d = desired.computeIfAbsent(teamBase(party.leader), k -> new Desired(party.leader));
                d.members.addAll(party.members);
            }
        }

        // 1) 移除已不存在的队伍
        for (org.bukkit.scoreboard.Team t : new ArrayList<>(sb.getTeams())) {
            String name = t.getName();
            if (!name.startsWith("NTP_")) continue;
            String base = name.endsWith("_L") || name.endsWith("_M") ? name.substring(0, name.length() - 2) : name;
            if (!desired.containsKey(base)) t.unregister();
        }

        // 2) 增量更新
        for (Map.Entry<String, Desired> e : desired.entrySet()) {
            Desired d = e.getValue();
            org.bukkit.scoreboard.Team tl = team(sb, e.getKey() + "_L");
            org.bukkit.scoreboard.Team tm = team(sb, e.getKey() + "_M");
            if (!leaderPrefix.equals(tl.getPrefix())) tl.setPrefix(leaderPrefix);
            if (!memberPrefix.equals(tm.getPrefix())) tm.setPrefix(memberPrefix);

            Set<String> wantLeader = new HashSet<>();
            Set<String> wantMember = new HashSet<>();
            for (UUID u : d.members) {
                Player pl = Bukkit.getPlayer(u);
                if (pl == null) continue;
                if (u.equals(d.leader)) wantLeader.add(pl.getName());
                else wantMember.add(pl.getName());
            }
            syncEntries(tl, wantLeader);
            syncEntries(tm, wantMember);
        }

        // 3) 让成员看主计分板（仅在需要时切换，避免破坏其他计分板插件）
        for (Desired d : desired.values()) {
            for (UUID u : d.members) {
                Player pl = Bukkit.getPlayer(u);
                if (pl == null || pl.getScoreboard() == sb) continue;
                if (scheduler != null) {
                    scheduler.runAtEntity(pl, () -> {
                        if (pl.isOnline() && pl.getScoreboard() != sb) pl.setScoreboard(sb);
                    });
                } else {
                    pl.setScoreboard(sb);
                }
            }
        }
    }

    private static void syncEntries(org.bukkit.scoreboard.Team team, Set<String> wanted) {
        for (String e : new HashSet<>(team.getEntries())) {
            if (!wanted.contains(e)) team.removeEntry(e);
        }
        for (String e : wanted) {
            if (!team.hasEntry(e)) team.addEntry(e);
        }
    }

    private static org.bukkit.scoreboard.Team team(org.bukkit.scoreboard.Scoreboard sb, String name) {
        org.bukkit.scoreboard.Team t = sb.getTeam(name);
        return t != null ? t : sb.registerNewTeam(name);
    }

    private static String teamBase(UUID leader) {
        String id = leader == null ? "unknown" : leader.toString();
        return ("NTP_" + id.substring(0, Math.min(8, id.length()))).toUpperCase(Locale.ROOT);
    }

    private static final class Desired {
        final UUID leader;
        final Set<UUID> members = new HashSet<>();

        Desired(UUID leader) {
            this.leader = leader;
        }
    }
}
