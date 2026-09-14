package com.novamclabs.listener;

import com.novamclabs.StarTeleport;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * 菜单防拖拽守卫。
 * 本插件每个自定义菜单都只取消了 InventoryClickEvent，拖拽事件无人处理，
 * 玩家仍能把物品拖进/拖出菜单，导致物品丢失或复制。
 */
public class MenuDragGuard implements Listener {

    /**
     * 本插件的菜单 holder 都是私有内部类，无法按类型引用：
     * TeleportCommandHandler.MenuHolder、TeleportLogCommand.LogMenuHolder、
     * SteleManager.SteleMenuHolder、TollWarpCommand.TollMenuHolder。
     * 因此按包名判定：只要容器是本插件自己造的菜单就取消拖拽，其他插件的容器一律放行。
     */
    private static final String PLUGIN_PACKAGE = "com.novamclabs.";

    public MenuDragGuard(StarTeleport plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!isPluginMenu(e.getView().getTopInventory().getHolder())) return;
        e.setCancelled(true);
    }

    private static boolean isPluginMenu(InventoryHolder holder) {
        return holder != null && holder.getClass().getName().startsWith(PLUGIN_PACKAGE);
    }
}
