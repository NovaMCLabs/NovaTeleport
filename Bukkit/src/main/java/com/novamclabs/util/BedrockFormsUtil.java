package com.novamclabs.util;

import com.novamclabs.StarTeleport;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.logging.Level;

/**
 * 基岩版 Form 表单工具（基于 Floodgate + Cumulus 反射调用）
 * Bedrock Forms util (Floodgate + Cumulus via reflection; both are soft dependencies)
 */
public final class BedrockFormsUtil {

    /** 每个阶段只告警一次：全局单一标志会让首个失败掩盖后续不相关的诊断 | warn once per stage */
    private static final Set<String> WARNED_STAGES = ConcurrentHashMap.newKeySet();

    /** 按钮数上限，与 Java 版 54 格箱子菜单取齐；再多客户端表单会长到无法操作 */
    private static final int MAX_BUTTONS = 54;

    private BedrockFormsUtil() {
    }

    /** 表单文本是纯文本，不解析 §；标题/正文/按钮都必须先剥色，否则玩家看到字面量 "§e" */
    private static String plain(String s) {
        if (s == null) return "";
        if (s.indexOf('§') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§') { i++; continue; }
            sb.append(c);
        }
        return sb.toString();
    }

    public static boolean isFloodgatePresent() {
        return Bukkit.getPluginManager().getPlugin("floodgate") != null;
    }

    /** config.yml 的总开关：关闭后所有表单立即返回 false，调用方自然落到聊天路径 */
    private static boolean formsEnabled(StarTeleport plugin) {
        return plugin.getConfig().getBoolean("bedrock.forms.enabled", true);
    }

    private static void warnOnce(StarTeleport plugin, String stage, Throwable t) {
        if (!WARNED_STAGES.add(stage)) return;
        plugin.getLogger().log(Level.WARNING, "[Bedrock] Form API call failed at " + stage
                + " (" + t.getClass().getSimpleName() + ": " + t.getMessage()
                + "). Bedrock players will fall back to chat messages.");
    }

    private static Object floodgateApi() throws Exception {
        Class<?> apiClz = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
        return apiClz.getMethod("getInstance").invoke(null);
    }

    private static Object newBuilder(String formClass) throws Exception {
        Class<?> formClz = Class.forName(formClass);
        return formClz.getMethod("builder").invoke(null);
    }

    /**
     * 找到 validResultHandler(BiConsumer) 重载。
     * Cumulus 同时提供 validResultHandler(Consumer) —— 只按参数个数匹配会拿到错误的重载。
     */
    private static Method findValidResultHandler(Object builder) {
        for (Method m : builder.getClass().getMethods()) {
            if (!m.getName().equals("validResultHandler") || m.getParameterCount() != 1) continue;
            if (BiConsumer.class.isAssignableFrom(m.getParameterTypes()[0])) return m;
        }
        return null;
    }

    /** FloodgateApi 有 4 个同参数个数的 sendForm 重载，只按个数会拿到 FormBuilder 版本而抛错 */
    private static boolean sendForm(Object api, Player player, Object form) throws Exception {
        for (Method m : api.getClass().getMethods()) {
            if (m.getName().equals("sendForm") && m.getParameterCount() == 2
                    && m.getParameterTypes()[1].isInstance(form)) {
                m.invoke(api, player.getUniqueId(), form);
                return true;
            }
        }
        return false;
    }

    private static Object build(Object builder) throws Exception {
        return builder.getClass().getMethod("build").invoke(builder);
    }

    /**
     * 显示一个包含 接受/拒绝 的传送请求表单
     * Show a TP request form with Accept / Deny buttons
     */
    public static boolean showTpaRequestForm(StarTeleport plugin, Player target, String requesterName, boolean here) {
        if (!formsEnabled(plugin)) return false;
        if (!isFloodgatePresent()) return false;
        try {
            Object api = floodgateApi();
            Object builder = newBuilder("org.geysermc.cumulus.form.SimpleForm");
            builder.getClass().getMethod("title", String.class).invoke(builder, plain(plugin.getLang().t("menu.main.title")));
            String msg = here ? plugin.getLang().tr("tpa.prompt.to_here", "requester", requesterName)
                    : plugin.getLang().tr("tpa.prompt.to_you", "requester", requesterName);
            builder.getClass().getMethod("content", String.class).invoke(builder, plain(msg));
            // 按钮必须用短标签：整句提示会撑爆按钮，且会重复 content 的内容
            Method button = builder.getClass().getMethod("button", String.class);
            button.invoke(builder, plain(plugin.getLang().t("tpa.button.accept")));
            button.invoke(builder, plain(plugin.getLang().t("tpa.button.deny")));

            Method valid = findValidResultHandler(builder);
            if (valid == null) return false;
            BiConsumer<Object, Object> handler = (form, response) -> {
                try {
                    int id = (int) response.getClass().getMethod("getClickedButtonId").invoke(response);
                    plugin.getScheduler().runAtEntity(target, () -> {
                        target.performCommand(id == 0 ? "tpaccept" : "tpdeny");
                    });
                } catch (Throwable t) {
                    warnOnce(plugin, "tpa-result", t);
                }
            };
            valid.invoke(builder, handler);

            return sendForm(api, target, build(builder));
        } catch (Throwable t) {
            warnOnce(plugin, "tpa-form", t);
            return false;
        }
    }

    /**
     * 显示列表选择表单（点击后执行命令）| Show a list selection form and run command on click
     * entries 是按钮显示文字，commandArgs 是与之对应的命令参数——两者必须分开，
     * 否则本地化后的菜单标签会被当成子命令执行。
     */
    public static boolean showListCommandForm(StarTeleport plugin, Player player, String titleText,
                                              List<String> entries, List<String> commandArgs, String commandPrefix) {
        if (!formsEnabled(plugin)) return false;
        if (!isFloodgatePresent()) return false;
        try {
            Object api = floodgateApi();
            Object builder = newBuilder("org.geysermc.cumulus.form.SimpleForm");
            builder.getClass().getMethod("title", String.class).invoke(builder, plain(titleText));
            builder.getClass().getMethod("content", String.class).invoke(builder, " ");
            int shown = Math.min(entries.size(), Math.min(MAX_BUTTONS, commandArgs.size()));
            Method button = builder.getClass().getMethod("button", String.class);
            for (int i = 0; i < shown; i++) button.invoke(builder, plain(entries.get(i)));
            // 截断就不能沉默：被截掉的目的地在表单里点不到，必须让玩家改用命令
            if (shown < entries.size()) {
                player.sendMessage(plugin.getLang().tr("bedrock.list.truncated",
                        "shown", shown, "total", entries.size()));
            }

            Method valid = findValidResultHandler(builder);
            if (valid == null) return false;
            BiConsumer<Object, Object> handler = (form, response) -> {
                try {
                    int id = (int) response.getClass().getMethod("getClickedButtonId").invoke(response);
                    if (id >= 0 && id < shown) {
                        String arg = commandArgs.get(id);
                        plugin.getScheduler().runAtEntity(player, () -> player.performCommand(commandPrefix + " " + arg));
                    }
                } catch (Throwable t) {
                    warnOnce(plugin, "list-result", t);
                }
            };
            valid.invoke(builder, handler);

            return sendForm(api, player, build(builder));
        } catch (Throwable t) {
            warnOnce(plugin, "list-form", t);
            return false;
        }
    }

    public static boolean showModalConfirm(StarTeleport plugin, Player player, String title, String content, String yes, String no, Runnable onYes) {
        if (!formsEnabled(plugin)) return false;
        if (!isFloodgatePresent()) return false;
        try {
            Object api = floodgateApi();
            Object builder = newBuilder("org.geysermc.cumulus.form.ModalForm");
            builder.getClass().getMethod("title", String.class).invoke(builder, plain(title));
            builder.getClass().getMethod("content", String.class).invoke(builder, plain(content));
            builder.getClass().getMethod("button1", String.class).invoke(builder, plain(yes));
            builder.getClass().getMethod("button2", String.class).invoke(builder, plain(no));

            Method valid = findValidResultHandler(builder);
            // 没有回调就发出去 = 玩家点“是”什么都不发生，与其余 show* 保持一致直接失败
            if (valid == null) return false;
            BiConsumer<Object, Object> handler = (form, response) -> {
                try {
                    boolean result = (boolean) response.getClass().getMethod("getResult").invoke(response);
                    if (result && onYes != null) plugin.getScheduler().runAtEntity(player, onYes);
                } catch (Throwable t) {
                    warnOnce(plugin, "modal-result", t);
                }
            };
            valid.invoke(builder, handler);

            return sendForm(api, player, build(builder));
        } catch (Throwable t) {
            warnOnce(plugin, "modal-form", t);
            return false;
        }
    }

    public static boolean showRtpRadiusForm(StarTeleport plugin, Player player, int current, int step, int max, IntConsumer consumer) {
        if (!formsEnabled(plugin)) return false;
        if (!isFloodgatePresent()) return false;
        try {
            Object api = floodgateApi();
            Object builder = newBuilder("org.geysermc.cumulus.form.CustomForm");
            builder.getClass().getMethod("title", String.class).invoke(builder, plain(plugin.getLang().t("menu.rtp.title")));

            // Cumulus 1.x 只有 float 重载：slider(text, min, max, step, defaultValue)。
            // 半径滑块的下界取一个步长，避免出现 0 半径的无意义选项。
            float minF = step;
            float maxF = max;
            float stepF = step;
            float defaultF = Math.max(minF, Math.min(maxF, current));
            builder.getClass().getMethod("slider", String.class, float.class, float.class, float.class, float.class)
                    .invoke(builder, plain(plugin.getLang().tr("menu.rtp.current", "radius", current)), minF, maxF, stepF, defaultF);

            BiConsumer<Object, Object> handler = (form, response) -> {
                try {
                    // Cumulus 1.x：CustomFormResponse#getSlider(int) 才是取值方法；
                    // next() 是“移动到下一个组件”，直接调用拿不到数值。
                    Object raw = response.getClass().getMethod("getSlider", int.class).invoke(response, 0);
                    final int radius = raw instanceof Number ? ((Number) raw).intValue() : current;
                    plugin.getScheduler().runAtEntity(player, () -> consumer.accept(radius));
                } catch (Throwable t) {
                    warnOnce(plugin, "rtp-result", t);
                }
            };
            Method valid = findValidResultHandler(builder);
            if (valid == null) return false;
            valid.invoke(builder, handler);

            return sendForm(api, player, build(builder));
        } catch (Throwable t) {
            warnOnce(plugin, "rtp-form", t);
            return false;
        }
    }
}
