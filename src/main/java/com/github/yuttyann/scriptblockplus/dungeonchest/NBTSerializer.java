package com.github.yuttyann.scriptblockplus.dungeonchest;

import de.tr7zw.changeme.nbtapi.NBTItem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class NBTSerializer {

    /**
     * Serialize an ItemStack to string (using Base64 for maximum compatibility)
     * This preserves ALL data including NBT, enchantments, lore, etc.
     */
    public static String serializeItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        
        return serializeItemBase64(itemStack);
    }

    /**
     * Deserialize an ItemStack from serialized string
     * 
     * 支持两种格式：
     * 1. 纯 Base64 格式 (DungeonChestGUI 保存的原始格式)
     * 2. Base64 + 概率格式 "base64|probability" (概率GUI修改后的格式)
     */
    public static ItemStack deserializeItem(String serialized) {
        if (serialized == null || serialized.isEmpty()) {
            return null;
        }
        
        // ★★★ 修复问题：支持带概率后缀的格式 ★★★
        // 如果数据包含 | 符号，说明是 LootItem 格式，需要先提取 base64 部分
        if (serialized.contains("|")) {
            String[] parts = serialized.split("\\|", 2);
            if (parts.length > 0 && !parts[0].isEmpty()) {
                return deserializeItemBase64(parts[0]);
            }
        }
        
        // Use Base64 deserialization (most reliable)
        return deserializeItemBase64(serialized);
    }

    /**
     * Serialize a list of items to list of strings
     */
    public static List<String> serializeItems(List<ItemStack> items) {
        List<String> serialized = new ArrayList<>();
        for (ItemStack item : items) {
            String serializedItem = serializeItem(item);
            if (serializedItem != null) {
                serialized.add(serializedItem);
            }
        }
        return serialized;
    }

    /**
     * Deserialize a list of strings back to items
     */
    public static List<ItemStack> deserializeItems(List<String> serializedItems) {
        List<ItemStack> items = new ArrayList<>();
        if (serializedItems == null) return items;
        
        for (String nbtString : serializedItems) {
            ItemStack item = deserializeItem(nbtString);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    /**
     * Get formatted NBT data as readable string (for debugging/info display)
     */
    public static String getFormattedNBT(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "Empty/Air Item";
        }
        
        try {
            NBTItem nbtItem = new NBTItem(itemStack);
            
            // Get basic info
            StringBuilder sb = new StringBuilder();
            sb.append("Material: ").append(itemStack.getType().name()).append("\n");
            sb.append("Amount: ").append(itemStack.getAmount()).append("\n");
            
            if (itemStack.hasItemMeta()) {
                var meta = itemStack.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    sb.append("Name: ").append(meta.getDisplayName()).append("\n");
                }
                if (meta != null && meta.hasLore()) {
                    sb.append("Lore: ").append(meta.getLore()).append("\n");
                }
            }
            
            sb.append("NBT Data:\n").append(nbtItem.toString());
            return sb.toString();
        } catch (Exception e) {
            return "Error reading NBT: " + e.getMessage();
        }
    }

    /**
     * Primary serialization method using Base64 encoding
     * This is Bukkit's native serialization - 100% reliable for all item types
     */
    public static String serializeItemBase64(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        
        try {
            ByteArrayOutputStream io = new ByteArrayOutputStream();
            BukkitObjectOutputStream os = new BukkitObjectOutputStream(io);
            os.writeObject(itemStack);
            os.flush();
            os.close();
            
            byte[] data = io.toByteArray();
            return Base64.getEncoder().encodeToString(data);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Deserialization from Base64 string
     */
    public static ItemStack deserializeItemBase64(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        
        try {
            byte[] data = Base64.getDecoder().decode(base64);
            ByteArrayInputStream io = new ByteArrayInputStream(data);
            BukkitObjectInputStream in = new BukkitObjectInputStream(io);
            Object obj = in.readObject();
            in.close();
            
            if (obj instanceof ItemStack) {
                return (ItemStack) obj;
            }
            return null;
        } catch (Exception e) {
            // Silently fail - this is expected for non-Base64 formats
            return null;
        }
    }
}
