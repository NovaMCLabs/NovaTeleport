package com.novamclabs.lang;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LanguageManager {
    /** Ultimate fallback when the active locale has no value for a key. */
    private static final String DEFAULT_LOCALE = "en_US";
    /** Cap on how many distinct missing keys we are willing to log. */
    private static final int MAX_MISSING_KEY_LOGS = 256;

    private final JavaPlugin plugin;
    private final Map<String, Map<String, String>> resourceSnapshots = new ConcurrentHashMap<>();
    private final Set<String> loggedMissingKeys = ConcurrentHashMap.newKeySet();

    private String locale;
    private YamlConfiguration langCfg;
    /** Bundled defaults for the active locale, used for keys absent from the owner's file. */
    private volatile Map<String, String> localeFallback = Collections.emptyMap();
    /** Bundled defaults of {@link #DEFAULT_LOCALE}, tried last. */
    private volatile Map<String, String> defaultLocaleFallback = Collections.emptyMap();

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
        // Existing installations keep their old lang files, so keys added by a newer
        // version are missing from disk. Resolve those from the bundled resources at
        // lookup time instead of rewriting the owner's (possibly customized) file.
        this.localeFallback = snapshot(locale);
        this.defaultLocaleFallback = DEFAULT_LOCALE.equals(locale)
                ? Collections.emptyMap() : snapshot(DEFAULT_LOCALE);
        this.langCfg = new YamlConfiguration();
        try {
            this.langCfg.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("Failed to load language file " + path + ": " + e.getMessage()
                    + " - falling back to bundled defaults.");
            this.langCfg = new YamlConfiguration();
        }
    }

    private Map<String, String> snapshot(String locale) {
        return resourceSnapshots.computeIfAbsent(locale, l -> {
            Map<String, String> map = new LinkedHashMap<>();
            try (InputStream in = plugin.getResource("langs/" + l + ".yml")) {
                if (in != null) {
                    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(
                            new InputStreamReader(in, StandardCharsets.UTF_8));
                    for (String key : cfg.getKeys(true)) {
                        if (cfg.isString(key)) {
                            map.put(key, cfg.getString(key));
                        }
                    }
                }
            } catch (IOException ignored) {}
            return Collections.unmodifiableMap(map);
        });
    }

    public String getLocale() { return locale; }

    public String t(String key) {
        if (langCfg != null) {
            String s = langCfg.getString(key);
            if (s != null) return s;
        }
        String s = localeFallback.get(key);
        if (s != null) return s;
        s = defaultLocaleFallback.get(key);
        if (s != null) return s;
        logMissingKey(key);
        return key;
    }

    private void logMissingKey(String key) {
        if (loggedMissingKeys.size() >= MAX_MISSING_KEY_LOGS) return;
        if (loggedMissingKeys.add(key)) {
            plugin.getLogger().info("Missing language key '" + key + "' in " + locale
                    + " (and " + DEFAULT_LOCALE + "), using the key as-is.");
        }
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
