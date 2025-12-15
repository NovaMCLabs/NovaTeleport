package com.massivecraft.factions;

/**
 * Minimal API stub for FactionsUUID.
 */
public class Factions {
    private static final Factions INSTANCE = new Factions();

    public static Factions getInstance() {
        return INSTANCE;
    }

    public Faction getFactionById(String id) {
        return null;
    }
}
