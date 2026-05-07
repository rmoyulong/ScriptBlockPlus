package com.github.yuttyann.scriptblockplus.dungeonchest;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import com.github.yuttyann.scriptblockplus.ScriptBlock;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 * ★★★ 数据事务管理器 - 确保所有数据操作的原子性和一致性 ★★★
 * 
 * 功能：
 * - 数据备份与回滚机制
 * - 事务性操作支持
 * - 并发访问控制
 * - 防止数据丢失和损坏
 */
public class DataTransactionManager {

    private final ScriptBlock plugin;
    private final File dataFile;
    private final File backupDir;
    
    // 每个箱子的数据锁
    private final Map<Location, ReentrantLock> chestLocks = new ConcurrentHashMap<>();
    
    // 全局备份存储
    private final Map<Location, List<String>> backupStorage = new ConcurrentHashMap<>();
    
    // 操作日志（用于调试）
    private final List<TransactionLog> transactionLogs = new ArrayList<>();

    public DataTransactionManager(ScriptBlock plugin, File dataFile) {
        this.plugin = plugin;
        this.dataFile = dataFile;
        this.backupDir = new File(dataFile.getParentFile(), "backups");
        
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }
    }

    /**
     * 开始事务：备份数据
     * 
     * @param location 箱子位置
     * @param currentData 当前数据
     * @return 事务ID（用于回滚）
     */
    public String beginTransaction(Location location, List<String> currentData) {
        String transactionId = UUID.randomUUID().toString().substring(0, 8);
        
        // 备份当前数据
        if (currentData != null && !currentData.isEmpty()) {
            backupStorage.put(location, new ArrayList<>(currentData));
            
            // 记录日志
            logTransaction(transactionId, "BEGIN", location, 
                "Backup created with " + currentData.size() + " items");
        }
        
        return transactionId;
    }

    /**
     * 提交事务：确认更改
     * 
     * @param location 箱子位置
     * @param transactionId 事务ID
     * @param newData 新数据
     */
    public void commitTransaction(Location location, String transactionId, List<String> newData) {
        // 清除备份（提交成功）
        backupStorage.remove(location);
        
        // 记录日志
        logTransaction(transactionId, "COMMIT", location,
            "Committed with " + (newData != null ? newData.size() : 0) + " items");
    }

    /**
     * 回滚事务：恢复备份数据
     * 
     * @param location 箱子位置
     * @param transactionId 事务ID
     * @param chestData 目标箱子数据对象
     * @return 是否成功回滚
     */
    public boolean rollbackTransaction(Location location, String transactionId, DungeonChestData chestData) {
        List<String> backup = backupStorage.get(location);
        
        if (backup == null) {
            plugin.getLogger().warning("[DataTx] No backup found for rollback: " + transactionId);
            return false;
        }
        
        // 恢复备份数据
        chestData.setSerializedItems(new ArrayList<>(backup));
        
        // 清除备份
        backupStorage.remove(location);
        
        // 记录日志
        logTransaction(transactionId, "ROLLBACK", location,
            "Rolled back to " + backup.size() + " items");
        
        return true;
    }

    /**
     * 获取箱子的锁（用于防止并发修改）
     */
    public ReentrantLock getChestLock(Location location) {
        return chestLocks.computeIfAbsent(location, k -> new ReentrantLock());
    }

    /**
     * 安全地执行数据修改操作（带锁）
     * 
     * @param location 箱子位置
     * @param operation 要执行的操作
     * @return 操作是否成功
     */
    public boolean executeWithLock(Location location, DataOperation operation) {
        ReentrantLock lock = getChestLock(location);
        
        try {
            // 尝试获取锁（最多等待5秒）
            if (!lock.tryLock(5, java.util.concurrent.TimeUnit.SECONDS)) {
                plugin.getLogger().warning("[DataTx] Failed to acquire lock for: " + location);
                return false;
            }
            
            try {
                // 执行操作
                return operation.execute();
                
            } finally {
                lock.unlock();
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().log(Level.WARNING, "[DataTx] Interrupted while waiting for lock", e);
            return false;
        }
    }

    /**
     * 创建文件级备份（用于重大操作前）
     * 
     * @param reason 备份原因
     * @return 是否成功创建备份
     */
    public boolean createFileBackup(String reason) {
        if (!dataFile.exists()) {
            return true; // 无需备份
        }
        
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        String backupFileName = "dungeonchests_" + timestamp + "_" + reason.replace(" ", "_") + ".json";
        File backupFile = new File(backupDir, backupFileName);
        
        try {
            java.nio.file.Files.copy(
                dataFile.toPath(),
                backupFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
            
            plugin.getLogger().info("[DataTx] ✓ Created file backup: " + backupFile.getName());
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[DataTx] Failed to create file backup", e);
            return false;
        }
    }

    /**
     * 清理过期备份（保留最近10个）
     */
    public void cleanupOldBackups() {
        if (!backupDir.exists()) return;
        
        File[] backups = backupDir.listFiles((dir, name) -> name.startsWith("dungeonchests_") && name.endsWith(".json"));
        
        if (backups == null || backups.length <= 10) return;
        
        // 按时间排序（最旧的在前）
        Arrays.sort(backups, Comparator.comparingLong(File::lastModified));
        
        // 删除超过10个的旧备份
        int toDelete = backups.length - 10;
        for (int i = 0; i < toDelete; i++) {
            if (backups[i].delete()) {
                plugin.getLogger().info("[DataTx] Cleaned up old backup: " + backups[i].getName());
            }
        }
    }

    /**
     * 获取当前备份状态
     */
    public Map<Location, List<String>> getActiveBackups() {
        return Collections.unmodifiableMap(backupStorage);
    }

    /**
     * 强制清除所有内存中的备份
     */
    public void clearAllBackups() {
        backupStorage.clear();
        plugin.getLogger().info("[DataTx] All memory backups cleared");
    }

    /**
     * 验证数据完整性
     * 
     * @param location 箱子位置
     * @param data 要验证的数据
     * @return 是否有效
     */
    public boolean validateData(Location location, List<String> data) {
        if (data == null || data.isEmpty()) {
            return true; // 空数据是有效的
        }
        
        for (String itemStr : data) {
            if (itemStr == null || itemStr.trim().isEmpty()) {
                plugin.getLogger().warning("[DataTx] Invalid empty item entry in chest at: " + location);
                return false;
            }
            
            // 尝试反序列化验证格式
            try {
                ItemStack item = NBTSerializer.deserializeItem(itemStr.split("\\|")[0]);
                if (item == null) {
                    plugin.getLogger().warning("[DataTx] Failed to deserialize item at: " + location);
                    return false;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[DataTx] Item validation failed", e);
                return false;
            }
        }
        
        return true;
    }

    /**
     * 记录事务日志
     */
    private void logTransaction(String transactionId, String action, Location location, String details) {
        TransactionLog log = new TransactionLog(transactionId, action, location, details);
        transactionLogs.add(log);
        
        // 只保留最近100条日志
        if (transactionLogs.size() > 100) {
            transactionLogs.remove(0);
        }
        
        // 调试级别输出
        if (plugin.getLogger().isLoggable(Level.FINE)) {
            plugin.getLogger().fine("[DataTx] [" + action + "] ID=" + transactionId + 
                " Loc=" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() +
                " - " + details);
        }
    }

    /**
     * 获取最近的事务日志
     */
    public List<TransactionLog> getRecentTransactions(int count) {
        int start = Math.max(0, transactionLogs.size() - count);
        return new ArrayList<>(transactionLogs.subList(start, transactionLogs.size()));
    }

    /**
     * 数据操作接口
     */
    @FunctionalInterface
    public interface DataOperation {
        boolean execute();
    }

    /**
     * 事务日志记录
     */
    public static class TransactionLog {
        private final String transactionId;
        private final String action;
        private final Location location;
        private final String details;
        private final long timestamp;

        public TransactionLog(String transactionId, String action, Location location, String details) {
            this.transactionId = transactionId;
            this.action = action;
            this.location = location;
            this.details = details;
            this.timestamp = System.currentTimeMillis();
        }

        public String getTransactionId() { return transactionId; }
        public String getAction() { return action; }
        public Location getLocation() { return location; }
        public String getDetails() { return details; }
        public long getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return String.format("[%s] %s at (%d,%d,%d) - %s",
                transactionId, action,
                location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                details);
        }
    }
}
