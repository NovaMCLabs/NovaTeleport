package com.bekvon.bukkit.residence;

import com.bekvon.bukkit.residence.protection.ResidenceManager;

/**
 * Minimal API stub for Residence.
 *
 * This module exists only to provide compile-time symbols for optional integrations.
 */
public class Residence {
    private static final Residence INSTANCE = new Residence();

    public static Residence getInstance() {
        return INSTANCE;
    }

    public ResidenceManager getResidenceManager() {
        return new ResidenceManager();
    }
}
