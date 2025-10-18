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
            case "tpmenu": return handleTpMenu(sender);
        }
        return false;
    }

    private boolean handleTpa(CommandSender sender, String[] args, boolean here) {
        if (!(sender instanceof Player)) { sender.sendMessage("仅玩家可用。"); return true; }
        Player requester = (Player) sender;
        if (args.length < 1) { requester.sendMessage("§e用法: /" + (here?"tpahere":"tpa") + " <玩家>"); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { requester.sendMessage("§c玩家不在线。"); return true; }
        if (target.getUniqueId().equals(requester.getUniqueId())) { requester.sendMessage("§c不能请求自己。"); return true; }

        long expireAt = System.currentTimeMillis() + 60_000; // 60秒过期
        TpaRequest req = new TpaRequest(requester.getUniqueId(), target.getUniqueId(), here, expireAt);
        incoming.put(target.getUniqueId(), req);
        outgoing.put(requester.getUniqueId(), req);

        requester.sendMessage("§a已向 §e" + target.getName() + " §a发送传送请求，60秒内有效。");
        // 目标收到请求
        target.sendMessage("§e玩家 §a" + requester.getName() + (here?" §e请求你传送到他的位置":" §e请求传送到你的位置") + "。输入 §a/tpaccept §e接受 或 §c/tpdeny §e拒绝。");
        // Bedrock 模态表单（若可用）省略：无依赖时退化为聊天提示。
        return true;
    }

    private boolean handleTpAccept(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage("仅玩家可用。"); return true; }
        Player target = (Player) sender;
        TpaRequest req = incoming.get(target.getUniqueId());
        if (req == null || req.expireAt < System.currentTimeMillis()) { target.sendMessage("§c没有有效的请求。"); return true; }
        Player requester = Bukkit.getPlayer(req.requester);
        if (requester == null) { target.sendMessage("§c请求者已离线。"); cleanup(req); return true; }

        // 传送对象
        Player mover = req.here ? target : requester;
        Location dest = (req.here ? requester : target).getLocation();
        try { if (store != null) store.setBack(mover.getUniqueId(), mover.getLocation()); } catch (Exception ignored) {}
        int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
        BukkitTask task = TeleportUtil.delayedTeleportWithAnimation(plugin, mover, dest, delay, () -> {
            cleanup(req);
            mover.sendMessage("§a传送请求已接受，传送完成！");
        });
        mover.sendMessage("§e传送请求已接受，开始传送...");
        if (task == null) cleanup(req);
        return true;
    }

    private boolean handleTpDeny(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage("仅玩家可用。"); return true; }
        Player target = (Player) sender;
        TpaRequest req = incoming.remove(target.getUniqueId());
        if (req == null) { target.sendMessage("§c没有待处理的请求。"); return true; }
        Player requester = Bukkit.getPlayer(req.requester);
        if (requester != null) requester.sendMessage("§c你的传送请求被 §e" + target.getName() + " §c拒绝。");
        outgoing.remove(req.requester);
        target.sendMessage("§a已拒绝传送请求。");
        return true;
    }

    private boolean handleTpCancel(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage("仅玩家可用。"); return true; }
        Player requester = (Player) sender;
        TpaRequest req = outgoing.remove(requester.getUniqueId());
        if (req == null) { requester.sendMessage("§c你没有已发送的请求。"); return true; }
        incoming.remove(req.target);
        Player target = Bukkit.getPlayer(req.target);
        if (target != null) target.sendMessage("§e对方已取消传送请求。");
        requester.sendMessage("§a已取消。");
        return true;
    }

    private void cleanup(TpaRequest req) {
        incoming.remove(req.target);
        outgoing.remove(req.requester);
    }

    // 家系统
    private boolean handleSetHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("仅玩家可用。"); return true; }
        Player p = (Player) sender;
        String name = args.length >= 1 ? args[0] : "home";
        int limit = getHomeLimit(p);
        List<String> current = store.listHomes(p.getUniqueId());
        if (!current.contains(name.toLowerCase(Locale.ROOT)) && current.size() >= limit) {
            p.sendMessage("§c你的家数量已达上限 (" + limit + ")。");
            return true;
        }
        try { store.setHome(p.getUniqueId(), name, p.getLocation()); } catch (IOException e) { p.sendMessage("§c保存失败。"); return true; }
        p.sendMessage("§a已设置家: §e" + name);
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
        if (!(sender instanceof Player)) { sender.sendMessage("仅玩家可用。"); return true; }
        Player p = (Player) sender;
        String name = args.length >= 1 ? args[0] : "home";
        Location loc = store.getHome(p.getUniqueId(), name);
        if (loc == null) { p.sendMessage("§c未找到家: " + name); return true; }
        try { store.setBack(p.getUniqueId(), p.getLocation()); } catch (Exception ignored) {}
        int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
        TeleportUtil.delayedTeleportWithAnimation(plugin, p, loc, delay, () -> p.sendMessage("§a欢迎回家！"));
        return true;
    }

    private boolean handleDelHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("仅玩家可用。"); return true; }
        Player p = (Player) sender;
        String name = args.length >= 1 ? args[0] : "home";
        try { store.delHome(p.getUniqueId(), name); } catch (IOException e) { p.sendMessage("§c删除失败。"); return true; }
        p.sendMessage("§a已删除家: §e" + name);
        return true;
    }

    private boolean handleHomes(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage("仅玩家可用。"); return true; }
        Player p = (Player) sender;
        List<String> list = store.listHomes(p.getUniqueId());
        if (list.isEmpty()) { p.sendMessage("§e你还没有设置家的位置。"); return true; }
        if (BedrockUtil.isBedrock(p)) {
            // 简化：基岩版暂用文本列表
            p.sendMessage("§6你的家: §f" + String.join(", ", list));
        } else {
            Inventory inv = Bukkit.createInventory(p, 27, "我的家");
            for (String name : list) {
                ItemStack it = new ItemStack(Material.ENDER_PEARL);
                ItemMeta im = it.getItemMeta();
                im.setDisplayName("§a家: " + name);
                it.setItemMeta(im);
                inv.addItem(it);
            }
            p.openInventory(inv);
        }
        return true;
    }

    // 传送点
    private boolean handleSetWarp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("仅玩家可用。"); return true; }
        if (!sender.hasPermission("novateleport.admin")) { sender.sendMessage("§c没有权限。"); return true; }
        Player p = (Player) sender;
        if (args.length < 1) { p.sendMessage("§e用法: /setwarp <名称>"); return true; }
        String name = args[0];
        try { store.setWarp(name, p.getLocation()); } catch (IOException e) { p.sendMessage("§c保存失败。"); return true; }
        p.sendMessage("§a已设置公共传送点: §e" + name);
        return true;
    }

    private boolean handleWarp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("仅玩家可用。"); return true; }
        Player p = (Player) sender;
        if (args.length < 1) { p.sendMessage("§e用法: /warp <名称>"); return true; }
        String name = args[0];
        Location loc = store.getWarp(name);
        if (loc == null) { p.sendMessage("§c未找到传送点: " + name); return true; }
        try { store.setBack(p.getUniqueId(), p.getLocation()); } catch (Exception ignored) {}
        int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
        TeleportUtil.delayedTeleportWithAnimation(plugin, p, loc, delay, () -> p.sendMessage("§a已到达传送点 §e" + name));
        return true;
    }

    private boolean handleDelWarp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("novateleport.admin")) { sender.sendMessage("§c没有权限。"); return true; }
        if (args.length < 1) { sender.sendMessage("§e用法: /delwarp <名称>"); return true; }
        String name = args[0];
        try { store.delWarp(name); } catch (IOException e) { sender.sendMessage("§c删除失败。"); return true; }
        sender.sendMessage("§a已删除传送点: §e" + name);
        return true;
    }

    private boolean handleWarps(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage("可用传送点: " + String.join(", ", store.listWarps())); return true; }
        Player p = (Player) sender;
        List<String> list = store.listWarps();
        if (list.isEmpty()) { p.sendMessage("§e没有公共传送点。"); return true; }
        if (BedrockUtil.isBedrock(p)) {
            p.sendMessage("§6公共传送点: §f" + String.join(", ", list));
        } else {
            Inventory inv = Bukkit.createInventory(p, 27, "公共传送点");
            for (String name : list) {
                ItemStack it = new ItemStack(Material.ENDER_EYE);
                ItemMeta im = it.getItemMeta();
                im.setDisplayName("§b传送点: " + name);
                it.setItemMeta(im);
                inv.addItem(it);
            }
            p.openInventory(inv);
        }
        return true;
    }

    private boolean handleSpawn(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage("仅玩家可用。"); return true; }
        Player p = (Player) sender;
        Location loc = p.getWorld().getSpawnLocation();
        try { store.setBack(p.getUniqueId(), p.getLocation()); } catch (Exception ignored) {}
        int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
        TeleportUtil.delayedTeleportWithAnimation(plugin, p, loc, delay, () -> p.sendMessage("§a已传送至出生点"));
        return true;
    }

    private boolean handleBack(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage("仅玩家可用。"); return true; }
        Player p = (Player) sender;
        Location back = store.getBack(p.getUniqueId());
        if (back == null) { p.sendMessage("§c没有可返回的位置。"); return true; }
        int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
        TeleportUtil.delayedTeleportWithAnimation(plugin, p, back, delay, () -> p.sendMessage("§a已返回上一个位置"));
        return true;
    }

    private boolean handleRtp(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage("仅玩家可用。"); return true; }
        Player p = (Player) sender;
        World world = p.getWorld();
        int radius = plugin.getConfig().getInt("rtp.radius", 2000);
        int tries = plugin.getConfig().getInt("rtp.tries", 20);
        Random rnd = new Random();
        Location dest = null;
        for (int i = 0; i < tries; i++) {
            double x = rnd.nextDouble() * radius * (rnd.nextBoolean()?1:-1);
            double z = rnd.nextDouble() * radius * (rnd.nextBoolean()?1:-1);
            Location test = new Location(world, x, world.getHighestBlockYAt((int) x, (int) z) + 1, z);
            Material feet = test.clone().add(0, -1, 0).getBlock().getType();
            if (!feet.isAir() && feet.isSolid()) { dest = test; break; }
        }
        if (dest == null) { p.sendMessage("§c未找到安全位置，请重试。"); return true; }
        try { store.setBack(p.getUniqueId(), p.getLocation()); } catch (Exception ignored) {}
        int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
        TeleportUtil.delayedTeleportWithAnimation(plugin, p, dest, delay, () -> p.sendMessage("§a随机传送完成"));
        return true;
    }

    private boolean handleTpMenu(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage("仅玩家可用。"); return true; }
        Player p = (Player) sender;
        if (BedrockUtil.isBedrock(p)) {
            p.sendMessage("§e[基岩版表单暂未启用依赖，已回退为聊天菜单]");
            p.sendMessage(" - 输入 /homes 查看我的家");
            p.sendMessage(" - 输入 /warps 查看公共传送点");
            p.sendMessage(" - 输入 /rtp 随机传送");
            p.sendMessage(" - 输入 /back 返回地点");
        } else {
            Inventory inv = Bukkit.createInventory(p, 27, "传送菜单");
            inv.setItem(10, named(new ItemStack(Material.RED_BED), "§a我的家"));
            inv.setItem(12, named(new ItemStack(Material.ENDER_EYE), "§b公共传送点"));
            inv.setItem(14, named(new ItemStack(Material.ENDER_PEARL), "§d随机传送"));
            inv.setItem(16, named(new ItemStack(Material.COMPASS), "§e返回地点"));
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

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (e.getView().getTitle() == null) return;
        String title = e.getView().getTitle();
        if (!title.equals("传送菜单") && !title.equals("公共传送点") && !title.equals("我的家")) return;
        e.setCancelled(true);
        Player p = (Player) e.getWhoClicked();
        ItemStack current = e.getCurrentItem();
        if (current == null || !current.hasItemMeta() || !current.getItemMeta().hasDisplayName()) return;
        String name = current.getItemMeta().getDisplayName();
        if (title.equals("传送菜单")) {
            if (name.contains("我的家")) { p.closeInventory(); handleHomes(p); }
            else if (name.contains("公共传送点")) { p.closeInventory(); handleWarps(p); }
            else if (name.contains("随机传送")) { p.closeInventory(); handleRtp(p); }
            else if (name.contains("返回地点")) { p.closeInventory(); handleBack(p); }
        } else if (title.equals("公共传送点")) {
            String warp = ChatColor.stripColor(name).replace("传送点: ", "").trim();
            p.closeInventory(); handleWarp(p, new String[]{warp});
        } else if (title.equals("我的家")) {
            String home = ChatColor.stripColor(name).replace("家: ", "").trim();
            p.closeInventory(); handleHome(p, new String[]{home});
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
