package com.novamclabs.region.impl;

import com.novamclabs.region.RegionAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Residence 领地适配器。
 *
 * Residence 存在两条并存的血脉，API 形状不同，无法用编译期依赖同时匹配：
 * <ul>
 *   <li><b>现代版</b>（Zrips 维护，6.x，JitPack 有构件）：静态 {@code getInstance()} +
 *       <b>实例</b> {@code getResidenceManager()}</li>
 *   <li><b>旧版</b>（bekvon 2.6.x，2019）：没有 {@code getInstance()}，
 *       {@code getResidenceManager()} 是<b>静态</b>方法</li>
 * </ul>
 * 两者都叫 {@code com.bekvon.bukkit.residence.Residence}，靠 invokevirtual / invokestatic
 * 的差异区分——编译期选定一种就会在另一种上抛 IncompatibleClassChangeError，
 * 因此这里全部走反射，两种都能用。
 */
public class ResidenceAdapter implements RegionAdapter {

    /** 解析一次后缓存，避免每次传送都做 Class.forName 与反射查找 */
    private volatile Object cachedManager;
    private volatile Method cachedPlayerHas;

    @Override
    public String name() {
        return "Residence";
    }

    @Override
    public boolean isPresent() {
        try {
            return Bukkit.getPluginManager().getPlugin("Residence") != null
                && resolveManager() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 取得 ResidenceManager（已缓存），兼容现代版的「静态 getInstance + 实例方法」与旧版的静态方法 */
    private Object resolveManager() throws Exception {
        Object cached = cachedManager;
        if (cached != null) return cached;

        Class<?> residenceClass = Class.forName("com.bekvon.bukkit.residence.Residence");
        Method accessor = residenceClass.getMethod("getResidenceManager");

        Object manager;
        if (java.lang.reflect.Modifier.isStatic(accessor.getModifiers())) {
            // 旧版：静态访问器
            manager = accessor.invoke(null);
        } else {
            // 现代版：先取插件实例，再取管理器
            Object instance;
            try {
                instance = residenceClass.getMethod("getInstance").invoke(null);
            } catch (NoSuchMethodException e) {
                // 没有 getInstance 又非静态访问器时，退回到 Bukkit 的插件实例
                instance = Bukkit.getPluginManager().getPlugin("Residence");
            }
            manager = instance == null ? null : accessor.invoke(instance);
        }
        cachedManager = manager;
        return manager;
    }

    @Override
    public boolean canEnter(Player p, Location dest) {
        if (!isPresent()) return true;

        try {
            Object manager = resolveManager();
            if (manager == null) return true;

            Object residence = manager.getClass()
                    .getMethod("getByLoc", Location.class)
                    .invoke(manager, dest);
            if (residence == null) {
                // 不在领地内，允许
                // Not in a residence, allow
                return true;
            }

            Object perms = residence.getClass().getMethod("getPermissions").invoke(residence);
            if (perms == null) return true;

            // Residence 没有独立注册的 "enter" 标志（未注册的标志会直接返回传入的默认值，
            // 等于检查恒为通过）；官方注册的传送权限标志是 "tp"。
            return playerHas(perms, p.getName(), "tp");
        } catch (Throwable t) {
            com.novamclabs.region.RegionAdapterManager.logOnce(name(), t);
            return true;
        }
    }

    private boolean playerHas(Object perms, String playerName, String flagName) throws Exception {
        Method cached = cachedPlayerHas;
        if (cached == null) {
            cached = resolvePlayerHas(perms);
            cachedPlayerHas = cached;
        }
        Object flagArg = cached.getParameterTypes()[1] == String.class
                ? flagName
                : resolveFlagConstant(cached.getParameterTypes()[1], flagName);
        if (flagArg == null) return true;
        return (Boolean) cached.invoke(perms, playerName, flagArg, true);
    }

    /**
     * 选择 {@code playerHas} 重载：优先 {@code (String, Flags, boolean)}（6.x 推荐），
     * 没有 {@code Flags} 容器时退回 {@code (String, String, boolean)}（旧版按标志名匹配）。
     */
    private Method resolvePlayerHas(Object perms) throws NoSuchMethodException {
        try {
            Class<?> flagsClass = Class.forName("com.bekvon.bukkit.residence.containers.Flags");
            return perms.getClass().getMethod("playerHas", String.class, flagsClass, boolean.class);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return perms.getClass().getMethod("playerHas", String.class, String.class, boolean.class);
        }
    }

    private Object resolveFlagConstant(Class<?> flagsClass, String flagName) {
        for (Object constant : flagsClass.getEnumConstants()) {
            if (((Enum<?>) constant).name().equalsIgnoreCase(flagName)) return constant;
        }
        return null;
    }
}
