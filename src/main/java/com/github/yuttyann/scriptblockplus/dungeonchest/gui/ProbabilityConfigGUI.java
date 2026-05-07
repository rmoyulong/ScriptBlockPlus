package com.github.yuttyann.scriptblockplus.dungeonchest.gui;

import com.github.yuttyann.scriptblockplus.ScriptBlock;
import com.github.yuttyann.scriptblockplus.dungeonchest.ChestDataObserver;
import com.github.yuttyann.scriptblockplus.dungeonchest.DungeonChestData;
import com.github.yuttyann.scriptblockplus.dungeonchest.LootItem;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * 概率配置GUI - 用于编辑每个物品的开出概率
 *
 * 操作说明：
 * - 左键：增加概率（+10%）
 * - 右键：减少概率（-10%）
 * - Shift + 左键：精确增加（+1%）
 * - Shift + 右键：精确减少（-1%）
 */
public class ProbabilityConfigGUI implements ChestDataObserver {

    private static final String TITLE = "§6⚙ 概率配置";
    private static final int SIZE = 54;

    private static final double NORMAL_CHANGE = 10.0;
    private static final double PRECISE_CHANGE = 1.0;

    // 固定的物品槽位（4行7列，不包含边框）
    private static final int[] ITEM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,           // 第1行
        19, 20, 21, 22, 23, 24, 25,           // 第2行
        28, 29, 30, 31, 32, 33, 34,           // 第3行
        37, 38, 39, 40, 41, 42, 43            // 第4行
    };

    private final DungeonChestData chestData;
    private final Inventory inventory;
    private final List<String> workingItems; // 操作中的物品副本

    // 槽位 -> 物品列表索引的映射
    private final Map<Integer, Integer> slotToIndex = new HashMap<>();

    public ProbabilityConfigGUI(DungeonChestData chestData) {
        this.chestData = chestData;
        this.inventory = Bukkit.createInventory(null, SIZE, TITLE);

        // 创建物品列表的可变副本
        List<String> original = chestData.getConfiguredItems();
        this.workingItems = new ArrayList<>(original != null ? original : Collections.emptyList());

        // 注册为观察者
        chestData.addObserver(this);
        chestData.createSnapshot();

        // 初始化GUI
        buildGUI();
    }

    /**
     * 构建完整的GUI
     */
    private void buildGUI() {
        // 填充边框
        fillBorder();

        // 放置物品
        placeItems();

        // 放置按钮
        placeButtons();

        // 放置信息面板
        placeInfoPanel();
    }

    /**
     * 填充边框
     */
    private void fillBorder() {
        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");

        // 顶部边框 (0-8)
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, border);
        }

        // 左侧边框 (9, 18, 27, 36)
        inventory.setItem(9, border);
        inventory.setItem(18, border);
        inventory.setItem(27, border);
        inventory.setItem(36, border);

        // 右侧边框 (17, 26, 35, 44)
        inventory.setItem(17, border);
        inventory.setItem(26, border);
        inventory.setItem(35, border);
        inventory.setItem(44, border);

        // 底部边框 (45-53)
        for (int i = 45; i < 54; i++) {
            if (i != 49) { // 49是重置按钮的位置
                inventory.setItem(i, border);
            }
        }
    }

    /**
     * 放置物品到物品槽位
     */
    private void placeItems() {
        slotToIndex.clear();

        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (i >= workingItems.size()) {
                break; // 没有更多物品
            }

            int slot = ITEM_SLOTS[i];
            String itemData = workingItems.get(i);

            try {
                LootItem lootItem = LootItem.fromString(itemData);
                if (lootItem != null) {
                    ItemStack display = lootItem.createDisplayItem();
                    if (display != null) {
                        inventory.setItem(slot, display);
                        slotToIndex.put(slot, i);
                    }
                }
            } catch (Exception e) {
                // 解析失败，显示错误物品
                ItemStack errorItem = createItem(Material.BARRIER, "§c解析失败", "§7索引: " + i);
                inventory.setItem(slot, errorItem);
                slotToIndex.put(slot, i);
            }
        }
    }

    /**
     * 放置底部按钮
     */
    private void placeButtons() {
        // 保存按钮 (槽位 45)
        inventory.setItem(45, createItem(
            Material.LIME_STAINED_GLASS_PANE,
            "§a✓ 保存",
            "§7点击保存所有概率设置"
        ));

        // 重置按钮 (槽位 49)
        inventory.setItem(49, createItem(
            Material.YELLOW_STAINED_GLASS_PANE,
            "§e↺ 重置",
            "§7将所有概率重置为50%"
        ));

        // 取消按钮 (槽位 53)
        inventory.setItem(53, createItem(
            Material.RED_STAINED_GLASS_PANE,
            "§c✗ 取消",
            "§7取消修改并关闭"
        ));
    }

    /**
     * 放置信息面板
     */
    private void placeInfoPanel() {
        inventory.setItem(4, createItem(
            Material.BOOK,
            "§6⚡ 概率配置指南",
            "",
            "§e左键: §f+10% 概率",
            "§e右键: §f-10% 概率",
            "§eShift+左键: §f+1%",
            "§eShift+右键: §f-1%",
            "",
            "§7范围: 0% ~ 100%"
        ));
    }

    /**
     * 处理点击事件
     */
    public void onClick(Player player, int slot, ItemStack item, ClickType clickType) {
        // 忽略空物品和边框
        if (item == null || item.getType() == Material.BLACK_STAINED_GLASS_PANE) {
            return;
        }

        // 按钮处理
        switch (slot) {
            case 45:
                handleSave(player);
                break;
            case 49:
                handleReset(player);
                break;
            case 53:
                handleCancel(player);
                break;
            default:
                // 检查是否是物品槽位
                if (slotToIndex.containsKey(slot)) {
                    handleItemClick(player, slot, clickType);
                }
                break;
        }
    }

    /**
     * 处理物品点击
     */
    private void handleItemClick(Player player, int slot, ClickType clickType) {
        Integer index = slotToIndex.get(slot);
        if (index == null || index < 0 || index >= workingItems.size()) {
            player.sendMessage("§c[DC] 无效的位置");
            return;
        }

        String itemData = workingItems.get(index);
        if (itemData == null || itemData.trim().isEmpty()) {
            player.sendMessage("§c[DC] 物品数据为空");
            return;
        }

        try {
            LootItem lootItem = LootItem.fromString(itemData);
            if (lootItem == null) {
                player.sendMessage("§c[DC] 无法解析物品");
                return;
            }

            // 计算变化量
            double change;
            switch (clickType) {
                case LEFT:
                    change = NORMAL_CHANGE;
                    break;
                case RIGHT:
                    change = -NORMAL_CHANGE;
                    break;
                case SHIFT_LEFT:
                    change = PRECISE_CHANGE;
                    break;
                case SHIFT_RIGHT:
                    change = -PRECISE_CHANGE;
                    break;
                default:
                    change = 0;
                    break;
            }

            if (change == 0) return;

            // 更新概率
            double oldProb = lootItem.getProbability();
            lootItem.increaseProbability(change);
            double newProb = lootItem.getProbability();

            // 保存到工作副本
            workingItems.set(index, lootItem.toString());

            // 更新GUI显示
            ItemStack display = lootItem.createDisplayItem();
            if (display != null) {
                inventory.setItem(slot, display);
            }

            // 发送消息
            String name = getItemName(lootItem);
            String sign = change > 0 ? "§a+" : "§c";
            player.sendMessage(String.format(
                "§6[DC] §f%s §7概率: §e%.1f%% §7→ §e%.1f%% (%s%.1f%%)",
                name, oldProb, newProb, sign, Math.abs(change)
            ));

            // 播放音效
            try {
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            } catch (Exception ignored) {}

        } catch (Exception e) {
            player.sendMessage("§c[DC] 错误: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    /**
     * 获取物品名称
     */
    private String getItemName(LootItem lootItem) {
        try {
            if (lootItem == null) return "未知";
            ItemStack stack = lootItem.getItemStack();
            if (stack == null) return "未知";
            ItemMeta meta = stack.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                return meta.getDisplayName();
            }
            return stack.getType().name().toLowerCase().replace("_", " ");
        } catch (Exception e) {
            return "未知";
        }
    }

    /**
     * 保存设置
     */
    private void handleSave(Player player) {
        try {
            chestData.setConfiguredItems(new ArrayList<>(workingItems),
                ChestDataObserver.ChangeType.PROBABILITY_CHANGED, "prob_gui_save");
            player.sendMessage("§a[DC] ✓ 概率已保存!");
        } catch (Exception e) {
            player.sendMessage("§c[DC] 保存失败: " + e.getMessage());
        }

        unregister(player);
        player.closeInventory();
    }

    /**
     * 重置为默认值
     */
    private void handleReset(Player player) {
        for (int i = 0; i < workingItems.size(); i++) {
            try {
                LootItem lootItem = LootItem.fromString(workingItems.get(i));
                if (lootItem != null) {
                    lootItem.setProbability(LootItem.DEFAULT_PROBABILITY);
                    workingItems.set(i, lootItem.toString());
                }
            } catch (Exception ignored) {}
        }

        buildGUI();
        player.sendMessage("§e[DC] 已重置为 " + LootItem.DEFAULT_PROBABILITY + "%");
    }

    /**
     * 取消操作
     */
    private void handleCancel(Player player) {
        unregister(player);
        player.sendMessage("§7[DC] 已取消");
        player.closeInventory();
    }

    /**
     * 注销GUI
     */
    private void unregister(Player player) {
        try {
            var listener = ScriptBlock.getGUIListener();
            if (listener != null) {
                listener.unregisterProbabilityGUI(player.getUniqueId());
            }
        } catch (Exception ignored) {}
    }

    /**
     * 打开GUI
     */
    public void open(Player player) {
        try {
            var listener = ScriptBlock.getGUIListener();
            if (listener != null) {
                listener.registerProbabilityGUI(player.getUniqueId(), this);
            }
        } catch (Exception ignored) {}
        player.openInventory(inventory);
    }

    /**
     * 获取Inventory
     */
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * 检查是否是当前GUI的Inventory
     */
    public boolean isInventory(Inventory inv) {
        return inventory.equals(inv);
    }

    /**
     * 创建物品
     */
    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    // ==================== ChestDataObserver 实现 ====================

    @Override
    public void onConfigItemsChanged(Location loc, List<String> items, ChangeType type) {
        if (!loc.equals(chestData.getLocation())) return;
        buildGUI();
    }

    @Override
    public void onProbabilityChanged(Location loc, Map<Integer, Double> map) {}

    @Override
    public void onChestRefreshed(Location loc, long time, List<String> items) {}

    @Override
    public void onPlayerInteraction(Location loc, UUID uuid, InteractionType type) {}
}
