package com.massivecraft.factions;

import com.massivecraft.factions.struct.Role;

/**
 * Minimal API stub for FPlayer.
 */
public class FPlayer {
    public boolean hasFaction() {
        return false;
    }

    public Faction getFaction() {
        return null;
    }

    /**
     * In FactionsUUID this is usually the player's UUID string.
     */
    public String getId() {
        return null;
    }

    public Role getRole() {
        return Role.NORMAL;
    }
}
