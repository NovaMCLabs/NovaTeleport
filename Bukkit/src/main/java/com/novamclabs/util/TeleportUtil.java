package com.novamclabs.util;

import com.novamclabs.StarTeleport;
import com.novamclabs.common.Constants;
import com.novamclabs.common.scheduler.SchedulerWrapper;
import org.bukkit.*;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TeleportUtil {

    /**
     * 传送费用支付回调。在传送真正执行前调用；返回 false 表示中止传送。
     * Payment hook, invoked right before the teleport actually happens.
     * Returning false aborts the teleport (and the implementation is responsible for messaging).
     */
    @FunctionalInterface
    public interface Payment {
        boolean pay(Player player);
    }

    /**
     * 使用 economy.costs.<type> 的扣费回调（经济未启用/未安装 Vault 时自动放行）。
     */
    public static Payment economyPayment(StarTeleport plugin, String type) {
        return player -> {
            double cost = EconomyUtil.getCost(plugin, type);
            if (cost <= 0) return true;
            if (!EconomyUtil.charge(plugin, player, cost)) {
                player.sendMessage(plugin.getLang().tr("economy.not_enough", "amount", EconomyUtil.format(cost)));
                return false;
            }
            return true;
        };
    }

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
        cfg.durationSeconds = Math.max(0, plugin.getConfig().getInt("post_teleport_effect.duration", 3));
        cfg.amplifier = plugin.getConfig().getInt("post_teleport_effect.amplifier", 0);
        return cfg;
    }

    public static void applyPostEffect(Player p, PostEffectConfig cfg) {
        if (cfg.enabled && cfg.type != null && cfg.durationSeconds > 0) {
            p.addPotionEffect(new PotionEffect(cfg.type, cfg.durationSeconds * 20, cfg.amplifier, true, true, true));
        }
    }

    /**
     * 延迟传送（使用 economy.costs.&lt;type&gt; 扣费）。
     */
    public static SchedulerWrapper.ScheduledTask delayedTeleportWithAnimation(StarTeleport plugin, Player player, Location target,
                                                                             int delaySeconds, String type, Runnable onComplete) {
        return delayedTeleportWithAnimation(plugin, player, target, delaySeconds, type, economyPayment(plugin, type), onComplete);
    }

    /**
     * 延迟传送。费用在传送真正执行前才扣除，因此倒计时取消/离线/权限不足都不会扣钱。
     * Charging happens at teleport time, so cancelling the countdown never costs the player anything.
     */
    public static SchedulerWrapper.ScheduledTask delayedTeleportWithAnimation(StarTeleport plugin, Player player, Location target,
                                                                             int delaySeconds, String type, Payment payment,
                                                                             Runnable onComplete) {
        return delayedTeleportWithAnimation(plugin, player, target, delaySeconds, type, payment, onComplete, null);
    }

    /**
     * 延迟传送（完整版）。
     *
     * @param payment  传送执行前的扣费回调，返回 false 中止传送
     * @param onAbort  传送在校验/扣费阶段被中止时触发（不会与 onComplete 同时触发）
     */
    public static SchedulerWrapper.ScheduledTask delayedTeleportWithAnimation(StarTeleport plugin, Player player, Location target,
                                                                             int delaySeconds, String type, Payment payment,
                                                                             Runnable onComplete, Runnable onAbort) {
        if (target == null) return null;
        final Location from = player.getLocation().clone();
        final boolean animation = plugin.getConfig().getBoolean("features.animation_enabled", true);

        if (delaySeconds <= 0) {
            if (plugin.getScheduler().isFolia()) {
                // Folia：必须在玩家所属区域线程执行传送与粒子
                plugin.getScheduler().runAtEntity(player, () -> execute(plugin, player, target, type, from, payment, onComplete, onAbort));
            } else {
                execute(plugin, player, target, type, from, payment, onComplete, onAbort);
            }
            return null;
        }

        if (plugin.getConfig().getBoolean("performance.preload_target_chunk", true)) {
            preloadTargetChunk(plugin, target);
        }

        final int[] remaining = {delaySeconds};
        final double[] angle = {0.0};
        final SchedulerWrapper.ScheduledTask[] holder = new SchedulerWrapper.ScheduledTask[1];

        Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    plugin.untrackTeleport(player.getUniqueId());
                    if (holder[0] != null) holder[0].cancel();
                    abort(onAbort);
                    return;
                }

                DisplayUtil.sendCountdown(plugin, player, remaining[0]);

                if (animation) {
                    playDuring(plugin, player, angle[0], delaySeconds, remaining[0]);
                    angle[0] += Math.PI / 8;
                }

                if (remaining[0] <= 0) {
                    if (holder[0] != null) holder[0].cancel();
                    plugin.untrackTeleport(player.getUniqueId());
                    execute(plugin, player, target, type, from, payment, onComplete, onAbort);
                    return;
                }
                remaining[0]--;
            }
        };

        SchedulerWrapper.ScheduledTask task = plugin.getScheduler().runAtEntityTimer(player, tick, 0L, 20L);
        holder[0] = task;
        plugin.trackTeleport(player, task, isCancelOnMove(plugin, type), type);
        return task;
    }

    private static void abort(Runnable onAbort) {
        if (onAbort != null) onAbort.run();
    }

    private static boolean isCancelOnMove(StarTeleport plugin, String type) {
        if (!plugin.getConfig().getBoolean("commands.cancel_on_move", true)) return false;
        if (type == null) return false;
        List<String> exempt = plugin.getConfig().getStringList("commands.move_cancel_exempt_types");
        if (exempt != null) {
            for (String e : exempt) {
                if (e != null && e.equalsIgnoreCase(type)) return false;
            }
        }
        return true;
    }

    /**
     * 传送执行体：校验 → 扣费 → 特效 → 传送 → 记录 → 后处理。
     * 所有校验都在扣费之前，避免扣了钱却传送失败。
     */
    private static void execute(StarTeleport plugin, Player player, Location target, String type, Location from,
                                Payment payment, Runnable onComplete, Runnable onAbort) {
        if (!player.isOnline()) {
            abort(onAbort);
            return;
        }

        if (SpatialAnchorUtil.isRequired(plugin, type) && !SpatialAnchorUtil.hasAnchor(plugin, target)) {
            player.sendMessage(plugin.getLang().t("anchor.missing"));
            abort(onAbort);
            return;
        }

        if (!RegionGuardUtil.canEnter(player, target)) {
            player.sendMessage(plugin.getLang().t("command.no_permission"));
            abort(onAbort);
            return;
        }

        if (payment != null && !payment.pay(player)) {
            abort(onAbort);
            return;
        }

        if (plugin.getScriptingManager() != null) plugin.getScriptingManager().callPre(player, target);

        if (plugin.getConfig().getBoolean("features.animation_enabled", true)) {
            playInstant(plugin, player);
            playPrepare(plugin, player, target);
        }

        teleportRespectingBoat(plugin, player, target);
        recordTeleport(plugin, player, type, from, target);

        // 传送后的特效/回调放到玩家所在区域执行（Folia 下传送后区域可能已改变）
        plugin.getScheduler().runAtEntity(player, () -> {
            if (!player.isOnline()) return;
            if (plugin.getConfig().getBoolean("features.post_effect_enabled", true)) {
                applyPostEffect(player, readPostEffectConfig(plugin));
            }
            if (plugin.getConfig().getBoolean("features.animation_enabled", true)) {
                playAfter(plugin, player, target);
            }
            if (plugin.getScriptingManager() != null) plugin.getScriptingManager().callPost(player, target);
            if (onComplete != null) onComplete.run();
        });
    }

    private static void recordTeleport(StarTeleport plugin, Player player, String type, Location from, Location to) {
        if (type == null || type.isBlank()) return;
        try {
            if (plugin.getTeleportLogManager() != null) {
                plugin.getTeleportLogManager().record(player.getUniqueId(), type, from, to);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 预加载目标区块。只使用 Paper 的异步 API；非 Paper 平台不做同步强制加载
     * （同步 getChunkAt 会造成主线程卡顿，反而比不预加载更慢）。
     */
    private static void preloadTargetChunk(StarTeleport plugin, Location target) {
        if (target == null || target.getWorld() == null) return;

        World w = target.getWorld();
        int cx = target.getBlockX() >> 4;
        int cz = target.getBlockZ() >> 4;

        Method async = findAsyncChunkMethod(w);
        if (async == null) return;

        plugin.getScheduler().runNextTick(() -> {
            try {
                Object future;
                if (async.getParameterCount() == 2) {
                    future = async.invoke(w, cx, cz);
                } else if (async.getParameterCount() == 3) {
                    future = async.invoke(w, cx, cz, true);
                } else if (async.getParameterCount() == 4) {
                    future = async.invoke(w, cx, cz, true, true);
                } else {
                    future = null;
                }
                if (future instanceof CompletableFuture<?>) {
                    ((CompletableFuture<?>) future).exceptionally(ex -> null);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private static Method findAsyncChunkMethod(World world) {
        try {
            for (Method m : world.getClass().getMethods()) {
                if (!m.getName().equals("getChunkAtAsync")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length >= 2 && p[0] == int.class && p[1] == int.class) {
                    return m;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * 实体传送：Folia 使用区域安全的 teleportAsync，其他平台保持同步 teleport。
     */
    private static void teleportEntity(StarTeleport plugin, Entity entity, Location target) {
        if (plugin.getScheduler().isFolia()) {
            plugin.getScheduler().teleportAsync(entity, target);
        } else {
            entity.teleport(target);
        }
    }

    // 若开启配置且玩家在船上，则携带船与乘客一起传送
    private static void teleportRespectingBoat(StarTeleport plugin, Player player, Location target) {
        boolean carryBoat = plugin.getConfig().getBoolean(Constants.CFG_CARRY_BOAT_WITH_PASSENGERS, false);
        Entity vehicle = player.getVehicle();
        if (!carryBoat || !(vehicle instanceof Boat)) {
            teleportEntity(plugin, player, target);
            return;
        }
        Boat boat = (Boat) vehicle;
        List<Entity> passengers = new ArrayList<>(boat.getPassengers());
        // 先让所有乘客下船，避免跨世界传送时异常
        for (Entity e : passengers) {
            try { e.leaveVehicle(); } catch (Throwable ignored) {}
        }
        teleportEntity(plugin, boat, target);
        // 下一个tick 将乘客传送至目标并重新上船（必须在船所属区域执行，Folia 下尤其重要）
        plugin.getScheduler().runAtEntity(boat, () -> {
            for (Entity e : passengers) {
                try {
                    teleportEntity(plugin, e, target);
                    boat.addPassenger(e);
                } catch (Throwable ignored) {}
            }
            // 确保玩家在船上
            if (!boat.getPassengers().contains(player)) {
                try { boat.addPassenger(player); } catch (Throwable ignored) {}
            }
        });
    }

    private static void playPrepare(StarTeleport plugin, Player player, Location target) {
        com.novamclabs.animations.AnimationManager.Style style = styleOf(plugin, player);
        switch (style) {
            case TECH:
                plugin.getScheduler().runAtLocationLater(target.getWorld(), target.getBlockX(), target.getBlockY(), target.getBlockZ(), () ->
                        spawn(target.getWorld(), "DUST", target.clone().add(0, 1, 0), 100, 0.5, 0.8, 0.5, 0,
                                new Particle.DustOptions(Color.AQUA, 1.0f)), 10L);
                break;
            case NATURAL:
                plugin.getScheduler().runAtLocationLater(target.getWorld(), target.getBlockX(), target.getBlockY(), target.getBlockZ(), () ->
                        spawn(target.getWorld(), "HAPPY_VILLAGER", target.clone().add(0, 0.2, 0), 60, 0.8, 0.2, 0.8, 0.01), 10L);
                break;
            default:
                // 魔法：空间涟漪
                plugin.getScheduler().runAtLocationLater(target.getWorld(), target.getBlockX(), target.getBlockY(), target.getBlockZ(), () ->
                        spawn(target.getWorld(), "PORTAL", target.clone().add(0, 1, 0), 80, 0.6, 0.8, 0.6, 0.1), 10L);
                break;
        }
    }

    private static com.novamclabs.animations.AnimationManager.Style styleOf(StarTeleport plugin, Player player) {
        return plugin.getAnimationManager() != null
                ? plugin.getAnimationManager().getStyle(player)
                : com.novamclabs.animations.AnimationManager.Style.MAGIC;
    }

    private static void playDuring(StarTeleport plugin, Player player, double angle, int delaySeconds, int remaining) {
        com.novamclabs.animations.AnimationManager.Style style = styleOf(plugin, player);
        switch (style) {
            case TECH: {
                // 蓝色扫描光线 + 网格
                Location base = player.getLocation().clone();
                spawn(player.getWorld(), "CRIT", base.add(0, 1.2, 0), 10, 0.3, 0.0, 0.3, 0.01);
                player.playSound(player.getLocation(), Sound.BLOCK_REDSTONE_TORCH_BURNOUT, SoundCategory.PLAYERS, 0.2f, 1.0f);
                break;
            }
            case NATURAL: {
                Location base = player.getLocation().clone();
                spawn(player.getWorld(), "CLOUD", base.add(0, 0.3, 0), 20, 0.6, 0.0, 0.6, 0.01);
                // 藤蔓上升感：绿色落沙
                org.bukkit.block.data.BlockData green = org.bukkit.Material.GREEN_CONCRETE.createBlockData();
                spawn(player.getWorld(), "FALLING_DUST", base, 10, 0.3, 0.6, 0.3, 0.01, green);
                player.playSound(player.getLocation(), Sound.BLOCK_GRASS_PLACE, SoundCategory.PLAYERS, 0.15f, 1.0f);
                break;
            }
            default: {
                // 魔法阵
                Location base = player.getLocation().clone().add(0, 0.1, 0);
                spawnMagicCircle(base, angle, 2.0, "END_ROD");
                spawnRuneSpiral(base, angle, "ENCHANT");
                float pitch = (float) (0.6 + (delaySeconds - remaining) * (0.8 / Math.max(delaySeconds, 1)));
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 0.3f, pitch);
                break;
            }
        }
    }

    private static void playInstant(StarTeleport plugin, Player player) {
        com.novamclabs.animations.AnimationManager.Style style = styleOf(plugin, player);
        switch (style) {
            case TECH: {
                spawn(player.getWorld(), "POOF", player.getLocation().add(0, 1, 0), 150, 0.5, 0.8, 0.5, 0.1);
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.3f, 0.8f);
                break;
            }
            case NATURAL: {
                org.bukkit.block.data.BlockData leaves = org.bukkit.Material.OAK_LEAVES.createBlockData();
                spawn(player.getWorld(), "FALLING_DUST", player.getLocation().add(0, 1, 0), 120, 0.5, 0.8, 0.5, 0.05, leaves);
                player.playSound(player.getLocation(), Sound.BLOCK_GRASS_BREAK, SoundCategory.PLAYERS, 0.6f, 1.0f);
                break;
            }
            default: {
                spawn(player.getWorld(), "PORTAL", player.getLocation().add(0, 1, 0), 200, 0.5, 0.8, 0.5, 0.1);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1f);
                break;
            }
        }
    }

    private static void playAfter(StarTeleport plugin, Player player, Location target) {
        com.novamclabs.animations.AnimationManager.Style style = styleOf(plugin, player);
        switch (style) {
            case TECH:
                spawn(target.getWorld(), "DUST", player.getLocation().add(0, 1, 0), 60, 0.4, 0.6, 0.4, 0,
                        new Particle.DustOptions(Color.AQUA, 1.0f));
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.6f, 1.3f);
                break;
            case NATURAL:
                spawn(target.getWorld(), "HAPPY_VILLAGER", player.getLocation().add(0, 0.2, 0), 30, 0.4, 0.2, 0.4, 0.01);
                player.playSound(player.getLocation(), Sound.BLOCK_GRASS_PLACE, SoundCategory.PLAYERS, 0.8f, 1.1f);
                break;
            default:
                // 星光洒落与柔和音效
                spawn(target.getWorld(), "CRIT", player.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.02);
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.PLAYERS, 0.8f, 1.2f);
                // 2秒持续的星光（任务在结束时自行取消，避免任务泄露）
                final int[] ticks = {40};
                final SchedulerWrapper.ScheduledTask[] holder = new SchedulerWrapper.ScheduledTask[1];
                holder[0] = plugin.getScheduler().runAtEntityTimer(player, () -> {
                    ticks[0] -= 5;
                    if (ticks[0] <= 0 || !player.isOnline()) {
                        if (holder[0] != null) holder[0].cancel();
                        return;
                    }
                    spawn(player.getWorld(), "CRIT", player.getLocation().add(0, 1, 0), 6, 0.2, 0.4, 0.2, 0.01);
                }, 5L, 5L);
                break;
        }
    }

    /**
     * 粒子名跨版本解析 + 空值保护。
     *
     * 粒子常量在 1.20.5 被改名（VILLAGER_HAPPY → HAPPY_VILLAGER 等），
     * 常量名无法同时兼容两侧，统一走 {@link ParticleCompat} 按名字解析。
     */
    private static void spawn(World world, String particleName, Location loc, int count,
                              double offsetX, double offsetY, double offsetZ, double speed) {
        Particle particle = ParticleCompat.get(particleName);
        if (particle == null || world == null) return;
        world.spawnParticle(particle, loc, count, offsetX, offsetY, offsetZ, speed);
    }

    private static void spawn(World world, String particleName, Location loc, int count,
                              double offsetX, double offsetY, double offsetZ, double speed, Object data) {
        Particle particle = ParticleCompat.get(particleName);
        if (particle == null || world == null) return;
        world.spawnParticle(particle, loc, count, offsetX, offsetY, offsetZ, speed, data);
    }

    private static void spawnMagicCircle(Location base, double angle, double radius, String particleName) {
        World world = base.getWorld();
        if (world == null) return;
        Particle particle = ParticleCompat.get(particleName);
        if (particle == null) return;
        for (int i = 0; i < 16; i++) {
            double a = angle + (Math.PI * 2) * (i / 16.0);
            double x = Math.cos(a) * radius;
            double z = Math.sin(a) * radius;
            world.spawnParticle(particle, base.clone().add(x, 0.05, z), 1, 0, 0, 0, 0);
        }
    }

    private static void spawnRuneSpiral(Location base, double angle, String particleName) {
        World world = base.getWorld();
        if (world == null) return;
        Particle particle = ParticleCompat.get(particleName);
        if (particle == null) return;
        for (int i = 0; i < 8; i++) {
            double h = i * 0.2;
            double a = angle + i * (Math.PI / 6);
            double x = Math.cos(a) * 0.6;
            double z = Math.sin(a) * 0.6;
            world.spawnParticle(particle, base.clone().add(x, 0.2 + h, z), 2, 0, 0, 0, 0.05);
        }
    }
}
