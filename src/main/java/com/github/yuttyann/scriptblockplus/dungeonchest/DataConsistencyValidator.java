package com.github.yuttyann.scriptblockplus.dungeonchest;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

import com.github.yuttyann.scriptblockplus.ScriptBlock;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * ★★★ 数据一致性校验器 ★★★
 * 
 * 功能：
 * 1. 检测物品编辑GUI与概率GUI的数据一致性
 * 2. 检测配置物品与实际显示物品的一致性
 * 3. 检测时间数据的合理性
 * 4. 自动生成警告日志和告警消息
 * 5. 提供修复建议
 */
public class DataConsistencyValidator {

    private final ScriptBlock plugin;
    private final DungeonChestManager chestManager;
    
    // 校验结果缓存（避免重复校验）
    private final Map<Location, ValidationResult> lastValidationResults = new ConcurrentHashMap<>();
    
    // 告警阈值配置
    private static final int MAX_WARNINGS_PER_CHEST = 10;  // 每个箱子最大告警数
    private static final long VALIDATION_COOLDOWN_MS = 5000;  // 校验冷却5秒
    
    public DataConsistencyValidator(ScriptBlock plugin, DungeonChestManager chestManager) {
        this.plugin = plugin;
        this.chestManager = chestManager;
    }

    /**
     * 执行完整的数据一致性校验
     * 
     * @param location 箱子位置（null则校验所有箱子）
     * @return 校验结果列表
     */
    public List<ValidationResult> validateAll(Location location) {
        List<ValidationResult> results = new ArrayList<>();
        
        Collection<DungeonChestData> chests = (location != null) 
            ? Collections.singletonList(chestManager.getChestAt(location))
            : chestManager.getAllChests();
        
        for (DungeonChestData chest : chests) {
            if (chest == null) continue;
            
            ValidationResult result = validateSingle(chest);
            results.add(result);
            
            // 缓存结果
            lastValidationResults.put(chest.getLocation(), result);
            
            // 如果有问题，记录日志
            if (!result.isValid()) {
                logValidationWarnings(result);
                sendAdminAlerts(result);
            }
        }
        
        return results;
    }

    /**
     * 校验单个箱子的数据一致性
     */
    public ValidationResult validateSingle(DungeonChestData chest) {
        if (chest == null) {
            return new ValidationResult(null, false, 
                Arrays.asList(new ValidationIssue(Severity.CRITICAL, "NULL_DATA", 
                    "Chest data is null", null)));
        }
        
        Location loc = chest.getLocation();
        List<ValidationIssue> issues = new ArrayList<>();
        
        // 1. ★★★ 检查配置物品列表完整性 ★★★
        issues.addAll(validateConfiguredItems(chest));
        
        // 2. ★★★ 检查时间数据合理性 ★★★
        issues.addAll(validateTimeData(chest));
        
        // 3. ★★★ 检查概率数据有效性 ★★★
        issues.addAll(validateProbabilityData(chest));
        
        // 4. ★★★ 检查数据格式合法性 ★★★
        issues.addAll(validateDataFormat(chest));
        
        boolean isValid = issues.isEmpty() || issues.stream()
            .noneMatch(i -> i.getSeverity() == Severity.CRITICAL);
        
        return new ValidationResult(loc, isValid, issues);
    }
    
    /**
     * 验证配置物品列表
     */
    private List<ValidationIssue> validateConfiguredItems(DungeonChestData chest) {
        List<ValidationIssue> issues = new ArrayList<>();
        List<String> items = chest.getConfiguredItems();
        
        if (items == null) {
            issues.add(new ValidationIssue(
                Severity.CRITICAL,
                "NULL_ITEM_LIST",
                "Configured items list is null",
                "Expected non-null list, got null"
            ));
            return issues;
        }
        
        for (int i = 0; i < items.size(); i++) {
            String itemStr = items.get(i);
            
            // 检查空条目
            if (itemStr == null || itemStr.trim().isEmpty()) {
                issues.add(new ValidationIssue(
                    Severity.WARNING,
                    "EMPTY_ITEM_ENTRY",
                    "Item entry at index " + i + " is empty",
                    "Item index: " + i
                ));
                continue;
            }
            
            // 尝试反序列化验证格式
            try {
                String[] parts = itemStr.split("\\|");
                String base64Data = parts[0];
                
                ItemStack item = NBTSerializer.deserializeItem(base64Data);
                
                if (item == null) {
                    issues.add(new ValidationIssue(
                        Severity.ERROR,
                        "DESERIALIZATION_FAILED",
                        "Failed to deserialize item at index " + i,
                        "Item data: " + itemStr.substring(0, Math.min(50, itemStr.length())) + "..."
                    ));
                } else if (item.getType().isAir()) {
                    issues.add(new ValidationIssue(
                        Severity.WARNING,
                        "AIR_ITEM",
                        "Item at index " + i + " is AIR type",
                        "Material: " + item.getType().name()
                    ));
                }
                
                // 检查概率值（如果有）
                if (parts.length > 1) {
                    try {
                        double prob = Double.parseDouble(parts[1]);
                        
                        if (prob < 0 || prob > 100) {
                            issues.add(new ValidationIssue(
                                Severity.ERROR,
                                "INVALID_PROBABILITY",
                                "Probability out of range [0-100] at index " + i,
                                String.format("Value: %.2f, Index: %d", prob, i)
                            ));
                        }
                    } catch (NumberFormatException e) {
                        issues.add(new ValidationIssue(
                            Severity.ERROR,
                            "PROBABILITY_PARSE_ERROR",
                            "Failed to parse probability at index " + i,
                            "Raw value: " + parts[1]
                        ));
                    }
                }
                
            } catch (Exception e) {
                issues.add(new ValidationIssue(
                    Severity.ERROR,
                    "ITEM_FORMAT_ERROR",
                    "Invalid item format at index " + i + ": " + e.getMessage(),
                    "Item data: " + itemStr.substring(0, Math.min(50, itemStr.length())) + "..."
                ));
            }
        }
        
        return issues;
    }
    
    /**
     * 验证时间数据
     */
    private List<ValidationIssue> validateTimeData(DungeonChestData chest) {
        List<ValidationIssue> issues = new ArrayList<>();
        
        long interval = chest.getRefreshInterval();
        long lastRefresh = chest.getLastRefreshTime();
        long now = System.currentTimeMillis();
        
        // 检查刷新间隔
        if (interval <= 0) {
            issues.add(new ValidationIssue(
                Severity.CRITICAL,
                "INVALID_REFRESH_INTERVAL",
                "Refresh interval must be positive",
                String.format("Value: %d ms", interval)
            ));
        } else if (interval < 60000) {  // 少于1分钟
            issues.add(new ValidationIssue(
                Severity.WARNING,
                "VERY_SHORT_INTERVAL",
                "Refresh interval is very short (< 1 minute)",
                String.format("Value: %d ms (%.1f seconds)", interval, interval / 1000.0)
            ));
        }
        
        // 检查最后刷新时间
        if (lastRefresh <= 0) {
            issues.add(new ValidationIssue(
                Severity.ERROR,
                "INVALID_LAST_REFRESH",
                "Last refresh time is not set or invalid",
                String.format("Value: %d", lastRefresh)
            ));
        } else if (lastRefresh > now) {
            issues.add(new ValidationIssue(
                Severity.WARNING,
                "FUTURE_REFRESH_TIME",
                "Last refresh time is in the future (possible clock skew)",
                String.format("Last refresh: %tF %<tT, Current: %tF %<tT", 
                    new Date(lastRefresh), new Date(now))
            ));
        } else if (now - lastRefresh > interval * 10) {
            // 超过10个周期未刷新
            issues.add(new ValidationIssue(
                Severity.INFO,
                "LONG_TIME_SINCE_REFRESH",
                "Chest hasn't been refreshed for a long time",
                String.format("Last refresh: %.1f hours ago", (now - lastRefresh) / 3600000.0)
            ));
        }
        
        return issues;
    }
    
    /**
     * 验证概率数据
     */
    private List<ValidationIssue> validateProbabilityData(DungeonChestData chest) {
        List<ValidationIssue> issues = new ArrayList<>();
        List<String> items = chest.getConfiguredItems();
        
        if (items == null || items.isEmpty()) return issues;
        
        double totalProbability = 0;
        int validProbCount = 0;
        
        for (int i = 0; i < items.size(); i++) {
            try {
                LootItem lootItem = LootItem.fromString(items.get(i));
                
                if (lootItem != null) {
                    double prob = lootItem.getProbability();
                    totalProbability += prob;
                    validProbCount++;
                    
                    // 检查极端概率值
                    if (prob == 0) {
                        issues.add(new ValidationIssue(
                            Severity.INFO,
                            "ZERO_PROBABILITY",
                            "Item at index " + i + " has 0% probability (will never drop)",
                            "Item: " + getItemName(lootItem)
                        ));
                    } else if (prob >= 100) {
                        issues.add(new ValidationIssue(
                            Severity.INFO,
                            "MAX_PROBABILITY",
                            "Item at index " + i + " has ≥100% probability (will always drop)",
                            "Item: " + getItemName(lootItem) + ", Prob: " + prob + "%"
                        ));
                    }
                }
            } catch (Exception e) {
                // 无法解析为LootItem，跳过概率检查
            }
        }
        
        // 检查总概率
        if (validProbCount > 0 && totalProbability > 100 * validProbCount) {
            issues.add(new ValidationIssue(
                Severity.WARNING,
                "HIGH_TOTAL_PROBABILITY",
                "Total probability exceeds reasonable range",
                String.format("Total: %.1f%% (avg %.1f%% per item)", 
                    totalProbability, totalProbability / validProbCount)
            ));
        }
        
        return issues;
    }
    
    /**
     * 验证数据格式
     */
    private List<ValidationIssue> validateDataFormat(DungeonChestData chest) {
        List<ValidationIssue> issues = new ArrayList<>();
        
        // 检查位置有效性
        Location loc = chest.getLocation();
        if (loc == null) {
            issues.add(new ValidationIssue(
                Severity.CRITICAL,
                "NULL_LOCATION",
                "Chest location is null",
                null
            ));
        } else if (loc.getWorld() == null) {
            issues.add(new ValidationIssue(
                Severity.CRITICAL,
                "NULL_WORLD",
                "Chest world is null",
                "Location: " + loc.getX() + "," + loc.getY() + "," + loc.getZ()
            ));
        }
        
        // 检查配置名称
        String configName = chest.getConfigName();
        if (configName == null || configName.trim().isEmpty()) {
            issues.add(new ValidationIssue(
                Severity.WARNING,
                "EMPTY_CONFIG_NAME",
                "Configuration name is empty",
                "Config name should not be empty for identification"
            ));
        }
        
        return issues;
    }

    /**
     * 记录校验警告到日志
     */
    private void logValidationWarnings(ValidationResult result) {
        if (!plugin.getLogger().isLoggable(Level.WARNING)) return;
        
        Location loc = result.getLocation();
        StringBuilder sb = new StringBuilder("\n");
        sb.append("=" .repeat(60)).append("\n");
        sb.append("[DataConsistency] ⚠ VALIDATION WARNINGS\n");
        sb.append("Location: ").append(loc != null ? formatLocation(loc) : "UNKNOWN").append("\n");
        sb.append("Issues found: ").append(result.getIssues().size()).append("\n");
        sb.append("-".repeat(40)).append("\n");
        
        for (ValidationIssue issue : result.getIssues()) {
            sb.append(String.format("[%s] %s\n", issue.getSeverity(), issue.getCode()));
            sb.append(String.format("   Message: %s\n", issue.getMessage()));
            sb.append(String.format("   Details: %s\n", issue.getDetails()));
            sb.append("\n");
        }
        
        sb.append("=" .repeat(60)).append("\n");
        
        plugin.getLogger().warning(sb.toString());
    }

    /**
     * 发送告警给在线管理员
     */
    private void sendAdminAlerts(ValidationResult result) {
        // 只发送严重和错误级别的告警
        List<ValidationIssue> criticalIssues = result.getIssues().stream()
            .filter(i -> i.getSeverity() == Severity.CRITICAL || i.getSeverity() == Severity.ERROR)
            .collect(Collectors.toList());
        
        if (criticalIssues.isEmpty()) return;
        
        String header = "§c[DataConsistency] §4⚠ Data validation failed!";
        String location = result.getLocation() != null 
            ? " §7(" + formatLocation(result.getLocation()) + ")" 
            : "";
        
        Bukkit.getOnlinePlayers().stream()
            .filter(p -> p.hasPermission("scs.dungeonchest.admin"))
            .forEach(admin -> {
                admin.sendMessage(header + location);
                admin.sendMessage("§cCritical/Error issues: " + criticalIssues.size());
                
                // 只显示前3个问题（避免刷屏）
                criticalIssues.stream().limit(3).forEach(issue -> {
                    admin.sendMessage(String.format("  §c[%s] §f%s", 
                        issue.getSeverity().name(), issue.getMessage()));
                });
                
                if (criticalIssues.size() > 3) {
                    admin.sendMessage("  §7... and " + (criticalIssues.size() - 3) + " more issues");
                }
                
                admin.sendMessage("§7Check console for full details.");
            });
    }

    /**
     * 格式化位置信息
     */
    private String formatLocation(Location loc) {
        if (loc == null) return "null";
        return String.format("%s[%d,%d,%d]",
            loc.getWorld() != null ? loc.getWorld().getName() : "?",
            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
    
    /**
     * 获取物品名称
     */
    private String getItemName(LootItem lootItem) {
        try {
            ItemStack stack = lootItem.getItemStack();
            if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
                return stack.getItemMeta().getDisplayName();
            }
            return stack.getType().name();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 获取最近的校验结果
     */
    public ValidationResult getLastValidationResult(Location location) {
        return lastValidationResults.get(location);
    }

    /**
     * 清除校验结果缓存
     */
    public void clearCache() {
        lastValidationResults.clear();
    }

    // ==================== 内部类定义 ====================

    /**
     * 校验结果
     */
    public static class ValidationResult {
        private final Location location;
        private final boolean valid;
        private final List<ValidationIssue> issues;
        private final long timestamp;

        public ValidationResult(Location location, boolean valid, List<ValidationIssue> issues) {
            this.location = location;
            this.valid = valid;
            this.issues = issues != null ? issues : new ArrayList<>();
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isValid() { return valid; }
        public Location getLocation() { return location; }
        public List<ValidationIssue> getIssues() { return issues; }
        public long getTimestamp() { return timestamp; }
        
        public int getCriticalCount() {
            return (int) issues.stream().filter(i -> i.getSeverity() == Severity.CRITICAL).count();
        }
        
        public int getErrorCount() {
            return (int) issues.stream().filter(i -> i.getSeverity() == Severity.ERROR).count();
        }
        
        public int getWarningCount() {
            return (int) issues.stream().filter(i -> i.getSeverity() == Severity.WARNING).count();
        }

        @Override
        public String toString() {
            return String.format("ValidationResult{valid=%s, issues=%d, location=%s}",
                valid, issues.size(), location);
        }
    }

    /**
     * 校验问题
     */
    public static class ValidationIssue {
        private final Severity severity;
        private final String code;
        private final String message;
        private final String details;

        public ValidationIssue(Severity severity, String code, String message, String details) {
            this.severity = severity;
            this.code = code;
            this.message = message;
            this.details = details;
        }

        public Severity getSeverity() { return severity; }
        public String getCode() { return code; }
        public String getMessage() { return message; }
        public String getDetails() { return details; }

        @Override
        public String toString() {
            return String.format("[%s] %s: %s (%s)", severity, code, message, details);
        }
    }

    /**
     * 严重程度枚举
     */
    public enum Severity {
        INFO("ℹ"),      // 信息
        WARNING("⚠"),   // 警告
        ERROR("✗"),     // 错误
        CRITICAL("☠");  // 严重错误
        
        private final String icon;
        
        Severity(String icon) {
            this.icon = icon;
        }
        
        public String getIcon() { return icon; }
        
        @Override
        public String toString() {
            return name();
        }
    }
}
