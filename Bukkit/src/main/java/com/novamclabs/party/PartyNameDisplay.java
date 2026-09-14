package com.novamclabs.party;

import com.novamclabs.common.scheduler.SchedulerWrapper;
import com.novamclabs.party.adapter.PartyAdapter;
import com.novamclabs.util.BedrockUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 队伍名前缀显示（基于主计分板队伍）。
 *
 * 刷新时只做增量更新：不再每次 unregister 全部 NTP_ 队伍再重建，
 * 否则每个刷新周期所有队伍成员的前缀都会闪一下。
 */
public class PartyNameDisplay {

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

        String leaderPrefix = builtIn != null ? builtIn.getLeaderPrefix()
                : defaultPrefix("party.prefix.leader", PartyManager.FALLBACK_LEADER_PREFIX);
        String memberPrefix = builtIn != null ? builtIn.getMemberPrefix()
                : defaultPrefix("party.prefix.member", PartyManager.FALLBACK_MEMBER_PREFIX);

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
                // 基岩玩家由侧边栏路径单独切换计分板，否则两个路径每轮都会互相覆盖
                if (BedrockUtil.isBedrock(pl)) continue;
                if (scheduler != null) {
                    scheduler.runAtEntity(pl, () -> {
                        if (pl.isOnline() && pl.getScoreboard() != sb) pl.setScoreboard(sb);
                    });
                } else {
                    pl.setScoreboard(sb);
                }
            }
        }

        // 4) 基岩玩家：队伍前缀不渲染，改用侧边栏
        syncBedrockSidebars(desired, leaderPrefix, memberPrefix, scheduler);
    }

    // 基岩版不渲染计分板队伍前缀，Geyser 也不会报错，整个队伍显示会静默失效。
    // 侧边栏在基岩版可见；每个基岩玩家一份私有计分板，避免把侧边栏推给共用主计分板的 Java 玩家。
    private static final Map<UUID, org.bukkit.scoreboard.Scoreboard> BEDROCK_BOARDS = new ConcurrentHashMap<>();
    private static final String BEDROCK_OBJECTIVE = "ntp_party";

    // 本类是纯静态工具，没有插件引用；语言值只能从插件管理器取回插件实例
    private static com.novamclabs.StarTeleport plugin() {
        org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("NovaTeleport");
        return plugin instanceof com.novamclabs.StarTeleport ? (com.novamclabs.StarTeleport) plugin : null;
    }

    private static String defaultPrefix(String key, String fallback) {
        com.novamclabs.StarTeleport plugin = plugin();
        if (plugin == null) return fallback;
        String value = plugin.getLang().t(key);
        return value == null || value.equals(key) ? fallback : value;
    }

    private static String bedrockSidebarTitle() {
        com.novamclabs.StarTeleport plugin = plugin();
        return plugin != null ? plugin.getLang().t("party.bedrock.sidebar") : "§6Party";
    }

    private static void syncBedrockSidebars(Map<String, Desired> desired, String leaderPrefix, String memberPrefix,
                                            SchedulerWrapper scheduler) {
        String sidebarTitle = bedrockSidebarTitle();
        Map<UUID, List<String>> wanted = new HashMap<>();
        for (Desired d : desired.values()) {
            List<String> lines = new ArrayList<>();
            for (UUID u : d.members) {
                Player m = Bukkit.getPlayer(u);
                if (m != null) lines.add((u.equals(d.leader) ? leaderPrefix : memberPrefix) + m.getName());
            }
            if (lines.isEmpty()) continue;
            for (UUID u : d.members) {
                Player m = Bukkit.getPlayer(u);
                if (m != null && BedrockUtil.isBedrock(m)) wanted.put(u, lines);
            }
        }

        // 与计分板队伍一样按期望状态回收：退队/解散/退出服务器后不再重建，避免侧边栏残留
        for (UUID u : new ArrayList<>(BEDROCK_BOARDS.keySet())) {
            if (wanted.containsKey(u)) continue;
            BEDROCK_BOARDS.remove(u);
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            onEntity(scheduler, p, () -> {
                if (p.isOnline()) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            });
        }

        for (Map.Entry<UUID, List<String>> e : wanted.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            List<String> lines = e.getValue();
            onEntity(scheduler, p, () -> {
                if (!p.isOnline()) return;
                org.bukkit.scoreboard.Scoreboard board = BEDROCK_BOARDS.computeIfAbsent(p.getUniqueId(), k -> {
                    org.bukkit.scoreboard.Scoreboard nb = Bukkit.getScoreboardManager().getNewScoreboard();
                    nb.registerNewObjective(BEDROCK_OBJECTIVE, "dummy", sidebarTitle)
                            .setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);
                    return nb;
                });
                org.bukkit.scoreboard.Objective obj = board.getObjective(BEDROCK_OBJECTIVE);
                if (obj == null) return;
                Set<String> want = new HashSet<>(lines);
                for (String entry : new HashSet<>(board.getEntries())) {
                    if (!want.contains(entry)) board.resetScores(entry);
                }
                int score = lines.size();
                for (String line : lines) {
                    org.bukkit.scoreboard.Score sc = obj.getScore(line);
                    if (sc.getScore() != score) sc.setScore(score);
                    score--;
                }
                if (p.getScoreboard() != board) p.setScoreboard(board);
            });
        }
    }

    private static void onEntity(SchedulerWrapper scheduler, Player p, Runnable task) {
        if (scheduler != null) scheduler.runAtEntity(p, task);
        else task.run();
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
