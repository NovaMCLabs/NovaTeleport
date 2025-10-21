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

    // 暴露给其他类使用语言系统
    public static com.novamclabs.lang.LanguageManager getLang(StarTeleport plugin) {
        return plugin.getLang();
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
            if (plugin.getScriptingManager() != null) plugin.getScriptingManager().callPre(player, target);
            playPrepare(plugin, player, target);
            player.teleport(target);
            applyPostEffect(player, readPostEffectConfig(plugin));
            playAfter(plugin, player, target);
            if (plugin.getScriptingManager() != null) plugin.getScriptingManager().callPost(player, target);
            if (onComplete != null) onComplete.run();
            return null;
        }

        boolean animation = plugin.getConfig().getBoolean("features.animation_enabled", true);
        org.bukkit.scheduler.BukkitRunnable runnable = new org.bukkit.scheduler.BukkitRunnable() {
            int remaining = delaySeconds;
            double angle = 0.0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                // 倒计时行动条
                String ab = plugin.getLang().tr("teleport.actionbar.countdown", "seconds", remaining);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ab));

                if (animation) {
                    playDuring(plugin, player, angle, delaySeconds, remaining);
                    angle += Math.PI / 8;
                }

                if (remaining <= 0) {
                    cancel();
                    if (plugin.getScriptingManager() != null) plugin.getScriptingManager().callPre(player, target);
                    if (animation) playInstant(plugin, player);
                    if (animation) playPrepare(plugin, player, target);
                    player.teleport(target);
                    if (plugin.getConfig().getBoolean("features.post_effect_enabled", true))
                        applyPostEffect(player, readPostEffectConfig(plugin));
                    if (animation) playAfter(plugin, player, target);
                    if (plugin.getScriptingManager() != null) plugin.getScriptingManager().callPost(player, target);
                    if (onComplete != null) onComplete.run();
                }
                remaining--;
            }
        };
        return runnable.runTaskTimer(plugin, 0L, 20L);
    }

    private static void playPrepare(StarTeleport plugin, Player player, Location target) {
        com.novamclabs.animations.AnimationManager.Style style = plugin.getAnimationManager() != null
                ? plugin.getAnimationManager().getStyle(player)
                : com.novamclabs.animations.AnimationManager.Style.MAGIC;
        switch (style) {
            case TECH:
                // 目标位置数据流粒子
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (target.getWorld() != null)
                        target.getWorld().spawnParticle(Particle.DUST, target.clone().add(0, 1, 0), 100, 0.5, 0.8, 0.5, new Particle.DustOptions(Color.AQUA, 1.0f));
                }, 10L);
                break;
            case NATURAL:
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (target.getWorld() != null)
                        target.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, target.clone().add(0, 0.2, 0), 60, 0.8, 0.2, 0.8, 0.01);
                }, 10L);
                break;
            default:
                // 魔法：空间涟漪
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (target.getWorld() != null)
                        target.getWorld().spawnParticle(Particle.PORTAL, target.clone().add(0, 1, 0), 80, 0.6, 0.8, 0.6, 0.1);
                }, 10L);
                break;
        }
    }

    private static void playDuring(StarTeleport plugin, Player player, double angle, int delaySeconds, int remaining) {
        com.novamclabs.animations.AnimationManager.Style style = plugin.getAnimationManager() != null
                ? plugin.getAnimationManager().getStyle(player)
                : com.novamclabs.animations.AnimationManager.Style.MAGIC;
        switch (style) {
            case TECH: {
                // 蓝色扫描光线 + 网格
                Location base = player.getLocation().clone();
                player.getWorld().spawnParticle(Particle.CRIT, base.add(0, 1.2, 0), 10, 0.3, 0.0, 0.3, 0.01);
                player.playSound(player.getLocation(), Sound.BLOCK_REDSTONE_TORCH_BURNOUT, SoundCategory.PLAYERS, 0.2f, 1.0f);
                break;
            }
            case NATURAL: {
                Location base = player.getLocation().clone();
                player.getWorld().spawnParticle(Particle.CLOUD, base.add(0, 0.3, 0), 20, 0.6, 0.0, 0.6, 0.01);
                // 藤蔓上升感：绿色落沙
                org.bukkit.block.data.BlockData green = org.bukkit.Material.GREEN_CONCRETE.createBlockData();
                player.getWorld().spawnParticle(Particle.FALLING_DUST, base, 10, 0.3, 0.6, 0.3, 0.01, green);
                player.playSound(player.getLocation(), Sound.BLOCK_GRASS_PLACE, SoundCategory.PLAYERS, 0.15f, 1.0f);
                break;
            }
            default: {
                // 魔法阵
                Location base = player.getLocation().clone().add(0, 0.1, 0);
                spawnMagicCircle(base, angle, 2.0, Particle.END_ROD);
                spawnRuneSpiral(base, angle, Particle.ENCHANT);
                float pitch = (float) (0.6 + (delaySeconds - remaining) * (0.8 / Math.max(delaySeconds, 1)));
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 0.3f, pitch);
                break;
            }
        }
    }

    private static void playInstant(StarTeleport plugin, Player player) {
        com.novamclabs.animations.AnimationManager.Style style = plugin.getAnimationManager() != null
                ? plugin.getAnimationManager().getStyle(player)
                : com.novamclabs.animations.AnimationManager.Style.MAGIC;
        switch (style) {
            case TECH: {
                player.getWorld().spawnParticle(Particle.POOF, player.getLocation().add(0, 1, 0), 150, 0.5, 0.8, 0.5, 0.1);
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.3f, 0.8f);
                break;
            }
            case NATURAL: {
                org.bukkit.block.data.BlockData leaves = org.bukkit.Material.OAK_LEAVES.createBlockData();
                player.getWorld().spawnParticle(Particle.FALLING_DUST, player.getLocation().add(0, 1, 0), 120, 0.5, 0.8, 0.5, 0.05, leaves);
                player.playSound(player.getLocation(), Sound.BLOCK_GRASS_BREAK, SoundCategory.PLAYERS, 0.6f, 1.0f);
                break;
            }
            default: {
                player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 200, 0.5, 0.8, 0.5, 0.1);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1f);
                break;
            }
        }
    }

    private static void playAfter(StarTeleport plugin, Player player, Location target) {
        com.novamclabs.animations.AnimationManager.Style style = plugin.getAnimationManager() != null
                ? plugin.getAnimationManager().getStyle(player)
                : com.novamclabs.animations.AnimationManager.Style.MAGIC;
        switch (style) {
            case TECH:
                target.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 60, 0.4, 0.6, 0.4, new Particle.DustOptions(Color.AQUA, 1.0f));
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.6f, 1.3f);
                break;
            case NATURAL:
                target.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 0.2, 0), 30, 0.4, 0.2, 0.4, 0.01);
                player.playSound(player.getLocation(), Sound.BLOCK_GRASS_PLACE, SoundCategory.PLAYERS, 0.8f, 1.1f);
                break;
            default:
                // 星光洒落与柔和音效
                target.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.02);
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.PLAYERS, 0.8f, 1.2f);
                // 2秒持续的星光（使用 BukkitRunnable 并在结束时取消，避免任务泄露）
                new org.bukkit.scheduler.BukkitRunnable() {
                    int ticks = 40;
                    @Override
                    public void run() {
                        if (ticks <= 0 || !player.isOnline()) {
                            cancel();
                            return;
                        }
                        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 6, 0.2, 0.4, 0.2, 0.01);
                        ticks -= 5;
                    }
                }.runTaskTimer(plugin, 0L, 5L);
                break;
        }
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
