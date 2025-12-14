package com.massivecraft.factions;

import org.bukkit.entity.Player;

/**
 * Minimal API stub for FPlayers.
 */
public class FPlayers {
    private static final FPlayers INSTANCE = new FPlayers();

    public static FPlayers getInstance() {
        return INSTANCE;
    }

    public FPlayer getByPlayer(Player player) {
        return null;
    }
}
