package com.novamclabs.util;

import com.novamclabs.StarTeleport;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 统一的传送成本模型。
 *
 * 原先「花钱传送」在 4 个 YAML、5 种键名下各实现了一遍，且各处行为不一致
 * （有的先校验后扣、有的先扣再判断）。这里把三类成本（金钱 / 经验等级 / 物品）
 * 和付费传送点的所有者分成收敛到一处，核心不变量是：
 *
 * <b>先校验全部，再统一扣减 —— 任何一项不足都不会产生部分扣减。</b>
 *
 * 各功能的 YAML 位置与键名保持不变，只统一代码路径。
 */
public final class CostModel {

    private CostModel() {
    }

    // ==================== 物品成本 ====================

    /** 一项物品成本：物品规格 + 数量 + 匹配器 */
    public static final class ItemReq {
        private final String spec;
        private final int amount;
        private final Predicate<ItemStack> matcher;

        private ItemReq(String spec, int amount, Predicate<ItemStack> matcher) {
            this.spec = spec;
            this.amount = amount;
            this.matcher = matcher;
        }

        /** 用 ItemResolver 的规格语法匹配（如 "itemsadder:ns:item"、"DIAMOND"） */
        public static ItemReq of(String spec, int amount) {
            return new ItemReq(spec, amount, stack -> ItemResolver.matches(spec, stack));
        }

        /** 自定义匹配器（传送卷轴那种没有规格字符串的场景） */
        public static ItemReq of(String displaySpec, int amount, Predicate<ItemStack> matcher) {
            return new ItemReq(displaySpec, amount, matcher);
        }

        public String spec() {
            return spec;
        }

        public int amount() {
            return amount;
        }
    }

    // ==================== 成本定义 ====================

    /** 一次传送的完整成本。不可变，用 {@link #builder()} 构造。 */
    public static final class Spec {
        private final double money;
        private final OfflinePlayer splitRecipient;
        private final double splitPercent;
        private final int xpLevels;
        private final List<ItemReq> items;

        private final String moneyDeniedKey;
        private final String xpDeniedKey;
        private final String itemDeniedKey;
        private final String unavailableKey;

        private Spec(Builder b) {
            this.money = Math.max(0, b.money);
            this.splitRecipient = b.splitRecipient;
            this.splitPercent = b.splitPercent;
            this.xpLevels = Math.max(0, b.xpLevels);
            this.items = b.items == null ? Collections.emptyList() : List.copyOf(b.items);
            this.moneyDeniedKey = b.moneyDeniedKey;
            this.xpDeniedKey = b.xpDeniedKey;
            this.itemDeniedKey = b.itemDeniedKey;
            this.unavailableKey = b.unavailableKey;
        }

        public static Builder builder() {
            return new Builder();
        }

        /** 完全免费（用于显式声明「本功能不收费」） */
        public static Spec free() {
            return builder().build();
        }

        public double money() {
            return money;
        }

        public OfflinePlayer splitRecipient() {
            return splitRecipient;
        }

        public double splitPercent() {
            return splitPercent;
        }

        public int xpLevels() {
            return xpLevels;
        }

        public List<ItemReq> items() {
            return items;
        }

        /** 是否完全免费（可跳过整个校验/扣费流程） */
        public boolean isFree() {
            return money <= 0 && xpLevels <= 0 && items.isEmpty();
        }
    }

    public static final class Builder {
        private double money;
        private OfflinePlayer splitRecipient;
        private double splitPercent;
        private int xpLevels;
        private List<ItemReq> items;
        private String moneyDeniedKey = "economy.not_enough";
        private String xpDeniedKey = "cost.need_xp";
        private String itemDeniedKey = "cost.need_items";
        private String unavailableKey = "economy.not_available";

        public Builder money(double amount) {
            this.money = amount;
            return this;
        }

        /** 金钱分成：{@code percent} 比例分给收款人，其余作为服务器收入（付费传送点用） */
        public Builder split(OfflinePlayer recipient, double percent) {
            this.splitRecipient = recipient;
            this.splitPercent = percent;
            return this;
        }

        public Builder xpLevels(int levels) {
            this.xpLevels = levels;
            return this;
        }

        public Builder item(ItemReq req) {
            if (this.items == null) this.items = new ArrayList<>();
            this.items.add(req);
            return this;
        }

        public Builder moneyDeniedKey(String key) {
            if (key != null) this.moneyDeniedKey = key;
            return this;
        }

        public Builder xpDeniedKey(String key) {
            if (key != null) this.xpDeniedKey = key;
            return this;
        }

        public Builder itemDeniedKey(String key) {
            if (key != null) this.itemDeniedKey = key;
            return this;
        }

        public Builder unavailableKey(String key) {
            if (key != null) this.unavailableKey = key;
            return this;
        }

        public Spec build() {
            return new Spec(this);
        }
    }

    // ==================== 校验 / 扣减 ====================

    /** 校验结果。{@code ok()} 为 true 时才允许调用 {@link #apply}。 */
    public static final class Result {
        private EconomyUtil.MoneyStatus money = EconomyUtil.MoneyStatus.NOT_APPLICABLE;
        private int xpNeeded;
        private ItemReq missingItem;
        private int missingItemAmount;

        public boolean ok() {
            return money != EconomyUtil.MoneyStatus.INSUFFICIENT_FUNDS
                    && money != EconomyUtil.MoneyStatus.UNAVAILABLE
                    && xpNeeded <= 0
                    && missingItem == null;
        }
    }

    /** 只读校验，无任何副作用 */
    public static Result preflight(StarTeleport plugin, Player player, Spec spec) {
        Result r = new Result();
        if (spec == null || spec.isFree()) return r;

        r.money = EconomyUtil.canCharge(plugin, player, spec.money());

        if (spec.xpLevels() > 0 && player.getLevel() < spec.xpLevels()) {
            r.xpNeeded = spec.xpLevels();
        }

        for (ItemReq req : spec.items()) {
            int have = countItems(player, req);
            if (have < req.amount()) {
                r.missingItem = req;
                r.missingItemAmount = req.amount() - have;
                break;
            }
        }
        return r;
    }

    /**
     * 执行扣减。传入的 {@link Result} 必须是 {@link #preflight} 的返回值。
     * 返回 false 表示扣减失败，此时不会留下部分扣减。
     */
    public static boolean apply(StarTeleport plugin, Player player, Spec spec, Result result) {
        if (spec == null || !result.ok()) return false;
        if (spec.isFree()) return true;

        if (result.money == EconomyUtil.MoneyStatus.OK) {
            if (!chargeMoney(plugin, player, spec)) return false;
        }

        if (spec.xpLevels() > 0) {
            player.setLevel(player.getLevel() - spec.xpLevels());
        }

        for (ItemReq req : spec.items()) {
            removeItems(player, req);
        }
        return true;
    }

    /**
     * 金钱扣减：有分成时按全额一次性从付款人扣款，再把分成支付给收款人。
     *
     * 单次扣款不会出现「扣了一半」的中间状态，因此失败时无需回滚——付款人的净变化恒为 0。
     * 分成支付失败时钱已经收进服务器，记一条日志即可；不做多步回滚（回滚本身也可能失败）。
     */
    private static boolean chargeMoney(StarTeleport plugin, Player player, Spec spec) {
        OfflinePlayer recipient = spec.splitRecipient();
        if (recipient == null || spec.splitPercent() <= 0) {
            return EconomyUtil.charge(plugin, player, spec.money());
        }

        double ownerFee = recipientShare(spec);

        // 全额单次扣除：失败则什么都没发生，付款人不会被白扣
        if (!EconomyUtil.charge(plugin, player, spec.money())) {
            return false;
        }
        if (ownerFee <= 0) {
            return true;
        }
        if (!EconomyUtil.deposit(plugin, recipient, ownerFee)) {
            plugin.getLogger().warning("[Economy] Failed to pay " + ownerFee + " to "
                    + recipient.getName() + " for a toll teleport; the amount is kept as server income.");
        }
        return true;
    }

    /**
     * 分成中收款人应得的金额，与 {@link #chargeMoney} 的计算保持一致。
     * 调用方需要给收款人发通知（「某某向你支付了 xx」）时用它取数，避免重复实现取整与截断。
     */
    public static double recipientShare(Spec spec) {
        if (spec == null || spec.splitRecipient() == null || spec.splitPercent() <= 0) return 0.0;
        double share = spec.money() * (spec.splitPercent() / 100.0);
        return Math.min(spec.money(), Math.max(0.0, share));
    }

    /** 包装成 TeleportUtil 的扣费回调。{@code specSupplier} 在每次扣费时求值，以支持配置热重载。 */
    public static TeleportUtil.Payment asPayment(StarTeleport plugin, Supplier<Spec> specSupplier) {
        return player -> {
            Spec spec = specSupplier.get();
            if (spec == null || spec.isFree()) return true;

            Result result = preflight(plugin, player, spec);
            if (!result.ok()) {
                notifyDenied(plugin, player, spec, result);
                return false;
            }
            return apply(plugin, player, spec, result);
        };
    }

    /** 只校验并提示，用于「不走本地传送」的分支（跨服切换） */
    public static boolean checkAndCharge(StarTeleport plugin, Player player, Spec spec) {
        if (spec == null || spec.isFree()) return true;
        Result result = preflight(plugin, player, spec);
        if (!result.ok()) {
            notifyDenied(plugin, player, spec, result);
            return false;
        }
        return apply(plugin, player, spec, result);
    }

    /**
     * 按校验结果给玩家一条说明。
     * 供 {@link #asPayment} / {@link #checkAndCharge} 之外的调用方复用
     * （例如石碑激活这种「不是传送但同样要扣东西」的场景）。
     */
    public static void notifyDenied(StarTeleport plugin, Player player, Spec spec, Result result) {
        if (result.money == EconomyUtil.MoneyStatus.UNAVAILABLE) {
            player.sendMessage(plugin.getLang().t(spec.unavailableKey));
            return;
        }
        if (result.money == EconomyUtil.MoneyStatus.INSUFFICIENT_FUNDS) {
            // 各功能的语言键对金额占位符的拼写不统一（economy.not_enough 用 {amount}，
            // toll.insufficient_funds 用 {price}），两个都填，避免消息里漏出原始占位符
            String amount = EconomyUtil.format(spec.money());
            player.sendMessage(plugin.getLang().tr(spec.moneyDeniedKey, "amount", amount, "price", amount));
            return;
        }
        if (result.xpNeeded > 0) {
            player.sendMessage(plugin.getLang().tr(spec.xpDeniedKey, "levels", result.xpNeeded));
            return;
        }
        if (result.missingItem != null) {
            player.sendMessage(plugin.getLang().tr(spec.itemDeniedKey,
                    "amount", result.missingItemAmount,
                    "item", result.missingItem.spec()));
        }
    }

    // ==================== 配置读取 ====================

    /** 全局配置里的费用（{@code economy.costs.<type>}） */
    public static Spec fromGlobal(StarTeleport plugin, String type) {
        return Spec.builder().money(EconomyUtil.getCost(plugin, type)).build();
    }

    /**
     * 读取金钱数额，支持「默认值层」。
     *
     * <ol>
     *   <li>特征配置里<b>显式写了</b>任一候选键 → 用它（写 0 就是明确表示免费）</li>
     *   <li>没写、且开启了 {@code economy.global_fallback.enabled} → 回退到
     *       {@code economy.costs.<globalType>}</li>
     *   <li>否则 0（免费）</li>
     * </ol>
     *
     * 默认关闭回退：现有各功能 YAML 都已写了 cost 键，回退本来就不生效；
     * 开启后真正的效果是「服主删掉特征键时改用全局值」，属于静默改变语义，必须由服主主动开启。
     *
     * @param keyPaths 候选键路径（用于兼容 {@code vault} 与 {@code vault_cost} 两种历史拼写）
     */
    public static double resolveMoney(StarTeleport plugin, ConfigurationSection cfg, String globalType, String... keyPaths) {
        if (cfg != null && keyPaths != null) {
            for (String path : keyPaths) {
                if (path != null && cfg.isSet(path)) {
                    return Math.max(0.0, cfg.getDouble(path));
                }
            }
        }
        if (isGlobalFallbackEnabled(plugin)) {
            return EconomyUtil.getCost(plugin, globalType);
        }
        return 0.0;
    }

    /** 读取经验等级消耗（没有全局回退层，{@code economy.costs} 只存放金钱） */
    public static int resolveXp(ConfigurationSection cfg, String... keyPaths) {
        if (cfg == null || keyPaths == null) return 0;
        for (String path : keyPaths) {
            if (path != null && cfg.isSet(path)) {
                return Math.max(0, cfg.getInt(path));
            }
        }
        return 0;
    }

    public static boolean isGlobalFallbackEnabled(StarTeleport plugin) {
        return plugin.getConfig().getBoolean("economy.global_fallback.enabled", false);
    }

    // ==================== 物品统计 / 移除 ====================

    private static int countItems(Player player, ItemReq req) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            if (req.matcher.test(stack)) total += stack.getAmount();
        }
        return total;
    }

    private static void removeItems(Player player, ItemReq req) {
        int remain = req.amount();
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack stack : contents) {
            if (remain <= 0) break;
            if (stack == null || stack.getType().isAir()) continue;
            if (!req.matcher.test(stack)) continue;
            int use = Math.min(remain, stack.getAmount());
            stack.setAmount(stack.getAmount() - use);
            remain -= use;
        }
    }

    // ==================== 启动诊断 ====================

    /**
     * 启动时提示「配置了收费但经济用不了」。只提示，不改变任何行为。
     *
     * 原有的静默降级（{@code charge} 在未启用经济/无提供者时直接放行）会让服主以为
     * 在收费、实际全免费，因此至少要留一条日志。
     *
     * 只统计<b>金钱</b>成本：经验和物品的消耗与经济系统无关，不受影响。
     */
    public static void warnIfCostsUnusable(StarTeleport plugin) {
        try {
            boolean enabled = EconomyUtil.isEnabled(plugin);
            boolean usable = enabled && EconomyUtil.hasProvider();
            if (usable) return;

            int configured = countConfiguredMoneyCosts(plugin);
            if (configured <= 0) return;

            String reason = !enabled
                    ? "economy.enabled is false"
                    : "economy.enabled is true but no Vault economy provider is available";
            plugin.getLogger().warning("[Economy] " + configured + " money cost(s) are configured but will have NO effect ("
                    + reason + "). Those teleports are currently free. Install Vault + an economy plugin and set "
                    + "economy.enabled: true, or set those costs to 0 to silence this warning.");
        } catch (Throwable t) {
            // 诊断本身绝不能影响启动
            plugin.getLogger().fine("[Economy] cost audit failed: " + t.getMessage());
        }
    }

    /**
     * 这些类型由各功能自己的配置接管，{@code economy.costs} 里的同名键只在
     * {@code global_fallback} 开启时作为回退使用，因此不计入「直接生效」的那些。
     */
    private static final java.util.Set<String> FEATURE_OWNED_COST_KEYS = java.util.Set.of(
            "deathback", "stele", "guild", "towny", "tollwarp", "scroll", "portal", "rewind");

    /**
     * 统计实际会被收取的金钱项数量（大于 0 的），每个费用槽位只算一次。
     *
     * 走的是与运行期同一套 {@link #resolveMoney} 解析，因此这里的结果也顺带验证了
     * 「默认值层」是否生效：某功能删掉自己的 cost 键后，若全局回退开着且全局值大于 0，
     * 它就应当被算进来。
     */
    private static int countConfiguredMoneyCosts(StarTeleport plugin) {
        int count = 0;

        // 直接由 economy.costs 定价的传送类型（排除被各功能自己接管的那些，避免重复计数）
        ConfigurationSection costs = plugin.getConfig().getConfigurationSection("economy.costs");
        if (costs != null) {
            for (String key : costs.getKeys(false)) {
                if (FEATURE_OWNED_COST_KEYS.contains(key.toLowerCase(java.util.Locale.ROOT))) continue;
                if (costs.getDouble(key, 0.0) > 0) count++;
            }
        }

        count += resolveMoney(plugin, loadFeatureConfig(plugin, "death.yml"), "deathback",
                "cost.vault", "cost.vault_cost") > 0 ? 1 : 0;
        count += resolveMoney(plugin, loadFeatureConfig(plugin, "steles.yml"), "stele",
                "teleport_cost.vault_cost", "teleport_cost.vault") > 0 ? 1 : 0;

        ConfigurationSection guild = loadFeatureConfig(plugin, "guild_config.yml");
        if (resolveMoney(plugin, guild, "guild", "warps.cost") > 0) count++;
        if (resolveMoney(plugin, guild, "guild", "headquarters.cost") > 0) count++;

        ConfigurationSection towny = loadFeatureConfig(plugin, "features_config.yml");
        if (resolveMoney(plugin, towny, "towny", "towny.home_cost") > 0) count++;
        if (resolveMoney(plugin, towny, "towny", "towny.other_cost") > 0) count++;

        return count;
    }

    private static ConfigurationSection loadFeatureConfig(StarTeleport plugin, String fileName) {
        File f = new File(plugin.getDataFolder(), fileName);
        return f.exists() ? YamlConfiguration.loadConfiguration(f) : null;
    }
}
