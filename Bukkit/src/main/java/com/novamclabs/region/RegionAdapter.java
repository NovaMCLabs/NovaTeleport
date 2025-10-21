package com.novamclabs.region;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface RegionAdapter {
    String name();
    boolean isPresent();
    boolean canEnter(Player p, Location dest);
}
