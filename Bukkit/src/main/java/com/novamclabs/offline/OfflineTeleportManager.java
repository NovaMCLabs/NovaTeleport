package com.novamclabs.offline;

import com.novamclabs.StarTeleport;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
        if (args.length < 5) { sender.sendMessage("/forcetp <player> <world> <x> <y> <z>"); return true; }
        String name = args[0];
        Player online = Bukkit.getPlayerExact(name);
        World w = Bukkit.getWorld(args[1]);
        if (w == null) { sender.sendMessage(plugin.getLang().tr("warn.world_not_loaded", "world", args[1])); return true; }
        double x = Double.parseDouble(args[2]);
        double y = Double.parseDouble(args[3]);
        double z = Double.parseDouble(args[4]);
        Location dest = new Location(w, x, y, z);
        if (online != null) {
            online.teleport(dest);
            sender.sendMessage("Teleported online player.");
            return true;
        }
        UUID uuid = resolveUUID(name);
        if (uuid == null) { sender.sendMessage("Unknown player"); return true; }
        cfg.set(uuid.toString()+".world", w.getName());
        cfg.set(uuid.toString()+".x", x);
        cfg.set(uuid.toString()+".y", y);
        cfg.set(uuid.toString()+".z", z);
        save();
        sender.sendMessage("Queued offline teleport for "+name);
        return true;
    }

    private UUID resolveUUID(String name) {
        try { return Bukkit.getOfflinePlayer(name).getUniqueId(); } catch (Throwable t) { return null; }
    }

    private void save() { try { cfg.save(file);} catch (IOException ignored) {} }

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
        Location dest = new Location(w, x, y, z);
        Bukkit.getScheduler().runTask(plugin, () -> p.teleport(dest));
        cfg.set(key, null);
        save();
    }
}
