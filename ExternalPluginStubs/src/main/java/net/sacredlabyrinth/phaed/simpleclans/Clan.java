package net.sacredlabyrinth.phaed.simpleclans;

import org.bukkit.Location;

import java.util.Collections;
import java.util.List;

/**
 * Minimal API stub for Clan.
 */
public class Clan {
    public String getTag() {
        return null;
    }

    public String getName() {
        return null;
    }

    public List<ClanPlayer> getMembers() {
        return Collections.emptyList();
    }

    public List<ClanPlayer> getLeaders() {
        return Collections.emptyList();
    }

    public Location getHomeLocation() {
        return null;
    }

    public void setHomeLocation(Location location) {
    }
}
