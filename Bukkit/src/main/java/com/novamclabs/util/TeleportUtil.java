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
     * {@link #NOTHING_CHARGED} 用于「成功」时报告实际扣了多少，
     * 退款据此进行 —— 早先用「扣费前后的余额差」推算，但玩家余额可能被别的插件在同一个
     * 窗口里改动，那样一次免费传送也会算出一个非零金额，传送随后失败就会倒贴钱给玩家。
     * Payment hook, invoked right before the teleport actually happens.
     * Returning false aborts the teleport (and the implementation is responsible for messaging).
     */
    @FunctionalInterface
    public interface Payment {
        /**
         * @param charged 实际扣除的金额；调用方据此退款。不涉及金钱时写 {@link #NOTHING_CHARGED}。
         *                amount actually charged, used for refunds on a later failure.
         */
        boolean pay(Player player, double[] charged);

        /**
         * 退款接收者，默认是被传送的玩家（费用通常由他本人支付）。
         * 费用由他人代付时必须覆写：{@code finish()} 的失败退款只退给这里返回的人，
         * 退给了没付钱的一方就等于凭空造钱（{@code /tplog rewind} 是唯一这种场景）。
         * 返回 null 表示「无人可退」，退款会跳过（portal 这种完全不涉钱的入口）。
         * Who gets a refund when the teleport turns out to be blocked; defaults to the player
         * being moved. Override when someone else pays, or return null when nobody paid.
         */
        default OfflinePlayer refundRecipient(Player player) {
            return player;
        }
    }

    /** 供 {@link Payment} 回填「本次没有扣钱」| nothing was charged */
    public static final double NOTHING_CHARGED = 0.0;

    /**
     * 使用 economy.costs.&lt;type&gt; 的扣费回调（经济未启用/未安装 Vault 时自动放行）。
     * 与 {@link CostModel#asPayment} 同构：Spec 在每次扣费时求值，因此 /stp reload 改价后立即生效；
     * 差别只在「预检通过但扣款失败」时会补一条提示。
     *
     * CostModel.asPayment 在 preflight 不通过时自己会发消息，但 apply 失败（两次调用之间余额变了、
     * 或经济提供者拒绝扣款）时只返回 false 不提示，玩家会以为传送「什么都没发生」。
     * 从外部无法区分这两种 false，所以这里把流程拆开：此时确定没发过消息，可以安全补发。
     */
    public static Payment economyPayment(StarTeleport plugin, String type) {
        return (player, charged) -> {
            CostModel.Spec spec = CostModel.fromGlobal(plugin, type);
            if (spec == null || spec.isFree()) return true;
            CostModel.Result result = CostModel.preflight(plugin, player, spec);
            if (!result.ok()) {
                CostModel.notifyDenied(plugin, player, spec, result);
                return false;
            }
            if (CostModel.apply(plugin, player, spec, result)) {
                // 经济未启用 / 有 bypass 权限时 apply 直接放行、一分没扣，金额如实记 0
                charged[0] = EconomyUtil.wouldCharge(plugin, player, spec.money()) ? spec.money() : NOTHING_CHARGED;
                return true;
            }
            player.sendMessage(plugin.getLang().tr("economy.not_enough",
                    "amount", EconomyUtil.format(spec.money())));
            return false;
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

        // 发起前的拦截：战斗标签 → 冷却。
        // 在这里拦而不是等倒计时结束，是为了不让玩家看到「正在传送」最后却什么都没发生；
        // 倒计时开始后默认不会因战斗而中断：打断倒计时的是 damage_interrupt.enabled
        // 控制的受伤监听，该开关默认关闭，只有服主显式打开时 CombatManager 才会取消倒计时。
        if (blockedByCombat(plugin, player, type)) {
            abort(onAbort);
            return null;
        }
        long cooldownSeconds = cooldownRemaining(plugin, player, type);
        if (cooldownSeconds > 0) {
            player.sendMessage(plugin.getLang().tr("cooldown.active", "seconds", cooldownSeconds));
            abort(onAbort);
            return null;
        }

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

        final int totalTicks = delaySeconds * 20;
        final int effectInterval = effectIntervalTicks(plugin);
        // 任务周期取「特效刷新间隔」与「总时长」的最大公约数：特效只在 elapsed % interval == 0 时播放，
        // 而 elapsed 每次递增一个整除总时长的周期，因此总时长那一跳一定被走到 ——
        // 特效频率独立于倒计时变化，传送时刻仍精确等于 delaySeconds。
        final long period = animation ? gcd(effectInterval, totalTicks) : 20L;
        final int[] elapsed = {0};
        final int[] shownSecond = {delaySeconds + 1}; // 初值保证第一次 tick 一定刷新
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

                // 先判断是否到点，否则最后一次 tick 会先把 "0" 显示给玩家再执行传送
                int elapsedTicks = elapsed[0];
                if (elapsedTicks >= totalTicks) {
                    if (holder[0] != null) holder[0].cancel();
                    plugin.untrackTeleport(player.getUniqueId());
                    execute(plugin, player, target, type, from, payment, onComplete, onAbort);
                    return;
                }

                int remainingSeconds = (totalTicks - elapsedTicks + 19) / 20;
                // 只在秒数变化时刷新：间隔大于 1 秒时不会漏秒，小于 1 秒时不会刷屏
                if (remainingSeconds < shownSecond[0]) {
                    shownSecond[0] = remainingSeconds;
                    DisplayUtil.sendCountdown(plugin, player, remainingSeconds);
                }

                if (animation && elapsedTicks % effectInterval == 0) {
                    playDuring(plugin, player, angle[0], delaySeconds, remainingSeconds);
                    angle[0] += Math.PI / 8;
                }
                elapsed[0] = elapsedTicks + (int) period;
            }
        };

        SchedulerWrapper.ScheduledTask task = plugin.getScheduler().runAtEntityTimer(player, tick, 0L, period);
        holder[0] = task;
        plugin.trackTeleport(player, task, isCancelOnMove(plugin, type), type);
        return task;
    }

    private static void abort(Runnable onAbort) {
        if (onAbort != null) onAbort.run();
    }

    /** 粒子/音效刷新间隔（tick），1..200；它不参与倒计时总时长的计算 */
    private static int effectIntervalTicks(StarTeleport plugin) {
        int ticks = plugin.getConfig().getInt("features.animation_effect_interval_ticks", 20);
        return Math.max(1, Math.min(200, ticks));
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        return a;
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

    /** 战斗标签拦截（功能默认关闭）| combat-tag gate, disabled by default */
    private static boolean blockedByCombat(StarTeleport plugin, Player player, String type) {
        com.novamclabs.combat.CombatManager combat = plugin.getCombatManager();
        if (combat == null) return false;
        long remaining = combat.remainingSeconds(player, type);
        if (remaining <= 0) return false;
        player.sendMessage(plugin.getLang().tr("combat.tagged", "seconds", remaining));
        return true;
    }

    /** 剩余冷却秒数，0 表示可以传送 | remaining cooldown in seconds, 0 = allowed */
    private static long cooldownRemaining(StarTeleport plugin, Player player, String type) {
        com.novamclabs.cooldown.CooldownManager cooldowns = plugin.getCooldownManager();
        if (cooldowns == null) return 0L;
        return cooldowns.remainingSeconds(player, type);
    }

    /** 传送真正执行后登记冷却。放在这里，倒计时被取消或校验失败都不会消耗冷却。 */
    private static void recordCooldown(StarTeleport plugin, Player player, String type) {
        com.novamclabs.cooldown.CooldownManager cooldowns = plugin.getCooldownManager();
        if (cooldowns == null) return;
        cooldowns.record(player, type);
    }

    /**
     * 传送执行体：校验 → 扣费 → 特效 → 传送 → （成功才）记录 → 后处理。
     * 所有校验都在扣费之前；扣费之后传送仍可能被第三方插件拦截，那时退回已扣的金钱。
     *
     * 目的地校验（锚点 / 领地）读的是**目标位置**的方块与区块状态。在 Folia 上那必须发生在
     * 目标所属区域线程，否则就是跨区域访问；领地适配器还可能顺带强制加载目标区块。
     * 因此校验走 {@link RegionGuardUtil#checkDestination} 异步回传，扣费与后续步骤放在回调里。
     * 同区域（以及 Spigot/Paper 永远单线程）时 checkDestination 直接同步回调，语义与改动前一致。
     */
    private static void execute(StarTeleport plugin, Player player, Location target, String type, Location from,
                                Payment payment, Runnable onComplete, Runnable onAbort) {
        if (!player.isOnline()) {
            abort(onAbort);
            return;
        }

        // 倒计时期间可能被别人打上战斗标签（damage_interrupt 默认关闭时不会打断倒计时），
        // 执行时再校验一次，否则「先起传送、倒计时中受击」依旧能跑出战斗限制。
        if (blockedByCombat(plugin, player, type)) {
            abort(onAbort);
            return;
        }

        // 校验通过后才扣费：checkDestination 未通过前不会发生任何扣费
        RegionGuardUtil.checkDestination(plugin, player, target, type, denial -> {
            if (denial != RegionGuardUtil.Denial.NONE) {
                if (player.isOnline()) player.sendMessage(plugin.getLang().t(denial.langKey()));
                abort(onAbort);
                return;
            }
            runTeleport(plugin, player, target, type, from, payment, onComplete, onAbort);
        });
    }

    /** 目的地校验通过后的实际传送：扣费 → 特效 → 传送 → 收尾 */
    private static void runTeleport(StarTeleport plugin, Player player, Location target, String type, Location from,
                                    Payment payment, Runnable onComplete, Runnable onAbort) {
        if (!player.isOnline()) {
            abort(onAbort);
            return;
        }

        // 由 payment 自己报告实际扣了多少，而不是用余额差推算：
        // 玩家余额可能被别的插件在同一窗口里改动，那样免费传送也会算出非零金额，
        // 传送随后失败就会倒贴钱给玩家。
        double[] charged = {NOTHING_CHARGED};
        if (payment != null && !payment.pay(player, charged)) {
            abort(onAbort);
            return;
        }

        if (plugin.getScriptingManager() != null) plugin.getScriptingManager().callPre(player, target);

        if (plugin.getConfig().getBoolean("features.animation_enabled", true)) {
            playInstant(plugin, player);
            playPrepare(plugin, player, target);
        }

        final double refund = charged[0];
        // 谁付的钱就退给谁：/tplog rewind 是管理员代付，退给被传送的目标等于凭空造钱
        final OfflinePlayer refundTo = payment == null ? null : payment.refundRecipient(player);
        CompletableFuture<Boolean> teleported = teleportRespectingBoat(plugin, player, target);
        if (plugin.getScheduler().isFolia()) {
            // Folia：异步结果即「传送是否真的发生」；回调必须回到玩家所属区域线程才能碰实体/背包 API
            teleported.whenComplete((ok, ex) -> plugin.getScheduler().runAtEntity(player, () ->
                    finish(plugin, player, refundTo, target, type, from, refund, ok != null && ok, onComplete, onAbort)));
            return;
        }

        finish(plugin, player, refundTo, target, type, from, refund, teleported.getNow(false), onComplete, onAbort);
    }

    /**
     * 传送结果落定后的收尾：失败退钱并告知；只有确认传送成功才记录冷却/日志并触发 onComplete。
     *
     * @param refundTo 失败退款接收者；{@link Payment#refundRecipient} 为 null 时不退款
     *                 （只有 payment == null 的入口会走到 null，例如 portal 不传 payment 时）
     */
    private static void finish(StarTeleport plugin, Player player, OfflinePlayer refundTo, Location target, String type,
                               Location from, double refund, boolean success, Runnable onComplete, Runnable onAbort) {
        if (!success) {
            refundMoney(plugin, refundTo, refund);
            if (player.isOnline()) {
                player.sendMessage(plugin.getLang().t("teleport.cancelled.title"));
            }
            abort(onAbort);
            return;
        }

        recordTeleport(plugin, player, type, from, target);
        recordCooldown(plugin, player, type);
        recordBack(plugin, player, type, from);

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

    /**
     * 退回扣费。只能退金钱：{@link Payment} 抽象不暴露经验等级/物品成本，
     * 无从得知该退多少，因此含 XP 或物品的传送点在这一步不会回滚（见 CostModel.Spec）。
     * 退给 {@link Payment#refundRecipient}（默认即被传送的玩家），而不是固定退给被传送者。
     */
    private static void refundMoney(StarTeleport plugin, OfflinePlayer player, double amount) {
        if (amount <= 0) return;
        if (!EconomyUtil.deposit(plugin, player, amount)) {
            plugin.getLogger().warning("[Economy] Failed to refund " + amount + " to " + player.getName()
                    + " after a blocked teleport.");
        }
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
     * 记录 /back 位置。只在传送确认成功后写入：
     * 命令发起时写会让取消/被拦截的传送毁掉玩家原有的 /back。
     * type 为 "back" 时跳过，否则 /back 会覆盖掉自己要去的位置。
     */
    private static void recordBack(StarTeleport plugin, Player player, String type, Location from) {
        if (from == null || "back".equalsIgnoreCase(type)) return;
        try {
            if (plugin.getDataStore() != null) plugin.getDataStore().setBack(player.getUniqueId(), from);
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
     * 返回值是「传送是否真的发生」——第三方插件取消传送事件时为 false。
     */
    private static CompletableFuture<Boolean> teleportEntity(StarTeleport plugin, Entity entity, Location target) {
        if (plugin.getScheduler().isFolia()) {
            CompletableFuture<Boolean> future = plugin.getScheduler().teleportAsync(entity, target);
            return future == null ? CompletableFuture.completedFuture(false) : future;
        }
        return CompletableFuture.completedFuture(entity.teleport(target));
    }

    // 若开启配置且玩家在船上，则携带船与乘客一起传送；返回船（或玩家）的传送结果
    private static CompletableFuture<Boolean> teleportRespectingBoat(StarTeleport plugin, Player player, Location target) {
        boolean carryBoat = plugin.getConfig().getBoolean(Constants.CFG_CARRY_BOAT_WITH_PASSENGERS, false);
        Entity vehicle = player.getVehicle();
        if (!carryBoat || !(vehicle instanceof Boat)) {
            return teleportEntity(plugin, player, target);
        }
        Boat boat = (Boat) vehicle;
        List<Entity> passengers = new ArrayList<>(boat.getPassengers());
        // 先让所有乘客下船，避免跨世界传送时异常
        for (Entity e : passengers) {
            try { e.leaveVehicle(); } catch (Throwable ignored) {}
        }
        CompletableFuture<Boolean> result = teleportEntity(plugin, boat, target);
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
        return result;
    }

    /** 总开关 + 逐风格开关：任一关闭则该风格的所有特效都不播放 */
    private static boolean styleEnabled(StarTeleport plugin, Player player) {
        if (!plugin.getConfig().getBoolean("features.animation_enabled", true)) return false;
        return plugin.getConfig().getBoolean("features.animation_styles." + styleOf(plugin, player).key(), true);
    }

    private static boolean particlesEnabled(StarTeleport plugin) {
        return plugin.getConfig().getBoolean("features.animation_particles", true);
    }

    private static boolean soundsEnabled(StarTeleport plugin) {
        return plugin.getConfig().getBoolean("features.animation_sounds", true);
    }

    /**
     * 基岩版粒子数量换算。
     *
     * Geyser 不会把一个 Java 粒子包当一个包转发：包内每个粒子各自产生一个上游包
     * （上限 100，超出部分被静默截断）。所以基岩版按倍率缩减，并在这里显式截断，
     * 让截断是有意为之而不是被 Geyser 悄悄丢掉。Java 侧数量不变。
     */
    private static int particleCount(StarTeleport plugin, Player player, int base) {
        if (base <= 0) return 0;
        if (!BedrockUtil.isBedrock(player)) return base;
        double multiplier = plugin.getConfig().getDouble("features.bedrock_particle_multiplier", 0.5);
        if (multiplier <= 0) return 0;
        int scaled = (int) Math.round(base * multiplier);
        return Math.min(Math.max(scaled, 1), 100);
    }

    private static void playPrepare(StarTeleport plugin, Player player, Location target) {
        if (!styleEnabled(plugin, player)) return;
        boolean particles = particlesEnabled(plugin);
        com.novamclabs.animations.AnimationManager.Style style = styleOf(plugin, player);
        switch (style) {
            case TECH:
                if (particles) plugin.getScheduler().runAtLocationLater(target.getWorld(), target.getBlockX(), target.getBlockY(), target.getBlockZ(), () ->
                        spawnAquaDust(plugin, player, target.getWorld(), target.clone().add(0, 1, 0), 100, 0.5, 0.8, 0.5, 0), 10L);
                break;
            case NATURAL:
                if (particles) plugin.getScheduler().runAtLocationLater(target.getWorld(), target.getBlockX(), target.getBlockY(), target.getBlockZ(), () ->
                        spawn(plugin, player, target.getWorld(), "HAPPY_VILLAGER", target.clone().add(0, 0.2, 0), 60, 0.8, 0.2, 0.8, 0.01), 10L);
                break;
            default:
                // 魔法：空间涟漪
                if (particles) plugin.getScheduler().runAtLocationLater(target.getWorld(), target.getBlockX(), target.getBlockY(), target.getBlockZ(), () ->
                        spawn(plugin, player, target.getWorld(), "PORTAL", target.clone().add(0, 1, 0), 80, 0.6, 0.8, 0.6, 0.1), 10L);
                break;
        }
    }

    private static com.novamclabs.animations.AnimationManager.Style styleOf(StarTeleport plugin, Player player) {
        return plugin.getAnimationManager() != null
                ? plugin.getAnimationManager().getStyle(player)
                : com.novamclabs.animations.AnimationManager.Style.MAGIC;
    }

    private static void playDuring(StarTeleport plugin, Player player, double angle, int delaySeconds, int remaining) {
        if (!styleEnabled(plugin, player)) return;
        boolean particles = particlesEnabled(plugin);
        boolean sounds = soundsEnabled(plugin);
        com.novamclabs.animations.AnimationManager.Style style = styleOf(plugin, player);
        switch (style) {
            case TECH: {
                // 蓝色扫描光线 + 网格
                if (particles) {
                    Location base = player.getLocation().clone();
                    spawn(plugin, player, player.getWorld(), "CRIT", base.add(0, 1.2, 0), 10, 0.3, 0.0, 0.3, 0.01);
                }
                if (sounds) player.playSound(player.getLocation(), Sound.BLOCK_REDSTONE_TORCH_BURNOUT, SoundCategory.PLAYERS, 0.2f, 1.0f);
                break;
            }
            case NATURAL: {
                if (particles) {
                    Location base = player.getLocation().clone();
                    spawn(plugin, player, player.getWorld(), "CLOUD", base.add(0, 0.3, 0), 20, 0.6, 0.0, 0.6, 0.01);
                    // 藤蔓上升感：绿色落沙
                    org.bukkit.block.data.BlockData green = org.bukkit.Material.GREEN_CONCRETE.createBlockData();
                    spawn(plugin, player, player.getWorld(), "FALLING_DUST", base, 10, 0.3, 0.6, 0.3, 0.01, green);
                }
                if (sounds) player.playSound(player.getLocation(), Sound.BLOCK_GRASS_PLACE, SoundCategory.PLAYERS, 0.15f, 1.0f);
                break;
            }
            default: {
                // 魔法阵
                if (particles) {
                    Location base = player.getLocation().clone().add(0, 0.1, 0);
                    spawnMagicCircle(plugin, player, base, angle, 2.0, "END_ROD");
                    spawnRuneSpiral(plugin, player, base, angle, "ENCHANT");
                }
                if (sounds) {
                    float pitch = (float) (0.6 + (delaySeconds - remaining) * (0.8 / Math.max(delaySeconds, 1)));
                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 0.3f, pitch);
                }
                break;
            }
        }
    }

    private static void playInstant(StarTeleport plugin, Player player) {
        if (!styleEnabled(plugin, player)) return;
        boolean particles = particlesEnabled(plugin);
        boolean sounds = soundsEnabled(plugin);
        com.novamclabs.animations.AnimationManager.Style style = styleOf(plugin, player);
        switch (style) {
            case TECH: {
                if (particles) spawn(plugin, player, player.getWorld(), "POOF", player.getLocation().add(0, 1, 0), 150, 0.5, 0.8, 0.5, 0.1);
                if (sounds) player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.3f, 0.8f);
                break;
            }
            case NATURAL: {
                if (particles) {
                    org.bukkit.block.data.BlockData leaves = org.bukkit.Material.OAK_LEAVES.createBlockData();
                    spawn(plugin, player, player.getWorld(), "FALLING_DUST", player.getLocation().add(0, 1, 0), 120, 0.5, 0.8, 0.5, 0.05, leaves);
                }
                if (sounds) player.playSound(player.getLocation(), Sound.BLOCK_GRASS_BREAK, SoundCategory.PLAYERS, 0.6f, 1.0f);
                break;
            }
            default: {
                if (particles) spawn(plugin, player, player.getWorld(), "PORTAL", player.getLocation().add(0, 1, 0), 200, 0.5, 0.8, 0.5, 0.1);
                if (sounds) player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1f);
                break;
            }
        }
    }

    private static void playAfter(StarTeleport plugin, Player player, Location target) {
        if (!styleEnabled(plugin, player)) return;
        boolean particles = particlesEnabled(plugin);
        boolean sounds = soundsEnabled(plugin);
        com.novamclabs.animations.AnimationManager.Style style = styleOf(plugin, player);
        switch (style) {
            case TECH:
                if (particles) spawnAquaDust(plugin, player, target.getWorld(), player.getLocation().add(0, 1, 0), 60, 0.4, 0.6, 0.4, 0);
                if (sounds) player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.6f, 1.3f);
                break;
            case NATURAL:
                if (particles) spawn(plugin, player, target.getWorld(), "HAPPY_VILLAGER", player.getLocation().add(0, 0.2, 0), 30, 0.4, 0.2, 0.4, 0.01);
                if (sounds) player.playSound(player.getLocation(), Sound.BLOCK_GRASS_PLACE, SoundCategory.PLAYERS, 0.8f, 1.1f);
                break;
            default:
                // 星光洒落与柔和音效
                if (particles) spawn(plugin, player, target.getWorld(), "CRIT", player.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.02);
                if (sounds) player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.PLAYERS, 0.8f, 1.2f);
                // 2秒持续的星光（任务在结束时自行取消，避免任务泄露）
                final int[] ticks = {40};
                final SchedulerWrapper.ScheduledTask[] holder = new SchedulerWrapper.ScheduledTask[1];
                holder[0] = plugin.getScheduler().runAtEntityTimer(player, () -> {
                    ticks[0] -= 5;
                    if (ticks[0] <= 0 || !player.isOnline()) {
                        if (holder[0] != null) holder[0].cancel();
                        return;
                    }
                    if (particles) spawn(plugin, player, player.getWorld(), "CRIT", player.getLocation().add(0, 1, 0), 6, 0.2, 0.4, 0.2, 0.01);
                }, 5L, 5L);
                break;
        }
    }

    /**
     * 青色 DUST 粒子，基岩版退化为 CRIT。
     *
     * Geyser 的 Java→Bedrock 粒子翻译里 DUST 是一个硬编码分支（上游标着 TODO）：它把颜色的
     * RGB 整数塞进 Bedrock 当作方块运行时 ID 读取的字段，自定义颜色根本传不过去，
     * 基岩玩家看到的是一团错误粒子。CRIT 是 Geyser 能正确映射的粒子，所以基岩版换掉它；
     * Java 侧保持 DUST + DustOptions 原样。这个分支不要"顺手"合并回去。
     */
    private static void spawnAquaDust(StarTeleport plugin, Player player, World world, Location loc, int count,
                                      double offsetX, double offsetY, double offsetZ, double speed) {
        if (BedrockUtil.isBedrock(player)) {
            spawn(plugin, player, world, "CRIT", loc, count, offsetX, offsetY, offsetZ, speed);
        } else {
            spawn(plugin, player, world, "DUST", loc, count, offsetX, offsetY, offsetZ, speed,
                    new Particle.DustOptions(Color.AQUA, 1.0f));
        }
    }

    /**
     * 粒子名跨版本解析 + 空值保护。
     *
     * 粒子常量在 1.20.5 被改名（VILLAGER_HAPPY → HAPPY_VILLAGER 等），
     * 常量名无法同时兼容两侧，统一走 {@link ParticleCompat} 按名字解析。
     */
    private static void spawn(StarTeleport plugin, Player player, World world, String particleName, Location loc, int count,
                              double offsetX, double offsetY, double offsetZ, double speed) {
        Particle particle = ParticleCompat.get(particleName);
        if (particle == null || world == null) return;
        world.spawnParticle(particle, loc, particleCount(plugin, player, count), offsetX, offsetY, offsetZ, speed);
    }

    private static void spawn(StarTeleport plugin, Player player, World world, String particleName, Location loc, int count,
                              double offsetX, double offsetY, double offsetZ, double speed, Object data) {
        Particle particle = ParticleCompat.get(particleName);
        if (particle == null || world == null) return;
        world.spawnParticle(particle, loc, particleCount(plugin, player, count), offsetX, offsetY, offsetZ, speed, data);
    }

    private static void spawnMagicCircle(StarTeleport plugin, Player player, Location base, double angle, double radius, String particleName) {
        World world = base.getWorld();
        if (world == null) return;
        Particle particle = ParticleCompat.get(particleName);
        if (particle == null) return;
        int count = particleCount(plugin, player, 1);
        if (count <= 0) return;
        for (int i = 0; i < 16; i++) {
            double a = angle + (Math.PI * 2) * (i / 16.0);
            double x = Math.cos(a) * radius;
            double z = Math.sin(a) * radius;
            world.spawnParticle(particle, base.clone().add(x, 0.05, z), count, 0, 0, 0, 0);
        }
    }

    private static void spawnRuneSpiral(StarTeleport plugin, Player player, Location base, double angle, String particleName) {
        World world = base.getWorld();
        if (world == null) return;
        Particle particle = ParticleCompat.get(particleName);
        if (particle == null) return;
        int count = particleCount(plugin, player, 2);
        if (count <= 0) return;
        for (int i = 0; i < 8; i++) {
            double h = i * 0.2;
            double a = angle + i * (Math.PI / 6);
            double x = Math.cos(a) * 0.6;
            double z = Math.sin(a) * 0.6;
            world.spawnParticle(particle, base.clone().add(x, 0.2 + h, z), count, 0, 0, 0, 0.05);
        }
    }
}
