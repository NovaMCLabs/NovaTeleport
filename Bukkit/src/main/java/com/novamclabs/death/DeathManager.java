package com.novamclabs.death;

import com.novamclabs.StarTeleport;
import com.novamclabs.util.BedrockFormsUtil;
import com.novamclabs.util.TeleportUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

/**
 * 死亡回溯管理（记录死亡点、提示、/deathback 传送）
 * Death back manager: record death point, prompt, and /deathback command
 */
public class DeathManager implements Listener, CommandExecutor {
    private final StarTeleport plugin;
    private final YamlConfiguration conf;

    public DeathManager(StarTeleport plugin) {
        this.plugin = plugin;
        File f = new File(plugin.getDataFolder(), "death.yml");
        if (!f.exists()) {
            try { plugin.saveResource("death.yml", false);} catch (IllegalArgumentException ignored) {}
        }
        conf = YamlConfiguration.loadConfiguration(f);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (!conf.getBoolean("enabled", true)) return;
        Player p = e.getEntity();
        Location loc = p.getLocation().clone();
        File f = playerFile(p);
        YamlConfiguration cfg = new YamlConfiguration();
        if (f.exists()) try { cfg.load(f);} catch (Exception ignored) {}
        cfg.set("death.last", com.novamclabs.storage.DataStore.serializeLocation(loc));
        try { cfg.save(f);} catch (IOException ignored) {}
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (!conf.getBoolean("enabled", true)) return;
        Player p = e.getPlayer();
        // 自动随机传送 | auto random teleport
        if (conf.getBoolean("auto_random.enabled", false)) {
            org.bukkit.World w = null;
            String wn = conf.getString("auto_random.world", "");
            if (wn != null && !wn.isEmpty()) w = Bukkit.getWorld(wn);
            if (w == null) w = p.getWorld();
            org.bukkit.Location dest = plugin.getRtpPoolManager() != null ? plugin.getRtpPoolManager().poll(w) : null;
            if (dest == null) dest = com.novamclabs.util.RTPUtil.findSafeLocation(plugin, w, new java.util.Random());
            if (dest != null) {
                int delay = conf.getInt("teleport_delay_seconds", plugin.getConfig().getInt("commands.teleport_delay_seconds", 3));
                TeleportUtil.delayedTeleportWithAnimation(plugin, p, dest, delay, () -> p.sendMessage(plugin.getLang().t("rtp.done")));
                return;
            }
        }
        boolean bedrock = com.novamclabs.util.BedrockUtil.isBedrock(p);
        boolean show = bedrock ? conf.getBoolean("auto_prompt.bedrock", true) : conf.getBoolean("auto_prompt.java", true);
        if (!show) return;
        if (bedrock) {
            BedrockFormsUtil.showModalConfirm(plugin, p, plugin.getLang().t("menu.main.title"), plugin.getLang().t("death.prompt"), plugin.getLang().t("death.back_now"), "§cCancel", () -> p.performCommand("deathback"));
        } else {
            net.md_5.bungee.api.chat.TextComponent yes = new net.md_5.bungee.api.chat.TextComponent(plugin.getLang().t("death.back_now"));
            yes.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            yes.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/deathback"));
            p.spigot().sendMessage(yes);
        }
    }

    private File playerFile(Player p) { return new File(new File(plugin.getDataFolder(), "data/players"), p.getUniqueId()+".yml"); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        Player p = (Player) sender;
        Location dest = getLastDeath(p);
        if (dest == null) { p.sendMessage(plugin.getLang().t("death.none")); return true; }
        int delay = conf.getInt("teleport_delay_seconds", plugin.getConfig().getInt("commands.teleport_delay_seconds", 3));
        TeleportUtil.delayedTeleportWithAnimation(plugin, p, dest, delay, () -> p.sendMessage(plugin.getLang().t("teleport.completed")));
        return true;
    }

    private Location getLastDeath(Player p) {
        File f = playerFile(p);
        if (!f.exists()) return null;
        YamlConfiguration cfg = new YamlConfiguration();
        try { cfg.load(f);} catch (Exception e) { return null; }
        if (!cfg.contains("death.last")) return null;
        java.util.Map<String, Object> map = Objects.requireNonNull(cfg.getConfigurationSection("death.last")).getValues(false);
        return com.novamclabs.storage.DataStore.deserializeLocation(map);
    }
}
