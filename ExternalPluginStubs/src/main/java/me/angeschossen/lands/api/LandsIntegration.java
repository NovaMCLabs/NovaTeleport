package me.angeschossen.lands.api;

import me.angeschossen.lands.api.land.Area;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

public class LandsIntegration {

    public static LandsIntegration of(Plugin plugin) {
        return new LandsIntegration();
    }

    public Area getArea(Location location) {
        return null;
    }
}
