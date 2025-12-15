package com.novamclabs.util;

import com.novamclabs.StarTeleport;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

/**
 * Display helper for sending messages via CHAT / ACTION_BAR / TITLE.
 */
public final class DisplayUtil {

    private DisplayUtil() {
    }

    public enum Mode {
        CHAT,
        ACTION_BAR,
        TITLE
    }

    public static Mode getDefaultMode(StarTeleport plugin) {
        String raw = plugin.getConfig().getString("display_settings.default_mode", "TITLE");
        if (raw == null) {
            return Mode.TITLE;
        }
        try {
            return Mode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return Mode.TITLE;
        }
    }

    public static void sendCountdown(StarTeleport plugin, Player player, int seconds) {
        Mode mode = getDefaultMode(plugin);
        switch (mode) {
            case TITLE -> sendCountdownTitle(plugin, player, seconds);
            case ACTION_BAR -> sendActionBar(player, plugin.getLang().tr("teleport.actionbar.countdown", "seconds", seconds));
            case CHAT -> player.sendMessage(plugin.getLang().tr("teleport.actionbar.countdown", "seconds", seconds));
        }
    }

    private static void sendCountdownTitle(StarTeleport plugin, Player player, int seconds) {
        String title = plugin.getLang().t("teleport.title.countdown");
        String subtitle = plugin.getLang().tr("teleport.subtitle.countdown", "seconds", seconds);

        int fadeIn = plugin.getConfig().getInt("display_settings.countdown_title_options.fade_in", 0);
        int stay = plugin.getConfig().getInt("display_settings.countdown_title_options.stay", 25);
        int fadeOut = plugin.getConfig().getInt("display_settings.countdown_title_options.fade_out", 0);

        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
    }

    public static void sendTitle(StarTeleport plugin, Player player, String title, String subtitle) {
        int fadeIn = plugin.getConfig().getInt("display_settings.title_options.fade_in", 10);
        int stay = plugin.getConfig().getInt("display_settings.title_options.stay", 40);
        int fadeOut = plugin.getConfig().getInt("display_settings.title_options.fade_out", 10);
        player.sendTitle(title, subtitle == null ? "" : subtitle, fadeIn, stay, fadeOut);
    }

    public static void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }
}
