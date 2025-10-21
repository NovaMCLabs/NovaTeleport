package com.novamclabs.region.impl;

import com.novamclabs.region.RegionAdapter;
import com.novamclabs.util.RegionGuardUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class ReflectionRegionAdapter implements RegionAdapter {
    @Override public String name() { return "ReflectionRegion"; }
    @Override public boolean isPresent() { return true; }
    @Override public boolean canEnter(Player p, Location dest) { return RegionGuardUtil.canEnter(p, dest); }
}
