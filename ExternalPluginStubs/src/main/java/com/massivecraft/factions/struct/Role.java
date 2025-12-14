package com.massivecraft.factions.struct;

/**
 * Minimal API stub for Factions Role.
 */
public enum Role {
    NORMAL,
    MODERATOR,
    COLEADER,
    LEADER;

    public boolean isAtLeast(Role other) {
        return this.ordinal() >= other.ordinal();
    }
}
