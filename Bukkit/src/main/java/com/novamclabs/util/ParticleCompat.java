package com.novamclabs.util;

import org.bukkit.Particle;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 粒子常量跨版本兼容层。
 *
 * Minecraft 1.20.5 起 Bukkit 把 {@link Particle} 的枚举常量改成了与注册表一致的新名字，
 * 旧名字随之消失。例如：
 * <pre>
 *   旧 (1.20.0–1.20.4)          新 (1.20.5+ / 1.21.x / 26.x)
 *   Particle.VILLAGER_HAPPY  →  Particle.HAPPY_VILLAGER
 *   Particle.REDSTONE        →  Particle.DUST
 *   Particle.ENCHANTMENT_TABLE → Particle.ENCHANT
 *   Particle.EXPLOSION_NORMAL → Particle.POOF
 * </pre>
 * 两个名字都被编译进字节码时，只要有一侧不存在就会抛 {@code NoSuchFieldError}，
 * 且 {@code Particle} 在两侧都是枚举，不能靠类型转换绕过——因此运行期必须按名字解析。
 *
 * 本类只使用 {@code Particle.valueOf(String)}（两侧都存在的枚举方法），
 * 先按新名字查找，失败再回退到旧名字；结果会被缓存。
 */
public final class ParticleCompat {

    /**
     * 新名字 → 旧名字。
     *
     * 每一对都已对着 spigot-api 1.20.1 与 26.2 的 {@code Particle} 枚举逐个核对过
     * （两侧常量名都必须真实存在），不存在的对应关系已剔除。
     */
    private static final Map<String, String> MODERN_TO_LEGACY = new HashMap<>();
    private static final Map<String, String> LEGACY_TO_MODERN = new HashMap<>();

    private static void alias(String legacy, String modern) {
        MODERN_TO_LEGACY.put(modern, legacy);
        // 同一新名字可能对应多个旧名字（如 BLOCK_CRACK / BLOCK_DUST 都变成了 BLOCK），
        // 反向表保留先注册的那个即可
        LEGACY_TO_MODERN.putIfAbsent(legacy, modern);
    }

    static {
        alias("EXPLOSION_NORMAL", "POOF");
        alias("EXPLOSION_LARGE", "EXPLOSION");
        alias("EXPLOSION_HUGE", "EXPLOSION_EMITTER");
        alias("FIREWORKS_SPARK", "FIREWORK");
        alias("WATER_BUBBLE", "BUBBLE");
        alias("WATER_SPLASH", "SPLASH");
        alias("WATER_WAKE", "FISHING");
        alias("SUSPENDED", "UNDERWATER");
        alias("CRIT_MAGIC", "ENCHANTED_HIT");
        alias("SMOKE_NORMAL", "SMOKE");
        alias("SMOKE_LARGE", "LARGE_SMOKE");
        alias("SPELL", "EFFECT");
        alias("SPELL_INSTANT", "INSTANT_EFFECT");
        alias("SPELL_MOB", "ENTITY_EFFECT");
        alias("SPELL_WITCH", "WITCH");
        alias("DRIP_WATER", "DRIPPING_WATER");
        alias("DRIP_LAVA", "DRIPPING_LAVA");
        alias("VILLAGER_ANGRY", "ANGRY_VILLAGER");
        alias("VILLAGER_HAPPY", "HAPPY_VILLAGER");
        alias("TOWN_AURA", "MYCELIUM");
        alias("ENCHANTMENT_TABLE", "ENCHANT");
        alias("REDSTONE", "DUST");
        alias("SNOWBALL", "ITEM_SNOWBALL");
        alias("SNOW_SHOVEL", "ITEM_SNOWBALL");
        alias("SLIME", "ITEM_SLIME");
        alias("ITEM_CRACK", "ITEM");
        alias("BLOCK_CRACK", "BLOCK");
        alias("BLOCK_DUST", "BLOCK");
        alias("WATER_DROP", "RAIN");
        alias("MOB_APPEARANCE", "ELDER_GUARDIAN");
        alias("TOTEM", "TOTEM_OF_UNDYING");
    }

    private static final Map<String, Particle> CACHE = new ConcurrentHashMap<>();
    private static final Particle MISSING = null;

    private ParticleCompat() {
    }

    /**
     * 按「新名字」取粒子，在 1.20.4 及更早的服务端上自动回退到旧名字。
     *
     * @param modernName 1.20.5+ 的常量名（如 {@code "HAPPY_VILLAGER"}）
     * @return 解析到的粒子；两侧都不存在时返回 {@code null}
     */
    public static Particle get(String modernName) {
        if (modernName == null) return null;
        String key = "m:" + modernName;
        if (CACHE.containsKey(key)) return CACHE.get(key);

        Particle result = valueOf(modernName);
        if (result == null) {
            String legacy = MODERN_TO_LEGACY.get(modernName);
            if (legacy != null) result = valueOf(legacy);
        }
        CACHE.put(key, result);
        return result;
    }

    /**
     * 解析玩家/脚本给出的粒子名，新旧两种命名都接受。
     * 先按字面名找（这样脚本里写服务端自己的名字永远有效），再尝试别名换算。
     *
     * @return 解析到的粒子；无法识别时返回 {@code null}
     */
    public static Particle parse(String anyName) {
        if (anyName == null || anyName.isBlank()) return null;

        String key = "p:" + anyName;
        if (CACHE.containsKey(key)) return CACHE.get(key);

        Particle result = valueOf(anyName);
        if (result == null) {
            // 脚本写的是旧名字 → 换算成新名字再试
            String modern = LEGACY_TO_MODERN.get(anyName);
            if (modern != null) result = valueOf(modern);
        }
        if (result == null) {
            // 脚本写的是新名字但服务端是旧版 → 换算成旧名字再试
            String legacy = MODERN_TO_LEGACY.get(anyName);
            if (legacy != null) result = valueOf(legacy);
        }
        CACHE.put(key, result);
        return result;
    }

    private static Particle valueOf(String name) {
        try {
            return Particle.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return MISSING;
        }
    }
}
