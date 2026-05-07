package com.github.yuttyann.scriptblockplus.damagehologram;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;

import com.github.yuttyann.scriptblockplus.ScriptBlock;
import com.github.yuttyann.scriptblockplus.FoliaCompat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量化伤害全息管理器
 * 只在玩家攻击生物时显示2秒的临时伤害数值
 */
public class DamageHologramManager {

    private static final long HOLOGRAM_DURATION_TICKS = 40L; // 2秒 (40 ticks)
    private static final double HEIGHT_OFFSET = 1.5; // 全息高度偏移
    private static final double HORIZONTAL_SPREAD = 0.3; // 水平随机偏移范围

    private final Map<UUID, HologramData> activeHolograms;

    public DamageHologramManager() {
        this.activeHolograms = new ConcurrentHashMap<>();
    }

    /**
     * 显示伤害全息
     * @param location 生物位置
     * @param damage 伤害数值
     * @param isCritical 是否是暴击
     */
    public void showDamageHologram(@NotNull Location location, double damage, boolean isCritical) {
        Location holoLocation = createOffsetLocation(location);
        
        FoliaCompat.runAtLocation(
            ScriptBlock.getInstance(),
            location,
            () -> createHologram(holoLocation, damage, isCritical),
            1L
        );
    }

    /**
     * 创建带偏移的位置
     */
    private Location createOffsetLocation(@NotNull Location location) {
        double offsetX = (Math.random() - 0.5) * 2 * HORIZONTAL_SPREAD;
        double offsetZ = (Math.random() - 0.5) * 2 * HORIZONTAL_SPREAD;
        return location.clone().add(0.5 + offsetX, HEIGHT_OFFSET, 0.5 + offsetZ);
    }

    /**
     * 创建全息实体
     */
    private void createHologram(@NotNull Location location, double damage, boolean isCritical) {
        try {
            ArmorStand armorStand = (ArmorStand) location.getWorld().spawnEntity(
                location,
                EntityType.ARMOR_STAND
            );

            // 配置全息属性
            armorStand.setVisible(false);
            armorStand.setSmall(true);
            armorStand.setGravity(false);
            armorStand.setBasePlate(false);
            armorStand.setCustomNameVisible(true);
            armorStand.setPersistent(false);
            armorStand.setMarker(true); // 不与碰撞箱碰撞
            armorStand.setInvulnerable(true);
            
            // 设置伤害文字
            String damageText = formatDamage(damage, isCritical);
            armorStand.setCustomName(damageText);
            String cleanDamageText = formatCleanDamage(damageText);

            // 存储全息数据
            UUID holoId = armorStand.getUniqueId();
            HologramData data = new HologramData(armorStand, cleanDamageText);
            activeHolograms.put(holoId, data);

            // 使用 FoliaCompat 进行调度（线程安全）
            scheduleHologramRemoval(location, holoId);
            scheduleFloatingAnimation(location, holoId);

        } catch (Exception e) {
            ScriptBlock.getInstance().getLogger().warning("[DamageHologram] Failed to create: " + e.getMessage());
        }
    }

    /**
     * 调度全息移除（使用 FoliaCompat）
     */
    private void scheduleHologramRemoval(@NotNull Location location, @NotNull UUID holoId) {
        // 使用 FoliaCompat.runAtLocation 进行延迟调度
        FoliaCompat.runAtLocation(
            ScriptBlock.getInstance(),
            location,
            () -> {
                HologramData data = activeHolograms.remove(holoId);
                if (data != null && !data.armorStand.isDead()) {
                    try {
                        data.armorStand.remove();
                    } catch (Exception ignored) {}
                }
            },
            HOLOGRAM_DURATION_TICKS
        );
    }

    /**
     * 调度浮动动画效果（使用 FoliaCompat）
     */
    private void scheduleFloatingAnimation(@NotNull Location location, @NotNull UUID holoId) {
        // 向上浮动的动画 - 0.5秒内上浮0.3格
        int steps = 10;
        long stepDelay = HOLOGRAM_DURATION_TICKS / steps;
        double yIncrement = 0.03;
        Location baseLocation = location.clone();
        
        for (int i = 0; i < steps; i++) {
            final int step = i;
            final double yOffset = step * yIncrement;
            FoliaCompat.runAtLocation(
                ScriptBlock.getInstance(),
                location,
                () -> {
                    HologramData data = activeHolograms.get(holoId);
                    if (data != null && !data.armorStand.isDead()) {
                        try {
                            Location loc = data.armorStand.getLocation();
                            loc.setY(baseLocation.getY() + yOffset);
                            data.armorStand.teleport(loc);
                        } catch (Exception ignored) {}
                    }
                },
                step * stepDelay
            );
        }
        
        // 淡出效果 - 最后5 ticks 逐渐消失
        for (int i = 1; i <= 5; i++) {
            final int alpha = 255 - (i * 51);
            final int fadeStep = i;
            HologramData capturedData = activeHolograms.get(holoId);
            String cleanText = capturedData != null ? capturedData.cleanText : "";
            
            FoliaCompat.runAtLocation(
                ScriptBlock.getInstance(),
                location,
                () -> {
                    HologramData data = activeHolograms.get(holoId);
                    if (data != null && !data.armorStand.isDead()) {
                        try {
                            String baseColor = getColorFromAlpha(alpha);
                            data.armorStand.setCustomName(baseColor + cleanText);
                        } catch (Exception ignored) {}
                    }
                },
                HOLOGRAM_DURATION_TICKS - fadeStep
            );
        }
    }

    /**
     * 获取基于透明度的颜色
     */
    private String getColorFromAlpha(int alpha) {
        if (alpha > 200) return "§f"; // 白色
        if (alpha > 150) return "§e"; // 黄色
        if (alpha > 100) return "§6"; // 橙色
        if (alpha > 50) return "§c"; // 红色
        return "§4"; // 深红色
    }

    /**
     * 格式化伤害文本
     */
    @NotNull
    private String formatDamage(double damage, boolean isCritical) {
        StringBuilder sb = new StringBuilder();
        
        if (isCritical) {
            sb.append("§6§l✧ "); // 暴击前缀
        }
        
        // 根据伤害大小选择颜色
        String color;
        if (damage >= 100) {
            color = "§c§l"; // 红色 (高伤害)
        } else if (damage >= 50) {
            color = "§e§l"; // 黄色 (中等)
        } else if (damage >= 20) {
            color = "§f§l"; // 白色 (普通)
        } else {
            color = "§7§l"; // 灰色 (低伤害)
        }
        
        sb.append(color);
        sb.append((int) damage);
        
        if (isCritical) {
            sb.append(" §6§l✧"); // 暴击后缀
        }
        
        return sb.toString();
    }

    /**
     * 获取干净的伤害数值
     */
    @NotNull
    private String formatCleanDamage(String name) {
        if (name == null) return "";
        // 移除颜色代码
        return name.replaceAll("§[0-9a-fk-or]", "");
    }

    /**
     * 清理所有全息
     */
    public void clearAll() {
        // 使用 FoliaCompat 在每个世界清理
        for (HologramData data : activeHolograms.values()) {
            try {
                if (data.armorStand != null && !data.armorStand.isDead()) {
                    data.armorStand.remove();
                }
            } catch (Exception ignored) {}
        }
        activeHolograms.clear();
    }

    /**
     * 获取活跃全息数量
     */
    public int getActiveHologramCount() {
        return activeHolograms.size();
    }

    /**
     * 全息数据内部类
     */
    private static class HologramData {
        final ArmorStand armorStand;
        final String cleanText;

        HologramData(ArmorStand armorStand, String cleanText) {
            this.armorStand = armorStand;
            this.cleanText = cleanText;
        }
    }
}
