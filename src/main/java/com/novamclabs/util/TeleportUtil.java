package com.novamclabs.util;

import com.novamclabs.StarTeleport;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class TeleportUtil {
    public static class PostEffectConfig {
        public boolean enabled;
        public PotionEffectType type;
        public int durationSeconds;
        public int amplifier;
    }

    public static PostEffectConfig readPostEffectConfig(StarTeleport plugin) {
        PostEffectConfig cfg = new PostEffectConfig();
        cfg.enabled = plugin.getConfig().getBoolean("post_teleport_effect.enabled", true);
        String type = plugin.getConfig().getString("post_teleport_effect.effect", "BLINDNESS");
        PotionEffectType t = PotionEffectType.getByName(type);
        if (t == null) t = PotionEffectType.BLINDNESS;
        cfg.type = t;
        cfg.durationSeconds = plugin.getConfig().getInt("post_teleport_effect.duration", 3);
        cfg.amplifier = plugin.getConfig().getInt("post_teleport_effect.amplifier", 0);
        return cfg;
    }

    public static void applyPostEffect(Player p, PostEffectConfig cfg) {
        if (cfg.enabled && cfg.type != null) {
            p.addPotionEffect(new PotionEffect(cfg.type, cfg.durationSeconds * 20, cfg.amplifier, true, true, true));
        }
    }

    public static BukkitTask delayedTeleportWithAnimation(StarTeleport plugin, Player player, Location target, int delaySeconds) {
        return delayedTeleportWithAnimation(plugin, player, target, delaySeconds, null);
    }

    public static BukkitTask delayedTeleportWithAnimation(StarTeleport plugin, Player player, Location target, int delaySeconds, Runnable onComplete) {
        if (delaySeconds <= 0) {
            prepareArrival(plugin, player, target);
            player.teleport(target);
            applyPostEffect(player, readPostEffectConfig(plugin));
            afterArrival(plugin, player, target);
            if (onComplete != null) onComplete.run();
            return null;
        }

        boolean animation = plugin.getConfig().getBoolean("features.animation_enabled", true);
        if (animation) {
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 0.6f);
        }
        org.bukkit.scheduler.BukkitRunnable runnable = new org.bukkit.scheduler.BukkitRunnable() {
            int remaining = delaySeconds;
            double angle = 0.0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                // 倒计时行动条，避免Title闪烁
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§e启明传送中 §7| §f剩余 §a" + remaining + "§f 秒"));

                if (animation) {
                    // 旋转法阵 + 符文粒子
                    Location base = player.getLocation().clone().add(0, 0.1, 0);
                    spawnMagicCircle(base, angle, 2.0, Particle.END_ROD);
                    spawnRuneSpiral(base, angle, Particle.ENCHANT);

                    // 渐变音效（音调随时间升高）
                    float pitch = (float) (0.6 + (delaySeconds - remaining) * (0.8 / Math.max(delaySeconds, 1)));
                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 0.3f, pitch);

                    angle += Math.PI / 8; // 旋转速度
                }

                if (remaining <= 0) {
                    cancel();
                    // 第二阶段：传送瞬间
                    if (animation) {
                        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 200, 0.5, 0.8, 0.5, 0.1);
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1f);
                    }

                    // 目标点提前0.5s产生涟漪
                    if (animation) prepareArrival(plugin, player, target);

                    // 执行传送
                    player.teleport(target);
                    if (plugin.getConfig().getBoolean("features.post_effect_enabled", true))
                        applyPostEffect(player, readPostEffectConfig(plugin));

                    // 第三阶段：到达新地点
                    if (animation) afterArrival(plugin, player, target);

                    if (onComplete != null) onComplete.run();
                }
                remaining--;
            }
        };
        return runnable.runTaskTimer(plugin, 0L, 20L);
    }

    private static void prepareArrival(StarTeleport plugin, Player player, Location target) {
        // 目标位置的空间涟漪（提前0.5秒）。用一次性调度
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (target.getWorld() != null)
                target.getWorld().spawnParticle(Particle.PORTAL, target.clone().add(0, 1, 0), 80, 0.6, 0.8, 0.6, 0.1);
        }, 10L);
    }

    private static void afterArrival(StarTeleport plugin, Player player, Location target) {
        // 星光洒落与柔和音效
        target.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.02);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.PLAYERS, 0.8f, 1.2f);
        // 2秒持续的星光
        plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int ticks = 40;
            @Override
            public void run() {
                if (ticks <= 0 || !player.isOnline()) return;
                player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 6, 0.2, 0.4, 0.2, 0.01);
                ticks -= 5;
            }
        }, 0L, 5L);
    }

    private static void spawnMagicCircle(Location base, double angle, double radius, Particle particle) {
        World world = base.getWorld();
        if (world == null) return;
        for (int i = 0; i < 16; i++) {
            double a = angle + (Math.PI * 2) * (i / 16.0);
            double x = Math.cos(a) * radius;
            double z = Math.sin(a) * radius;
            world.spawnParticle(particle, base.clone().add(x, 0.05, z), 1, 0, 0, 0, 0);
        }
    }

    private static void spawnRuneSpiral(Location base, double angle, Particle particle) {
        World world = base.getWorld();
        if (world == null) return;
        for (int i = 0; i < 8; i++) {
            double h = i * 0.2;
            double a = angle + i * (Math.PI / 6);
            double x = Math.cos(a) * 0.6;
            double z = Math.sin(a) * 0.6;
            world.spawnParticle(particle, base.clone().add(x, 0.2 + h, z), 2, 0, 0, 0, 0.05);
        }
    }
}
