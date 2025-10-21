package com.novamclabs.util;

import com.novamclabs.StarTeleport;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * 基岩版 Form 表单工具（基于 Floodgate + Cumulus 反射调用）
 * Bedrock Forms util (Floodgate + Cumulus via reflection; no compile deps)
 */
public class BedrockFormsUtil {
    public static boolean isFloodgatePresent() {
        return Bukkit.getPluginManager().getPlugin("floodgate") != null;
    }

    /**
     * 显示一个包含 接受/拒绝 的传送请求表单
     * Show a TP request form with Accept / Deny buttons
     */
    public static boolean showTpaRequestForm(StarTeleport plugin, Player target, String requesterName, boolean here) {
        if (!isFloodgatePresent()) return false;
        try {
            Class<?> apiClz = Class.forName("floodgate.api.FloodgateApi");
            Object api = apiClz.getMethod("getInstance").invoke(null);

            // SimpleForm builder
            Class<?> formClz = Class.forName("org.geysermc.cumulus.form.SimpleForm");
            Object builder = formClz.getMethod("builder").invoke(null);
            Method title = builder.getClass().getMethod("title", String.class);
            Method content = builder.getClass().getMethod("content", String.class);
            Method button = builder.getClass().getMethod("button", String.class);

            title.invoke(builder, plugin.getLang().t("menu.main.title"));
            String msg = here ? plugin.getLang().tr("tpa.prompt.to_here", "requester", requesterName)
                    : plugin.getLang().tr("tpa.prompt.to_you", "requester", requesterName);
            content.invoke(builder, msg.replace('§', ' '));
            button.invoke(builder, plugin.getLang().t("tpa.accepted.start")); // accept button
            button.invoke(builder, plugin.getLang().t("tpa.denied.target"));  // deny button

            // handler: BiConsumer<form, response>
            Method valid = null;
            for (Method m : builder.getClass().getMethods()) {
                if (m.getName().equals("validResultHandler") && m.getParameterCount() == 1) {
                    valid = m; break;
                }
            }
            if (valid != null) {
                BiConsumer<Object, Object> handler = (form, response) -> {
                    try {
                        int id = (int) response.getClass().getMethod("getClickedButtonId").invoke(response);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (id == 0) target.performCommand("tpaccept");
                            else target.performCommand("tpdeny");
                        });
                    } catch (Throwable ignored) {}
                };
                valid.invoke(builder, handler);
            }

            Method build = builder.getClass().getMethod("build");
            Object form = build.invoke(builder);

            // api.sendForm(UUID, form)
            Method send = null;
            for (Method m : apiClz.getMethods()) {
                if (m.getName().equals("sendForm") && m.getParameterCount() == 2) {
                    send = m; break;
                }
            }
            if (send == null) return false;
            send.invoke(api, target.getUniqueId(), form);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 显示列表选择表单（点击后执行命令）| Show a list selection form and run command on click
     */
    public static boolean showListCommandForm(StarTeleport plugin, Player player, String titleText, java.util.List<String> entries, String commandPrefix) {
        if (!isFloodgatePresent()) return false;
        try {
            Class<?> apiClz = Class.forName("floodgate.api.FloodgateApi");
            Object api = apiClz.getMethod("getInstance").invoke(null);
            Class<?> formClz = Class.forName("org.geysermc.cumulus.form.SimpleForm");
            Object builder = formClz.getMethod("builder").invoke(null);
            java.lang.reflect.Method title = builder.getClass().getMethod("title", String.class);
            java.lang.reflect.Method content = builder.getClass().getMethod("content", String.class);
            java.lang.reflect.Method button = builder.getClass().getMethod("button", String.class);
            title.invoke(builder, titleText);
            content.invoke(builder, " ");
            for (String s : entries) button.invoke(builder, s);
            java.lang.reflect.Method valid = null;
            for (java.lang.reflect.Method m : builder.getClass().getMethods()) {
                if (m.getName().equals("validResultHandler") && m.getParameterCount() == 1) { valid = m; break; }
            }
            if (valid != null) {
                java.util.function.BiConsumer<Object, Object> handler = (form, response) -> {
                    try {
                        int id = (int) response.getClass().getMethod("getClickedButtonId").invoke(response);
                        if (id >= 0 && id < entries.size()) {
                            String entry = entries.get(id);
                            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(commandPrefix + " " + entry));
                        }
                    } catch (Throwable ignored) {}
                };
                valid.invoke(builder, handler);
            }
            java.lang.reflect.Method build = builder.getClass().getMethod("build");
            Object form = build.invoke(builder);
            java.lang.reflect.Method send = null;
            for (java.lang.reflect.Method m : apiClz.getMethods()) {
                if (m.getName().equals("sendForm") && m.getParameterCount() == 2) { send = m; break; }
            }
            if (send == null) return false;
            send.invoke(api, player.getUniqueId(), form);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean showModalConfirm(StarTeleport plugin, Player player, String title, String content, String yes, String no, Runnable onYes) {
        if (!isFloodgatePresent()) return false;
        try {
            Class<?> apiClz = Class.forName("floodgate.api.FloodgateApi");
            Object api = apiClz.getMethod("getInstance").invoke(null);
            Class<?> formClz = Class.forName("org.geysermc.cumulus.form.ModalForm");
            Object builder = formClz.getMethod("builder").invoke(null);
            java.lang.reflect.Method titleM = builder.getClass().getMethod("title", String.class);
            java.lang.reflect.Method contentM = builder.getClass().getMethod("content", String.class);
            java.lang.reflect.Method b1 = builder.getClass().getMethod("button1", String.class);
            java.lang.reflect.Method b2 = builder.getClass().getMethod("button2", String.class);
            titleM.invoke(builder, title);
            contentM.invoke(builder, content);
            b1.invoke(builder, yes);
            b2.invoke(builder, no);
            java.util.function.BiConsumer<Object, Object> handler = (form, response) -> {
                try {
                    boolean result;
                    try {
                        result = (boolean) response.getClass().getMethod("getResult").invoke(response);
                    } catch (Throwable t0) {
                        int id = (int) response.getClass().getMethod("getClickedButtonId").invoke(response);
                        result = (id == 0);
                    }
                    if (result && onYes != null) org.bukkit.Bukkit.getScheduler().runTask(plugin, onYes);
                } catch (Throwable ignored) {}
            };
            java.lang.reflect.Method valid2 = null;
            for (java.lang.reflect.Method m : builder.getClass().getMethods()) {
                if (m.getName().equals("validResultHandler") && m.getParameterCount() == 1) { valid2 = m; break; }
            }
            if (valid2 != null) valid2.invoke(builder, handler);
            java.lang.reflect.Method build = builder.getClass().getMethod("build");
            Object form = build.invoke(builder);
            java.lang.reflect.Method send = null;
            for (java.lang.reflect.Method m : apiClz.getMethods()) {
                if (m.getName().equals("sendForm") && m.getParameterCount() == 2) { send = m; break; }
            }
            if (send == null) return false;
            send.invoke(api, player.getUniqueId(), form);
            return true;
        } catch (Throwable t) { return false; }
    }

    public static boolean showRtpRadiusForm(StarTeleport plugin, Player player, int current, int step, int max, java.util.function.IntConsumer consumer) {
        if (!isFloodgatePresent()) return false;
        try {
            Class<?> apiClz = Class.forName("floodgate.api.FloodgateApi");
            Object api = apiClz.getMethod("getInstance").invoke(null);
            Class<?> formClz = Class.forName("org.geysermc.cumulus.form.CustomForm");
            Object builder = formClz.getMethod("builder").invoke(null);
            java.lang.reflect.Method title = builder.getClass().getMethod("title", String.class);
            title.invoke(builder, plugin.getLang().t("menu.rtp.title"));
            boolean ok = false;
            try {
                java.lang.reflect.Method slider = builder.getClass().getMethod("slider", String.class, int.class, int.class, int.class, int.class);
                slider.invoke(builder, plugin.getLang().t("menu.rtp.current"), step, max, step, Math.max(step, Math.min(max, current)));
                ok = true;
            } catch (Throwable ignored) {}
            if (!ok) {
                try {
                    java.lang.reflect.Method slider = builder.getClass().getMethod("slider", String.class, double.class, double.class, double.class, double.class);
                    slider.invoke(builder, plugin.getLang().t("menu.rtp.current"), (double) step, (double) max, (double) step, (double) Math.max(step, Math.min(max, current)));
                } catch (Throwable ignored) {}
            }
            java.util.function.BiConsumer<Object, Object> handler = (form, response) -> {
                try {
                    Object nextVal = null;
                    try { nextVal = response.getClass().getMethod("next").invoke(response); }
                    catch (Throwable t2) {
                        try { nextVal = response.getClass().getMethod("getSlider", int.class).invoke(response, 0); } catch (Throwable t3) { nextVal = 0; }
                    }
                    final int radius = nextVal instanceof Number ? ((Number) nextVal).intValue() : current;
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> consumer.accept(radius));
                } catch (Throwable ignored) {}
            };
            java.lang.reflect.Method valid = null;
            for (java.lang.reflect.Method m : builder.getClass().getMethods()) {
                if (m.getName().equals("validResultHandler") && m.getParameterCount() == 1) { valid = m; break; }
            }
            if (valid != null) valid.invoke(builder, handler);
            java.lang.reflect.Method build = builder.getClass().getMethod("build");
            Object form = build.invoke(builder);
            java.lang.reflect.Method send = null;
            for (java.lang.reflect.Method m : apiClz.getMethods()) {
                if (m.getName().equals("sendForm") && m.getParameterCount() == 2) { send = m; break; }
            }
            if (send == null) return false;
            send.invoke(api, player.getUniqueId(), form);
            return true;
        } catch (Throwable t) { return false; }
    }
}
