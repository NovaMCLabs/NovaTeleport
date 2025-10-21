package com.novamclabs.party;

import com.novamclabs.party.adapter.PartyAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class PartyNameDisplay {
    public static void refreshAll(PartyAdapter adapter) {
        if (adapter == null) return;
        org.bukkit.scoreboard.Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        // 清理旧团队（仅清理以 NTP_ 前缀的队伍）
        for (org.bukkit.scoreboard.Team t : sb.getTeams()) {
            if (t.getName().startsWith("NTP_")) t.unregister();
        }
        Map<UUID, Set<UUID>> parties = new HashMap<>();
        Map<UUID, UUID> leaderOf = new HashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PartyAdapter.PartyInfo info = adapter.getParty(p);
            if (info == null || info.members == null || info.members.isEmpty()) continue;
            parties.computeIfAbsent(info.leader, k -> new HashSet<>()).addAll(info.members);
            leaderOf.put(p.getUniqueId(), info.leader);
        }
        for (Map.Entry<UUID, Set<UUID>> e : parties.entrySet()) {
            UUID leader = e.getKey();
            String base = ("NTP_" + leader.toString().substring(0, 8)).toUpperCase();
            org.bukkit.scoreboard.Team tl = sb.getTeam(base + "_L");
            if (tl == null) tl = sb.registerNewTeam(base + "_L");
            tl.setPrefix("§6[队长] ");
            org.bukkit.scoreboard.Team tm = sb.getTeam(base + "_M");
            if (tm == null) tm = sb.registerNewTeam(base + "_M");
            tm.setPrefix("§a[队友] ");
            for (UUID u : e.getValue()) {
                Player pl = Bukkit.getPlayer(u);
                if (pl == null) continue;
                if (u.equals(leader)) tl.addEntry(pl.getName()); else tm.addEntry(pl.getName());
                pl.setScoreboard(sb);
            }
        }
    }
}
