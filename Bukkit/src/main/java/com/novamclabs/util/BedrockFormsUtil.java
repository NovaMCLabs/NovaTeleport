package com.novamclabs.util;

import com.novamclabs.StarTeleport;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.logging.Level;

/**
 * 基岩版 Form 表单工具（基于 Floodgate + Cumulus 反射调用）
 * Bedrock Forms util (Floodgate + Cumulus via reflection; both are soft dependencies)
 */
public final class BedrockFormsUtil {

    /** 只在首次失败时告警一次，避免刷屏 | warn only once */
    private static boolean warned = false;

    private BedrockFormsUtil() {
    }

    public static boolean isFloodgatePresent() {
        return Bukkit.getPluginManager().getPlugin("floodgate") != null;
    }

    private static void warnOnce(StarTeleport plugin, String stage, Throwable t) {
        if (warned) return;
        warned = true;
        plugin.getLogger().log(Level.WARNING, "[Bedrock] Form API call failed at " + stage
                + " (" + t.getClass().getSimpleName() + ": " + t.getMessage()
                + "). Bedrock players will fall back to chat messages.");
    }

    private static Object floodgateApi() throws Exception {
        Class<?> apiClz = Class.forName("floodgate.api.FloodgateApi");
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

    private static boolean sendForm(Object api, Player player, Object form) throws Exception {
        for (Method m : api.getClass().getMethods()) {
            if (m.getName().equals("sendForm") && m.getParameterCount() == 2) {
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
        if (!isFloodgatePresent()) return false;
        try {
            Object api = floodgateApi();
            Object builder = newBuilder("org.geysermc.cumulus.form.SimpleForm");
            builder.getClass().getMethod("title", String.class).invoke(builder, plugin.getLang().t("menu.main.title"));
            String msg = here ? plugin.getLang().tr("tpa.prompt.to_here", "requester", requesterName)
                    : plugin.getLang().tr("tpa.prompt.to_you", "requester", requesterName);
            builder.getClass().getMethod("content", String.class).invoke(builder, msg.replace('§', ' '));
            Method button = builder.getClass().getMethod("button", String.class);
            button.invoke(builder, plugin.getLang().t("tpa.accepted.start"));
            button.invoke(builder, plugin.getLang().t("tpa.denied.target"));

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
     */
    public static boolean showListCommandForm(StarTeleport plugin, Player player, String titleText, List<String> entries, String commandPrefix) {
        if (!isFloodgatePresent()) return false;
        try {
            Object api = floodgateApi();
            Object builder = newBuilder("org.geysermc.cumulus.form.SimpleForm");
            builder.getClass().getMethod("title", String.class).invoke(builder, titleText);
            builder.getClass().getMethod("content", String.class).invoke(builder, " ");
            Method button = builder.getClass().getMethod("button", String.class);
            for (String s : entries) button.invoke(builder, s);

            Method valid = findValidResultHandler(builder);
            if (valid == null) return false;
            BiConsumer<Object, Object> handler = (form, response) -> {
                try {
                    int id = (int) response.getClass().getMethod("getClickedButtonId").invoke(response);
                    if (id >= 0 && id < entries.size()) {
                        String entry = entries.get(id);
                        plugin.getScheduler().runAtEntity(player, () -> player.performCommand(commandPrefix + " " + entry));
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
        if (!isFloodgatePresent()) return false;
        try {
            Object api = floodgateApi();
            Object builder = newBuilder("org.geysermc.cumulus.form.ModalForm");
            builder.getClass().getMethod("title", String.class).invoke(builder, title);
            builder.getClass().getMethod("content", String.class).invoke(builder, content);
            builder.getClass().getMethod("button1", String.class).invoke(builder, yes);
            builder.getClass().getMethod("button2", String.class).invoke(builder, no);

            BiConsumer<Object, Object> handler = (form, response) -> {
                try {
                    boolean result = (boolean) response.getClass().getMethod("getResult").invoke(response);
                    if (result && onYes != null) plugin.getScheduler().runAtEntity(player, onYes);
                } catch (Throwable t) {
                    warnOnce(plugin, "modal-result", t);
                }
            };
            Method valid = findValidResultHandler(builder);
            if (valid != null) valid.invoke(builder, handler);

            return sendForm(api, player, build(builder));
        } catch (Throwable t) {
            warnOnce(plugin, "modal-form", t);
            return false;
        }
    }

    public static boolean showRtpRadiusForm(StarTeleport plugin, Player player, int current, int step, int max, IntConsumer consumer) {
        if (!isFloodgatePresent()) return false;
        try {
            Object api = floodgateApi();
            Object builder = newBuilder("org.geysermc.cumulus.form.CustomForm");
            builder.getClass().getMethod("title", String.class).invoke(builder, plugin.getLang().t("menu.rtp.title"));

            boolean sliderOk = false;
            try {
                builder.getClass().getMethod("slider", String.class, int.class, int.class, int.class, int.class)
                        .invoke(builder, plugin.getLang().t("menu.rtp.current"), step, max, step, Math.max(step, Math.min(max, current)));
                sliderOk = true;
            } catch (Throwable ignored) {
            }
            if (!sliderOk) {
                builder.getClass().getMethod("slider", String.class, double.class, double.class, double.class, double.class)
                        .invoke(builder, plugin.getLang().t("menu.rtp.current"),
                                (double) step, (double) max, (double) step, (double) Math.max(step, Math.min(max, current)));
            }

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
