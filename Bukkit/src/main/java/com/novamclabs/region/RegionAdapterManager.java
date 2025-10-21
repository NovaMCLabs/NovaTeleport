package com.novamclabs.region;

import com.novamclabs.region.impl.ReflectionRegionAdapter;

public class RegionAdapterManager {
    private final RegionAdapter adapter = new ReflectionRegionAdapter();
    public RegionAdapter get() { return adapter; }
}
