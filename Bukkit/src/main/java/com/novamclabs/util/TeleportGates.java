package com.novamclabs.util;

import com.novamclabs.StarTeleport;
import org.bukkit.entity.Player;

/**
 * 传送前置校验（战斗标签 / 冷却）的公共入口。
 * 本地传送在倒计时发起前调用，跨服传送在 connect 前调用——两者必须走同一套判断。
 * Shared entry point for the combat-tag and cooldown gates so local and cross-server
 * teleports cannot diverge.
 */
public final class TeleportGates {

    private TeleportGates() {
    }

    /** 战斗标签 + 冷却的放行判断；不通过时已向玩家发送原因 */
    public static boolean passes(StarTeleport plugin, Player player, String type) {
        com.novamclabs.combat.CombatManager combat = plugin.getCombatManager();
        if (combat != null) {
            long remaining = combat.remainingSeconds(player, type);
            if (remaining > 0) {
                player.sendMessage(plugin.getLang().tr("combat.tagged", "seconds", remaining));
                return false;
            }
        }
        com.novamclabs.cooldown.CooldownManager cooldowns = plugin.getCooldownManager();
        if (cooldowns != null) {
            long remaining = cooldowns.remainingSeconds(player, type);
            if (remaining > 0) {
                player.sendMessage(plugin.getLang().tr("cooldown.active", "seconds", remaining));
                return false;
            }
        }
        return true;
    }

    /** 跨服传送发出后登记冷却（本地传送在 TeleportUtil.execute 里登记） */
    public static void record(StarTeleport plugin, Player player, String type) {
        com.novamclabs.cooldown.CooldownManager cooldowns = plugin.getCooldownManager();
        if (cooldowns != null) cooldowns.record(player, type);
    }
}
