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
    private String boundName;

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
        this.boundName = cfg.getString("bound.name", plugin.getLang().t("scroll.bound.default_name"));
    }

    public ItemStack createBoundScroll(String type, String targetName) {
        ItemStack it = com.novamclabs.util.ItemResolver.resolveItem(boundSpec);
        if (it == null) it = new ItemStack(Material.PAPER);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.setDisplayName(boundName.replace("{target}", targetName));
            List<String> lore = new ArrayList<>();
            lore.add(plugin.getLang().tr("scroll.bound.lore_type", "type", type));
            lore.add(plugin.getLang().tr("scroll.bound.lore_target", "target", targetName));
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

        // 确实是传送卷轴：吃掉这次右键，否则右键箱子会既开箱又起传送
        e.setCancelled(true);

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

        // 卷轴在传送真正成功之后才消耗：TeleportUtil 的失败回滚只能退金钱，
        // 如果在扣费回调里移除物品，被第三方插件拦截的传送就白吃一张卷轴。
        com.novamclabs.util.CostModel.Spec spec = com.novamclabs.util.CostModel.Spec.builder()
                .item(com.novamclabs.util.CostModel.ItemReq.of("scroll", 1,
                        stack -> isBoundScroll(stack, type, target)))
                .itemDeniedKey("scroll.invalid_target")
                .build();
        com.novamclabs.util.TeleportUtil.Payment check = player -> {
            com.novamclabs.util.CostModel.Result result = com.novamclabs.util.CostModel.preflight(plugin, player, spec);
            if (!result.ok()) {
                com.novamclabs.util.CostModel.notifyDenied(plugin, player, spec, result);
                return false;
            }
            return true;
        };

        int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
        com.novamclabs.util.TeleportUtil.delayedTeleportWithAnimation(plugin, p, loc, delay, "scroll", check, () -> {
            removeScroll(p, type, target);
            p.sendMessage(plugin.getLang().t("scroll.done"));
        });
    }

    /** 传送成功后移除一张卷轴；找不到就什么都不做（正常路径下必然还在背包里） */
    private void removeScroll(Player p, String type, String target) {
        for (ItemStack stack : p.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            if (!isBoundScroll(stack, type, target)) continue;
            stack.setAmount(stack.getAmount() - 1);
            return;
        }
    }
}
