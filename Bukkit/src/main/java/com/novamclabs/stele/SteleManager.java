package com.novamclabs.stele;

import com.novamclabs.StarTeleport;
import com.novamclabs.menu.JavaMenuConfig;
import com.novamclabs.util.ItemResolver;
import com.novamclabs.util.TeleportUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 传送石碑系统管理（发现/激活/使用/索引）
 * Teleportation Stele system manager (discover/activate/use/index)
 */
public class SteleManager implements Listener {
    private final StarTeleport plugin;
    private final File dataFile;
    private YamlConfiguration index; // name -> location
    private YamlConfiguration conf;  // steles.yml

    private final JavaMenuConfig menus;
    private final NamespacedKey keyAction;
    private final NamespacedKey keyValue;

    private static final class SteleMenuHolder implements InventoryHolder {
        private Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }

        private void bind(Inventory inv) {
            this.inv = inv;
        }
    }

    public SteleManager(StarTeleport plugin) {
        this.plugin = plugin;
        this.menus = plugin.getJavaMenus();
        this.keyAction = new NamespacedKey(plugin, "stele_menu_action");
        this.keyValue = new NamespacedKey(plugin, "stele_menu_value");

        this.dataFile = new File(plugin.getDataFolder(), "data/steles_index.yml");
        if (!dataFile.getParentFile().exists()) dataFile.getParentFile().mkdirs();
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "steles.yml");
        if (!f.exists()) {
            try {
                plugin.saveResource("steles.yml", false);
            } catch (IllegalArgumentException ignored) {
            }
        }
        conf = YamlConfiguration.loadConfiguration(f);

        index = new YamlConfiguration();
        if (dataFile.exists()) {
            try {
                index.load(dataFile);
            } catch (Exception ignored) {
            }
        }
    }

    public boolean enabled() {
        return conf.getBoolean("enabled", true);
    }

    public void saveIndex() {
        try {
            index.save(dataFile);
        } catch (IOException ignored) {
        }
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

    public void removeStele(String name) {
        index.set(name, null);
        saveIndex();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (!enabled()) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null) return;

        ConfigurationSection structs = conf.getConfigurationSection("structures");
        if (structs == null) return;
        for (String key : structs.getKeys(false)) {
            ConfigurationSection s = structs.getConfigurationSection(key);
            if (s == null) continue;
            String coreSpec = s.getString("core_block", "LODESTONE");
            if (!isCore(coreSpec, b)) continue;

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
                if (!ItemResolver.blockMatchesFrame(arr[1], fb)) {
                    ok = false;
                    break;
                }
            }
            if (!ok) continue;

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
        if (spec.toLowerCase(Locale.ROOT).startsWith("itemsadder:")) {
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
            if (p.getLevel() >= xp) p.setLevel(p.getLevel() - xp);
            else ok = false;
        }
        return ok;
    }

    private File playerFile(UUID u) {
        return new File(new File(plugin.getDataFolder(), "data/players"), u + ".yml");
    }

    private boolean isUnlocked(Player p, String key) {
        File f = playerFile(p.getUniqueId());
        YamlConfiguration cfg = new YamlConfiguration();
        if (f.exists()) try {
            cfg.load(f);
        } catch (Exception ignored) {
        }
        List<String> unlocked = cfg.getStringList("steles.unlocked");
        return unlocked.contains(key);
    }

    private void unlock(Player p, String key) {
        File f = playerFile(p.getUniqueId());
        YamlConfiguration cfg = new YamlConfiguration();
        if (f.exists()) try {
            cfg.load(f);
        } catch (Exception ignored) {
        }
        List<String> unlocked = cfg.getStringList("steles.unlocked");
        if (!unlocked.contains(key)) unlocked.add(key);
        cfg.set("steles.unlocked", unlocked);
        try {
            cfg.save(f);
        } catch (IOException ignored) {
        }
    }

    public void openSteleMenu(Player p) {
        List<String> names = new ArrayList<>(listSteles());
        if (names.isEmpty()) {
            p.sendMessage(plugin.getLang().t("stele.none"));
            return;
        }

        if (com.novamclabs.util.BedrockUtil.isBedrock(p)) {
            com.novamclabs.util.BedrockFormsUtil.showListCommandForm(plugin, p, plugin.getLang().t("stele.menu"), names, "stele travel");
            return;
        }

        JavaMenuConfig.Template template = menus.getTemplate("steles");
        SteleMenuHolder holder = new SteleMenuHolder();
        Inventory inv = Bukkit.createInventory(holder, menus.getSize("steles", 54), menus.getTitle("steles", Collections.emptyMap()));
        holder.bind(inv);

        for (String n : names) {
            Map<String, Object> placeholders = new HashMap<>();
            placeholders.put("name", n);
            ItemStack it = menus.buildTemplateItem(template, placeholders);
            it = tagAction(it, template != null ? template.action() : "stele_travel", n);
            inv.addItem(it);
        }

        p.openInventory(inv);
    }

    @EventHandler
    public void onSteleMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (!(e.getInventory().getHolder() instanceof SteleMenuHolder)) return;

        e.setCancelled(true);
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return;

        String action = meta.getPersistentDataContainer().get(keyAction, PersistentDataType.STRING);
        String value = meta.getPersistentDataContainer().get(keyValue, PersistentDataType.STRING);
        if (!"stele_travel".equalsIgnoreCase(action) || value == null) return;

        Player p = (Player) e.getWhoClicked();
        p.closeInventory();

        Location dest = getSteleLocation(value);
        if (dest == null) {
            p.sendMessage(plugin.getLang().t("stele.none"));
            return;
        }

        try {
            if (plugin.getDataStore() != null) {
                plugin.getDataStore().setBack(p.getUniqueId(), p.getLocation());
            }
        } catch (Exception ignored) {
        }

        int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
        TeleportUtil.delayedTeleportWithAnimation(plugin, p, dest, delay, "stele", () -> p.sendMessage(plugin.getLang().t("teleport.completed")));
    }

    private ItemStack tagAction(ItemStack it, String action, String value) {
        if (it == null) return null;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(keyValue, PersistentDataType.STRING, value);
        it.setItemMeta(meta);
        return it;
    }
}
