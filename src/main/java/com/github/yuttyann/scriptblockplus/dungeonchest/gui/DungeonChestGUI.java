package com.github.yuttyann.scriptblockplus.dungeonchest.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.github.yuttyann.scriptblockplus.dungeonchest.DataTransactionManager;
import com.github.yuttyann.scriptblockplus.dungeonchest.ChestDataObserver;
import com.github.yuttyann.scriptblockplus.dungeonchest.DungeonChestData;
import com.github.yuttyann.scriptblockplus.dungeonchest.DungeonChestManager;
import com.github.yuttyann.scriptblockplus.dungeonchest.LootItem;
import com.github.yuttyann.scriptblockplus.dungeonchest.NBTSerializer;

import java.util.*;  // Includes ArrayList, HashSet, Set

public class DungeonChestGUI implements Listener, ChestDataObserver {

    private static final int GUI_SIZE = 36; // 9x4 = 36格
    private static final int ITEM_SLOTS = 27; // 3行 x 9列 = 27格物品
    private static final int BUTTON_ROW_START = 27; // 第4行开始

    private final DungeonChestManager manager;
    private final Map<UUID, DungeonChestData> editingPlayers;
    private final Map<UUID, Inventory> playerInventories;

    public DungeonChestGUI(DungeonChestManager manager) {
        this.manager = manager;
        this.editingPlayers = new HashMap<>();
        this.playerInventories = new HashMap<>();
    }

    public void openEditor(Player player, DungeonChestData chestData) {
        chestData.addObserver(this);
        chestData.createSnapshot();

        // 9x4 = 36格 inventory
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE,
            ChatColor.GOLD + "Dungeon Chest Editor: " + chestData.getConfigName());

        // 加载物品（前27格）
        List<ItemStack> existingItems = NBTSerializer.deserializeItems(chestData.getConfiguredItems());
        for (int i = 0; i < existingItems.size() && i < ITEM_SLOTS; i++) {
            gui.setItem(i, existingItems.get(i));
        }

        // 第4行放控制按钮
        createControlButtons(gui, chestData);

        player.openInventory(gui);
        editingPlayers.put(player.getUniqueId(), chestData);
        playerInventories.put(player.getUniqueId(), gui);

        player.sendMessage(ChatColor.YELLOW + "[DungeonChest] Editing: " + chestData.getConfigName() +
            " (" + existingItems.size() + " configured items)");
    }

    private void createControlButtons(Inventory gui, DungeonChestData chestData) {
        // 第4行按钮布局: 27=保存, 28=清空, 29=信息, 30=概率配置, 31-34=空, 35=取消

        // Save button (slot 27)
        ItemStack saveButton = new ItemStack(Material.LIME_WOOL);
        ItemMeta saveMeta = saveButton.getItemMeta();
        saveMeta.setDisplayName(ChatColor.GREEN + "Save Items");
        saveMeta.setLore(Arrays.asList(
            ChatColor.GRAY + "Click to save all items",
            ChatColor.GRAY + "Items will be stored with NBT data"
        ));
        saveButton.setItemMeta(saveMeta);
        gui.setItem(27, saveButton);

        // Clear button (slot 28)
        ItemStack clearButton = new ItemStack(Material.RED_WOOL);
        ItemMeta clearMeta = clearButton.getItemMeta();
        clearMeta.setDisplayName(ChatColor.RED + "Clear All");
        clearMeta.setLore(Arrays.asList(
            ChatColor.GRAY + "Remove all items from this chest"
        ));
        clearButton.setItemMeta(clearMeta);
        gui.setItem(28, clearButton);

        // Info button (slot 29)
        ItemStack infoButton = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = infoButton.getItemMeta();
        infoMeta.setDisplayName(ChatColor.YELLOW + "Info");
        int itemCount = chestData.getConfiguredItems() != null ? chestData.getConfiguredItems().size() : 0;
        infoMeta.setLore(Arrays.asList(
            ChatColor.GRAY + "Config: " + ChatColor.WHITE + chestData.getConfigName(),
            ChatColor.GRAY + "Items: " + ChatColor.WHITE + itemCount,
            "",
            ChatColor.YELLOW + "Instructions:",
            ChatColor.GRAY + "• Place items in slots 0-26",
            ChatColor.GRAY + "• Click Save when done"
        ));
        infoButton.setItemMeta(infoMeta);
        gui.setItem(29, infoButton);

        // Probability config button (slot 30)
        ItemStack probButton = new ItemStack(Material.NETHER_STAR);
        ItemMeta probMeta = probButton.getItemMeta();
        probMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "⚡ Probability Config");
        probMeta.setLore(Arrays.asList(
            ChatColor.GRAY + "Configure drop probability",
            ChatColor.GRAY + "for each item",
            "",
            ChatColor.GREEN + "Left click: +10%",
            ChatColor.RED + "Right click: -10%",
            ChatColor.AQUA + "Shift+click: ±1%"
        ));
        probButton.setItemMeta(probMeta);
        gui.setItem(30, probButton);

        // Cancel button (slot 35)
        ItemStack cancelButton = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancelButton.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.RED + "Cancel");
        cancelButton.setItemMeta(cancelMeta);
        gui.setItem(35, cancelButton);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();

        if (!editingPlayers.containsKey(playerId)) return;

        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null || !clickedInv.equals(player.getOpenInventory().getTopInventory())) return;

        event.setCancelled(true);

        DungeonChestData chestData = editingPlayers.get(playerId);
        int slot = event.getRawSlot();

        // 处理按钮区域 (slot 27-35)
        if (slot >= BUTTON_ROW_START) {
            handleControlButtonClick(player, chestData, slot, clickedInv);
        } else {
            // 允许与物品槽位交互 (slot 0-26)
            event.setCancelled(false);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (!editingPlayers.containsKey(player.getUniqueId())) return;

        // 防止拖拽到按钮区域
        for (int slot : event.getRawSlots()) {
            if (slot >= BUTTON_ROW_START) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void handleControlButtonClick(Player player, DungeonChestData chestData, int slot, Inventory inv) {
        switch (slot) {
            case 27: // Save
                saveItemsFromGUI(player, chestData, inv);
                break;

            case 28: // Clear
                clearAllItems(inv);
                break;

            case 29: // Info - do nothing
                break;

            case 30: // Probability Config
                openProbabilityConfig(player, chestData);
                break;

            case 35: // Cancel
                player.closeInventory();
                editingPlayers.remove(player.getUniqueId());
                playerInventories.remove(player.getUniqueId());
                player.sendMessage(ChatColor.YELLOW + "[DungeonChest] Editor cancelled.");
                break;
        }
    }

    private void saveItemsFromGUI(Player player, DungeonChestData chestData, Inventory inv) {
        Location location = chestData.getLocation();

        DataTransactionManager txManager = manager.getTransactionManager();
        String txId = txManager.beginTransaction(location, chestData.getConfiguredItems());

        try {
            List<String> existingItems = chestData.getConfiguredItems();

            // 从物品槽位收集物品 (slot 0-26)
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < ITEM_SLOTS; i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && !item.getType().isAir()) {
                    items.add(item);
                }
            }
            
            // ★★★ 修复问题：保留现有的概率信息 ★★★
            // 序列化物品并保留概率信息
            List<String> serializedItems = serializeItemsWithProbability(items, existingItems);
            
            // 验证数据完整性
            if (!txManager.validateData(location, serializedItems)) {
                player.sendMessage(ChatColor.RED + "[DungeonChest] ✗ Data validation failed! Rolling back...");
                
                // 回滚到备份数据
                txManager.rollbackTransaction(location, txId, chestData);
                return;
            }
            
            // 使用锁确保原子性操作
            boolean success = txManager.executeWithLock(location, () -> {
                // ★★★ 使用带通知的设置方法（会自动通知概率GUI）★★★
                chestData.setConfiguredItems(serializedItems, 
                    ChestDataObserver.ChangeType.ITEMS_REPLACED, "gui_save");
                
                // 立即持久化保存（不等待下次定时保存）
                manager.saveChestDataImmediately(chestData);
                
                return true;
            });
            
            if (success) {
                // 提交事务
                txManager.commitTransaction(location, txId, serializedItems);
                
                // ★★★ 修复问题1: 保存后触发倒计时刷新和全息更新 ★★★
                manager.resetAndRefreshChest(chestData);
                
                // Close editor
                player.closeInventory();
                editingPlayers.remove(player.getUniqueId());
                playerInventories.remove(player.getUniqueId()); // ★★★ 清理库存引用 ★★★
                
                player.sendMessage(ChatColor.GREEN + "[DungeonChest] ✓ Saved " + items.size() + " items with full NBT data!");
                player.sendMessage(ChatColor.GRAY + "[DungeonChest] Chest has been refreshed with new items.");
                player.sendMessage(ChatColor.YELLOW + "[DungeonChest] 🔒 Transaction " + txId + " committed safely");
                
            } else {
                // 锁获取失败，回滚
                txManager.rollbackTransaction(location, txId, chestData);
                player.sendMessage(ChatColor.RED + "[DungeonChest] ✗ Failed to acquire data lock! Please try again.");
            }
            
        } catch (Exception e) {
            // 异常时回滚
            txManager.rollbackTransaction(location, txId, chestData);
            
            player.sendMessage(ChatColor.RED + "[DungeonChest] ✗ Error saving items: " + e.getMessage());
            com.github.yuttyann.scriptblockplus.ScriptBlock.getInstance().getLogger()
                .log(java.util.logging.Level.SEVERE, "[DungeonChest] Error in saveItemsFromGUI", e);
        }
    }
    
    /**
     * ★★★ 序列化物品列表并保留概率信息 ★★★
     * 
     * 如果现有的配置物品中有概率信息，会保留这些概率。
     * 如果物品被替换或新增，使用默认概率(50%)。
     */
    private List<String> serializeItemsWithProbability(List<ItemStack> newItems, List<String> existingItems) {
        List<String> result = new ArrayList<>();
        
        // 解析现有物品的Base64数据，用于匹配
        Set<String> existingBase64Set = new HashSet<>();
        Map<String, Double> existingProbabilityMap = new HashMap<>();
        
        if (existingItems != null) {
            for (String itemData : existingItems) {
                try {
                    LootItem lootItem = LootItem.fromString(itemData);
                    if (lootItem != null) {
                        String base64 = NBTSerializer.serializeItem(lootItem.getItemStack());
                        existingBase64Set.add(base64);
                        existingProbabilityMap.put(base64, lootItem.getProbability());
                    } else {
                        // 尝试直接提取base64部分
                        String base64 = itemData.contains("|") ? itemData.split("\\|")[0] : itemData;
                        existingBase64Set.add(base64);
                        existingProbabilityMap.put(base64, LootItem.DEFAULT_PROBABILITY);
                    }
                } catch (Exception e) {
                    // 解析失败，忽略
                }
            }
        }
        
        // 序列化新物品，保留概率信息
        for (ItemStack item : newItems) {
            String base64 = NBTSerializer.serializeItem(item);
            if (base64 != null) {
                // 检查是否有现有的概率信息
                double probability = LootItem.DEFAULT_PROBABILITY;
                if (existingProbabilityMap.containsKey(base64)) {
                    probability = existingProbabilityMap.get(base64);
                }
                // 保存为 LootItem 格式
                result.add(base64 + "|" + probability);
            }
        }
        
        return result;
    }

    private void clearAllItems(Inventory inv) {
        for (int i = 0; i < ITEM_SLOTS; i++) {
            inv.setItem(i, null);
        }
    }
    
    /**
     * 打开概率配置GUI
     */
    private void openProbabilityConfig(Player player, DungeonChestData chestData) {
        if (chestData.getSerializedItems() == null || chestData.getSerializedItems().isEmpty()) {
            player.sendMessage(ChatColor.RED + "[DungeonChest] No items to configure! Add items first.");
            return;
        }

        // ★★★ 关键修复：在打开概率GUI前清除当前GUI的编辑状态 ★★★
        UUID playerId = player.getUniqueId();
        editingPlayers.remove(playerId);
        playerInventories.remove(playerId);

        ProbabilityConfigGUI probGUI = new ProbabilityConfigGUI(chestData);
        probGUI.open(player);

        player.sendMessage(ChatColor.LIGHT_PURPLE + "[DungeonChest] ⚡ Opening probability config...");
    }

    public boolean isEditing(UUID playerId) {
        return editingPlayers.containsKey(playerId);
    }

    public void removeEditor(UUID playerId) {
        editingPlayers.remove(playerId);
        playerInventories.remove(playerId); // ★★★ 同时清理库存引用 ★★★
    }
    
    /**
     * ★★★ 获取所有正在编辑的玩家映射（用于reload通知）★★★
     */
    public Map<UUID, DungeonChestData> getEditingPlayers() {
        return editingPlayers;
    }
    
    /**
     * ★★★ 清理所有编辑器状态（用于reload时）★★★
     */
    public void clearAllEditors() {
        editingPlayers.clear();
        playerInventories.clear();
    }
    
    // ==================== ★★★ ChestDataObserver 实现 ★★★ ====================
    
    /**
     * 当配置物品列表变化时调用（例如概率GUI修改了数据）
     * 
     * 如果当前有玩家正在编辑这个箱子的GUI，需要更新显示
     */
    @Override
    public void onConfigItemsChanged(Location chestLocation, List<String> newItems, ChangeType changeType) {
        // 检查是否有玩家正在编辑这个箱子
        for (Map.Entry<UUID, DungeonChestData> entry : editingPlayers.entrySet()) {
            if (entry.getValue().getLocation().equals(chestLocation)) {
                UUID playerId = entry.getKey();
                Player player = Bukkit.getPlayer(playerId);
                
                if (player != null && player.isOnline()) {
                    // ★★★ 修复问题3: 刷新GUI显示 ★★★
                    Inventory gui = playerInventories.get(playerId);
                    if (gui != null) {
                        refreshGUIDisplay(gui, newItems, entry.getValue());
                        player.sendMessage(ChatColor.GREEN + "[DungeonChest] ✓ Items refreshed from probability config!");
                    } else {
                        // 如果没有库存引用（异常情况），通知玩家重新打开
                        player.sendMessage(ChatColor.YELLOW + "[DungeonChest] ⚠ Items updated externally (" + 
                            changeType.name() + ")");
                        player.sendMessage(ChatColor.GRAY + "[DungeonChest] Please close and reopen the editor.");
                    }
                }
                
                break;  // 只处理第一个匹配的
            }
        }
        
        Bukkit.getLogger().fine("[DungeonChestGUI] Received config items change notification: " + 
            changeType.name() + " at " + chestLocation);
    }
    
    /**
     * ★★★ 刷新GUI显示的物品 ★★★
     * 
     * 当概率GUI或其他地方修改了物品列表后，调用此方法刷新DungeonChestGUI的显示
     */
    private void refreshGUIDisplay(Inventory gui, List<String> newItems, DungeonChestData chestData) {
        // 清除物品区域的旧物品 (slot 0-26)
        for (int i = 0; i < ITEM_SLOTS; i++) {
            gui.setItem(i, null);
        }

        // 从新的配置物品列表加载物品
        List<ItemStack> items = NBTSerializer.deserializeItems(newItems);
        for (int i = 0; i < items.size() && i < ITEM_SLOTS; i++) {
            gui.setItem(i, items.get(i));
        }

        // 更新信息按钮
        ItemStack infoItem = gui.getItem(29);
        if (infoItem != null && infoItem.hasItemMeta()) {
            ItemMeta meta = infoItem.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Config: " + ChatColor.WHITE + chestData.getConfigName());
            lore.add(ChatColor.GRAY + "Items: " + ChatColor.WHITE + newItems.size());
            lore.add("");
            lore.add(ChatColor.YELLOW + "Instructions:");
            lore.add(ChatColor.GRAY + "• Place items in slots 0-26");
            lore.add(ChatColor.GRAY + "• Click Save when done");
            lore.add("");
            lore.add(ChatColor.AQUA + "Items updated from probability config!");
            meta.setLore(lore);
            infoItem.setItemMeta(meta);
        }

        createControlButtons(gui, chestData);

        Bukkit.getLogger().info("[DungeonChestGUI] GUI display refreshed with " + items.size() + " items");
    }
    
    @Override
    public void onProbabilityChanged(Location chestLocation, Map<Integer, Double> probabilityMap) {
        // 物品GUI不关心概率变化（概率GUI自己会处理）
        Bukkit.getLogger().fine("[DungeonChestGUI] Probability changed at " + chestLocation);
    }
    
    @Override
    public void onChestRefreshed(Location chestLocation, long refreshTime, List<String> generatedItems) {
        // 箱子刷新后，如果玩家正在编辑，提醒他们
        for (Map.Entry<UUID, DungeonChestData> entry : editingPlayers.entrySet()) {
            if (entry.getValue().getLocation().equals(chestLocation)) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && player.isOnline()) {
                    player.sendMessage(ChatColor.AQUA + "[DungeonChest] Chest was just refreshed with new items!");
                }
                break;
            }
        }
    }
    
    @Override
    public void onPlayerInteraction(Location chestLocation, UUID playerUUID, InteractionType interactionType) {
        // 物品GUI不关心玩家交互（除非需要特殊处理）
        Bukkit.getLogger().fine("[DungeonChestGUI] Player interaction: " + interactionType.name() + 
            " at " + chestLocation);
    }
}
