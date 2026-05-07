package com.github.yuttyann.scriptblockplus.dungeonchest.listener;

import com.github.yuttyann.scriptblockplus.dungeonchest.gui.DungeonChestGUI;
import com.github.yuttyann.scriptblockplus.dungeonchest.gui.ProbabilityConfigGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * GUI 事件监听器 - 处理所有 DungeonChest 相关的 GUI 点击事件
 * 
 * 支持的 GUI：
 * - DungeonChestGUI: 物品编辑器
 * - ProbabilityConfigGUI: 概率配置器
 */
public class GUIListener implements Listener {

    private final DungeonChestGUI dungeonChestGUI;
    private final Map<UUID, ProbabilityConfigGUI> probabilityGUIs = new HashMap<>();

    public GUIListener(DungeonChestGUI dungeonChestGUI) {
        this.dungeonChestGUI = dungeonChestGUI;
    }

    /**
     * 处理所有库存点击事件
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();
        Inventory clickedInventory = event.getClickedInventory();
        
        if (clickedInventory == null) return;
        
        // 检查是否是 DungeonChestGUI
        if (dungeonChestGUI.isEditing(playerId)) {
            // 只处理点击 GUI 内部的物品
            if (clickedInventory.equals(player.getOpenInventory().getTopInventory())) {
                event.setCancelled(true);
                
                // 直接传递事件给 DungeonChestGUI 处理
                dungeonChestGUI.onInventoryClick(event);
            }
            return;
        }
        
        // 检查是否是 ProbabilityConfigGUI
        ProbabilityConfigGUI probGUI = probabilityGUIs.get(playerId);
        if (probGUI != null && probGUI.isInventory(clickedInventory)) {
            // 防止玩家拿走物品
            event.setCancelled(true);
            
            int slot = event.getRawSlot();
            ItemStack clickedItem = event.getCurrentItem();
            var clickType = event.getClick();
            
            probGUI.onClick(player, slot, clickedItem, clickType);
        }
    }

    /**
     * 注册概率配置 GUI
     */
    public void registerProbabilityGUI(UUID playerId, ProbabilityConfigGUI gui) {
        probabilityGUIs.put(playerId, gui);
    }

    /**
     * 注销概率配置 GUI
     */
    public void unregisterProbabilityGUI(UUID playerId) {
        probabilityGUIs.remove(playerId);
    }

    /**
     * 清理所有注册的 GUI（插件禁用时）
     */
    public void cleanup() {
        probabilityGUIs.clear();
    }
}
