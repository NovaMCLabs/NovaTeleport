package com.novamclabs.party.adapter;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * 组队适配器管理器（已移除工会插件，工会功能移至独立的 guild 包）
 * Party adapter manager (guild plugins removed, guild features moved to separate guild package)
 */
public class PartyAdapterManager {

    /**
     * 按类名反射构造：适配器字节码直接引用对应插件类型，插件缺席时 JVM 链接该类会抛
     * NoClassDefFoundError。若在同一个表达式里一次性构造，一个插件缺席就会连累后面的适配器，
     * 因此逐个隔离。
     */
    private static final String[] ADAPTER_CLASSES = {
            "com.novamclabs.party.adapter.impl.PartiesAdapter",
            "com.novamclabs.party.adapter.impl.BetterTeamsAdapter",
    };

    private final List<PartyAdapter> candidates = new ArrayList<>();
    private PartyAdapter active;

    public PartyAdapterManager() {
        for (String className : ADAPTER_CLASSES) {
            try {
                Object instance = Class.forName(className).getDeclaredConstructor().newInstance();
                candidates.add((PartyAdapter) instance);
            } catch (Throwable ignored) {
                // 对应插件未安装/未启用
            }
        }
    }

    public void detectAndRegister(JavaPlugin plugin, Runnable refreshCallback) {
        for (PartyAdapter adapter : candidates) {
            if (adapter.isPresent()) {
                try {
                    adapter.register(plugin, refreshCallback);
                } catch (Throwable t) {
                    // 适配器注册失败不能让插件整体加载失败（例如事件类型不合法会抛
                    // IllegalPluginAccessException），记录后回退到内置组队
                    plugin.getLogger().warning("[Party] " + adapter.name()
                            + " adapter failed to register, falling back to built-in party: "
                            + t.getClass().getSimpleName());
                    continue;
                }
                this.active = adapter;
                plugin.getLogger().info("[Party] Adapter loaded: " + adapter.name());
                break;
            }
        }
        if (active == null) {
            plugin.getLogger().info("[Party] No external party plugin detected, fallback to built-in party.");
        }
    }

    public PartyAdapter getActive() { 
        return active; 
    }
}
