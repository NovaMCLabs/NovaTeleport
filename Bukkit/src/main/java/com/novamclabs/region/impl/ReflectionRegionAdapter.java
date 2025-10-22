package com.novamclabs.region.impl;

import com.novamclabs.region.RegionAdapter;
import com.novamclabs.util.RegionGuardUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * 保留的区域检查适配器（委托 RegionGuardUtil），不包含反射调用。
 */
public class ReflectionRegionAdapter implements RegionAdapter {
    @Override public String name() { return "RegionGuard"; }
    @Override public boolean isPresent() { return true; }
    @Override public boolean canEnter(Player p, Location dest) { return RegionGuardUtil.canEnter(p, dest); }
}
