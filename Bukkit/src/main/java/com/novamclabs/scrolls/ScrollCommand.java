package com.novamclabs.scrolls;

import com.novamclabs.StarTeleport;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ScrollCommand implements CommandExecutor {
    private final StarTeleport plugin;
    private final ScrollManager manager;

    public ScrollCommand(StarTeleport plugin, ScrollManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        Player p = (Player) sender;
        if (!p.hasPermission("novateleport.scroll.bind")) {
            p.sendMessage(plugin.getLang().t("command.no_permission"));
            return true;
        }
        if (args.length < 3 || !args[0].equalsIgnoreCase("bind")) {
            p.sendMessage(plugin.getLang().t("usage.scroll.bind"));
            return true;
        }
        String type = args[1].toLowerCase();
        String name = com.novamclabs.storage.DataStore.normalizeName(args[2]);
        if ((!type.equals("home") && !type.equals("warp")) || name == null) {
            p.sendMessage(plugin.getLang().t("usage.scroll.bind"));
            return true;
        }
        // 不校验目的地是否存在，使用时再提示
        org.bukkit.inventory.ItemStack it = manager.createBoundScroll(type, name);
        java.util.Map<Integer, org.bukkit.inventory.ItemStack> leftover = p.getInventory().addItem(it);
        for (org.bukkit.inventory.ItemStack drop : leftover.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), drop);
        }
        p.sendMessage(plugin.getLang().tr("scroll.given", "name", name));
        return true;
    }
}
