package com.novamclabs.animations;

import com.novamclabs.StarTeleport;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AnimationManager {
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
    private final Map<UUID, Style> styles = new ConcurrentHashMap<>();

    public AnimationManager(StarTeleport plugin) {
        this.plugin = plugin;
    }

    public Style getStyle(Player player) {
        Style s = styles.get(player.getUniqueId());
        if (s != null) return s;
        // 从玩家数据文件读取
        File f = new File(new File(plugin.getDataFolder(), "data/players"), player.getUniqueId() + ".yml");
        if (f.exists()) {
            YamlConfiguration cfg = new YamlConfiguration();
            try { cfg.load(f); } catch (Exception ignored) {}
            String name = cfg.getString("animation.style");
            if (name != null) {
                s = Style.fromString(name, getDefaultStyle());
                styles.put(player.getUniqueId(), s);
                return s;
            }
        }
        return getDefaultStyle();
    }

    public void setStyle(Player player, Style style) {
        styles.put(player.getUniqueId(), style);
        // 写入玩家数据文件
        File dir = new File(plugin.getDataFolder(), "data/players");
        if (!dir.exists()) dir.mkdirs();
        File f = new File(dir, player.getUniqueId() + ".yml");
        YamlConfiguration cfg = new YamlConfiguration();
        if (f.exists()) {
            try { cfg.load(f);} catch (Exception ignored) {}
        }
        cfg.set("animation.style", style.key());
        try { cfg.save(f);} catch (IOException ignored) {}
    }

    public Style getDefaultStyle() {
        String s = plugin.getConfig().getString("animations.default_style", "magic");
        return Style.fromString(s, Style.MAGIC);
    }
}
