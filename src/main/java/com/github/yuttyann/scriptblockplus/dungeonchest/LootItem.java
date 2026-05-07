package com.github.yuttyann.scriptblockplus.dungeonchest;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class LootItem {

    private final ItemStack itemStack;
    private final int weight;
    private final int minAmount;
    private final int maxAmount;
    private double probability; // 概率 (0-100%)
    
    public static final double DEFAULT_PROBABILITY = 50.0;
    public static final double MIN_PROBABILITY = 0.0;
    public static final double MAX_PROBABILITY = 100.0;

    public LootItem(ItemStack itemStack, int weight, int minAmount, int maxAmount, double probability) {
        this.itemStack = itemStack;
        this.weight = weight;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        setProbability(probability);
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public int getWeight() {
        return weight;
    }

    public int getMinAmount() {
        return minAmount;
    }

    public int getMaxAmount() {
        return maxAmount;
    }
    
    /**
     * 获取概率 (0-100)
     */
    public double getProbability() {
        return probability;
    }
    
    /**
     * 设置概率 (自动限制在 0-100 范围内)
     */
    public void setProbability(double probability) {
        this.probability = Math.max(MIN_PROBABILITY, Math.min(MAX_PROBABILITY, probability));
    }
    
    /**
     * 增加概率
     * @param amount 增加量
     */
    public void increaseProbability(double amount) {
        setProbability(probability + amount);
    }
    
    /**
     * 减少概率
     * @param amount 减少量
     */
    public void decreaseProbability(double amount) {
        setProbability(probability - amount);
    }
    
    @Deprecated
    public double getChance() {
        return probability;
    }

    public static LootItem fromConfig(ConfigurationSection section) {
        if (section == null) return null;

        ItemStack item = (ItemStack) section.get("item");
        int weight = section.getInt("weight", 1);
        int minAmount = section.getInt("min-amount", 1);
        int maxAmount = section.getInt("max-amount", 1);
        double probability = section.getDouble("probability", section.getDouble("chance", DEFAULT_PROBABILITY));

        if (item == null) return null;

        return new LootItem(item, weight, minAmount, maxAmount, probability);
    }
    
    /**
     * 保存到配置
     */
    public void saveToConfig(ConfigurationSection section) {
        section.set("item", itemStack);
        section.set("weight", weight);
        section.set("min-amount", minAmount);
        section.set("max-amount", maxAmount);
        section.set("probability", probability);
    }

    public ItemStack generateItem(Random random) {
        if (random.nextDouble() * 100 > probability) {
            return null;
        }

        int amount = minAmount;
        if (maxAmount > minAmount) {
            amount = minAmount + random.nextInt(maxAmount - minAmount + 1);
        }

        ItemStack result = itemStack.clone();
        result.setAmount(amount);

        return result;
    }
    
    /**
     * 创建带有概率显示的物品（用于GUI显示）
     */
    public ItemStack createDisplayItem() {
        ItemStack display = itemStack.clone();
        ItemMeta meta = display.getItemMeta();
        
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            
            lore.add("");
            lore.add(String.format("§e⚡ 概率: §f%.1f%%", probability));
            
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        
        return display;
    }
    
    /**
     * 从字符串反序列化
     * 
     * 支持两种格式：
     * 1. 纯 Base64 格式 (DungeonChestGUI 保存的原始格式) - 使用默认概率
     * 2. Base64 + 概率格式 "base64|probability" (概率GUI修改后的格式)
     * 
     * 注意：如果原始物品有自定义数量，会保留该数量（minAmount = maxAmount = 原始数量）
     */
    public static LootItem fromString(String data) {
        if (data == null || data.isEmpty()) return null;
        
        try {
            String base64Data;
            double prob = DEFAULT_PROBABILITY;
            
            // ★★★ 修复问题：支持纯Base64格式和带概率后缀的格式 ★★★
            if (data.contains("|")) {
                // 带概率后缀的格式：base64|probability
                String[] parts = data.split("\\|", 2);
                base64Data = parts[0];
                if (parts.length > 1) {
                    prob = Double.parseDouble(parts[1]);
                }
            } else {
                // 纯Base64格式：使用默认概率
                base64Data = data;
            }
            
            // 使用 NBTSerializer 反序列化物品
            ItemStack itemStack = NBTSerializer.deserializeItem(base64Data);
            if (itemStack == null) return null;
            
            // ★★★ 修复核心问题：保留物品的原始数量 ★★★
            // 当玩家在 GUI 中放入 6 颗钻石时，序列化的物品数量就是 6
            // 我们需要用这个原始数量作为 minAmount 和 maxAmount
            int originalAmount = itemStack.getAmount();
            
            // 如果原始数量大于0，使用原始数量；否则默认为1
            int effectiveAmount = Math.max(1, originalAmount);
            
            return new LootItem(itemStack, 1, effectiveAmount, effectiveAmount, prob);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 序列化为字符串
     */
    @Override
    public String toString() {
        try {
            // 序列化物品数据 + 概率信息
            String base64 = NBTSerializer.serializeItem(itemStack);
            if (base64 == null) return "";
            return base64 + "|" + probability;
        } catch (Exception e) {
            return "";
        }
    }
}
