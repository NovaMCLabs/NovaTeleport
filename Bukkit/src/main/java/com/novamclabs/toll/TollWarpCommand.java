package com.novamclabs.toll;

import com.novamclabs.StarTeleport;
import com.novamclabs.menu.JavaMenuConfig;
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

import java.util.*;
import java.util.stream.Collectors;

/**
 * 付费传送点命令 & GUI
 * Toll warp command & GUI
 */
public class TollWarpCommand implements CommandExecutor, TabCompleter, Listener {
    private final StarTeleport plugin;
    private final TollWarpManager manager;

    private final JavaMenuConfig menus;
    private final NamespacedKey keyAction;
    private final NamespacedKey keyValue;

    private static final class TollMenuHolder implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
        private void bind(Inventory inv) { this.inv = inv; }
    }

    public TollWarpCommand(StarTeleport plugin, TollWarpManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.menus = plugin.getJavaMenus();
        this.keyAction = new NamespacedKey(plugin, "toll_menu_action");
        this.keyValue = new NamespacedKey(plugin, "toll_menu_value");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLang().t("command.player_only"));
            return true;
        }

        Player player = (Player) sender;

        if (!manager.isEnabled()) {
            player.sendMessage(plugin.getLang().t("toll.not_enabled"));
            return true;
        }

        if (args.length == 0) {
            openMenu(player, false);
            return true;
        }

        String subCmd = args[0].toLowerCase(Locale.ROOT);

        switch (subCmd) {
            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /tollwarp create <name> [price]");
                    return true;
                }
                double price = 0.0;
                if (args.length >= 3) {
                    try {
                        price = Double.parseDouble(args[2]);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cPrice must be a number!");
                        return true;
                    }
                }
                manager.createWarp(player, args[1], price);
                return true;
            }
            case "delete", "remove" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /tollwarp delete <name>");
                    return true;
                }
                manager.deleteWarp(player, args[1]);
                return true;
            }
            case "setprice" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /tollwarp setprice <name> <price>");
                    return true;
                }
                try {
                    double price = Double.parseDouble(args[2]);
                    manager.setPrice(player, args[1], price);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cPrice must be a number!");
                }
                return true;
            }
            case "list" -> {
                openMenu(player, false);
                return true;
            }
            case "mywarps" -> {
                openMenu(player, true);
                return true;
            }
            case "tp", "teleport" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /tollwarp tp <name>");
                    return true;
                }
                manager.teleportToWarp(player, args[1]);
                return true;
            }
            default -> {
                // shorthand: /tollwarp <name>
                manager.teleportToWarp(player, args[0]);
                return true;
            }
        }
    }

    private void openMenu(Player player, boolean mine) {
        if (manager.getMode() == TollWarpManager.Mode.PERSONAL_FREE) {
            mine = true;
        }

        List<TollWarp> warps = mine ? manager.getPlayerWarps(player.getUniqueId()) : manager.getAllWarps();
        if (warps.isEmpty()) {
            player.sendMessage(plugin.getLang().t(mine ? "toll.no_own_warps" : "toll.no_warps"));
            return;
        }

        JavaMenuConfig.Template template = menus.getTemplate("toll_warps");
        TollMenuHolder holder = new TollMenuHolder();
        Inventory inv = Bukkit.createInventory(holder, menus.getSize("toll_warps", 54), menus.getTitle("toll_warps", Map.of()));
        holder.bind(inv);

        for (TollWarp warp : warps) {
            String ownerName = plugin.getServer().getOfflinePlayer(warp.getOwnerId()).getName();
            if (ownerName == null) ownerName = warp.getOwnerId().toString();
            Location loc = warp.getLocation();

            Map<String, Object> placeholders = new HashMap<>();
            placeholders.put("name", warp.getName());
            placeholders.put("owner", ownerName);
            placeholders.put("price", com.novamclabs.util.EconomyUtil.format(warp.getPrice()));
            placeholders.put("world", loc.getWorld() != null ? loc.getWorld().getName() : "world");
            placeholders.put("x", loc.getBlockX());
            placeholders.put("y", loc.getBlockY());
            placeholders.put("z", loc.getBlockZ());
            placeholders.put("usage", warp.getUsageCount());

            ItemStack it = menus.buildTemplateItem(template, placeholders);
            it = tag(it, template != null ? template.action() : "tollwarp_tp", warp.getName());
            inv.addItem(it);
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (!(e.getInventory().getHolder() instanceof TollMenuHolder)) return;

        e.setCancelled(true);
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return;

        String action = meta.getPersistentDataContainer().get(keyAction, PersistentDataType.STRING);
        String value = meta.getPersistentDataContainer().get(keyValue, PersistentDataType.STRING);
        if (!"tollwarp_tp".equalsIgnoreCase(action) || value == null) return;

        Player player = (Player) e.getWhoClicked();
        player.closeInventory();
        manager.teleportToWarp(player, value);
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
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(List.of("create", "delete", "setprice", "list", "mywarps", "tp"));
            completions.addAll(manager.getAllWarps().stream().map(TollWarp::getName).collect(Collectors.toList()));

            return completions.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String subCmd = args[0].toLowerCase(Locale.ROOT);
            if (subCmd.equals("delete") || subCmd.equals("setprice") || subCmd.equals("tp")) {
                return manager.getAllWarps().stream()
                    .map(TollWarp::getName)
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
            }
        }

        return completions;
    }
}
