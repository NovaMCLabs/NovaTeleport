package com.novamclabs.party.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 外部组队插件适配（Parties/MMOCore 等，基于反射，无编译依赖）
 * External party adapters (reflection based, no compile deps)
 */
public class PartyBridge {
    public static class PartyInfo {
        public Set<UUID> members = new HashSet<>();
        public UUID leader;
    }

    public static PartyInfo findFor(Player p) {
        PartyInfo info = tryParties(p);
        if (info != null) return info;
        info = tryMMOCore(p);
        if (info != null) return info;
        return null;
    }

    private static PartyInfo tryParties(Player p) {
        try {
            if (Bukkit.getPluginManager().getPlugin("Parties") == null) return null;
            Class<?> apiClz = Class.forName("com.alessiodp.parties.api.PartiesAPI");
            Object api = apiClz.getMethod("getApi").invoke(null);
            Object pp = api.getClass().getMethod("getPartyPlayer", java.util.UUID.class).invoke(api, p.getUniqueId());
            Object party = pp.getClass().getMethod("getParty").invoke(pp);
            if (party == null) return null;
            PartyInfo out = new PartyInfo();
            java.util.List<java.util.UUID> list = (java.util.List<java.util.UUID>) party.getClass().getMethod("getMembersUUID").invoke(party);
            out.members.addAll(list);
            Object leader = party.getClass().getMethod("getLeader").invoke(party);
            if (leader instanceof java.util.UUID) out.leader = (UUID) leader;
            return out;
        } catch (Throwable ignored) { return null; }
    }

    private static PartyInfo tryMMOCore(Player p) {
        try {
            if (Bukkit.getPluginManager().getPlugin("MMOCore") == null) return null;
            Class<?> pd = Class.forName("net.Indyuce.mmocore.api.player.PlayerData");
            Object data = pd.getMethod("get", java.util.UUID.class).invoke(null, p.getUniqueId());
            Object party = data.getClass().getMethod("getParty").invoke(data);
            if (party == null) return null;
            PartyInfo out = new PartyInfo();
            java.util.Collection<?> members = (java.util.Collection<?>) party.getClass().getMethod("getOnlineMembers").invoke(party);
            for (Object m : members) {
                Object uuid = m.getClass().getMethod("getUniqueId").invoke(m);
                if (uuid instanceof java.util.UUID) out.members.add((UUID) uuid);
            }
            // 无可用 leader API 时回退：自己是 leader | fallback leader self
            out.leader = p.getUniqueId();
            return out;
        } catch (Throwable ignored) { return null; }
    }
}
