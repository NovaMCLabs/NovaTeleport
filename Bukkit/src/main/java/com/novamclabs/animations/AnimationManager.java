package com.novamclabs.animations;

import com.novamclabs.StarTeleport;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AnimationManager implements Listener {
    public enum Style {
        MAGIC,
        TECH,
        NATURAL;
        public static Style fromString(String s, Style def) {
            if (s == null) return def;
            switch (s.toLowerCase()) {
                case "magic": return MAGIC;
                case "tech": return TECH;
                case "natural": return NATURAL;
                default: return def;
            }
        }
        public String key() {
            switch (this) {
                case TECH: return "tech";
                case NATURAL: return "natural";
                default: return "magic";
            }
        }
    }

    private final StarTeleport plugin;
    /** 玩家自己存过的样式 | styles explicitly stored by the player */
    private final Map<UUID, Style> storedStyles = new ConcurrentHashMap<>();
    /** 「查过 YAML，确认没存过样式」的玩家。存的是这个事实而不是解析结果，
     *  否则 /stp reload 改了 animations.default_style 后老玩家会一直用旧默认值 */
    private final Set<UUID> noStoredStyle = ConcurrentHashMap.newKeySet();

    public AnimationManager(StarTeleport plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public Style getStyle(Player player) {
        UUID uuid = player.getUniqueId();
        if (noStoredStyle.contains(uuid)) return getDefaultStyle();
        Style s = storedStyles.get(uuid);
        if (s != null) return s;
        // 未存过样式的玩家也要缓存（查过这一事实），否则倒计时每秒都会重读一次玩家 YAML
        String name = plugin.getDataStore().getPlayerString(uuid, "animation.style");
        Style parsed = Style.fromString(name, null);
        if (parsed == null) {
            noStoredStyle.add(uuid);
            return getDefaultStyle();
        }
        storedStyles.put(uuid, parsed);
        return parsed;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        storedStyles.remove(uuid);
        noStoredStyle.remove(uuid);
    }

    public void setStyle(Player player, Style style) {
        UUID uuid = player.getUniqueId();
        noStoredStyle.remove(uuid);
        storedStyles.put(uuid, style);
        plugin.getDataStore().setPlayerValue(uuid, "animation.style", style.key());
    }

    public Style getDefaultStyle() {
        String s = plugin.getConfig().getString("animations.default_style", "magic");
        return Style.fromString(s, Style.MAGIC);
    }
}
