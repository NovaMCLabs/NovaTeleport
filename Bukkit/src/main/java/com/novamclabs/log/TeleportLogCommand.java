package com.novamclabs.log;

import com.novamclabs.StarTeleport;
import com.novamclabs.menu.JavaMenuConfig;
import com.novamclabs.util.TeleportUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class TeleportLogCommand implements CommandExecutor, TabCompleter, Listener {
    private final StarTeleport plugin;
    private final TeleportLogManager manager;
    private final JavaMenuConfig menus;

    private final NamespacedKey keyAction;
    private final NamespacedKey keyValue;

    private static final class LogMenuHolder implements InventoryHolder {
        private final UUID target;
        private Inventory inv;

        private LogMenuHolder(UUID target) {
            this.target = target;
        }

        public UUID target() {
            return target;
        }

        private void bind(Inventory inv) {
            this.inv = inv;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    public TeleportLogCommand(StarTeleport plugin, TeleportLogManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.menus = plugin.getJavaMenus();
        this.keyAction = new NamespacedKey(plugin, "tplog_action");
        this.keyValue = new NamespacedKey(plugin, "tplog_value");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLang().t("command.player_only"));
            return true;
        }

        Player viewer = (Player) sender;
        if (!viewer.hasPermission(manager.getRewindPermission())) {
            viewer.sendMessage(plugin.getLang().t("command.no_permission"));
            return true;
        }

        if (args.length < 1) {
            viewer.sendMessage("§cUsage: /tplog <player>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            viewer.sendMessage(plugin.getLang().t("tplog.player_offline"));
            return true;
        }

        openMenu(viewer, target);
        return true;
    }

    private void openMenu(Player viewer, Player target) {
        List<TeleportLogEntry> logs = manager.getLogs(target.getUniqueId());
        if (logs.isEmpty()) {
            viewer.sendMessage(plugin.getLang().t("tplog.no_logs"));
            return;
        }

        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put("player", target.getName());

        JavaMenuConfig.Template tpl = menus.getTemplate("teleport_log");
        LogMenuHolder holder = new LogMenuHolder(target.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, menus.getSize("teleport_log", 54), menus.getTitle("teleport_log", placeholders));
        holder.bind(inv);

        for (TeleportLogEntry entry : logs) {
            if (inv.firstEmpty() < 0) break;

            Location from = entry.from();
            Location to = entry.to();

            Map<String, Object> map = new HashMap<>();
            map.put("type", entry.type());
            map.put("time", timeFmt.format(Instant.ofEpochMilli(entry.timeMillis())));

            map.put("from_world", from.getWorld() != null ? from.getWorld().getName() : "world");
            map.put("from_x", from.getBlockX());
            map.put("from_y", from.getBlockY());
            map.put("from_z", from.getBlockZ());

            map.put("to_world", to.getWorld() != null ? to.getWorld().getName() : "world");
            map.put("to_x", to.getBlockX());
            map.put("to_y", to.getBlockY());
            map.put("to_z", to.getBlockZ());

            ItemStack it = menus.buildTemplateItem(tpl, map);
            it = tag(it, tpl != null ? tpl.action() : "tplog_rewind", String.valueOf(entry.timeMillis()));
            inv.addItem(it);
        }

        viewer.openInventory(inv);
        viewer.sendMessage(plugin.getLang().tr("tplog.opened", "player", target.getName()));
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (!(e.getInventory().getHolder() instanceof LogMenuHolder)) return;

        e.setCancelled(true);

        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return;

        String action = meta.getPersistentDataContainer().get(keyAction, PersistentDataType.STRING);
        String value = meta.getPersistentDataContainer().get(keyValue, PersistentDataType.STRING);
        if (!"tplog_rewind".equalsIgnoreCase(action) || value == null) return;

        LogMenuHolder holder = (LogMenuHolder) e.getInventory().getHolder();
        Player viewer = (Player) e.getWhoClicked();
        if (!viewer.hasPermission(manager.getRewindPermission())) {
            viewer.sendMessage(plugin.getLang().t("command.no_permission"));
            return;
        }

        Player target = Bukkit.getPlayer(holder.target());
        if (target == null) {
            viewer.sendMessage(plugin.getLang().t("tplog.player_offline"));
            viewer.closeInventory();
            return;
        }

        long time;
        try {
            time = Long.parseLong(value);
        } catch (Exception ignored) {
            return;
        }

        TeleportLogEntry entry = manager.getLogs(holder.target()).stream()
            .filter(le -> le.timeMillis() == time)
            .findFirst()
            .orElse(null);
        if (entry == null) return;

        viewer.closeInventory();
        Location from = entry.from();
        if (from == null || from.getWorld() == null) return;

        try {
            if (plugin.getDataStore() != null) {
                plugin.getDataStore().setBack(target.getUniqueId(), target.getLocation());
            }
        } catch (Exception ignored) {
        }

        TeleportUtil.delayedTeleportWithAnimation(plugin, target, from, 0, "rewind", () -> {
            viewer.sendMessage(plugin.getLang().t("tplog.rewind_done"));
        });
    }

    private ItemStack tag(ItemStack it, String action, String value) {
        if (it == null) return null;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(keyValue, PersistentDataType.STRING, value);
        it.setItemMeta(meta);
        return it;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
