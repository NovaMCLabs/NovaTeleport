package com.novamclabs.party.adapter;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;

/**
 * 队伍系统适配器接口 | Party System Adapter Interface
 */
public interface PartyAdapter {
    class PartyInfo {
        public UUID leader;
        public Set<UUID> members;
        public PartyInfo(UUID leader, Set<UUID> members) { this.leader = leader; this.members = members; }
    }

    String name();
    boolean isPresent();
    PartyInfo getParty(Player player);

    /**
     * 注册必要的监听（插件创建/解散/加入/离开等事件）
     * 注册后触发 refreshCallback.run() 用于刷新名字显示等
     */
    void register(JavaPlugin plugin, Runnable refreshCallback);
}
