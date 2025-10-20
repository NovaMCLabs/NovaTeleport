package com.novamclabs.commands;

import com.novamclabs.StarTeleport;
import com.novamclabs.storage.DataStore;
import com.novamclabs.util.BedrockUtil;
import com.novamclabs.util.TeleportUtil;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TeleportCommandHandler implements CommandExecutor, TabCompleter, Listener {
    private final StarTeleport plugin;
    private final DataStore store;
    private final Map<UUID, Integer> rtpRadiusChoices = new ConcurrentHashMap<>();

    public TeleportCommandHandler(StarTeleport plugin) {
        this.plugin = plugin;
        this.store = plugin.getDataStore();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // TPA 请求管理
    private static class TpaRequest {
        final UUID requester;
        final UUID target;
        final boolean here; // true 表示 /tpahere
        final long expireAt;
        TpaRequest(UUID requester, UUID target, boolean here, long expireAt) {
            this.requester = requester; this.target = target; this.here = here; this.expireAt = expireAt;
        }
    }
    private final Map<UUID, TpaRequest> incoming = new ConcurrentHashMap<>(); // target -> request
    private final Map<UUID, TpaRequest> outgoing = new ConcurrentHashMap<>(); // requester -> request

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        switch (cmd) {
            case "tpa": return handleTpa(sender, args, false);
            case "tpahere": return handleTpa(sender, args, true);
            case "tpaccept": return handleTpAccept(sender);
            case "tpdeny": return handleTpDeny(sender);
            case "tpcancel": return handleTpCancel(sender);
            case "sethome": return handleSetHome(sender, args);
            case "home": return handleHome(sender, args);
            case "delhome": return handleDelHome(sender, args);
            case "homes": return handleHomes(sender);
            case "setwarp": return handleSetWarp(sender, args);
            case "warp": return handleWarp(sender, args);
            case "delwarp": return handleDelWarp(sender, args);
            case "warps": return handleWarps(sender);
            case "spawn": return handleSpawn(sender);
            case "back": return handleBack(sender);
            case "rtp": return handleRtp(sender);
            case "rtpgui": return handleRtpGui(sender);
            case "tpmenu": return handleTpMenu(sender);
        }
        return false;
    }

    private boolean handleTpa(CommandSender sender, String[] args, boolean here) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        Player requester = (Player) sender;
        if (args.length < 1) { requester.sendMessage(plugin.getLang().t(here?"usage.tpahere":"usage.tpa")); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { requester.sendMessage(plugin.getLang().t("common.no_online_player")); return true; }
        if (target.getUniqueId().equals(requester.getUniqueId())) { requester.sendMessage(plugin.getLang().t("common.cannot_target_self")); return true; }

        long expireAt = System.currentTimeMillis() + 60_000; // 60秒过期
        TpaRequest req = new TpaRequest(requester.getUniqueId(), target.getUniqueId(), here, expireAt);
        incoming.put(target.getUniqueId(), req);
        outgoing.put(requester.getUniqueId(), req);

        requester.sendMessage(plugin.getLang().tr("tpa.sent", "target", target.getName(), "seconds", 60));
        // 目标收到请求
        if (here) {
            target.sendMessage(plugin.getLang().tr("tpa.prompt.to_here", "requester", requester.getName()));
        } else {
            target.sendMessage(plugin.getLang().tr("tpa.prompt.to_you", "requester", requester.getName()));
        }
        // Bedrock 模态表单（若可用）省略：无依赖时退化为聊天提示。
        return true;
    }

    private boolean handleTpAccept(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        Player target = (Player) sender;
        TpaRequest req = incoming.get(target.getUniqueId());
        if (req == null || req.expireAt < System.currentTimeMillis()) { target.sendMessage(plugin.getLang().t("tpa.no_request")); return true; }
        Player requester = Bukkit.getPlayer(req.requester);
        if (requester == null) { target.sendMessage(plugin.getLang().t("tpa.requester_offline")); cleanup(req); return true; }

        // 传送对象
        Player mover = req.here ? target : requester;
        Location dest = (req.here ? requester : target).getLocation();
        // 经济扣费
        String actionKey = req.here ? "tpahere" : "tpa";
        if (!ensurePaid(mover, actionKey)) {
            return true;
        }
        try { if (store != null) store.setBack(mover.getUniqueId(), mover.getLocation()); } catch (Exception ignored) {}
        int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
        BukkitTask task = TeleportUtil.delayedTeleportWithAnimation(plugin, mover, dest, delay, () -> {
            cleanup(req);
            mover.sendMessage(plugin.getLang().t("tpa.accepted.complete"));
        });
        mover.sendMessage(plugin.getLang().t("tpa.accepted.start"));
        if (task == null) cleanup(req);
        return true;
    }

    private boolean handleTpDeny(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        Player target = (Player) sender;
        TpaRequest req = incoming.remove(target.getUniqueId());
        if (req == null) { target.sendMessage(plugin.getLang().t("tpa.none_pending")); return true; }
        Player requester = Bukkit.getPlayer(req.requester);
        if (requester != null) requester.sendMessage(plugin.getLang().tr("tpa.denied.sender", "target", target.getName()));
        outgoing.remove(req.requester);
        target.sendMessage(plugin.getLang().t("tpa.denied.target"));
        return true;
    }

    private boolean handleTpCancel(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        Player requester = (Player) sender;
        TpaRequest req = outgoing.remove(requester.getUniqueId());
        if (req == null) { requester.sendMessage(plugin.getLang().t("tpa.no_outgoing")); return true; }
        incoming.remove(req.target);
        Player target = Bukkit.getPlayer(req.target);
        if (target != null) target.sendMessage(plugin.getLang().t("tpa.cancelled.target"));
        requester.sendMessage(plugin.getLang().t("tpa.cancelled.requester"));
        return true;
    }

    private void cleanup(TpaRequest req) {
        incoming.remove(req.target);
        outgoing.remove(req.requester);
    }

    // 家系统
    private boolean handleSetHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        Player p = (Player) sender;
        String name = args.length >= 1 ? args[0] : "home";
        int limit = getHomeLimit(p);
        List<String> current = store.listHomes(p.getUniqueId());
        if (!current.contains(name.toLowerCase(Locale.ROOT)) && current.size() >= limit) {
            p.sendMessage(plugin.getLang().tr("homes.limit_reached", "limit", limit));
            return true;
        }
        try { store.setHome(p.getUniqueId(), name, p.getLocation()); } catch (IOException e) { p.sendMessage(plugin.getLang().t("common.save_failed")); return true; }
        p.sendMessage(plugin.getLang().tr("homes.set", "name", name));
        return true;
    }

    private int getHomeLimit(Player p) {
        // 根据权限 novateleport.home.limit.X 取最大X
        int def = plugin.getConfig().getInt("homes.default_limit", 1);
        int max = def;
        for (PermissionAttachmentInfo pi : p.getEffectivePermissions()) {
            String perm = pi.getPermission().toLowerCase(Locale.ROOT);
            if (perm.startsWith("novateleport.home.limit.")) {
                try { int v = Integer.parseInt(perm.substring("novateleport.home.limit.".length())); max = Math.max(max, v);} catch (Exception ignored) {}
            }
        }
        return max;
    }

    private boolean handleHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        Player p = (Player) sender;
        String name = args.length >= 1 ? args[0] : "home";
        Location loc = store.getHome(p.getUniqueId(), name);
        if (loc == null) { p.sendMessage(plugin.getLang().tr("homes.not_found", "name", name)); return true; }
        if (!ensurePaid(p, "home")) { return true; }
        try { store.setBack(p.getUniqueId(), p.getLocation()); } catch (Exception ignored) {}
        int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
        TeleportUtil.delayedTeleportWithAnimation(plugin, p, loc, delay, () -> p.sendMessage(plugin.getLang().t("homes.welcome")));
        return true;
    }

    private boolean handleDelHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        Player p = (Player) sender;
        String name = args.length >= 1 ? args[0] : "home";
        try { store.delHome(p.getUniqueId(), name); } catch (IOException e) { p.sendMessage(plugin.getLang().t("common.delete_failed")); return true; }
        p.sendMessage(plugin.getLang().tr("homes.deleted", "name", name));
        return true;
    }

    private boolean handleHomes(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        Player p = (Player) sender;
        List<String> list = store.listHomes(p.getUniqueId());
        if (list.isEmpty()) { p.sendMessage(plugin.getLang().t("homes.none")); return true; }
        if (BedrockUtil.isBedrock(p)) {
            // 简化：基岩版暂用文本列表
            p.sendMessage("§6" + plugin.getLang().t("menu.homes.title") + ": §f" + String.join(", ", list));
        } else {
            String title = plugin.getLang().t("menu.homes.title");
            Inventory inv = Bukkit.createInventory(p, 27, title);
            for (String name : list) {
                ItemStack it = new ItemStack(Material.ENDER_PEARL);
                ItemMeta im = it.getItemMeta();
                im.setDisplayName(plugin.getLang().tr("display.home.item", "name", name));
                it.setItemMeta(im);
                inv.addItem(it);
            }
            p.openInventory(inv);
        }
        return true;
    }

    // 传送点
    private boolean handleSetWarp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        if (!sender.hasPermission("novateleport.admin")) { sender.sendMessage(plugin.getLang().t("command.no_permission")); return true; }
        Player p = (Player) sender;
        if (args.length < 1) { p.sendMessage(plugin.getLang().t("usage.setwarp")); return true; }
        String name = args[0];
        try { store.setWarp(name, p.getLocation()); } catch (IOException e) { p.sendMessage(plugin.getLang().t("common.save_failed")); return true; }
        p.sendMessage(plugin.getLang().tr("warps.set", "name", name));
        return true;
    }

    private boolean handleWarp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        Player p = (Player) sender;
        if (args.length < 1) { p.sendMessage(plugin.getLang().t("usage.warp")); return true; }
        String name = args[0];
        Location loc = store.getWarp(name);
        if (loc == null) { p.sendMessage(plugin.getLang().tr("warps.not_found", "name", name)); return true; }
        if (!ensurePaid(p, "warp")) { return true; }
        try { store.setBack(p.getUniqueId(), p.getLocation()); } catch (Exception ignored) {}
        int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
        TeleportUtil.delayedTeleportWithAnimation(plugin, p, loc, delay, () -> p.sendMessage(plugin.getLang().tr("warps.arrived", "name", name)));
        return true;
    }

    private boolean handleDelWarp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("novateleport.admin")) { sender.sendMessage(plugin.getLang().t("command.no_permission")); return true; }
        if (args.length < 1) { sender.sendMessage(plugin.getLang().t("usage.delwarp")); return true; }
        String name = args[0];
        try { store.delWarp(name); } catch (IOException e) { sender.sendMessage(plugin.getLang().t("common.delete_failed")); return true; }
        sender.sendMessage(plugin.getLang().tr("warps.deleted", "name", name));
        return true;
    }

    private boolean handleWarps(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().tr("warps.list", "list", String.join(", ", store.listWarps()))); return true; }
        Player p = (Player) sender;
        List<String> list = store.listWarps();
        if (list.isEmpty()) { p.sendMessage(plugin.getLang().t("warps.none")); return true; }
        if (BedrockUtil.isBedrock(p)) {
            p.sendMessage("§6" + plugin.getLang().t("menu.warps.title") + ": §f" + String.join(", ", list));
        } else {
            String title = plugin.getLang().t("menu.warps.title");
            Inventory inv = Bukkit.createInventory(p, 27, title);
            for (String name : list) {
                ItemStack it = new ItemStack(Material.ENDER_EYE);
                ItemMeta im = it.getItemMeta();
                im.setDisplayName(plugin.getLang().tr("display.warp.item", "name", name));
                it.setItemMeta(im);
                inv.addItem(it);
            }
            p.openInventory(inv);
        }
        return true;
    }

    private boolean handleSpawn(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        Player p = (Player) sender;
        Location loc = p.getWorld().getSpawnLocation();
        if (!ensurePaid(p, "spawn")) { return true; }
        try { store.setBack(p.getUniqueId(), p.getLocation()); } catch (Exception ignored) {}
        int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
        TeleportUtil.delayedTeleportWithAnimation(plugin, p, loc, delay, () -> p.sendMessage(plugin.getLang().t("spawn.done")));
        return true;
    }

    private boolean handleBack(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        Player p = (Player) sender;
        Location back = store.getBack(p.getUniqueId());
        if (back == null) { p.sendMessage(plugin.getLang().t("back.none")); return true; }
        if (!ensurePaid(p, "back")) { return true; }
        int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
        TeleportUtil.delayedTeleportWithAnimation(plugin, p, back, delay, () -> p.sendMessage(plugin.getLang().t("back.done")));
        return true;
    }

    private boolean handleRtp(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        Player p = (Player) sender;
        World world = p.getWorld();
        Location dest = null;
        if (plugin.getRtpPoolManager() != null) {
            dest = plugin.getRtpPoolManager().poll(world);
        }
        if (dest == null) {
            dest = com.novamclabs.util.RTPUtil.findSafeLocation(plugin, world, new Random());
        }
        if (dest == null) { p.sendMessage(plugin.getLang().t("rtp.no_safe")); return true; }
        if (!ensurePaid(p, "rtp")) { return true; }
        try { store.setBack(p.getUniqueId(), p.getLocation()); } catch (Exception ignored) {}
        int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
        TeleportUtil.delayedTeleportWithAnimation(plugin, p, dest, delay, () -> p.sendMessage(plugin.getLang().t("rtp.done")));
        return true;
    }

    private boolean handleRtpGui(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        Player p = (Player) sender;
        openRtpGui(p);
        return true;
    }

    private void openRtpGui(Player p) {
        int defaultRadius = plugin.getConfig().getInt("rtp.radius", 2000);
        rtpRadiusChoices.putIfAbsent(p.getUniqueId(), defaultRadius);
        int radius = rtpRadiusChoices.get(p.getUniqueId());
        String title = plugin.getLang().t("menu.rtp.title");
        Inventory inv = Bukkit.createInventory(p, 27, title);
        int step = plugin.getConfig().getInt("rtp.gui.step", 500);
        inv.setItem(11, named(new ItemStack(Material.REDSTONE), "§c" + plugin.getLang().tr("menu.rtp.decrease", "step", step)));
        inv.setItem(13, named(new ItemStack(Material.COMPASS), "§e" + plugin.getLang().tr("menu.rtp.current", "radius", radius)));
        inv.setItem(15, named(new ItemStack(Material.SLIME_BALL), "§a" + plugin.getLang().tr("menu.rtp.increase", "step", step)));
        inv.setItem(22, named(new ItemStack(Material.ENDER_PEARL), "§d" + plugin.getLang().t("menu.rtp.start")));
        p.openInventory(inv);
    }

    private boolean handleTpMenu(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        Player p = (Player) sender;
        if (BedrockUtil.isBedrock(p)) {
            p.sendMessage(plugin.getLang().t("bedrock.menu.header"));
            p.sendMessage(plugin.getLang().t("bedrock.menu.tip.homes"));
            p.sendMessage(plugin.getLang().t("bedrock.menu.tip.warps"));
            p.sendMessage(plugin.getLang().t("bedrock.menu.tip.rtp"));
            p.sendMessage(plugin.getLang().t("bedrock.menu.tip.back"));
        } else {
            String title = plugin.getLang().t("menu.main.title");
            Inventory inv = Bukkit.createInventory(p, 27, title);
            inv.setItem(10, named(new ItemStack(Material.RED_BED), "§a" + plugin.getLang().t("menu.main.homes")));
            inv.setItem(12, named(new ItemStack(Material.ENDER_EYE), "§b" + plugin.getLang().t("menu.main.warps")));
            inv.setItem(14, named(new ItemStack(Material.ENDER_PEARL), "§d" + plugin.getLang().t("menu.main.rtp")));
            inv.setItem(16, named(new ItemStack(Material.COMPASS), "§e" + plugin.getLang().t("menu.main.back")));
            p.openInventory(inv);
        }
        return true;
    }

    private ItemStack named(ItemStack it, String name) {
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        it.setItemMeta(im);
        return it;
    }

    private boolean ensurePaid(Player p, String actionKey) {
        double cost = com.novamclabs.util.EconomyUtil.getCost(plugin, actionKey);
        if (!com.novamclabs.util.EconomyUtil.charge(plugin, p, cost)) {
            p.sendMessage(plugin.getLang().tr("economy.not_enough", "amount", com.novamclabs.util.EconomyUtil.format(cost)));
            return false;
        }
        return true;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (e.getView().getTitle() == null) return;
        String title = e.getView().getTitle();
        String mainTitle = plugin.getLang().t("menu.main.title");
        String warpsTitle = plugin.getLang().t("menu.warps.title");
        String homesTitle = plugin.getLang().t("menu.homes.title");
        String rtpTitle = plugin.getLang().t("menu.rtp.title");
        if (!title.equals(mainTitle) && !title.equals(warpsTitle) && !title.equals(homesTitle) && !title.equals(rtpTitle)) return;
        e.setCancelled(true);
        Player p = (Player) e.getWhoClicked();
        ItemStack current = e.getCurrentItem();
        if (current == null || !current.hasItemMeta() || !current.getItemMeta().hasDisplayName()) return;
        String name = current.getItemMeta().getDisplayName();
        if (title.equals(mainTitle)) {
            String nHomes = plugin.getLang().t("menu.main.homes");
            String nWarps = plugin.getLang().t("menu.main.warps");
            String nRtp = plugin.getLang().t("menu.main.rtp");
            String nBack = plugin.getLang().t("menu.main.back");
            String stripped = ChatColor.stripColor(name);
            if (stripped.contains(nHomes)) { p.closeInventory(); handleHomes(p); }
            else if (stripped.contains(nWarps)) { p.closeInventory(); handleWarps(p); }
            else if (stripped.contains(nRtp)) { p.closeInventory(); openRtpGui(p); }
            else if (stripped.contains(nBack)) { p.closeInventory(); handleBack(p); }
        } else if (title.equals(warpsTitle)) {
            String prefix = plugin.getLang().t("display.warp.prefix");
            String warp = ChatColor.stripColor(name).replace(prefix, "").trim();
            p.closeInventory(); handleWarp(p, new String[]{warp});
        } else if (title.equals(homesTitle)) {
            String prefix = plugin.getLang().t("display.home.prefix");
            String home = ChatColor.stripColor(name).replace(prefix, "").trim();
            p.closeInventory(); handleHome(p, new String[]{home});
        } else if (title.equals(rtpTitle)) {
            int step = plugin.getConfig().getInt("rtp.gui.step", 500);
            String dec = plugin.getLang().tr("menu.rtp.decrease", "step", step);
            String inc = plugin.getLang().tr("menu.rtp.increase", "step", step);
            String currentLabel = plugin.getLang().t("menu.rtp.current");
            String start = plugin.getLang().t("menu.rtp.start");
            String stripped = ChatColor.stripColor(name);
            if (stripped.contains(dec.replace("§", ""))) {
                int radius = rtpRadiusChoices.getOrDefault(p.getUniqueId(), plugin.getConfig().getInt("rtp.radius", 2000));
                radius = Math.max(step, radius - step);
                rtpRadiusChoices.put(p.getUniqueId(), radius);
                openRtpGui(p);
            } else if (stripped.contains(inc.replace("§", ""))) {
                int radius = rtpRadiusChoices.getOrDefault(p.getUniqueId(), plugin.getConfig().getInt("rtp.radius", 2000));
                int max = com.novamclabs.util.RTPUtil.loadSettings(plugin, p.getWorld()).radius;
                radius = Math.min(max, radius + step);
                rtpRadiusChoices.put(p.getUniqueId(), radius);
                openRtpGui(p);
            } else if (stripped.contains(start.replace("§", ""))) {
                p.closeInventory();
                int radius = rtpRadiusChoices.getOrDefault(p.getUniqueId(), plugin.getConfig().getInt("rtp.radius", 2000));
                java.util.Random rnd = new java.util.Random();
                org.bukkit.Location dest = com.novamclabs.util.RTPUtil.findSafeLocation(plugin, p.getWorld(), rnd, radius);
                if (dest == null) {
                    p.sendMessage(plugin.getLang().t("rtp.no_safe"));
                } else {
                    if (!ensurePaid(p, "rtp")) { return; }
                    try { store.setBack(p.getUniqueId(), p.getLocation()); } catch (Exception ignored) {}
                    int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
                    com.novamclabs.util.TeleportUtil.delayedTeleportWithAnimation(plugin, p, dest, delay, () -> p.sendMessage(plugin.getLang().t("rtp.done")));
                }
            }
        }
    }

    // Tab 补全
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("tpa") || cmd.equals("tpahere")) {
            if (args.length == 1) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase())).collect(Collectors.toList());
            }
        } else if (cmd.equals("home") || cmd.equals("delhome")) {
            if (sender instanceof Player) {
                Player p = (Player) sender;
                List<String> list = store.listHomes(p.getUniqueId());
                if (args.length == 1) return list.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
            }
        } else if (cmd.equals("warp") || cmd.equals("delwarp")) {
            List<String> list = store.listWarps();
            if (args.length == 1) return list.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
