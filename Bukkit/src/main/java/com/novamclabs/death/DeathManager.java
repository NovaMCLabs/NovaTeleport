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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 死亡回溯管理（记录死亡点、提示、/deathback 传送）
 * Death back manager: record death point, prompt, and /deathback command
 */
public class DeathManager implements Listener, CommandExecutor {
    private final StarTeleport plugin;
    // 配置可被 /stp reload 替换，因此不能是 final；读取发生在区域线程，用 volatile 保证可见性
    private volatile YamlConfiguration conf;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public DeathManager(StarTeleport plugin) {
        this.plugin = plugin;
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** 重新载入 death.yml | reload death.yml */
    public void reload() {
        File f = new File(plugin.getDataFolder(), "death.yml");
        if (!f.exists()) {
            try { plugin.saveResource("death.yml", false);} catch (IllegalArgumentException ignored) {}
        }
        conf = YamlConfiguration.loadConfiguration(f);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (!conf.getBoolean("enabled", true)) return;
        Player p = e.getEntity();
        Location loc = p.getLocation().clone();
        plugin.getDataStore().setPlayerValue(p.getUniqueId(), "death.last",
                com.novamclabs.storage.DataStore.serializeLocation(loc));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cooldowns.remove(e.getPlayer().getUniqueId());
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
                TeleportUtil.delayedTeleportWithAnimation(plugin, p, dest, delay, "rtp", () -> p.sendMessage(plugin.getLang().t("rtp.done")));
                return;
            }
        }
        boolean bedrock = com.novamclabs.util.BedrockUtil.isBedrock(p);
        boolean show = bedrock ? conf.getBoolean("auto_prompt.bedrock", true) : conf.getBoolean("auto_prompt.java", true);
        if (!show) return;
        if (bedrock) {
            boolean sent = BedrockFormsUtil.showModalConfirm(plugin, p, plugin.getLang().t("menu.main.title"), plugin.getLang().t("death.prompt"), plugin.getLang().t("death.back_now"), plugin.getLang().t("tpa.button.deny"), () -> p.performCommand("deathback"));
            // 表单发不出去时退回聊天提示，否则基岩玩家既没有表单也没有任何提示（走投无路）
            if (!sent) {
                com.novamclabs.util.ChatCompat.sendRunCommand(p, plugin.getLang().t("death.back_now"), "/deathback", "GREEN");
            }
        } else {
            com.novamclabs.util.ChatCompat.sendRunCommand(p, plugin.getLang().t("death.back_now"), "/deathback", "GREEN");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getLang().t("common.only_player")); return true; }
        Player p = (Player) sender;
        Location dest = getLastDeath(p);
        if (dest == null) { p.sendMessage(plugin.getLang().t("death.none")); return true; }

        int cooldownSeconds = Math.max(0, conf.getInt("cooldown_seconds", 0));
        if (cooldownSeconds > 0) {
            long now = System.currentTimeMillis();
            Long last = cooldowns.get(p.getUniqueId());
            long remaining = last == null ? 0 : (last + cooldownSeconds * 1000L - now);
            if (remaining > 0) {
                p.sendMessage(plugin.getLang().tr("death.cooldown", "seconds", (remaining + 999) / 1000));
                return true;
            }
            cooldowns.put(p.getUniqueId(), now);
        }

        int delay = conf.getInt("teleport_delay_seconds", plugin.getConfig().getInt("commands.teleport_delay_seconds", 3));
        TeleportUtil.delayedTeleportWithAnimation(plugin, p, dest, delay, "deathback", deathPayment(), () ->
                p.sendMessage(plugin.getLang().t("teleport.completed")));
        return true;
    }

    /** death.yml 的传送费用（Vault 金币 + 经验等级）| death-back cost from death.yml */
    private TeleportUtil.Payment deathPayment() {
        // Spec 延后求值，这样 conf 被替换后能读到新值
        return com.novamclabs.util.CostModel.asPayment(plugin, () -> com.novamclabs.util.CostModel.Spec.builder()
                .money(com.novamclabs.util.CostModel.resolveMoney(plugin, conf, "deathback", "cost.vault", "cost.vault_cost"))
                .xpLevels(com.novamclabs.util.CostModel.resolveXp(conf, "cost.xp_levels", "cost.xp_level_cost"))
                .xpDeniedKey("death.need_xp")
                .build());
    }

    private Location getLastDeath(Player p) {
        YamlConfiguration cfg = plugin.getDataStore().readPlayer(p.getUniqueId());
        return com.novamclabs.storage.DataStore.readLocation(cfg.getConfigurationSection("death.last"));
    }
}
