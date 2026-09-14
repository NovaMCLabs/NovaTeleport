package com.novamclabs.offline;

import com.novamclabs.StarTeleport;
import com.novamclabs.storage.DataStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/**
 * 离线传送管理（支持在玩家上线后自动传送）
 * Offline teleport manager (queue teleport to execute when player joins)
 */
public class OfflineTeleportManager implements CommandExecutor, Listener {
    private final StarTeleport plugin;
    private final File file;
    private final YamlConfiguration cfg;

    public OfflineTeleportManager(StarTeleport plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/offline.yml");
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
        this.cfg = new YamlConfiguration();
        if (file.exists()) try { cfg.load(file);} catch (Exception ignored) {}
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("novateleport.admin")) { sender.sendMessage(plugin.getLang().t("command.no_permission")); return true; }
        if (args.length < 5) { sender.sendMessage(plugin.getLang().t("offline.usage")); return true; }
        String name = args[0];
        Player online = findOnlinePlayer(name);
        World w = Bukkit.getWorld(args[1]);
        if (w == null) { sender.sendMessage(plugin.getLang().tr("warn.world_not_loaded", "world", args[1])); return true; }
        double x, y, z;
        try {
            x = Double.parseDouble(args[2]);
            y = Double.parseDouble(args[3]);
            z = Double.parseDouble(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getLang().t("offline.coords_nan"));
            return true;
        }
        if (!isFinite(x) || !isFinite(y) || !isFinite(z)) {
            sender.sendMessage(plugin.getLang().t("offline.coords_infinite"));
            return true;
        }
        Location dest = new Location(w, x, y, z);
        if (online != null) {
            online.teleport(dest);
            sender.sendMessage(plugin.getLang().t("offline.teleported_online"));
            return true;
        }
        UUID uuid = resolvePlayerUUID(name);
        if (uuid == null) { sender.sendMessage(plugin.getLang().tr("offline.unknown_player", "player", name)); return true; }
        cfg.set(uuid.toString()+".world", w.getName());
        cfg.set(uuid.toString()+".x", x);
        cfg.set(uuid.toString()+".y", y);
        cfg.set(uuid.toString()+".z", z);
        save();
        sender.sendMessage(plugin.getLang().tr("offline.queued", "player", name));
        return true;
    }

    private static boolean isFinite(double d) {
        return !Double.isNaN(d) && !Double.isInfinite(d);
    }

    /** Floodgate 默认给基岩账号名加 "." 前缀：账号叫 .Steve，玩家自己和其他人都输入 Steve */
    private static final String FLOODGATE_NAME_PREFIX = ".";

    /**
     * 按名字查在线玩家：先按原样查，再试 Floodgate 的 "." 前缀。
     * 只按原样查会让所有基岩玩家被当成“不在线”。
     */
    public static Player findOnlinePlayer(String name) {
        if (name == null) return null;
        Player p = Bukkit.getPlayerExact(name);
        return p != null ? p : Bukkit.getPlayerExact(FLOODGATE_NAME_PREFIX + name);
    }

    /**
     * 按名字解析 UUID，查无此人返回 null（同样带 "." 前缀回退）。
     * Bukkit.getOfflinePlayer(String) 对从未见过的名字不抛异常，而是造一个随机 UUID 的假玩家，
     * 因此必须用 hasPlayedBefore()/isOnline() 判定，否则离线传送会排进一个永不存在的账号且永不触发。
     */
    public static UUID resolvePlayerUUID(String name) {
        if (name == null) return null;
        UUID u = knownUuid(name);
        return u != null ? u : knownUuid(FLOODGATE_NAME_PREFIX + name);
    }

    private static UUID knownUuid(String name) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        return op.hasPlayedBefore() || op.isOnline() ? op.getUniqueId() : null;
    }

    private void save() {
        try {
            DataStore.atomicSave(cfg, file);
        } catch (IOException e) {
            plugin.getLogger().warning("[OfflineTeleport] Failed to save: " + e.getMessage());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String key = p.getUniqueId().toString();
        if (!cfg.contains(key)) return;
        World w = Bukkit.getWorld(cfg.getString(key+".world", "world"));
        if (w == null) return;
        double x = cfg.getDouble(key+".x");
        double y = cfg.getDouble(key+".y");
        double z = cfg.getDouble(key+".z");
        cfg.set(key, null);
        save();
        Location dest = new Location(w, x, y, z);
        // 必须在玩家所属区域线程传送（Folia 下全局线程无法操作实体）
        plugin.getScheduler().runAtEntity(p, () -> {
            if (p.isOnline()) p.teleport(dest);
        });
    }
}
