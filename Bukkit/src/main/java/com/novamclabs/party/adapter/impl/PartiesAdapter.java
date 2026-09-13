package com.novamclabs.party.adapter.impl;

import com.alessiodp.parties.api.Parties;
import com.alessiodp.parties.api.interfaces.PartiesAPI;
import com.alessiodp.parties.api.interfaces.Party;
import com.alessiodp.parties.api.interfaces.PartyPlayer;
import com.novamclabs.party.adapter.PartyAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Parties 适配器（编译期依赖，provided）。
 *
 * 之前用反射实现，但把入口类写成了 {@code com.alessiodp.parties.api.PartiesAPI}
 * （真实位置是 {@code api.interfaces.PartiesAPI}），且 {@code getParty()}、
 * {@code getMembersUUID()} 两个方法名都不存在，异常被静默吞掉 → 恒返回 null，
 * 队伍功能完全失效。改为编译期依赖后由编译器保证签名正确。
 */
public class PartiesAdapter implements PartyAdapter {
    @Override public String name() { return "Parties"; }

    @Override
    public boolean isPresent() {
        return Bukkit.getPluginManager().getPlugin("Parties") != null;
    }

    @Override
    public PartyInfo getParty(Player player) {
        try {
            PartiesAPI api = Parties.getApi();
            if (api == null) return null;

            PartyPlayer partyPlayer = api.getPartyPlayer(player.getUniqueId());
            if (partyPlayer == null) return null;

            // Parties 用 partyId 关联队伍，PartyPlayer 本身没有 getParty()
            UUID partyId = partyPlayer.getPartyId();
            if (partyId == null) return null;

            Party party = api.getParty(partyId);
            if (party == null) return null;

            Set<UUID> members = new HashSet<>(party.getMembers());
            UUID leader = party.getLeader();
            return new PartyInfo(leader != null ? leader : player.getUniqueId(), members);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public void register(JavaPlugin plugin, Runnable refresh) {
        // Parties 的队伍变动是普通 Bukkit 事件，但事件类在 parties-api 里（provided），
        // 因此放进静态内部类：只有本方法被调用（即确认 Parties 已安装）时才会加载这些类。
        // 注意不能注册 @EventHandler 的基类 Event —— Bukkit 要求具体事件类型有
        // static getHandlerList()，否则 registerEvents 直接抛 IllegalPluginAccessException。
        Bukkit.getPluginManager().registerEvents(new PartiesListener(refresh), plugin);
    }

    /**
     * Parties 事件监听：加入/离开队伍、队伍解散时刷新队伍名前缀。
     * 单独成类是为了把对 parties-api 类的引用推迟到 Parties 确实存在时。
     */
    private static final class PartiesListener implements org.bukkit.event.Listener {
        private final Runnable refresh;

        PartiesListener(Runnable refresh) {
            this.refresh = refresh;
        }

        @org.bukkit.event.EventHandler
        public void onJoin(com.alessiodp.parties.api.events.bukkit.player.BukkitPartiesPlayerPostJoinEvent e) {
            refresh.run();
        }

        @org.bukkit.event.EventHandler
        public void onLeave(com.alessiodp.parties.api.events.bukkit.player.BukkitPartiesPlayerPostLeaveEvent e) {
            refresh.run();
        }

        @org.bukkit.event.EventHandler
        public void onDelete(com.alessiodp.parties.api.events.bukkit.party.BukkitPartiesPartyPostDeleteEvent e) {
            refresh.run();
        }
    }
}
