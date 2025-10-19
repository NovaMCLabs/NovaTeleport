package com.novamclabs.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

public class BedrockUtil {
    public static boolean isBedrock(Player player) {
        try {
            if (Bukkit.getPluginManager().getPlugin("floodgate") == null) return false;
            Class<?> apiClz = Class.forName("floodgate.api.FloodgateApi");
            Method inst = apiClz.getMethod("getInstance");
            Object api = inst.invoke(null);
            Method isFg = apiClz.getMethod("isFloodgatePlayer", UUID.class);
            return (boolean) isFg.invoke(api, player.getUniqueId());
        } catch (Throwable ignored) {
            return false;
        }
    }
}
