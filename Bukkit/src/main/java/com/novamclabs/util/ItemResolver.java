package com.novamclabs.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 物品/方块解析与匹配工具
 * Item/block resolver & matcher util
 */
public class ItemResolver {

    public static ItemStack resolveItem(String spec) {
        if (spec == null) return null;
        spec = spec.trim();
        // ItemsAdder: itemsadder:namespace:item
        if (spec.toLowerCase().startsWith("itemsadder:")) {
            if (Bukkit.getPluginManager().getPlugin("ItemsAdder") != null) {
                try {
                    String id = spec.substring("itemsadder:".length());
                    Class<?> itemsAdder = Class.forName("dev.lone.itemsadder.api.CustomStack");
                    Object cs = itemsAdder.getMethod("getInstance", String.class).invoke(null, id);
                    if (cs != null) {
                        return (ItemStack) itemsAdder.getMethod("getItemStack").invoke(cs);
                    }
                } catch (Throwable ignored) {}
            }
            return null;
        }
        // MMOItems: mmoitems:TYPE:ID
        if (spec.toLowerCase().startsWith("mmoitems:")) {
            if (Bukkit.getPluginManager().getPlugin("MMOItems") != null) {
                try {
                    String remain = spec.substring("mmoitems:".length());
                    String[] arr = remain.split(":", 2);
                    String type = arr[0];
                    String id = arr.length > 1 ? arr[1] : null;
                    if (id == null) return null;
                    Class<?> typeEnum = Class.forName("net.Indyuce.mmoitems.api.Type");
                    Object t = typeEnum.getMethod("get", String.class).invoke(null, type);
                    Class<?> mmoItems = Class.forName("net.Indyuce.mmoitems.MMOItems");
                    Object plugin = mmoItems.getMethod("plugin").invoke(null);
                    Object manager = mmoItems.getMethod("getItemManager").invoke(plugin);
                    return (ItemStack) manager.getClass().getMethod("getItem", typeEnum, String.class).invoke(manager, t, id);
                } catch (Throwable ignored) {}
            }
            return null;
        }
        // Vanilla material
        Material m = Material.matchMaterial(spec);
        if (m != null) return new ItemStack(m);
        return null;
    }

    public static boolean matches(String spec, ItemStack stack) {
        if (stack == null) return false;
        if (spec == null) return false;
        spec = spec.trim();
        // ItemsAdder match
        if (spec.toLowerCase().startsWith("itemsadder:")) {
            if (Bukkit.getPluginManager().getPlugin("ItemsAdder") != null) {
                try {
                    Class<?> customStack = Class.forName("dev.lone.itemsadder.api.CustomStack");
                    Object cs = customStack.getMethod("byItemStack", ItemStack.class).invoke(null, stack);
                    if (cs == null) return false;
                    String id = (String) customStack.getMethod("getNamespacedID").invoke(cs);
                    return spec.substring("itemsadder:".length()).equalsIgnoreCase(id);
                } catch (Throwable ignored) {}
            }
            return false;
        }
        // MMOItems match
        if (spec.toLowerCase().startsWith("mmoitems:")) {
            if (Bukkit.getPluginManager().getPlugin("MMOItems") != null) {
                try {
                    String remain = spec.substring("mmoitems:".length());
                    String[] arr = remain.split(":", 2);
                    String type = arr[0];
                    String id = arr.length > 1 ? arr[1] : null;
                    if (id == null) return false;
                    Class<?> rpgItemClass = Class.forName("net.Indyuce.mmoitems.api.item.NBTItem");
                    Object nbt = rpgItemClass.getMethod("get", ItemStack.class).invoke(null, stack);
                    if (nbt == null) return false;
                    String sid = (String) nbt.getClass().getMethod("getString", String.class).invoke(nbt, "MMOITEMS_ITEM_ID");
                    String stype = (String) nbt.getClass().getMethod("getString", String.class).invoke(nbt, "MMOITEMS_ITEM_TYPE");
                    return id.equalsIgnoreCase(sid) && type.equalsIgnoreCase(stype);
                } catch (Throwable ignored) {}
            }
            return false;
        }
        // Vanilla
        Material m = Material.matchMaterial(spec);
        return m != null && stack.getType() == m;
    }

    public static boolean blockMatchesFrame(String spec, Block block) {
        if (block == null || spec == null) return false;
        if (spec.toLowerCase().startsWith("itemsadder:")) {
            if (Bukkit.getPluginManager().getPlugin("ItemsAdder") != null) {
                try {
                    Class<?> cb = Class.forName("dev.lone.itemsadder.api.CustomBlock");
                    Object custom = cb.getMethod("byAlreadyPlaced", Block.class).invoke(null, block);
                    if (custom == null) return false;
                    String id = (String) cb.getMethod("getNamespacedID").invoke(custom);
                    return spec.substring("itemsadder:".length()).equalsIgnoreCase(id);
                } catch (Throwable ignored) {}
            }
            return false;
        }
        Material m = Material.matchMaterial(spec);
        return m != null && block.getType() == m;
    }
}
