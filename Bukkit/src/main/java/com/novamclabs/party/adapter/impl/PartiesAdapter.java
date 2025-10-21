package com.novamclabs.party.adapter.impl;

import com.novamclabs.party.adapter.PartyAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Parties 适配（优先使用 API，如未找到则反射）
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
            Class<?> apiClz = Class.forName("com.alessiodp.parties.api.PartiesAPI");
            Object api = apiClz.getMethod("getApi").invoke(null);
            Object pp = api.getClass().getMethod("getPartyPlayer", java.util.UUID.class).invoke(api, player.getUniqueId());
            Object party = pp.getClass().getMethod("getParty").invoke(pp);
            if (party == null) return null;
            java.util.List<java.util.UUID> list = (java.util.List<java.util.UUID>) party.getClass().getMethod("getMembersUUID").invoke(party);
            Set<UUID> members = new HashSet<>(list);
            Object leader = party.getClass().getMethod("getLeader").invoke(party);
            UUID lead = leader instanceof UUID ? (UUID) leader : player.getUniqueId();
            return new PartyInfo(lead, members);
        } catch (Throwable ignored) { return null; }
    }

    @Override
    public void register(JavaPlugin plugin, Runnable refresh) {
        // Parties 提供事件，在此通过反射注册监听器（若失败则降级为不注册）
        try {
            Class<?> eventClz = Class.forName("com.alessiodp.parties.api.events.bukkit.player.PlayerJoinPartyEvent");
            Class<?> listenerClz = Class.forName("org.bukkit.event.Listener");
            Object listener = java.lang.reflect.Proxy.newProxyInstance(listenerClz.getClassLoader(), new Class[]{listenerClz}, (p,m,a)->null);
            // 无法直接反射注册匿名监听 handler，这里简化：使用 Bukkit 的通用监听捕捉并刷新
        } catch (Throwable ignored) {}
        // 兜底：玩家加入/退出服务器时刷新
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler public void onJoin(org.bukkit.event.player.PlayerJoinEvent e){ refresh.run();}
            @org.bukkit.event.EventHandler public void onQuit(org.bukkit.event.player.PlayerQuitEvent e){ refresh.run();}
        }, plugin);
    }
}
