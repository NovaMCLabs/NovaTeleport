package com.novamclabs.stele;

import com.novamclabs.StarTeleport;
import com.novamclabs.util.ItemResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 传送石碑系统管理（发现/激活/使用/索引）
 * Teleportation Stele system manager (discover/activate/use/index)
 */
public class SteleManager implements Listener {
    private final StarTeleport plugin;
    private final File dataFile;
    private YamlConfiguration index; // name -> location
    private YamlConfiguration conf;  // steles.yml

    public SteleManager(StarTeleport plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data/steles_index.yml");
        if (!dataFile.getParentFile().exists()) dataFile.getParentFile().mkdirs();
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void reload() {
        // load config
        File f = new File(plugin.getDataFolder(), "steles.yml");
        if (!f.exists()) {
            try { plugin.saveResource("steles.yml", false);} catch (IllegalArgumentException ignored) {}
        }
        conf = YamlConfiguration.loadConfiguration(f);
        // load index
        index = new YamlConfiguration();
        if (dataFile.exists()) {
            try { index.load(dataFile);} catch (Exception ignored) {}
        }
    }

    public boolean enabled() { return conf.getBoolean("enabled", true); }

    public void saveIndex() {
        try { index.save(dataFile);} catch (IOException ignored) {}
    }

    public Set<String> listSteles() {
        return index.getKeys(false);
    }

    public Location getSteleLocation(String name) {
        ConfigurationSection s = index.getConfigurationSection(name);
        if (s == null) return null;
        World w = Bukkit.getWorld(s.getString("world", "world"));
        if (w == null) return null;
        double x = s.getDouble("x"), y = s.getDouble("y"), z = s.getDouble("z");
        return new Location(w, x, y, z);
    }

    public void setStele(String name, Location loc) {
        index.set(name + ".world", Objects.requireNonNull(loc.getWorld()).getName());
        index.set(name + ".x", loc.getX());
        index.set(name + ".y", loc.getY());
        index.set(name + ".z", loc.getZ());
        saveIndex();
    }

    public void removeStele(String name) { index.set(name, null); saveIndex(); }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (!enabled()) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        // 遍历结构定义，查找是否是核心方块 | iterate structures and see if core matched
        ConfigurationSection structs = conf.getConfigurationSection("structures");
        if (structs == null) return;
        for (String key : structs.getKeys(false)) {
            ConfigurationSection s = structs.getConfigurationSection(key);
            if (s == null) continue;
            String coreSpec = s.getString("core_block", "LODESTONE");
            if (!isCore(coreSpec, b)) continue;
            // 检查框架相对坐标 | verify frame
            List<String> frames = s.getStringList("frame");
            boolean ok = true;
            for (String def : frames) {
                String[] arr = def.split(":");
                if (arr.length != 2) continue;
                String[] xyz = arr[0].split(",");
                int dx = Integer.parseInt(xyz[0]);
                int dy = Integer.parseInt(xyz[1]);
                int dz = Integer.parseInt(xyz[2]);
                Block fb = b.getWorld().getBlockAt(b.getX() + dx, b.getY() + dy, b.getZ() + dz);
                if (!ItemResolver.blockMatchesFrame(arr[1], fb)) { ok = false; break; }
            }
            if (!ok) continue;
            // 是一座石碑核心，处理 激活/使用 | core stele found
            Player p = e.getPlayer();
            if (!isUnlocked(p, key)) {
                if (tryActivate(p)) {
                    unlock(p, key);
                    p.sendMessage(plugin.getLang().t("stele.activated"));
                } else {
                    p.sendMessage(plugin.getLang().t("stele.need_item_or_xp"));
                }
            } else {
                openSteleMenu(p);
            }
            return;
        }
    }

    private boolean isCore(String spec, Block b) {
        if (spec.toLowerCase().startsWith("itemsadder:")) {
            return ItemResolver.blockMatchesFrame(spec, b);
        }
        Material m = Material.matchMaterial(spec);
        return m != null && b.getType() == m;
    }

    private boolean tryActivate(Player p) {
        String itemSpec = conf.getString("activation.item_required", "ENDER_PEARL");
        int amt = conf.getInt("activation.item_amount", 1);
        int xp = conf.getInt("activation.xp_level_cost", 0);
        boolean ok = true;
        if (itemSpec != null && !itemSpec.isEmpty() && amt > 0) {
            ItemStack need = ItemResolver.resolveItem(itemSpec);
            if (need == null) return false;
            int remain = amt;
            for (ItemStack it : p.getInventory().getContents()) {
                if (it == null) continue;
                if (ItemResolver.matches(itemSpec, it)) {
                    int use = Math.min(remain, it.getAmount());
                    it.setAmount(it.getAmount() - use);
                    remain -= use;
                    if (remain <= 0) break;
                }
            }
            if (remain > 0) ok = false;
        }
        if (ok && xp > 0) {
            if (p.getLevel() >= xp) p.setLevel(p.getLevel() - xp); else ok = false;
        }
        return ok;
    }

    private File playerFile(UUID u) { return new File(new File(plugin.getDataFolder(), "data/players"), u + ".yml"); }

    private boolean isUnlocked(Player p, String key) {
        File f = playerFile(p.getUniqueId());
        YamlConfiguration cfg = new YamlConfiguration();
        if (f.exists()) try { cfg.load(f);} catch (Exception ignored) {}
        List<String> unlocked = cfg.getStringList("steles.unlocked");
        return unlocked.contains(key);
    }

    private void unlock(Player p, String key) {
        File f = playerFile(p.getUniqueId());
        YamlConfiguration cfg = new YamlConfiguration();
        if (f.exists()) try { cfg.load(f);} catch (Exception ignored) {}
        List<String> unlocked = cfg.getStringList("steles.unlocked");
        if (!unlocked.contains(key)) unlocked.add(key);
        cfg.set("steles.unlocked", unlocked);
        try { cfg.save(f);} catch (IOException ignored) {}
    }

    public void openSteleMenu(Player p) {
        // 已解锁石碑 -> 按注册表（index）展示 | unlocked -> list index
        List<String> names = new ArrayList<>(listSteles());
        if (names.isEmpty()) { p.sendMessage(plugin.getLang().t("stele.none")); return; }
        if (com.novamclabs.util.BedrockUtil.isBedrock(p)) {
            com.novamclabs.util.BedrockFormsUtil.showListCommandForm(plugin, p, plugin.getLang().t("stele.menu"), names, "stele travel");
        } else {
            org.bukkit.inventory.Inventory inv = Bukkit.createInventory(p, 54, plugin.getLang().t("stele.menu"));
            for (String n : names) {
                org.bukkit.inventory.ItemStack it = new org.bukkit.inventory.ItemStack(Material.LODESTONE);
                org.bukkit.inventory.meta.ItemMeta im = it.getItemMeta();
                im.setDisplayName("§a" + n);
                it.setItemMeta(im);
                inv.addItem(it);
            }
            p.openInventory(inv);
        }
    }
}
