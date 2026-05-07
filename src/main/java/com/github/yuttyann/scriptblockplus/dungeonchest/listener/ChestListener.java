package com.github.yuttyann.scriptblockplus.dungeonchest.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import com.github.yuttyann.scriptblockplus.dungeonchest.DungeonChestData;
import com.github.yuttyann.scriptblockplus.dungeonchest.DungeonChestManager;

/**
 * ★★★ 箱子交互监听器 - 玩家触发机制 ★★★
 * 
 * 核心逻辑：
 * - 玩家打开箱子 → 触发刷新倒计时
 * - 不拦截任何玩家操作
 * - 只负责触发计时和记录日志
 */
public class ChestListener implements Listener {

    private final DungeonChestManager manager;

    public ChestListener(DungeonChestManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        if (block == null || block.getType() != Material.CHEST) return;

        if (manager.isDungeonChest(block.getLocation())) {
            // ★★★ 玩家打开了地牢箱子 → 触发刷新倒计时 ★★★
            DungeonChestData chestData = manager.getChestAt(block.getLocation());
            
            if (chestData != null) {
                // 触发箱子（开始倒计时 + 显示全息）
                chestData.triggerByPlayer(player.getUniqueId());
                
                // 记录日志
                manager.getPlugin().getLogger().fine(String.format(
                    "[DungeonChest] Player %s opened chest at %s (triggered=%s)",
                    player.getName(),
                    block.getLocation().getBlockX() + "," + block.getLocation().getBlockY() + "," + block.getLocation().getBlockZ(),
                    chestData.isTriggered()
                ));
            }
        }
    }
}
