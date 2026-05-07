package com.github.yuttyann.scriptblockplus.dungeonchest;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HologramManager {

    private final Map<Location, ArmorStand> holograms;
    private final DungeonChestManager chestManager;
    private int updateTaskId = -1;
    private volatile boolean isStopping = false;

    public HologramManager(DungeonChestManager chestManager) {
        this.chestManager = chestManager;
        this.holograms = new ConcurrentHashMap<>();
    }

    public void start() {
        isStopping = false;
        updateTaskId = com.github.yuttyann.scriptblockplus.ScriptBlock.getScheduler().run(() -> {
            checkAndUpdateHolograms();
        }, 0L, 20L).getTaskId();
    }

    public void stop() {
        isStopping = true;
        
        if (updateTaskId != -1) {
            try {
                com.github.yuttyann.scriptblockplus.FoliaCompat.cancelTask(
                    com.github.yuttyann.scriptblockplus.ScriptBlock.getInstance(),
                    updateTaskId
                );
            } catch (Exception e) {
            }
        }
        
        clearAllHologramsSync();
    }

    /**
     * ★★★ 核心逻辑：只在被触发的箱子上显示全息 ★★★
     * 
     * 规则：
     * - 未触发（triggered=false）→ 不显示全息
     * - 已触发（triggered=true）→ 显示倒计时全息
     * - 刷新完成（triggered被重置为false）→ 隐藏全息
     */
    private void checkAndUpdateHolograms() {
        if (isStopping) return;
        
        Collection<DungeonChestData> chests = chestManager.getAllChests();
        
        for (DungeonChestData chest : chests) {
            Location loc = chest.getLocation();
            
            if (chest.isTriggered()) {
                // ★ 已触发：显示/更新全息
                if (!holograms.containsKey(loc)) {
                    createHologramAsync(loc);
                } else {
                    updateHologramText(loc);
                }
            } else {
                // ★ 未触发：隐藏全息
                if (holograms.containsKey(loc)) {
                    hideHologramSync(loc);
                }
            }
        }
        
        // 清理已不存在的箱子的全息
        Set<Location> activeLocations = new HashSet<>();
        for (DungeonChestData chest : chests) {
            activeLocations.add(chest.getLocation());
        }
        
        for (Location loc : new ArrayList<>(holograms.keySet())) {
            if (!activeLocations.contains(loc)) {
                hideHologramSync(loc);
            }
        }
    }

    private void createHologramAsync(Location location) {
        if (isStopping) return;
        
        final Location holoLocation = location.clone().add(0.5, 0.5, 0.5);
        
        com.github.yuttyann.scriptblockplus.FoliaCompat.runAtLocation(
            com.github.yuttyann.scriptblockplus.ScriptBlock.getInstance(),
            location,
            () -> {
                if (isStopping || holograms.containsKey(location)) return;
                
                try {
                    ArmorStand armorStand = (ArmorStand) location.getWorld().spawnEntity(
                        holoLocation, 
                        EntityType.ARMOR_STAND
                    );
                    
                    armorStand.setVisible(false);
                    armorStand.setSmall(true);
                    armorStand.setGravity(false);
                    armorStand.setBasePlate(false);
                    armorStand.setCustomNameVisible(true);
                    armorStand.setPersistent(false);
                    
                    String displayText = getDisplayText(location);
                    armorStand.setCustomName(displayText);
                    
                    holograms.put(location, armorStand);
                    
                } catch (Exception e) {
                    com.github.yuttyann.scriptblockplus.ScriptBlock.getInstance()
                        .getLogger().warning("[Hologram] Failed to create: " + e.getMessage());
                }
            },
            1L
        );
    }

    private void updateHologramText(Location location) {
        if (isStopping) return;
        
        ArmorStand holo = holograms.get(location);
        if (holo != null && !holo.isDead()) {
            String text = getDisplayText(location);
            try {
                holo.setCustomName(text);
            } catch (Exception e) {
                removeHologramInternal(location);
            }
        } else {
            if (holo != null) {
                holograms.remove(location);
            }
        }
    }

    private void removeHologramInternal(Location location) {
        ArmorStand armorStand = holograms.remove(location);
        if (armorStand != null && !armorStand.isDead()) {
            try {
                armorStand.remove();
            } catch (Exception e) {
            }
        }
    }

    /**
     * 同步隐藏全息（不使用调度器）
     */
    private void hideHologramSync(Location location) {
        removeHologramInternal(location);
    }

    /**
     * 异步隐藏全息
     */
    public void hideHologram(Location location) {
        if (isStopping) {
            removeHologramInternal(location);
            return;
        }
        
        com.github.yuttyann.scriptblockplus.FoliaCompat.runAtLocation(
            com.github.yuttyann.scriptblockplus.ScriptBlock.getInstance(),
            location,
            () -> removeHologramInternal(location),
            1L
        );
    }

    private void clearAllHologramsSync() {
        for (Map.Entry<Location, ArmorStand> entry : new ArrayList<>(holograms.entrySet())) {
            ArmorStand stand = entry.getValue();
            if (stand != null && !stand.isDead()) {
                try {
                    stand.remove();
                } catch (Exception ignored) {
                }
            }
        }
        holograms.clear();
    }

    /**
     * ★★★ 计算显示文本（基于触发机制）★★★
     */
    private String getDisplayText(Location location) {
        DungeonChestData chest = chestManager.getChestAt(location);
        if (chest == null) return "";
        
        // 未触发时不显示（但此方法只在触发后调用）
        if (!chest.isTriggered()) return "";
        
        long remainingMs = chest.getRemainingRefreshTimeMs();
        
        if (remainingMs < 0) {
            return "§c⚡ Refreshing now!";
        }
        
        if (remainingMs == 0) {
            return "§c⚡ Refreshing now!";
        }
        
        // 刚触发（剩余时间 > 总时间 - 10秒）
        long totalMs = chest.getRefreshInterval();
        if (remainingMs > totalMs - 10000) {
            return "§a✓ Chest opened! Refreshing in " + formatTimeMs(remainingMs);
        }
        
        // 正常倒计时
        List<String> items = chest.getConfiguredItems();
        int itemCount = items != null ? items.size() : 0;
        return String.format("§e⏱ %s §7(%d items)", formatTimeMs(remainingMs), itemCount);
    }
    
    private String formatTimeMs(long timeMs) {
        if (timeMs <= 0) return "0:00";
        
        long totalSeconds = timeMs / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        
        return String.format("%d:%02d", minutes, seconds);
    }

    public ArmorStand getHologram(Location location) {
        return holograms.get(location);
    }

    public boolean hasHologram(Location location) {
        return holograms.containsKey(location);
    }
    
    public int getActiveHologramCount() {
        return holograms.size();
    }
    
    public boolean isStopping() {
        return isStopping;
    }
}
