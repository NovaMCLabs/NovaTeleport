package com.novamclabs.commands;

import com.novamclabs.StarTeleport;
import com.novamclabs.common.scheduler.SchedulerWrapper;
import com.novamclabs.menu.JavaMenuConfig;
import com.novamclabs.storage.DataStore;
import com.novamclabs.util.BedrockUtil;
import com.novamclabs.util.TeleportUtil;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TeleportCommandHandler implements CommandExecutor, TabCompleter, Listener {
    private final StarTeleport plugin;
    private final DataStore store;
    private final Map<UUID, Integer> rtpRadiusChoices = new ConcurrentHashMap<>();

    private final JavaMenuConfig menus;
    private final NamespacedKey keyAction;
    private final NamespacedKey keyValue;

    private static final long TPA_EXPIRE_MILLIS = 60_000L;

    private static final class MenuHolder implements InventoryHolder {
        private final String id;
        private Inventory inventory;

        private MenuHolder(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        private void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public TeleportCommandHandler(StarTeleport plugin) {
        this.plugin = plugin;
        this.store = plugin.getDataStore();
        this.menus = plugin.getJavaMenus();
        this.keyAction = new NamespacedKey(plugin, "menu_action");
        this.keyValue = new NamespacedKey(plugin, "menu_value");
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // 跨服消息处理 | cross-server message handling
        if (plugin.getCrossServerService() != null) {
            plugin.getCrossServerService().setHandler(this::onCrossServerMessage);
        }

        // 定期清理过期请求，避免内存中残留 | sweep expired requests
        plugin.getScheduler().runTimer(this::sweepExpired, 600L, 600L);
    }

    // TPA 请求管理
    private static class TpaRequest {
        final UUID requester;
        /** 跨服请求时请求方不在本服，无法通过 UUID 解析名字，因此显式保存 */
        final String requesterName;
        final UUID target;
        final boolean here; // true 表示 /tpahere
        final long expireAt;
        final boolean crossServer;
        final String requesterServer; // 跨服时请求方所在服务器
        TpaRequest(UUID requester, String requesterName, UUID target, boolean here, long expireAt, boolean crossServer, String requesterServer) {
            this.requester = requester; this.requesterName = requesterName; this.target = target; this.here = here; this.expireAt = expireAt;
            this.crossServer = crossServer; this.requesterServer = requesterServer;
        }
    }
    private final Map<UUID, TpaRequest> incoming = new ConcurrentHashMap<>(); // target -> request
    private final Map<UUID, TpaRequest> outgoing = new ConcurrentHashMap<>(); // requester -> request

    /** 跨服请求被接受后，等待“换服到达”的玩家：小写名字 -> 要抵达的目标玩家 */
    private static final class Arrival {
        final UUID meet;
        final long expireAt;
        Arrival(UUID meet, long expireAt) { this.meet = meet; this.expireAt = expireAt; }
    }
    private final Map<String, Arrival> arrivals = new ConcurrentHashMap<>();

    private void sweepExpired() {
        long now = System.currentTimeMillis();
        incoming.values().removeIf(r -> r.expireAt < now);
        outgoing.values().removeIf(r -> r.expireAt < now);
        arrivals.values().removeIf(a -> a.expireAt < now);
    }

    /**
     * 跨服请求：对方换服到达本服后，把它送到申请者身边。
     * 代理只能把玩家送到目标服的登录点，因此“到达后再定位”这一步由服务端完成。
     */
    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        Player joined = e.getPlayer();
        Arrival arrival = arrivals.remove(joined.getName().toLowerCase(Locale.ROOT));
        if (arrival == null) return;
        if (arrival.expireAt < System.currentTimeMillis()) return;

        deliverArrival(joined, arrival.meet);
    }

    /** 把换服到达的玩家送到会合玩家身边；进服与迟到通知两条路径共用 | shared arrival delivery */
    private void deliverArrival(Player arriving, UUID meetId) {
        Player meet = Bukkit.getPlayer(meetId);
        if (meet == null) {
            arriving.sendMessage(plugin.getLang().t("tpa.cross.meet_offline"));
            return;
        }
        TeleportUtil.delayedTeleportWithAnimation(plugin, arriving, meet.getLocation(), 0, "tpa", () ->
                arriving.sendMessage(plugin.getLang().t("tpa.accepted.complete")));
    }

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
            case "rtp": return handleRtp(sender, args);
            case "rtpgui": return handleRtp(sender, args);
            case "tpmenu": return handleTpMenu(sender);
        }
        return false;
    }

    // ===== TPA =====

    private boolean handleTpa(CommandSender sender, String[] args, boolean here) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        Player requester = (Player) sender;
        if (args.length < 1) { requester.sendMessage(plugin.getLang().t(here?"usage.tpahere":"usage.tpa")); return true; }
        // Floodgate 会给基岩版名字加 "." 前缀，必须走兼容查找，否则 /tpa 永远报“玩家不在线”
        Player target = BedrockUtil.findPlayer(args[0]);
        if (target == null) {
            // 目标不在本服：尝试通过 Redis 转发到其所在服务器
            if (plugin.getCrossServerService() != null && plugin.getCrossServerService().isActive()
                    && plugin.getCrossServerService().publishTpaRequest(args[0], requester.getName(), here)) {
                TpaRequest req = new TpaRequest(requester.getUniqueId(), requester.getName(), null, here,
                        System.currentTimeMillis() + TPA_EXPIRE_MILLIS, true, plugin.getCrossServerService().getServerName());
                outgoing.put(requester.getUniqueId(), req);
                requester.sendMessage(plugin.getLang().tr("tpa.cross.sent", "target", args[0], "seconds", TPA_EXPIRE_MILLIS / 1000));
                return true;
            }
            requester.sendMessage(plugin.getLang().t("common.no_online_player"));
            return true;
        }
        if (target.getUniqueId().equals(requester.getUniqueId())) { requester.sendMessage(plugin.getLang().t("common.cannot_target_self")); return true; }

        long expireAt = System.currentTimeMillis() + TPA_EXPIRE_MILLIS;
        TpaRequest req = new TpaRequest(requester.getUniqueId(), requester.getName(), target.getUniqueId(), here, expireAt, false, null);
        incoming.put(target.getUniqueId(), req);
        outgoing.put(requester.getUniqueId(), req);

        requester.sendMessage(plugin.getLang().tr("tpa.sent", "target", target.getName(), "seconds", TPA_EXPIRE_MILLIS / 1000));
        promptTarget(target, requester.getName(), here);
        return true;
    }

    /** 向目标玩家展示接受/拒绝（基岩版用表单，Java 版用可点击消息）| prompt the target */
    private void promptTarget(Player target, String requesterName, boolean here) {
        if (BedrockUtil.isBedrock(target)) {
            boolean sent = com.novamclabs.util.BedrockFormsUtil.showTpaRequestForm(plugin, target, requesterName, here);
            if (!sent) {
                target.sendMessage(plugin.getLang().tr(here?"tpa.prompt.to_here":"tpa.prompt.to_you", "requester", requesterName));
            }
            return;
        }
        com.novamclabs.util.ChatCompat.sendAcceptDeny(target,
                plugin.getLang().t("tpa.click.accept"), "/tpaccept",
                plugin.getLang().t("tpa.click.deny"), "/tpdeny");
        target.sendMessage(plugin.getLang().tr(here?"tpa.prompt.to_here":"tpa.prompt.to_you", "requester", requesterName));
    }

    private boolean handleTpAccept(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        Player target = (Player) sender;
        TpaRequest req = incoming.get(target.getUniqueId());
        if (req == null || req.expireAt < System.currentTimeMillis()) {
            incoming.remove(target.getUniqueId());
            target.sendMessage(plugin.getLang().t("tpa.no_request"));
            return true;
        }

        if (req.crossServer) {
            // /tpahere：换服的是目标本人（就在本服），因此校验与扣费都在这里完成；
            // /tpa：换服的是请求方，由请求方所在服务器在收到 tpa_accept 时处理。
            if (req.here) {
                if (!passesCrossServerGates(target, "tpahere")) return true;
                if (!ensurePaid(target, "tpahere")) return true;
            }
            incoming.remove(target.getUniqueId());
            long expireAt = System.currentTimeMillis() + TPA_EXPIRE_MILLIS;
            if (req.here) {
                // /tpahere：目标需要前往请求方所在的服务器，目标就在本服，直接切服。
                // 同时告知请求方所在服务器：该玩家到达后要送到请求者身边。
                Map<String, String> notice = new LinkedHashMap<>();
                notice.put("type", "tpa_arriving");
                notice.put("arriving", target.getName());
                notice.put("meet", req.requesterName == null ? "" : req.requesterName);
                if (plugin.getCrossServerService() != null) plugin.getCrossServerService().publish(notice);
                target.sendMessage(plugin.getLang().tr("tpa.cross.travel", "server", req.requesterServer));
                com.novamclabs.util.ProxyMessenger.connect(plugin, target, req.requesterServer);
                recordCrossServerCooldown(target, "tpahere");
            } else {
                // /tpa：请求方会前来本服，登记到达后要见的目标，并通知请求方所在服务器
                if (req.requesterName != null && !req.requesterName.isEmpty()) {
                    arrivals.put(req.requesterName.toLowerCase(Locale.ROOT), new Arrival(target.getUniqueId(), expireAt));
                }
                notifyRequesterServer("tpa_accept", req, target.getName());
                target.sendMessage(plugin.getLang().tr("tpa.cross.accepted", "requester", req.requesterName == null ? "?" : req.requesterName));
            }
            return true;
        }

        Player requester = Bukkit.getPlayer(req.requester);
        if (requester == null) { target.sendMessage(plugin.getLang().t("tpa.requester_offline")); cleanup(req); return true; }

        // 传送对象：/tpa 时请求方移动；/tpahere 时目标移动
        Player mover = req.here ? target : requester;
        Location dest = (req.here ? requester : target).getLocation();
        String actionKey = req.here ? "tpahere" : "tpa";
        int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
        SchedulerWrapper.ScheduledTask task = TeleportUtil.delayedTeleportWithAnimation(plugin, mover, dest, delay, actionKey, () -> {
            cleanup(req);
            mover.sendMessage(plugin.getLang().t("tpa.accepted.complete"));
        });
        mover.sendMessage(plugin.getLang().t("tpa.accepted.start"));
        if (task == null) cleanup(req);
        return true;
    }

    private void notifyRequesterServer(String type, TpaRequest req, String targetName) {
        if (plugin.getCrossServerService() == null) return;
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", type);
        data.put("requester", req.requesterName == null ? "" : req.requesterName);
        data.put("target", targetName);
        data.put("here", Boolean.toString(req.here));
        plugin.getCrossServerService().publish(data);
    }

    private boolean handleTpDeny(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        Player target = (Player) sender;
        TpaRequest req = incoming.remove(target.getUniqueId());
        if (req == null) { target.sendMessage(plugin.getLang().t("tpa.none_pending")); return true; }
        if (req.crossServer) {
            notifyRequesterServer("tpa_deny", req, target.getName());
            target.sendMessage(plugin.getLang().t("tpa.denied.target"));
            return true;
        }
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
        if (req.target != null) incoming.remove(req.target);
        Player target = req.target != null ? Bukkit.getPlayer(req.target) : null;
        if (target != null) target.sendMessage(plugin.getLang().t("tpa.cancelled.target"));
        requester.sendMessage(plugin.getLang().t("tpa.cancelled.requester"));
        return true;
    }

    private void cleanup(TpaRequest req) {
        if (req.target != null) incoming.remove(req.target);
        outgoing.remove(req.requester);
    }

    /** 处理来自其他服务器的消息（已切回主线程）| handle a message relayed from another server */
    private void onCrossServerMessage(String type, Map<String, String> data) {
        switch (type) {
            case "tpa": {
                String targetName = data.get("target");
                String requesterName = data.get("requester");
                if (targetName == null || requesterName == null) return;
                Player target = BedrockUtil.findPlayer(targetName);
                if (target == null) return;
                boolean here = Boolean.parseBoolean(data.getOrDefault("here", "false"));
                TpaRequest req = new TpaRequest(null, requesterName, target.getUniqueId(), here,
                        System.currentTimeMillis() + TPA_EXPIRE_MILLIS, true, data.get("server"));
                incoming.put(target.getUniqueId(), req);
                promptTarget(target, requesterName, here);
                break;
            }
            case "tpa_arriving": {
                // 另一台服务器上的玩家即将换到本服，到达后应被送到 meetPlayer 身边。
                // 只有 /tpahere 会产生这条消息（/tpa 走 tpa_accept），因此本服必须存在一条
                // meet 自己发起的、跨服的、here=true 的待处理请求；否则任何人都能往频道里
                // 发一条消息，让指定玩家被送到攻击者指定的玩家身边。
                String arriving = data.get("arriving");
                String meetName = data.get("meet");
                if (arriving == null || arriving.isEmpty() || meetName == null || meetName.isEmpty()) return;
                Player meet = BedrockUtil.findPlayer(meetName);
                if (meet == null) return;
                TpaRequest pending = outgoing.get(meet.getUniqueId());
                if (pending == null || !pending.crossServer || !pending.here) return;
                outgoing.remove(meet.getUniqueId());
                Player arrivingPlayer = BedrockUtil.findPlayer(arriving);
                if (arrivingPlayer != null) {
                    // 通知可能晚于玩家进服（Redis 延迟或重连），此时直接完成会合，
                    // 结果不再取决于通知与进服的先后顺序。
                    deliverArrival(arrivingPlayer, meet.getUniqueId());
                    break;
                }
                arrivals.put(arriving.toLowerCase(Locale.ROOT),
                        new Arrival(meet.getUniqueId(), System.currentTimeMillis() + TPA_EXPIRE_MILLIS));
                break;
            }
            case "tpa_accept": {
                String requesterName = data.get("requester");
                if (requesterName == null || requesterName.isEmpty()) return;
                Player requester = BedrockUtil.findPlayer(requesterName);
                if (requester == null) return;
                // 只处理确实由本服发起的跨服请求，避免响应伪造的 Redis 消息
                TpaRequest pending = outgoing.get(requester.getUniqueId());
                if (pending == null || !pending.crossServer) return;
                String targetServer = data.get("server");
                if (targetServer == null || targetServer.isEmpty()) return;
                // /tpa 换服的是请求方本人，请求方就在本服，校验与扣费必须在这里完成
                if (!passesCrossServerGates(requester, "tpa")) return;
                if (!ensurePaid(requester, "tpa")) return;
                outgoing.remove(requester.getUniqueId());
                requester.sendMessage(plugin.getLang().tr("tpa.cross.travel", "server", targetServer));
                com.novamclabs.util.ProxyMessenger.connect(plugin, requester, targetServer);
                recordCrossServerCooldown(requester, "tpa");
                break;
            }
            case "tpa_deny": {
                String requesterName = data.get("requester");
                if (requesterName == null || requesterName.isEmpty()) return;
                Player requester = BedrockUtil.findPlayer(requesterName);
                if (requester == null) return;
                TpaRequest pending = outgoing.get(requester.getUniqueId());
                if (pending == null || !pending.crossServer) return;
                outgoing.remove(requester.getUniqueId());
                requester.sendMessage(plugin.getLang().tr("tpa.cross.denied", "target", data.getOrDefault("target", "?")));
                break;
            }
            default:
                break;
        }
    }

    // ===== 家系统 | homes =====

    private boolean handleSetHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        Player p = (Player) sender;
        String raw = args.length >= 1 ? args[0] : "home";
        String name = DataStore.normalizeName(raw);
        if (name == null) { p.sendMessage(plugin.getLang().tr("homes.invalid_name", "name", raw)); return true; }
        int limit = getHomeLimit(p);
        List<String> current = store.listHomes(p.getUniqueId());
        if (!current.contains(name) && current.size() >= limit) {
            p.sendMessage(plugin.getLang().tr("homes.limit_reached", "limit", limit));
            return true;
        }
        try { store.setHome(p.getUniqueId(), name, p.getLocation()); } catch (IllegalArgumentException e) {
            p.sendMessage(plugin.getLang().tr("homes.invalid_name", "name", raw)); return true;
        } catch (IOException e) { p.sendMessage(plugin.getLang().t("common.save_failed")); return true; }
        p.sendMessage(plugin.getLang().tr("homes.set", "name", name));
        return true;
    }

    private int getHomeLimit(Player p) {
        return com.novamclabs.util.HomeLimitUtil.getHomeLimit(plugin, p);
    }

    private boolean handleHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        if (!requirePermission(sender, "novateleport.command.home")) return true;
        Player p = (Player) sender;
        // 无参数：打开菜单；带参数：直达
        if (args.length == 0) {
            return handleHomes(sender);
        }
        String name = DataStore.normalizeName(args[0]);
        if (name == null) { p.sendMessage(plugin.getLang().tr("homes.invalid_name", "name", args[0])); return true; }
        DataStore.Destination dest = store.getHomeDest(p.getUniqueId(), name);
        if (dest == null) { p.sendMessage(plugin.getLang().tr("homes.not_found", "name", name)); return true; }

        // 跨服家：记录的目标服务器不是本服，交给代理切服（本地无法校验该世界的存在性）
        if (isRemoteServer(dest.server)) {
            if (!passesCrossServerGates(p, "home")) return true;
            if (!ensurePaid(p, "home")) return true;
            com.novamclabs.util.ProxyMessenger.connect(plugin, p, dest.server);
            recordCrossServerCooldown(p, "home");
            p.sendMessage(plugin.getLang().tr("city.proxy", "server", dest.server));
            return true;
        }
        if (dest.location == null) { p.sendMessage(plugin.getLang().tr("homes.not_found", "name", name)); return true; }
        int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
        TeleportUtil.delayedTeleportWithAnimation(plugin, p, dest.location, delay, "home", () -> p.sendMessage(plugin.getLang().t("homes.welcome")));
        return true;
    }

    /** 目标服务器不是本服 | whether the stored destination lives on another server */
    private boolean isRemoteServer(String server) {
        if (server == null) return false;
        String myServer = plugin.getConfig().getString("network.server_name", "local");
        return !server.equalsIgnoreCase(myServer);
    }

    private boolean handleDelHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        Player p = (Player) sender;
        String name = DataStore.normalizeName(args.length >= 1 ? args[0] : "home");
        if (name == null) { p.sendMessage(plugin.getLang().tr("homes.invalid_name", "name", args[0])); return true; }
        // delHome 不报告是否真的删掉了什么，先查存在性，否则删不存在的家也会报「已删除」
        if (store.getHomeDest(p.getUniqueId(), name) == null) { p.sendMessage(plugin.getLang().tr("homes.not_found", "name", name)); return true; }
        try { store.delHome(p.getUniqueId(), name); } catch (IOException e) { p.sendMessage(plugin.getLang().t("common.delete_failed")); return true; }
        p.sendMessage(plugin.getLang().tr("homes.deleted", "name", name));
        return true;
    }

    private boolean handleHomes(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        if (!requirePermission(sender, "novateleport.command.home")) return true;
        Player p = (Player) sender;
        List<String> list = store.listHomes(p.getUniqueId());
        if (list.isEmpty()) { p.sendMessage(plugin.getLang().t("homes.none")); return true; }
        if (BedrockUtil.isBedrock(p)) {
            // 基岩版：使用表单列出并点击执行 /home <name> | Bedrock: form list -> /home <name>
            boolean ok = com.novamclabs.util.BedrockFormsUtil.showListCommandForm(plugin, p,
                    plugin.getLang().t("menu.homes.title"), list, list, "home");
            if (!ok) {
                p.sendMessage("§6" + plugin.getLang().t("menu.homes.title") + ": §f" + String.join(", ", list));
            }
        } else {
            openHomesMenu(p, list);
        }
        return true;
    }

    // ===== 传送点 | warps =====

    private boolean handleSetWarp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        if (!sender.hasPermission("novateleport.command.setwarp")) { sender.sendMessage(plugin.getLang().t("command.no_permission")); return true; }
        Player p = (Player) sender;
        if (args.length < 1) { p.sendMessage(plugin.getLang().t("usage.setwarp")); return true; }
        String name = DataStore.normalizeName(args[0]);
        if (name == null) { p.sendMessage(plugin.getLang().tr("warps.invalid_name", "name", args[0])); return true; }
        try { store.setWarp(name, p.getLocation()); } catch (IllegalArgumentException e) {
            p.sendMessage(plugin.getLang().tr("warps.invalid_name", "name", args[0])); return true;
        } catch (IOException e) { p.sendMessage(plugin.getLang().t("common.save_failed")); return true; }
        p.sendMessage(plugin.getLang().tr("warps.set", "name", name));
        return true;
    }

    private boolean handleWarp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        if (!requirePermission(sender, "novateleport.command.warp")) return true;
        Player p = (Player) sender;
        if (args.length < 1) { return handleWarps(sender); }
        String name = DataStore.normalizeName(args[0]);
        if (name == null) { p.sendMessage(plugin.getLang().tr("warps.invalid_name", "name", args[0])); return true; }
        DataStore.Destination dest = store.getWarpDest(name);
        if (dest == null) { p.sendMessage(plugin.getLang().tr("warps.not_found", "name", name)); return true; }

        // 跨服传送点：目标服务器不是本服，交给代理切服
        if (isRemoteServer(dest.server)) {
            if (!passesCrossServerGates(p, "warp")) return true;
            if (!ensurePaid(p, "warp")) return true;
            com.novamclabs.util.ProxyMessenger.connect(plugin, p, dest.server);
            recordCrossServerCooldown(p, "warp");
            p.sendMessage(plugin.getLang().tr("city.proxy", "server", dest.server));
            return true;
        }
        if (dest.location == null) { p.sendMessage(plugin.getLang().tr("warps.not_found", "name", name)); return true; }
        int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
        TeleportUtil.delayedTeleportWithAnimation(plugin, p, dest.location, delay, "warp", () -> p.sendMessage(plugin.getLang().tr("warps.arrived", "name", name)));
        return true;
    }

    private boolean handleDelWarp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("novateleport.command.setwarp")) { sender.sendMessage(plugin.getLang().t("command.no_permission")); return true; }
        if (args.length < 1) { sender.sendMessage(plugin.getLang().t("usage.delwarp")); return true; }
        String name = DataStore.normalizeName(args[0]);
        if (name == null) { sender.sendMessage(plugin.getLang().tr("warps.invalid_name", "name", args[0])); return true; }
        try { store.delWarp(name); } catch (IOException e) { sender.sendMessage(plugin.getLang().t("common.delete_failed")); return true; }
        sender.sendMessage(plugin.getLang().tr("warps.deleted", "name", name));
        return true;
    }

    private boolean handleWarps(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().tr("warps.list", "list", String.join(", ", store.listWarps()))); return true; }
        if (!requirePermission(sender, "novateleport.command.warp")) return true;
        Player p = (Player) sender;
        List<String> list = store.listWarps();
        if (list.isEmpty()) { p.sendMessage(plugin.getLang().t("warps.none")); return true; }
        if (BedrockUtil.isBedrock(p)) {
            boolean ok = com.novamclabs.util.BedrockFormsUtil.showListCommandForm(plugin, p,
                    plugin.getLang().t("menu.warps.title"), list, list, "warp");
            if (!ok) {
                p.sendMessage("§6" + plugin.getLang().t("menu.warps.title") + ": §f" + String.join(", ", list));
            }
        } else {
            openWarpsMenu(p, list);
        }
        return true;
    }

    private boolean handleSpawn(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        Player p = (Player) sender;
        Location loc = p.getWorld().getSpawnLocation();
        int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
        TeleportUtil.delayedTeleportWithAnimation(plugin, p, loc, delay, "spawn", () -> p.sendMessage(plugin.getLang().t("spawn.done")));
        return true;
    }

    private boolean handleBack(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        if (!requirePermission(sender, "novateleport.command.back")) return true;
        Player p = (Player) sender;
        Location back = store.getBack(p.getUniqueId());
        if (back == null) { p.sendMessage(plugin.getLang().t("back.none")); return true; }
        int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
        TeleportUtil.delayedTeleportWithAnimation(plugin, p, back, delay, "back", () -> p.sendMessage(plugin.getLang().t("back.done")));
        return true;
    }

    private boolean handleRtp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        if (!requirePermission(sender, "novateleport.command.rtp")) return true;
        Player p = (Player) sender;
        if (args.length == 0) {
            return handleRtpGui(sender);
        }
        // /rtp now | /rtp start | /rtp <radius>
        World world = p.getWorld();
        int maxRadius = com.novamclabs.util.RTPUtil.loadSettings(plugin, world).radius;
        int radius = -1;
        if (args.length >= 1) {
            if (args[0].equalsIgnoreCase("now") || args[0].equalsIgnoreCase("start")) {
                // keep radius default
            } else {
                try { radius = Integer.parseInt(args[0]); } catch (Exception ignored) {}
            }
        }
        if (radius > maxRadius) radius = maxRadius;
        Location dest = null;
        if (radius > 0) {
            dest = com.novamclabs.util.RTPUtil.findSafeLocation(plugin, world, new Random(), radius);
        }
        if (dest == null && plugin.getRtpPoolManager() != null) {
            dest = plugin.getRtpPoolManager().poll(world);
        }
        if (dest == null) {
            dest = com.novamclabs.util.RTPUtil.findSafeLocation(plugin, world, new Random());
        }
        if (dest == null) { p.sendMessage(plugin.getLang().t("rtp.no_safe")); return true; }
        int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
        TeleportUtil.delayedTeleportWithAnimation(plugin, p, dest, delay, "rtp", () -> p.sendMessage(plugin.getLang().t("rtp.done")));
        return true;
    }

    private boolean handleRtpGui(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        Player p = (Player) sender;
        if (BedrockUtil.isBedrock(p)) {
            int defaultRadius = plugin.getConfig().getInt("rtp.radius", 2000);
            rtpRadiusChoices.putIfAbsent(p.getUniqueId(), defaultRadius);
            int current = rtpRadiusChoices.get(p.getUniqueId());
            int step = plugin.getConfig().getInt("rtp.gui.step", 500);
            int max = com.novamclabs.util.RTPUtil.loadSettings(plugin, p.getWorld()).radius;
            boolean sent = com.novamclabs.util.BedrockFormsUtil.showRtpRadiusForm(plugin, p, current, step, max, (val) -> {
                rtpRadiusChoices.put(p.getUniqueId(), val);
                org.bukkit.Location dest = com.novamclabs.util.RTPUtil.findSafeLocation(plugin, p.getWorld(), new java.util.Random(), val);
                if (dest == null) {
                    p.sendMessage(plugin.getLang().t("rtp.no_safe"));
                } else {
                    int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
                    com.novamclabs.util.TeleportUtil.delayedTeleportWithAnimation(plugin, p, dest, delay, "rtp", () -> p.sendMessage(plugin.getLang().t("rtp.done")));
                }
            });
            if (!sent) {
                // 不能再提示 /rtp：无参 /rtp 又会回到这里，玩家会卡在同一个提示上
                p.sendMessage(plugin.getLang().t("bedrock.menu.header"));
                p.sendMessage(plugin.getLang().t("usage.rtp"));
            }
            return true;
        }
        openRtpGui(p);
        return true;
    }

    private void openRtpGui(Player p) {
        // 菜单直接调用这里，绕过了命令层的权限校验 | menu bypasses the command-level permission gate
        if (!requirePermission(p, "novateleport.command.rtp")) return;
        int defaultRadius = plugin.getConfig().getInt("rtp.radius", 2000);
        rtpRadiusChoices.putIfAbsent(p.getUniqueId(), defaultRadius);
        int radius = rtpRadiusChoices.get(p.getUniqueId());
        int step = plugin.getConfig().getInt("rtp.gui.step", 500);

        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put("radius", radius);
        placeholders.put("step", step);

        MenuHolder holder = new MenuHolder("rtp");
        Inventory inv = Bukkit.createInventory(holder, menus.getSize("rtp", 27), menus.getTitle("rtp", placeholders));
        holder.bind(inv);

        for (JavaMenuConfig.FixedItem it : menus.getFixedItems("rtp", placeholders)) {
            ItemStack stack = tagAction(it.stack(), it.action(), null);
            inv.setItem(it.slot(), stack);
        }

        p.openInventory(inv);
    }

    private boolean handleTpMenu(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        Player p = (Player) sender;
        if (BedrockUtil.isBedrock(p)) {
            java.util.List<String> entries = java.util.Arrays.asList(
                    plugin.getLang().t("menu.main.homes"),
                    plugin.getLang().t("menu.main.warps"),
                    plugin.getLang().t("menu.main.rtp"),
                    plugin.getLang().t("menu.main.back")
            );
            // 标签是本地化文本，命令参数必须另给 | labels are localized, args must be separate
            java.util.List<String> subcommands = java.util.Arrays.asList("homes", "warps", "rtp", "back");
            boolean ok = com.novamclabs.util.BedrockFormsUtil.showListCommandForm(plugin, p,
                    plugin.getLang().t("menu.main.title"), entries, subcommands, "ntp");
            if (!ok) {
                p.sendMessage(plugin.getLang().t("bedrock.menu.header"));
                p.sendMessage(plugin.getLang().t("bedrock.menu.tip.homes"));
                p.sendMessage(plugin.getLang().t("bedrock.menu.tip.warps"));
                p.sendMessage(plugin.getLang().t("bedrock.menu.tip.rtp"));
                p.sendMessage(plugin.getLang().t("bedrock.menu.tip.back"));
            }
        } else {
            openMainMenu(p);
        }
        return true;
    }

    private void openMainMenu(Player p) {
        MenuHolder holder = new MenuHolder("main");
        Inventory inv = Bukkit.createInventory(holder, menus.getSize("main", 27), menus.getTitle("main", Collections.emptyMap()));
        holder.bind(inv);

        for (JavaMenuConfig.FixedItem it : menus.getFixedItems("main", Collections.emptyMap())) {
            inv.setItem(it.slot(), tagAction(it.stack(), it.action(), null));
        }

        p.openInventory(inv);
    }

    private void openHomesMenu(Player p, List<String> homes) {
        JavaMenuConfig.Template tpl = menus.getTemplate("homes");
        MenuHolder holder = new MenuHolder("homes");
        Inventory inv = Bukkit.createInventory(holder, menus.getSize("homes", 27), menus.getTitle("homes", Collections.emptyMap()));
        holder.bind(inv);

        int limit = inv.getSize();
        for (String name : homes) {
            if (inv.firstEmpty() < 0 || inv.firstEmpty() >= limit) break;
            Map<String, Object> placeholders = new HashMap<>();
            placeholders.put("name", name);
            ItemStack stack = menus.buildTemplateItem(tpl, placeholders);
            inv.addItem(tagAction(stack, tpl != null ? tpl.action() : "home", name));
        }

        p.openInventory(inv);
    }

    private void openWarpsMenu(Player p, List<String> warps) {
        JavaMenuConfig.Template tpl = menus.getTemplate("warps");
        MenuHolder holder = new MenuHolder("warps");
        Inventory inv = Bukkit.createInventory(holder, menus.getSize("warps", 27), menus.getTitle("warps", Collections.emptyMap()));
        holder.bind(inv);

        int limit = inv.getSize();
        for (String name : warps) {
            if (inv.firstEmpty() < 0 || inv.firstEmpty() >= limit) break;
            Map<String, Object> placeholders = new HashMap<>();
            placeholders.put("name", name);
            ItemStack stack = menus.buildTemplateItem(tpl, placeholders);
            inv.addItem(tagAction(stack, tpl != null ? tpl.action() : "warp", name));
        }

        p.openInventory(inv);
    }

    private ItemStack tagAction(ItemStack it, String action, String value) {
        if (it == null) return null;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        if (action != null && !action.isEmpty()) {
            meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, action);
        }
        if (value != null) {
            meta.getPersistentDataContainer().set(keyValue, PersistentDataType.STRING, value);
        }
        it.setItemMeta(meta);
        return it;
    }

    /**
     * 共享处理入口的权限校验。菜单与基岩表单直接调用这些 handler，
     * 不像命令那样经过 plugin.yml/PluginCommand 的权限门，所以它们必须自己再查一次。
     */
    private boolean requirePermission(CommandSender sender, String node) {
        if (sender.hasPermission(node)) return true;
        sender.sendMessage(plugin.getLang().t("command.no_permission"));
        return false;
    }

    /**
     * 立即扣费。仅用于“不走本地传送”的分支（跨服切换），
     * 本地传送的费用由 TeleportUtil 在真正传送时扣除。
     *
     * 注意：扣费发生在 {@code ProxyMessenger.connect} 之前，代理切服失败不会退款——
     * 没有代理侧回执就无法可靠回滚。这是既有行为，已在文档中标注。
     */
    private boolean ensurePaid(Player p, String actionKey) {
        return com.novamclabs.util.CostModel.checkAndCharge(plugin, p,
                com.novamclabs.util.CostModel.fromGlobal(plugin, actionKey));
    }

    /**
     * 跨服传送不走 {@code TeleportUtil}，战斗标签与冷却必须在这里单独把关，
     * 否则跨服会绕过这两项限制。实际判断统一走 {@link com.novamclabs.util.TeleportGates}。
     */
    private boolean passesCrossServerGates(Player p, String type) {
        return com.novamclabs.util.TeleportGates.passes(plugin, p, type);
    }

    /** 跨服传送发出后登记冷却（与本地传送在 execute 中登记相对应） */
    private void recordCrossServerCooldown(Player p, String type) {
        com.novamclabs.util.TeleportGates.record(plugin, p, type);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (!(e.getInventory().getHolder() instanceof MenuHolder)) return;

        e.setCancelled(true);
        Player p = (Player) e.getWhoClicked();

        ItemStack current = e.getCurrentItem();
        if (current == null || !current.hasItemMeta()) return;
        ItemMeta meta = current.getItemMeta();
        if (meta == null) return;

        String action = meta.getPersistentDataContainer().get(keyAction, PersistentDataType.STRING);
        String value = meta.getPersistentDataContainer().get(keyValue, PersistentDataType.STRING);
        if (action == null || action.isEmpty()) return;

        switch (action) {
            case "open_homes" -> {
                p.closeInventory();
                handleHomes(p);
            }
            case "open_warps" -> {
                p.closeInventory();
                handleWarps(p);
            }
            case "open_rtp" -> {
                p.closeInventory();
                openRtpGui(p);
            }
            case "do_back" -> {
                p.closeInventory();
                handleBack(p);
            }
            case "home" -> {
                if (value == null) return;
                p.closeInventory();
                handleHome(p, new String[]{value});
            }
            case "warp" -> {
                if (value == null) return;
                p.closeInventory();
                handleWarp(p, new String[]{value});
            }
            case "rtp_decrease" -> {
                int step = plugin.getConfig().getInt("rtp.gui.step", 500);
                int radius = rtpRadiusChoices.getOrDefault(p.getUniqueId(), plugin.getConfig().getInt("rtp.radius", 2000));
                radius = Math.max(step, radius - step);
                rtpRadiusChoices.put(p.getUniqueId(), radius);
                openRtpGui(p);
            }
            case "rtp_increase" -> {
                int step = plugin.getConfig().getInt("rtp.gui.step", 500);
                int radius = rtpRadiusChoices.getOrDefault(p.getUniqueId(), plugin.getConfig().getInt("rtp.radius", 2000));
                int max = com.novamclabs.util.RTPUtil.loadSettings(plugin, p.getWorld()).radius;
                radius = Math.min(max, radius + step);
                rtpRadiusChoices.put(p.getUniqueId(), radius);
                openRtpGui(p);
            }
            case "rtp_start" -> {
                p.closeInventory();
                int radius = rtpRadiusChoices.getOrDefault(p.getUniqueId(), plugin.getConfig().getInt("rtp.radius", 2000));
                java.util.Random rnd = new java.util.Random();
                org.bukkit.Location dest = com.novamclabs.util.RTPUtil.findSafeLocation(plugin, p.getWorld(), rnd, radius);
                if (dest == null) {
                    p.sendMessage(plugin.getLang().t("rtp.no_safe"));
                    return;
                }
                int delay = plugin.getConfig().getInt("commands.teleport_delay_seconds", 3);
                com.novamclabs.util.TeleportUtil.delayedTeleportWithAnimation(plugin, p, dest, delay, "rtp",
                    () -> p.sendMessage(plugin.getLang().t("rtp.done")));
            }
            default -> { }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        rtpRadiusChoices.remove(uuid);

        // 清理 TPA 请求，避免离线后残留 | cleanup requests on quit
        TpaRequest outgoingReq = outgoing.remove(uuid);
        if (outgoingReq != null && outgoingReq.target != null) {
            incoming.remove(outgoingReq.target);
        }

        TpaRequest incomingReq = incoming.remove(uuid);
        if (incomingReq != null) {
            outgoing.remove(incomingReq.requester);
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
