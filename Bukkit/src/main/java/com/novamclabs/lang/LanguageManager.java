package com.novamclabs.lang;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class LanguageManager {
    private final JavaPlugin plugin;
    private String locale;
    private YamlConfiguration langCfg;

    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void ensureDefaults(String... locales) {
        for (String l : locales) {
            String path = "langs/" + l + ".yml";
            File out = new File(plugin.getDataFolder(), path);
            if (!out.exists()) {
                out.getParentFile().mkdirs();
                try (InputStream in = plugin.getResource(path)) {
                    if (in != null) {
                        Files.copy(in, out.toPath());
                    }
                } catch (IOException ignored) {}
            }
        }
    }

    public void load(String locale) {
        this.locale = locale;
        String path = "langs/" + locale + ".yml";
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            ensureDefaults(locale);
        }
        this.langCfg = new YamlConfiguration();
        try {
            this.langCfg.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            this.langCfg = new YamlConfiguration();
        }
    }

    public String getLocale() { return locale; }

    public String t(String key) {
        if (langCfg == null) return key;
        String s = langCfg.getString(key);
        if (s == null) return key;
        return s;
    }

    public String tr(String key, Object... args) {
        String s = t(key);
        if (args != null && args.length >= 2) {
            for (int i = 0; i + 1 < args.length; i += 2) {
                String k = String.valueOf(args[i]);
                String v = String.valueOf(args[i + 1]);
                s = s.replace("{" + k + "}", v);
            }
        }
        return s;
    }
}
