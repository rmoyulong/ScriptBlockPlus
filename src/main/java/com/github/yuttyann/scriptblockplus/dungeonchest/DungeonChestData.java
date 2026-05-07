package com.github.yuttyann.scriptblockplus.dungeonchest;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/**
 * ★★★ 增强版地牢箱子数据模型 ★★★
 * 
 * 核心改进：
 * 1. 观察者模式支持 - 数据变化时自动通知所有监听器
 * 2. 双层物品管理 - 配置物品 vs 实际显示物品
 * 3. 数据快照 - GUI打开时保存，关闭时验证一致性
 * 4. 数据完整性校验 - 防止数据损坏
 */
public class DungeonChestData {

    private final UUID chestId;
    private final Location location;
    private final String configName;
    
    // 时间管理字段
    private long lastRefreshTime;           // 上次刷新时间
    private long refreshInterval;            // 刷新间隔（毫秒）
    private long playerCooldown;              // 玩家冷却时间（毫秒）
    private boolean isOnCooldown;             // 是否在冷却中
    
    // ★★★ 双层物品管理 ★★★
    
    /**
     * 配置的物品列表（持久化存储）
     * - 这是"应该掉落的物品池"
     * - 包含概率信息
     * - 保存到JSON文件
     * - 物品编辑GUI和概率GUI都操作这个列表
     */
    private List<String> configuredItems;     // 序列化的配置物品数据
    
    /**
     * 当前显示在箱子中的物品（运行时数据，不持久化）
     * - 这是"玩家实际看到的物品"
     * - 是从 configuredItems 经过概率计算后生成的子集
     * - 玩家拿走后不会影响 configuredItems
     * - 每次刷新时重新生成
     */
    private transient List<String> currentDisplayItems;  // 当前显示的物品（内存中）
    
    // 玩家交互记录
    private Map<UUID, Long> playerLastOpen;  // 每个玩家最后打开时间
    
    // ★★★ 玩家触发刷新机制 ★★★
    private boolean triggered;               // 是否已被玩家触发（打开过）
    private long triggerTime;                 // 触发时间（玩家首次打开时间）
    
    // ★★★ 观察者模式 ★★★
    private final List<ChestDataObserver> observers = new CopyOnWriteArrayList<>();
    
    // ★★★ 数据快照（用于GUI操作）★★★
    private List<String> snapshotConfiguredItems;  // 打开GUI时的快照
    
    // 一致性标志
    private volatile boolean dataModified = false;  // 数据是否被修改但未保存

    public DungeonChestData(Location location, String configName) {
        this.chestId = UUID.randomUUID();
        this.location = location;
        this.configName = configName;
        this.lastRefreshTime = System.currentTimeMillis();
        this.refreshInterval = 300000L; // Default 5 minutes
        this.playerCooldown = 60000L;      // Default 60 seconds
        this.isOnCooldown = false;
        this.triggered = false;             // ★ 初始未触发
        this.triggerTime = 0;               // ★ 未触发时为0
        this.configuredItems = new ArrayList<>();
        this.currentDisplayItems = new ArrayList<>();  // 运行时初始化为空
        this.playerLastOpen = new HashMap<>();
    }

    // ==================== 基础Getter/Setter ====================

    public UUID getChestId() { return chestId; }

    public Location getLocation() { return location; }

    public String getConfigName() { return configName; }

    public long getLastRefreshTime() { return lastRefreshTime; }

    public void setLastRefreshTime(long lastRefreshTime) {
        this.lastRefreshTime = lastRefreshTime;
    }

    public long getRefreshInterval() { return refreshInterval; }

    public void setRefreshInterval(long refreshInterval) {
        this.refreshInterval = refreshInterval;
    }

    public long getPlayerCooldown() { return playerCooldown; }

    public void setPlayerCooldown(long playerCooldown) {
        this.playerCooldown = playerCooldown;
    }

    public boolean isOnCooldown() { return isOnCooldown; }

    public void setOnCooldown(boolean onCooldown) {
        this.isOnCooldown = onCooldown;
    }
    
    public Map<UUID, Long> getPlayerLastOpen() { return playerLastOpen; }

    // ==================== ★★★ 玩家触发刷新机制 ★★★ ====================

    /**
     * 是否已被玩家触发（打开过）
     * 只有触发后的箱子才会显示全息和执行刷新倒计时
     */
    public boolean isTriggered() { return triggered; }

    /**
     * 获取触发时间
     */
    public long getTriggerTime() { return triggerTime; }

    /**
     * ★★★ 玩家触发箱子 - 开始刷新倒计时 ★★★
     * 
     * 当且仅当箱子被玩家打开时调用此方法。
     * 触发后：
     * - 开始刷新倒计时
     * - 显示全息倒计时
     * - 倒计时结束后自动刷新物品
     */
    public void triggerByPlayer(UUID playerId) {
        if (!triggered) {
            this.triggered = true;
            this.triggerTime = System.currentTimeMillis();
            this.lastRefreshTime = System.currentTimeMillis();
            
            // 通知观察者
            notifyPlayerInteraction(playerId, ChestDataObserver.InteractionType.CHEST_OPENED);
        }
        
        // 记录玩家打开时间
        playerLastOpen.put(playerId, System.currentTimeMillis());
    }

    /**
     * ★★★ 重置触发状态 - 刷新完成后调用 ★★★
     * 
     * 刷新完成后：
     * - 隐藏全息
     * - 停止倒计时
     * - 等待下次玩家打开
     */
    public void resetTrigger() {
        this.triggered = false;
        this.triggerTime = 0;
        this.isOnCooldown = false;
        this.playerLastOpen.clear();
    }

    /**
     * 检查是否需要刷新（基于触发机制）
     * 
     * 只有被触发的箱子才会检查刷新条件
     */
    public boolean needsRefreshing() {
        if (!triggered) return false;
        
        long timeSinceRefresh = System.currentTimeMillis() - lastRefreshTime;
        return timeSinceRefresh >= refreshInterval;
    }
    
    /**
     * 获取距离刷新的剩余时间（毫秒）
     * 如果未被触发，返回 -1
     */
    public long getRemainingRefreshTimeMs() {
        if (!triggered) return -1;
        
        long elapsed = System.currentTimeMillis() - lastRefreshTime;
        long remaining = refreshInterval - elapsed;
        return Math.max(0, remaining);
    }

    // ==================== ★★★ 配置物品管理（核心）★★★ ====================

    /**
     * 获取配置的物品列表（这是权威数据源）
     * 
     * ⚠️ 重要：所有GUI都应该使用这个方法获取物品列表！
     */
    public List<String> getConfiguredItems() {
        return Collections.unmodifiableList(configuredItems);
    }
    
    /**
     * @deprecated 使用 getConfiguredItems() 替代
     */
    @Deprecated
    public List<String> getSerializedItems() {
        return getConfiguredItems();
    }

    /**
     * 设置配置的物品列表（带通知）
     * 
     * @param items 新的物品列表
     * @param changeType 变更类型
     * @param source 来源（用于日志）
     */
    public void setConfiguredItems(List<String> items, ChestDataObserver.ChangeType changeType, String source) {
        List<String> oldItems = new ArrayList<>(this.configuredItems);
        
        // 更新数据
        this.configuredItems = items != null ? new ArrayList<>(items) : new ArrayList<>();
        this.dataModified = true;
        
        // 通知观察者
        notifyConfigItemsChanged(changeType, source);
        
        // 日志记录
        if (Bukkit.getLogger().isLoggable(Level.FINE)) {
            Bukkit.getLogger().fine(String.format(
                "[DungeonChestData] Items changed at %s: %d → %d items (%s) [%s]",
                location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ(),
                oldItems.size(),
                this.configuredItems.size(),
                changeType.name(),
                source
            ));
        }
    }
    
    /**
     * 简单设置（向后兼容）
     */
    public void setSerializedItems(List<String> items) {
        setConfiguredItems(items, ChestDataObserver.ChangeType.ITEMS_REPLACED, "legacy_api");
    }

    // ==================== ★★★ 当前显示物品管理 ★★★ ====================

    /**
     * 获取当前显示在箱子中的物品（运行时数据）
     * 
     * 这是经过概率计算后实际放入箱子的物品。
     * 玩家从这里拿走物品不影响 configuredItems。
     */
    public List<String> getCurrentDisplayItems() {
        return Collections.unmodifiableList(currentDisplayItems);
    }
    
    /**
     * 设置当前显示的物品（刷新时调用）
     * 
     * @param items 本次生成的物品
     */
    public void setCurrentDisplayItems(List<String> items) {
        this.currentDisplayItems = items != null ? new ArrayList<>(items) : new ArrayList<>();
        
        // 通知观察者：箱子已刷新
        notifyChestRefreshed(items);
    }
    
    /**
     * 清空当前显示的物品（不清理配置物品）
     */
    public void clearCurrentDisplayItems() {
        this.currentDisplayItems.clear();
    }

    // ==================== 观者者模式实现 ====================

    /**
     * 注册观察者
     */
    public void addObserver(ChestDataObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    /**
     * 移除观察者
     */
    public void removeObserver(ChestDataObserver observer) {
        observers.remove(observer);
    }

    /**
     * 清除所有观察者
     */
    public void clearObservers() {
        observers.clear();
    }

    /**
     * 通知所有观察者：配置物品发生变化
     */
    private void notifyConfigItemsChanged(ChestDataObserver.ChangeType changeType, String source) {
        for (ChestDataObserver observer : observers) {
            try {
                observer.onConfigItemsChanged(location, configuredItems, changeType);
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING, 
                    "[DungeonChestData] Error notifying observer: " + observer.getClass().getSimpleName(), e);
            }
        }
    }
    
    /**
     * 通知所有观察者：概率发生变化
     */
    public void notifyProbabilityChanged(Map<Integer, Double> probabilityMap) {
        for (ChestDataObserver observer : observers) {
            try {
                observer.onProbabilityChanged(location, probabilityMap);
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING,
                    "[DungeonChestData] Error notifying probability change", e);
            }
        }
    }
    
    /**
     * 通知所有观察者：箱子已刷新
     */
    private void notifyChestRefreshed(List<String> generatedItems) {
        for (ChestDataObserver observer : observers) {
            try {
                observer.onChestRefreshed(location, lastRefreshTime, generatedItems);
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING,
                    "[DungeonChestData] Error notifying chest refresh", e);
            }
        }
    }
    
    /**
     * 通知所有观察者：玩家交互
     */
    public void notifyPlayerInteraction(UUID playerId, ChestDataObserver.InteractionType type) {
        for (ChestDataObserver observer : observers) {
            try {
                observer.onPlayerInteraction(location, playerId, type);
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING,
                    "[DungeonChestData] Error notifying player interaction", e);
            }
        }
    }

    // ==================== ★★★ 数据快照机制 ★★★ ====================

    /**
     * 创建数据快照（GUI打开前调用）
     * 
     * 用于检测用户是否真的修改了数据，
     * 避免不必要的保存操作。
     */
    public void createSnapshot() {
        snapshotConfiguredItems = new ArrayList<>(configuredItems);
        dataModified = false;
        
        Bukkit.getLogger().fine("[DungeonChestData] Snapshot created at " + location + 
            " with " + snapshotConfiguredItems.size() + " items");
    }
    
    /**
     * 检查数据是否与快照不同
     * 
     * @return true 如果数据已被修改
     */
    public boolean isDataDifferentFromSnapshot() {
        if (snapshotConfiguredItems == null) return dataModified;
        
        if (snapshotConfiguredItems.size() != configuredItems.size()) {
            return true;
        }
        
        // 深度比较
        for (int i = 0; i < configuredItems.size(); i++) {
            if (!Objects.equals(snapshotConfiguredItems.get(i), configuredItems.get(i))) {
                return true;
            }
        }
        
        return dataModified;
    }
    
    /**
     * 清除快照
     */
    public void clearSnapshot() {
        snapshotConfiguredItems = null;
    }
    
    /**
     * 检查是否有未保存的修改
     */
    public boolean hasUnsavedChanges() {
        return dataModified || isDataDifferentFromSnapshot();
    }

    // ==================== 时间管理与刷新逻辑 ====================

    /**
     * Get remaining cooldown time in seconds for a specific player
     */
    public long getPlayerRemainingCooldown(UUID playerId) {
        Long lastOpen = playerLastOpen.get(playerId);
        if (lastOpen == null) return 0;
        
        long elapsed = System.currentTimeMillis() - lastOpen;
        long remaining = playerCooldown - elapsed;
        return Math.max(0, remaining / 1000); // Convert to seconds
    }

    /**
     * Check if a specific player can open this chest
     */
    public boolean canPlayerOpen(UUID playerId) {
        // If chest is empty or needs refresh, don't allow opening
        if (needsRefreshing()) {
            return false;
        }
        
        // Check individual player cooldown
        long remainingCooldown = getPlayerRemainingCooldown(playerId);
        return remainingCooldown <= 0;
    }

    /**
     * Mark that a player has opened the chest
     */
    public void recordPlayerOpen(UUID playerId) {
        playerLastOpen.put(playerId, System.currentTimeMillis());
        
        // 通知观察者
        notifyPlayerInteraction(playerId, ChestDataObserver.InteractionType.CHEST_OPENED);
    }
    
    /**
     * 记录玩家拿取物品
     */
    public void recordItemTaken(UUID playerId) {
        notifyPlayerInteraction(playerId, ChestDataObserver.InteractionType.ITEM_TAKEN);
    }

    /**
     * ★★★ 正确的刷新标记逻辑 ★★★
     * 
     * 刷新完成后重置触发状态：
     * - 隐藏全息
     * - 停止倒计时
     * - 等待下次玩家打开
     */
    public void markAsRefreshed(List<String> generatedItems) {
        this.lastRefreshTime = System.currentTimeMillis();
        
        // 更新当前显示物品
        if (generatedItems != null) {
            setCurrentDisplayItems(generatedItems);
        }
        
        // ★★★ 重置触发状态（刷新完成后回到空闲）★★★
        resetTrigger();
        
        // 创建新快照
        createSnapshot();
        
        Bukkit.getLogger().info(String.format(
            "[DungeonChestData] ✓ Chest refreshed and reset to idle at %s (%d items)",
            location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ(),
            generatedItems != null ? generatedItems.size() : 0
        ));
    }
    
    /**
     * 向后兼容的刷新标记
     */
    public void markAsRefreshed() {
        markAsRefreshed(null);
    }

    /**
     * Get remaining time until next refresh in seconds
     */
    // ==================== 实体访问方法（线程安全）====================

    public Chest getChestBlock() {
        Block block = location.getBlock();
        if (block.getState() instanceof Chest) {
            return (Chest) block.getState();
        }
        return null;
    }

    public Inventory getInventory() {
        Chest chest = getChestBlock();
        return chest != null ? chest.getInventory() : null;
    }

    public void clearInventory() {
        Inventory inventory = getInventory();
        if (inventory != null) {
            inventory.clear();
        }
    }

    // ==================== 序列化与反序列化 ====================

    /**
     * Convert to map for JSON serialization
     * 
     * ⚠️ 只序列化 configuredItems（配置物品），不序列化 currentDisplayItems（运行时数据）
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("chestId", chestId.toString());
        map.put("world", location.getWorld().getName());
        map.put("x", location.getX());
        map.put("y", location.getY());
        map.put("z", location.getZ());
        map.put("configName", configName);
        map.put("lastRefreshTime", lastRefreshTime);
        map.put("refreshInterval", refreshInterval);
        map.put("playerCooldown", playerCooldown);
        map.put("isOnCooldown", isOnCooldown);
        map.put("serializedItems", configuredItems);  // ★ 只保存配置物品
        map.put("triggered", triggered);              // ★ 保存触发状态
        map.put("triggerTime", triggerTime);          // ★ 保存触发时间
        
        // Serialize player last open times
        Map<String, Long> playerOpenMap = new HashMap<>();
        playerLastOpen.forEach((uuid, time) -> playerOpenMap.put(uuid.toString(), time));
        map.put("playerLastOpen", playerOpenMap);
        
        return map;
    }

    /**
     * Create from map for JSON deserialization
     */
    public static DungeonChestData fromMap(Map<String, Object> map) {
        try {
            // ★★★ 深度防御：确保 map 不为 null ★★★
            if (map == null) {
                Bukkit.getLogger().severe("[DungeonChestData] Cannot deserialize null map");
                return null;
            }

            String worldName = (String) map.get("world");
            if (worldName == null) {
                Bukkit.getLogger().severe("[DungeonChestData] Missing world name in serialized data");
                return null;
            }

            // ★★★ 安全获取坐标（处理 Double vs Long 类型转换问题）★★★
            double x = getNumberValue(map.get("x"), 0.0).doubleValue();
            double y = getNumberValue(map.get("y"), 0.0).doubleValue();
            double z = getNumberValue(map.get("z"), 0.0).doubleValue();
            
            Location loc = new Location(Bukkit.getWorld(worldName), x, y, z);
            if (loc.getWorld() == null) {
                Bukkit.getLogger().severe("[DungeonChestData] World '" + worldName + "' not loaded");
                return null;
            }

            String configName = (String) map.get("configName");
            if (configName == null) {
                configName = "default";
            }
            
            DungeonChestData data = new DungeonChestData(loc, configName);
            
            // ★★★ 安全获取数值字段（处理 Gson 可能将整数解析为 Double 的问题）★★★
            data.lastRefreshTime = getNumberValue(map.get("lastRefreshTime"), 0L).longValue();
            data.refreshInterval = getNumberValue(map.get("refreshInterval"), 300000L).longValue();
            data.playerCooldown = getNumberValue(map.get("playerCooldown"), 60000L).longValue();
            data.isOnCooldown = getBooleanValue(map.get("isOnCooldown"), false);
            data.triggered = getBooleanValue(map.get("triggered"), false);
            data.triggerTime = getNumberValue(map.get("triggerTime"), 0L).longValue();
            
            // 获取配置的物品列表
            @SuppressWarnings("unchecked")
            List<String> items = (List<String>) map.get("serializedItems");
            data.setConfiguredItems(items, ChestDataObserver.ChangeType.ITEMS_REPLACED, "deserialization");
            
            // ★★★ 安全处理玩家打开记录（关键修复：处理 Double vs Long 类型转换）★★★
            @SuppressWarnings("unchecked")
            Map<String, Object> rawPlayerOpenMap = (Map<String, Object>) map.get("playerLastOpen");
            if (rawPlayerOpenMap != null) {
                for (Map.Entry<String, Object> entry : rawPlayerOpenMap.entrySet()) {
                    try {
                        String uuidStr = entry.getKey();
                        Object timeObj = entry.getValue();
                        
                        // ★★★ 关键修复：安全转换数值类型 ★★★
                        // Gson/JSON 解析可能产生 Double 而不是 Long
                        long timeValue = getNumberValue(timeObj, 0L).longValue();
                        
                        if (isValidUUID(uuidStr)) {
                            data.playerLastOpen.put(UUID.fromString(uuidStr), timeValue);
                        }
                    } catch (Exception e) {
                        Bukkit.getLogger().warning("[DungeonChestData] Failed to parse player open record: " + e.getMessage());
                    }
                }
            }
            
            // 创建初始快照
            data.createSnapshot();
            
            // ★★★ 验证数据完整性 ★★★
            if (!data.validateIntegrity()) {
                Bukkit.getLogger().warning("[DungeonChestData] Data integrity check failed for chest at " + loc);
            }
            
            return data;
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[DungeonChestData] Failed to deserialize chest data", e);
            return null;
        }
    }
    
    /**
     * ★★★ 安全获取数值（处理 Double vs Long 类型转换）★★★
     */
    private static Number getNumberValue(Object value, Number defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return (Number) value;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * ★★★ 安全获取布尔值 ★★★
     */
    private static boolean getBooleanValue(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
    
    /**
     * ★★★ 验证字符串是否为有效的 UUID 格式 ★★★
     */
    private static boolean isValidUUID(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            UUID.fromString(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // ==================== 数据验证 ====================

    /**
     * 验证数据完整性
     * 
     * @return true 如果数据有效
     */
    public boolean validateIntegrity() {
        // 检查位置有效性
        if (location == null || location.getWorld() == null) {
            Bukkit.getLogger().severe("[DungeonChestData] Invalid location in " + chestId);
            return false;
        }
        
        // 检查配置物品格式
        if (configuredItems != null) {
            for (int i = 0; i < configuredItems.size(); i++) {
                String item = configuredItems.get(i);
                if (item == null || item.trim().isEmpty()) {
                    Bukkit.getLogger().warning("[DungeonChestData] Empty item entry at index " + i + 
                        " in chest at " + location);
                    // 不返回false，只是警告
                }
            }
        }
        
        // 检查时间合理性
        if (refreshInterval <= 0) {
            Bukkit.getLogger().warning("[DungeonChestData] Invalid refresh interval: " + refreshInterval +
                " in chest at " + location);
        }
        
        return true;
    }

    @Override
    public String toString() {
        return "DungeonChestData{" +
                "chestId=" + chestId +
                ", world='" + (location.getWorld() != null ? location.getWorld().getName() : "null") + '\'' +
                ", x=" + String.format("%.1f", location.getX()) +
                ", y=" + String.format("%.1f", location.getY()) +
                ", z=" + String.format("%.1f", location.getZ()) +
                ", config='" + configName + '\'' +
                ", configured=" + configuredItems.size() + " items" +
                ", display=" + currentDisplayItems.size() + " items" +
                ", modified=" + dataModified +
                '}';
    }
}
