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
}
