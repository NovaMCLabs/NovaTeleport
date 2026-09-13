package com.novamclabs.scrolls;

import com.novamclabs.StarTeleport;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ScrollManager implements Listener {
    private final StarTeleport plugin;
    private String boundSpec = "PAPER";
    private String boundName = "§a传送卷轴: §f{target}";

    private final NamespacedKey keyType;
    private final NamespacedKey keyName;

    public ScrollManager(StarTeleport plugin) {
        this.plugin = plugin;
        this.keyType = new NamespacedKey(plugin, "scroll_type");
        this.keyName = new NamespacedKey(plugin, "scroll_target");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadConfig();
    }

    public void loadConfig() {
        File out = new File(plugin.getDataFolder(), "scrolls.yml");
        if (!out.exists()) {
            try { plugin.saveResource("scrolls.yml", false);} catch (IllegalArgumentException ignored) {}
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(out);
        this.boundSpec = cfg.getString("bound.material", "PAPER");
        this.boundName = cfg.getString("bound.name", this.boundName);
    }

    public ItemStack createBoundScroll(String type, String targetName) {
        ItemStack it = com.novamclabs.util.ItemResolver.resolveItem(boundSpec);
        if (it == null) it = new ItemStack(Material.PAPER);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.setDisplayName(boundName.replace("{target}", targetName));
            List<String> lore = new ArrayList<>();
            lore.add("§7类型: " + type);
            lore.add("§7目的地: " + targetName);
            im.setLore(lore);
            im.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            im.getPersistentDataContainer().set(keyType, PersistentDataType.STRING, type);
            im.getPersistentDataContainer().set(keyName, PersistentDataType.STRING, targetName);
            it.setItemMeta(im);
        }
        return it;
    }

    private boolean isBoundScroll(ItemStack stack, String type, String target) {
        if (stack == null || !stack.hasItemMeta()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        String t = meta.getPersistentDataContainer().get(keyType, PersistentDataType.STRING);
        String n = meta.getPersistentDataContainer().get(keyName, PersistentDataType.STRING);
        return type.equalsIgnoreCase(t) && target.equalsIgnoreCase(n);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        ItemStack it = e.getItem();
        if (it == null || !it.hasItemMeta()) return;
        ItemMeta im = it.getItemMeta();
        if (im == null) return;
        String type = im.getPersistentDataContainer().get(keyType, PersistentDataType.STRING);
        String target = im.getPersistentDataContainer().get(keyName, PersistentDataType.STRING);
        if (type == null || target == null) return;

        Player p = e.getPlayer();
        Location loc = null;
        if (type.equalsIgnoreCase("warp")) {
            loc = plugin.getDataStore() != null ? plugin.getDataStore().getWarp(target) : null;
        } else if (type.equalsIgnoreCase("home")) {
            loc = plugin.getDataStore() != null ? plugin.getDataStore().getHome(p.getUniqueId(), target) : null;
        }
        if (loc == null) {
            p.sendMessage(plugin.getLang().t("scroll.invalid_target"));
            return;
        }
        if (plugin.isTeleporting(p.getUniqueId())) {
            return; // 已经在倒计时中，避免连续消耗
        }

        // 卷轴在传送真正执行时才消耗，避免传送被取消却把卷轴吃掉
        com.novamclabs.util.TeleportUtil.Payment consume = player -> {
            for (ItemStack stack : player.getInventory().getContents()) {
                if (isBoundScroll(stack, type, target)) {
                    stack.setAmount(stack.getAmount() - 1);
                    return true;
                }
            }
            player.sendMessage(plugin.getLang().t("scroll.invalid_target"));
            return false;
        };

        int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
        com.novamclabs.util.TeleportUtil.delayedTeleportWithAnimation(plugin, p, loc, delay, "scroll", consume,
                () -> p.sendMessage(plugin.getLang().t("scroll.done")));
    }
}
