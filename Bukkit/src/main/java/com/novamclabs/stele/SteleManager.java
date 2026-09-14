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
    // 索引/配置会被 reload 整体替换，且从多个区域线程与 Bedrock 回调线程读写，必须 volatile 保证可见性
    private volatile YamlConfiguration index; // name -> location
    private volatile YamlConfiguration conf;  // steles.yml
    // 索引的「改 + 存」必须成对串行，否则并发写入会互相覆盖
    private final Object indexLock = new Object();

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
        YamlConfiguration newConf = YamlConfiguration.loadConfiguration(f);
        YamlConfiguration newIndex = new YamlConfiguration();
        if (dataFile.exists()) {
            try {
                newIndex.load(dataFile);
            } catch (Exception ignored) {
            }
        }
        // 在同一把锁里整体替换，避免与 setStele/removeStele 的「改 + 存」交错
        synchronized (indexLock) {
            conf = newConf;
            index = newIndex;
        }
    }

    public boolean enabled() {
        return conf.getBoolean("enabled", true);
    }

    public void saveIndex() {
        synchronized (indexLock) {
            saveIndexLocked();
        }
    }

    private void saveIndexLocked() {
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
        synchronized (indexLock) {
            index.set(name + ".world", Objects.requireNonNull(loc.getWorld()).getName());
            index.set(name + ".x", loc.getX());
            index.set(name + ".y", loc.getY());
            index.set(name + ".z", loc.getZ());
            saveIndexLocked();
        }
    }

    public void removeStele(String name) {
        synchronized (indexLock) {
            index.set(name, null);
            saveIndexLocked();
        }
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
        int xp = Math.max(0, conf.getInt("activation.xp_level_cost", 0));

        com.novamclabs.util.CostModel.Builder builder =
                com.novamclabs.util.CostModel.Spec.builder().xpLevels(xp).xpDeniedKey("stele.need_xp");
        if (itemSpec != null && !itemSpec.isEmpty() && amt > 0) {
            builder.item(com.novamclabs.util.CostModel.ItemReq.of(itemSpec, amt));
        }
        com.novamclabs.util.CostModel.Spec spec = builder.build();

        // 先校验全部再统一扣减：经验或物品任何一项不足都不会产生部分扣减
        // （原实现在物品不够时会先把背包里的同类物品扣掉一部分再失败返回）
        com.novamclabs.util.CostModel.Result result = com.novamclabs.util.CostModel.preflight(plugin, p, spec);
        if (!result.ok()) {
            com.novamclabs.util.CostModel.notifyDenied(plugin, p, spec, result);
            return false;
        }
        return com.novamclabs.util.CostModel.apply(plugin, p, spec, result);
    }

    private boolean isUnlocked(Player p, String key) {
        return plugin.getDataStore().readPlayer(p.getUniqueId()).getStringList("steles.unlocked").contains(key);
    }

    private void unlock(Player p, String key) {
        plugin.getDataStore().updatePlayer(p.getUniqueId(), cfg -> {
            List<String> unlocked = cfg.getStringList("steles.unlocked");
            if (!unlocked.contains(key)) {
                unlocked.add(key);
                cfg.set("steles.unlocked", unlocked);
            }
        });
    }

    /** 石碑传送费用（steles.yml: teleport_cost）| stele travel cost */
    public com.novamclabs.util.TeleportUtil.Payment travelPayment() {
        return com.novamclabs.util.CostModel.asPayment(plugin, () -> com.novamclabs.util.CostModel.Spec.builder()
                .money(com.novamclabs.util.CostModel.resolveMoney(plugin, conf, "stele",
                        "teleport_cost.vault_cost", "teleport_cost.vault"))
                .xpLevels(com.novamclabs.util.CostModel.resolveXp(conf,
                        "teleport_cost.xp_level_cost", "teleport_cost.xp_levels"))
                .xpDeniedKey("stele.need_xp")
                .build());
    }

    public void openSteleMenu(Player p) {
        List<String> names = new ArrayList<>(listSteles());
        if (names.isEmpty()) {
            p.sendMessage(plugin.getLang().t("stele.none"));
            return;
        }

        if (com.novamclabs.util.BedrockUtil.isBedrock(p)) {
            boolean ok = com.novamclabs.util.BedrockFormsUtil.showListCommandForm(plugin, p, plugin.getLang().t("stele.menu"), names, names, "stele travel");
            if (!ok) {
                p.sendMessage("§6" + plugin.getLang().t("stele.menu") + ": §f" + String.join(", ", names));
            }
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
        travelTo(p, value);
    }

    /** 传送到指定石碑（含费用与 /back 记录）| travel to a stele by name */
    public boolean travelTo(Player p, String name) {
        Location dest = getSteleLocation(name);
        if (dest == null) {
            p.sendMessage(plugin.getLang().tr("stele.not_found", "name", name));
            return false;
        }
        // 激活是使用的前置条件：/stele travel、菜单、Bedrock 表单都汇聚到这里，因此只需在此处拦截
        if (!isUnlocked(p, name)) {
            p.sendMessage(plugin.getLang().t("stele.need_item_or_xp"));
            return false;
        }
        int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
        TeleportUtil.delayedTeleportWithAnimation(plugin, p, dest, delay, "stele", travelPayment(),
                () -> p.sendMessage(plugin.getLang().t("teleport.completed")));
        return true;
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
