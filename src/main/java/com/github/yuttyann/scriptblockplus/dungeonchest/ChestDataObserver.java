package com.github.yuttyann.scriptblockplus.dungeonchest;

import org.bukkit.Location;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ★★★ 地牢箱子数据观察者接口 ★★★
 * 
 * 实现观察者模式（Observer Pattern），
 * 当 DungeonChestData 发生变化时，所有注册的观察者都会收到通知。
 * 
 * 用途：
 * - GUI 实时更新（物品编辑GUI、概率配置GUI）
 * - 全息显示刷新
 * - 数据同步检查
 */
public interface ChestDataObserver {
    
    /**
     * 当箱子的配置物品列表发生变化时调用
     * 
     * @param chestLocation 箱子位置
     * @param newItems 新的物品列表（序列化后的）
     * @param changeType 变更类型
     */
    void onConfigItemsChanged(Location chestLocation, List<String> newItems, ChangeType changeType);
    
    /**
     * 当箱子的概率配置发生变化时调用
     * 
     * @param chestLocation 箱子位置
     * @param probabilityMap 物品索引 → 概率值 的映射
     */
    void onProbabilityChanged(Location chestLocation, Map<Integer, Double> probabilityMap);
    
    /**
     * 当箱子被刷新时调用
     * 
     * @param chestLocation 箱子位置
     * @param refreshTime 刷新时间戳
     * @param generatedItems 本次生成的物品（可为null如果未知）
     */
    void onChestRefreshed(Location chestLocation, long refreshTime, List<String> generatedItems);
    
    /**
     * 当玩家与箱子交互时调用（拿取/放入物品）
     * 
     * @param chestLocation 箱子位置
     * @param playerUUID 玩家UUID
     * @param interactionType 交互类型
     */
    void onPlayerInteraction(Location chestLocation, UUID playerUUID, InteractionType interactionType);
    
    /**
     * 变更类型枚举
     */
    enum ChangeType {
        /** 物品被添加 */
        ITEM_ADDED,
        /** 物品被移除 */
        ITEM_REMOVED,
        /** 物品被修改（NBT等） */
        ITEM_MODIFIED,
        /** 所有物品被替换（批量操作） */
        ITEMS_REPLACED,
        /** 所有物品被清除 */
        ITEMS_CLEARED,
        /** 概率被修改 */
        PROBABILITY_CHANGED
    }
    
    /**
     * 交互类型枚举
     */
    enum InteractionType {
        /** 玩家打开了箱子 */
        CHEST_OPENED,
        /** 玩家从箱子拿取物品 */
        ITEM_TAKEN,
        /** 玩家向箱子放入物品 */
        ITEM_PLACED,
        /** 箱子被刷新 */
        CHEST_REFRESHED
    }
}
