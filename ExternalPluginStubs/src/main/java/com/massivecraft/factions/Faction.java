package com.massivecraft.factions;

import org.bukkit.Location;

import java.util.Collections;
import java.util.List;

/**
 * Minimal API stub for Faction.
 */
public class Faction {
    public String getId() {
        return null;
    }

    public String getTag() {
        return null;
    }

    public Location getHome() {
        return null;
    }

    public void setHome(Location location) {
    }

    public List<FPlayer> getFPlayers() {
        return Collections.emptyList();
    }
}
