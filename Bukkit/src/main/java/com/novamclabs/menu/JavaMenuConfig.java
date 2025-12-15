package com.novamclabs.menu;

import com.novamclabs.StarTeleport;
import com.novamclabs.util.ItemResolver;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java 版菜单配置（独立文件 java_menus.yml）。
 */
public class JavaMenuConfig {
    private static final Pattern LANG_PATTERN = Pattern.compile("\\{lang:([^}]+)}");

    private final StarTeleport plugin;
    private FileConfiguration config;

    public JavaMenuConfig(StarTeleport plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "java_menus.yml");
        if (!f.exists()) {
            try {
                plugin.saveResource("java_menus.yml", false);
            } catch (IllegalArgumentException ignored) {
            }
        }
        this.config = YamlConfiguration.loadConfiguration(f);
    }

    public ConfigurationSection getMenu(String id) {
        return config.getConfigurationSection("menus." + id);
    }

    public int getSize(String id, int def) {
        ConfigurationSection sec = getMenu(id);
        if (sec == null) return def;
        return sec.getInt("size", def);
    }

    public String getTitle(String id, Map<String, Object> placeholders) {
        ConfigurationSection sec = getMenu(id);
        if (sec == null) return id;
        String raw = sec.getString("title", id);
        return resolveText(raw, placeholders);
    }

    public List<FixedItem> getFixedItems(String id, Map<String, Object> placeholders) {
        ConfigurationSection sec = getMenu(id);
        if (sec == null) return Collections.emptyList();
        ConfigurationSection items = sec.getConfigurationSection("items");
        if (items == null) return Collections.emptyList();

        List<FixedItem> out = new ArrayList<>();
        for (String key : items.getKeys(false)) {
            ConfigurationSection is = items.getConfigurationSection(key);
            if (is == null) continue;
            int slot = is.getInt("slot", -1);
            if (slot < 0) continue;
            String action = is.getString("action", key);
            ItemStack stack = buildItemFromSection(is, placeholders);
            out.add(new FixedItem(slot, action, stack));
        }
        return out;
    }

    public Template getTemplate(String id) {
        ConfigurationSection sec = getMenu(id);
        if (sec == null) return null;
        ConfigurationSection tpl = sec.getConfigurationSection("template");
        if (tpl == null) return null;
        return new Template(
            tpl.getString("item", "PAPER"),
            tpl.getString("name", "{name}"),
            tpl.getStringList("lore"),
            tpl.getString("action", "")
        );
    }

    private ItemStack buildItemFromSection(ConfigurationSection is, Map<String, Object> placeholders) {
        String spec = is.getString("item", "PAPER");
        int amount = Math.max(1, is.getInt("amount", 1));
        String name = is.getString("name", "");
        List<String> lore = is.getStringList("lore");

        ItemStack stack = ItemResolver.resolveItem(spec);
        if (stack == null) {
            Material mat = Material.matchMaterial(spec);
            stack = new ItemStack(mat != null ? mat : Material.PAPER);
        }
        stack.setAmount(amount);

        if (name != null && !name.isEmpty()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(resolveText(name, placeholders));
                if (lore != null && !lore.isEmpty()) {
                    List<String> outLore = new ArrayList<>();
                    for (String l : lore) {
                        outLore.add(resolveText(l, placeholders));
                    }
                    meta.setLore(outLore);
                }
                stack.setItemMeta(meta);
            }
        }

        return stack;
    }

    public ItemStack buildTemplateItem(Template template, Map<String, Object> placeholders) {
        if (template == null) return null;
        Map<String, Object> map = placeholders != null ? placeholders : Collections.emptyMap();

        ItemStack stack = ItemResolver.resolveItem(template.itemSpec());
        if (stack == null) {
            Material mat = Material.matchMaterial(template.itemSpec());
            stack = new ItemStack(mat != null ? mat : Material.PAPER);
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(resolveText(template.name(), map));
            if (template.lore() != null && !template.lore().isEmpty()) {
                List<String> outLore = new ArrayList<>();
                for (String l : template.lore()) {
                    outLore.add(resolveText(l, map));
                }
                meta.setLore(outLore);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public String resolveText(String raw, Map<String, Object> placeholders) {
        if (raw == null) return "";
        String s = raw;

        Matcher m = LANG_PATTERN.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String rep = plugin.getLang() != null ? plugin.getLang().t(key) : key;
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        s = sb.toString();

        if (placeholders != null) {
            for (Map.Entry<String, Object> e : placeholders.entrySet()) {
                s = s.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
            }
        }

        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public record FixedItem(int slot, String action, ItemStack stack) {
    }

    public record Template(String itemSpec, String name, List<String> lore, String action) {
    }
}
