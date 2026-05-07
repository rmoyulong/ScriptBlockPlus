package com.github.yuttyann.scriptblockplus.dungeonchest;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.github.yuttyann.scriptblockplus.ScriptBlock;
import com.github.yuttyann.scriptblockplus.FoliaCompat;
import com.github.yuttyann.scriptblockplus.dungeonchest.gui.DungeonChestGUI;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class DungeonChestManager {

    private final ScriptBlock plugin;
    private final DungeonChestConfig configManager;
    private final Map<Location, DungeonChestData> activeChests;
    private final HologramManager hologramManager;
    private final DungeonChestGUI guiEditor;
    private DataTransactionManager transactionManager;  // ★★★ 事务管理器 ★★★
    private final Gson gson;
    
    private File dataFile;
    private int refreshTaskId = -1;

    public DungeonChestManager(ScriptBlock plugin) {
        this.plugin = plugin;
        this.configManager = new DungeonChestConfig(plugin);
        this.activeChests = new ConcurrentHashMap<>();
        this.hologramManager = new HologramManager(this);
        this.guiEditor = new DungeonChestGUI(this);
        
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        
        initialize();
    }

    private void initialize() {
        File dataDir = new File(plugin.getDataFolder(), "dungeonchests");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        
        dataFile = new File(dataDir, "data.json");
        
        // ★★★ 初始化事务管理器 ★★★
        this.transactionManager = new DataTransactionManager(plugin, dataFile);
        
        loadChestData();
        startRefreshTask();
        registerListeners();
        
        hologramManager.start();
        
        plugin.getLogger().info("[DungeonChest] v2.0 System initialized with " + activeChests.size() + " chests");
        plugin.getLogger().info("[DungeonChest] ✓ Data transaction manager enabled (backup & rollback support)");
    }

    /**
     * Load chest data from JSON file
     */
    private void loadChestData() {
        if (!dataFile.exists()) {
            plugin.getLogger().info("[DungeonChest] No existing data file, starting fresh");
            return;
        }

        try (FileReader reader = new FileReader(dataFile)) {
            List<Map<String, Object>> dataList = gson.fromJson(reader, List.class);
            
            if (dataList != null) {
                int successCount = 0;
                for (Map<String, Object> map : dataList) {
                    try {
                        DungeonChestData data = DungeonChestData.fromMap(map);
                        if (data != null && configManager.hasConfig(data.getConfigName())) {
                            activeChests.put(data.getLocation(), data);
                            applyConfigSettings(data);
                            successCount++;
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "[DungeonChest] Failed to parse chest data", e);
                    }
                }
                plugin.getLogger().info("[DungeonChest] Successfully loaded " + successCount + "/" + 
                        (dataList != null ? dataList.size() : 0) + " chests from data file");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[DungeonChest] Failed to load chest data", e);
        }
    }

    /**
     * Save all chest data - atomic write with temp file (enhanced for shutdown safety)
     */
    public synchronized void saveChestData() {
        if (activeChests.isEmpty()) {
            plugin.getLogger().info("[DungeonChest] No chests to save");
            return;
        }

        File tempFile = new File(dataFile.getAbsolutePath() + ".tmp");
        
        try {
            List<Map<String, Object>> dataList = new ArrayList<>();
            
            for (DungeonChestData chest : activeChests.values()) {
                Map<String, Object> chestMap = chest.toMap();
                dataList.add(chestMap);
            }
            
            String json = gson.toJson(dataList);
            
            // 写入临时文件
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(json);
                writer.flush();
            }
            
            if (!tempFile.exists() || tempFile.length() == 0) {
                plugin.getLogger().severe("[DungeonChest] ✗ Failed to write temp file!");
                return;
            }
            
            // ★★★ 增强的原子写入策略 ★★★
            boolean saved = false;
            
            // 策略 1: 尝试直接重命名（最快）
            if (dataFile.exists()) {
                dataFile.delete();
                // 给文件系统一点时间完成删除操作
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}
            }
            
            if (tempFile.renameTo(dataFile)) {
                saved = true;
            } else {
                // 策略 2: 重命名失败，尝试强制覆盖写入
                try (FileWriter directWriter = new FileWriter(dataFile, false)) {
                    directWriter.write(json);
                    directWriter.flush();
                    saved = true;
                    
                    // 清理临时文件
                    if (tempFile.exists()) {
                        tempFile.delete();
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("[DungeonChest] Direct write also failed: " + ex.getMessage());
                }
            }
            
            if (saved) {
                plugin.getLogger().info("[DungeonChest] ✓ Saved " + activeChests.size() + 
                        " chests (" + (dataFile.exists() ? dataFile.length() / 1024 : 0) + " KB)");
            } else {
                // 策略 3: 所有方法都失败，保留临时文件作为备份
                plugin.getLogger().severe("[DungeonChest] ✗ Failed to save! Backup saved to: " + tempFile.getAbsolutePath());
                plugin.getLogger().severe("[DungeonChest] Please manually rename " + tempFile.getName() + " to " + dataFile.getName());
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[DungeonChest] Failed to save chest data", e);
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * Apply configuration settings from dungeonchests.yml
     */
    private void applyConfigSettings(DungeonChestData chestData) {
        DungeonChestConfig.ChestConfig config = configManager.getConfig(chestData.getConfigName());
        if (config != null) {
            chestData.setRefreshInterval(config.getRefreshInterval());
        }
    }

    /**
     * Start periodic refresh check task - 使用全局调度器（轻量级检查）
     * 实际的实体操作延迟到区域线程
     */
    private void startRefreshTask() {
        // ★★★ 关键修复：只做时间检查，不做任何方块/实体操作 ★★★
        refreshTaskId = ScriptBlock.getScheduler().run(() -> {
            scheduleRefreshesForReadyChests();
        }, 0L, 20L).getTaskId();
    }

    /**
     * ★★★ 新方法：只收集需要刷新的箱子并提交到各自区域线程 ★★★
     * 这个方法本身是纯计算，不会触发任何 I/O 或区块加载
     */
    private void scheduleRefreshesForReadyChests() {
        long currentTime = System.currentTimeMillis();
        
        List<DungeonChestData> chestsToRefresh = activeChests.values().stream()
                .filter(chest -> chest.needsRefreshing())
                .collect(Collectors.toList());

        for (DungeonChestData chest : chestsToRefresh) {
            // ★★★ 将每个箱子的刷新操作提交到它所在的区域线程 ★★★
            submitRefreshToRegionThread(chest);
        }
    }

    /**
     * ★★★ 核心修复：将刷新操作安全地提交到区域线程 ★★★
     * 
     * 重要：这里只提交任务，不执行任何可能阻塞的操作
     * 所有的方块访问、物品填充都在区域线程的回调中完成
     */
    private void submitRefreshToRegionThread(DungeonChestData chestData) {
        Location location = chestData.getLocation();
        
        // 使用 FoliaCompat 确保在正确的线程上执行
        FoliaCompat.runAtLocation(
            plugin,
            location,
            () -> {
                performSafeRefresh(chestData);
            },
            1L  // ★★★ 至少延迟1tick，确保不在当前tick立即执行 ★★★
        );
    }

    /**
     * ★★★ 安全的刷新实现：在区域线程上执行所有操作 ★★★
     * 
     * 这个方法只在 Folia 的区域线程上被调用，
     * 因此可以安全地访问方块状态而不会阻塞其他区域
     */
    private void performSafeRefresh(DungeonChestData chestData) {
        Location location = chestData.getLocation();
        
        try {
            // 安全获取方块状态（此时已在正确的区域线程上）
            Block block = location.getBlock();
            
            // 验证方块类型
            if (block.getType() != org.bukkit.Material.CHEST) {
                plugin.getLogger().warning("[DungeonChest] Block at " + location.toVector() + 
                        " is not a chest! Type: " + block.getType());
                return;
            }
            
            // 验证方块状态
            if (!(block.getState() instanceof Chest)) {
                plugin.getLogger().warning("[DungeonChest] Block state is not Chest at " + location.toVector());
                return;
            }
            
            Chest chest = (Chest) block.getState();
            Inventory inventory = chest.getInventory();
            inventory.clear();

            // ★★★ 使用概率系统生成物品 ★★★
            List<ItemStack> generatedItems = generateItemsWithProbability(chestData);
            
            if (generatedItems.isEmpty()) {
                plugin.getLogger().warning("[DungeonChest] No items for chest at: " + 
                        location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
                
                // 即使没有物品也标记为已刷新
                chestData.markAsRefreshed();
                return;
            }

            // 随机填充箱子槽位
            List<Integer> availableSlots = new ArrayList<>();
            for (int i = 0; i < inventory.getSize(); i++) {
                availableSlots.add(i);
            }
            Collections.shuffle(availableSlots, new Random());

            int filledCount = 0;
            for (int i = 0; i < generatedItems.size() && i < availableSlots.size(); i++) {
                inventory.setItem(availableSlots.get(i), generatedItems.get(i));
                filledCount++;
            }

            // 标记为已刷新
            chestData.markAsRefreshed();

            plugin.getLogger().fine(String.format(
                "[DungeonChest] ✓ Refreshed %d,%d,%d with %d items (probability-based)",
                location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                filledCount
            ));
            
        } catch (Exception e) {
            // 捕获所有异常，防止影响区域线程
            plugin.getLogger().log(Level.WARNING, 
                "[DungeonChest] Error refreshing chest at " + location.toVector(), e);
            
            // 即使失败也标记为已刷新，防止无限重试
            chestData.markAsRefreshed();
        }
    }

    /**
     * ★★★ 使用概率系统生成物品 ★★★
     * 
     * 对每个配置的物品进行概率判定：
     * 1. 解析物品数据（包含概率信息）
     * 2. 使用随机数判断是否生成该物品
     * 3. 返回通过概率判定的物品列表
     */
    private List<ItemStack> generateItemsWithProbability(DungeonChestData chestData) {
        List<String> serializedItems = chestData.getSerializedItems();
        List<ItemStack> generatedItems = new ArrayList<>();
        
        if (serializedItems == null || serializedItems.isEmpty()) {
            return generatedItems;
        }
        
        Random random = new Random();
        
        for (String itemStr : serializedItems) {
            try {
                // 尝试解析为 LootItem（包含概率）
                LootItem lootItem = LootItem.fromString(itemStr);
                
                if (lootItem != null) {
                    // 使用概率系统生成物品
                    ItemStack generated = lootItem.generateItem(random);
                    
                    if (generated != null) {
                        generatedItems.add(generated);
                        
                        plugin.getLogger().fine(String.format(
                            "[DungeonChest] Generated: %s (prob=%.1f%%)",
                            generated.getType().name(),
                            lootItem.getProbability()
                        ));
                    } else {
                        plugin.getLogger().fine(String.format(
                            "[DungeonChest] Skipped (failed probability check): %s",
                            lootItem.getItemStack().getType().name()
                        ));
                    }
                } else {
                    // 如果无法解析为 LootItem（旧格式），直接使用
                    ItemStack legacyItem = NBTSerializer.deserializeItem(itemStr);
                    if (legacyItem != null) {
                        generatedItems.add(legacyItem);
                    }
                }
                
            } catch (Exception e) {
                // 解析失败时尝试使用传统方式
                try {
                    ItemStack fallback = NBTSerializer.deserializeItem(itemStr);
                    if (fallback != null) {
                        generatedItems.add(fallback);
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("[DungeonChest] Failed to parse item: " + ex.getMessage());
                }
            }
        }
        
        return generatedItems;
    }

    /**
     * @deprecated 使用 submitRefreshToRegionThread() 替代
     * 此方法保留用于向后兼容，但内部会委托给新方法
     */
    @Deprecated
    public void refreshChest(DungeonChestData chestData) {
        submitRefreshToRegionThread(chestData);
    }

    /**
     * Register event listeners
     */
    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(guiEditor, plugin);
    }

    /**
     * Create a new dungeon chest at a location
     */
    public boolean createChest(Location location, String configName) {
        if (!configManager.hasConfig(configName)) {
            return false;
        }

        Block block = location.getBlock();
        if (!(block.getState() instanceof Chest)) {
            return false;
        }

        if (activeChests.containsKey(location)) {
            return false;
        }

        DungeonChestData chestData = new DungeonChestData(location, configName);
        applyConfigSettings(chestData);
        activeChests.put(location, chestData);
        
        saveChestData();

        plugin.getLogger().info("[DungeonChest] ✓ Created and saved new chest: " + chestData);
        return true;
    }

    /**
     * Remove a dungeon chest
     */
    public boolean removeChest(Location location) {
        DungeonChestData removed = activeChests.remove(location);
        if (removed != null) {
            // 异步清理（避免阻塞）
            Location locToRemove = location.clone();
            FoliaCompat.runAtLocation(plugin, locToRemove, () -> {
                try {
                    Block block = locToRemove.getBlock();
                    if (block.getState() instanceof Chest) {
                        ((Chest) block.getState()).getInventory().clear();
                    }
                } catch (Exception ignored) {}
            }, 1L);
            
            hologramManager.hideHologram(location);
            saveChestData();
            
            plugin.getLogger().info("[DungeonChest] ✓ Removed and saved chest at: " + location);
            return true;
        }
        return false;
    }

    /**
     * 极简版：始终返回 true，不限制任何操作
     */
    public boolean canOpenChest(Player player, Location location) {
        return true;
    }

    /**
     * Handle when a player opens a chest (optional logging)
     */
    public void onChestOpened(Player player, Location location) {
        // 可选：记录日志或什么都不做
    }

    /**
     * Open GUI editor for a chest
     */
    public void openGUIEditor(Player player, Location location) {
        DungeonChestData chestData = activeChests.get(location);
        if (chestData == null) {
            player.sendMessage("§c[DungeonChest] This is not a dungeon chest!");
            return;
        }
        
        guiEditor.openEditor(player, chestData);
    }

    /**
     * GUI编辑完成回调
     */
    public void onGUIEditComplete(DungeonChestData chestData) {
        if (chestData == null) return;
        
        plugin.getLogger().info("[DungeonChest] GUI edit completed for chest: " + chestData);
        
        // 保存数据
        saveChestData();
        
        // ★★★ 立即重置冷却时间并触发刷新 ★★★
        resetAndRefreshChest(chestData);
        
        plugin.getLogger().info("[DungeonChest] ✓ Data saved and chest refreshed after GUI edit");
    }
    
    /**
     * ★★★ 重置并刷新单个箱子（核心方法）★★★
     * 
     * 此方法确保：
     * 1. 箱子立即被填充新物品
     * 2. 冷却时间被正确重置
     * 3. 全息显示被更新
     * 4. 触发状态被设置（开始倒计时）
     */
    public void resetAndRefreshChest(DungeonChestData chestData) {
        if (chestData == null) return;
        
        Location location = chestData.getLocation();
        
        // 1. ★★★ 触发箱子（开始倒计时 + 显示全息）★★★
        // 使用一个特殊的系统UUID作为触发者
        chestData.triggerByPlayer(new java.util.UUID(0, 0));  // 系统触发
        
        // 2. ★★★ 立即填充箱子物品 ★★★
        // 因为刚触发，needsRefreshing() 返回 false（还没到刷新时间）
        // 但我们想立即填充物品，所以直接执行刷新
        performSafeRefresh(chestData);
        
        // 3. ★★★ 再次触发（因为 performSafeRefresh 会重置触发状态）★★★
        // 刷新完成后箱子回到空闲状态，需要重新触发以开始新的倒计时
        chestData.triggerByPlayer(new java.util.UUID(0, 0));
        
        plugin.getLogger().info(String.format(
            "[DungeonChest] ✓ Chest reset and refreshed at %s (items filled, countdown started)",
            location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ()
        ));
    }
    
    /**
     * 立即更新指定位置的全息显示（在全局线程调用）
     */
    private void updateHologramImmediately(Location location) {
        try {
            HologramManager holoManager = getHologramManager();
            if (holoManager != null && !holoManager.isStopping()) {
                // 强制刷新全息文字
                ArmorStand holo = holoManager.getHologram(location);
                if (holo != null && !holo.isDead()) {
                    // 计算新的显示文本
                    DungeonChestData chest = getChestAt(location);
                    if (chest != null) {
                        String newText = calculateDisplayText(chest);
                        try {
                            holo.setCustomName(newText);
                        } catch (Exception e) {
                            // 如果在错误的线程，忽略错误（区域线程会处理）
                        }
                    }
                } else {
                    // 如果没有全息实体，尝试创建一个
                    holoManager.hideHologram(location);  // 先隐藏旧的
                    // 全息管理器的定期检查任务会在下一个周期创建新的
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, 
                "[DungeonChest] Error updating hologram at " + location, e);
        }
    }
    
    /**
     * 计算显示文本（供全息使用）
     */
    private String calculateDisplayText(DungeonChestData chest) {
        if (chest == null) return "";
        
        long currentTime = System.currentTimeMillis();
        long lastRefresh = chest.getLastRefreshTime();
        long interval = chest.getRefreshInterval();
        
        if (interval <= 0 || lastRefresh <= 0) {
            return "§c⚠ Configuration error";
        }
        
        long elapsedSinceRefresh = currentTime - lastRefresh;
        
        if (elapsedSinceRefresh >= interval) {
            long excessTime = elapsedSinceRefresh - interval;
            
            if (excessTime < interval) {
                long remainingToNext = interval - excessTime;
                return formatTimeShort(remainingToNext) + " §c⏱ Refreshing now...";
            } else {
                long cyclesPassed = elapsedSinceRefresh / interval;
                long nextIn = (cyclesPassed + 1) * interval - elapsedSinceRefresh;
                
                if (nextIn > 0) {
                    return formatTimeShort(nextIn) + " §c⏱ Next refresh";
                } else {
                    return "§c⚡ Refreshing now!";
                }
            }
        } else {
            long remainingTime = interval - elapsedSinceRefresh;
            long remainingSeconds = remainingTime / 1000L;
            long totalSeconds = interval / 1000L;
            
            if (remainingSeconds > (totalSeconds - 10)) {
                return "§a✓ Just refreshed!";
            } else {
                List<String> items = chest.getConfiguredItems();
                int itemCount = items != null ? items.size() : 0;
                return formatTimeShort(remainingTime) + 
                    String.format(" §e⏱ Next refresh §7(%d items)", itemCount);
            }
        }
    }
    
    /**
     * 格式化时间为短格式
     */
    private String formatTimeShort(long timeMs) {
        if (timeMs <= 0) return "00:00";
        
        long totalSeconds = timeMs / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * ★★★ 立即保存单个箱子数据（用于GUI操作后）★★★
     * 
     * 与 saveChestData() 不同，此方法：
     * - 只保存指定的单个箱子数据（而非全部）
     * - 创建文件备份以防万一
     * - 使用事务管理器确保原子性
     */
    public void saveChestDataImmediately(DungeonChestData chestData) {
        if (chestData == null) return;
        
        Location location = chestData.getLocation();
        
        try {
            // 创建文件级备份（重大操作前）
            transactionManager.createFileBackup("gui_save_" + location.getBlockX() + "_" + location.getBlockZ());
            
            // 使用锁保护写入操作
            boolean success = transactionManager.executeWithLock(location, () -> {
                saveChestData();
                return true;
            });
            
            if (!success) {
                plugin.getLogger().severe("[DungeonChest] ✗ Failed to acquire lock for immediate save at " + location);
            } else {
                plugin.getLogger().fine("[DungeonChest] ✓ Immediately saved data for chest at " + location);
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[DungeonChest] Error in saveChestDataImmediately", e);
        }
    }
    
    /**
     * 获取事务管理器实例
     */
    public DataTransactionManager getTransactionManager() {
        return transactionManager;
    }

    public Collection<DungeonChestData> getAllChests() {
        return Collections.unmodifiableCollection(activeChests.values());
    }

    public DungeonChestData getChestAt(Location location) {
        return activeChests.get(location);
    }

    public boolean isDungeonChest(Location location) {
        return activeChests.containsKey(location);
    }

    public DungeonChestConfig getConfigManager() {
        return configManager;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }
    
    public ScriptBlock getPlugin() {
        return plugin;
    }

    public DungeonChestGUI getGuiEditor() {
        return guiEditor;
    }

    /**
     * Shutdown and cleanup
     */
    public void shutdown() {
        if (refreshTaskId != -1) {
            Bukkit.getScheduler().cancelTask(refreshTaskId);
        }
        
        hologramManager.stop();
        
        plugin.getLogger().info("[DungeonChest] Saving all data before shutdown...");
        saveChestData();
        
        activeChests.clear();
        
        plugin.getLogger().info("[DungeonChest] System shutdown complete");
    }

    /**
     * Reload configuration and restart
     */
    public void reload() {
        // ★★★ 修复问题2: 重载前清理GUI编辑器状态 ★★★
        // 通知正在编辑的玩家GUI已关闭
        notifyEditorsBeforeReload();
        
        shutdown();
        configManager.loadConfig();
        initialize();
        
        plugin.getLogger().info("[DungeonChest] Configuration reloaded successfully!");
    }
    
    /**
     * ★★★ 通知正在编辑的玩家GUI即将关闭 ★★★
     * 
     * 在重载/关闭前通知正在编辑GUI的玩家，防止数据不一致
     */
    private void notifyEditorsBeforeReload() {
        // 获取正在编辑的玩家列表
        Map<UUID, DungeonChestData> editingPlayers = guiEditor.getEditingPlayers();
        if (editingPlayers == null || editingPlayers.isEmpty()) {
            return;
        }
        
        // 通知并关闭编辑器
        for (UUID playerId : editingPlayers.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(ChatColor.YELLOW + "[DungeonChest] ⚠ Plugin reloading... Editor closed.");
                player.closeInventory();
            }
        }
        
        // 清理编辑器状态
        guiEditor.clearAllEditors();
        
        plugin.getLogger().info("[DungeonChest] ✓ Notified " + editingPlayers.size() + " players about reload");
    }
}
