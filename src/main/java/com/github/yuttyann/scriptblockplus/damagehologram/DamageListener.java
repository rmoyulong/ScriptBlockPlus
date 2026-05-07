package com.github.yuttyann.scriptblockplus.damagehologram;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 伤害事件监听器
 * 监听玩家攻击生物事件，生成伤害全息
 */
public class DamageListener implements Listener {

    private final DamageHologramManager hologramManager;

    public DamageListener(@NotNull DamageHologramManager hologramManager) {
        this.hologramManager = hologramManager;
    }

    /**
     * 监听玩家对生物造成伤害事件
     * 当玩家攻击LivingEntity时显示伤害全息
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(@NotNull EntityDamageByEntityEvent event) {
        // 只处理玩家造成的伤害
        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        // 只对生物显示伤害
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity victim = (LivingEntity) event.getEntity();
        Player attacker = (Player) event.getDamager();

        // 不显示自己对自己的伤害
        if (attacker.equals(victim)) {
            return;
        }

        // 获取伤害数值
        double damage = event.getFinalDamage();
        
        // 伤害太小不显示（防止显示0或负数）
        if (damage < 0.5) {
            return;
        }

        // 判断是否暴击
        boolean isCritical = isCriticalHit(attacker);

        // 显示伤害全息
        hologramManager.showDamageHologram(victim.getLocation(), damage, isCritical);
    }

    /**
     * 判断是否是暴击
     * 暴击条件：玩家在空中（下落后或跳跃中）
     */
    @SuppressWarnings("deprecation")
    private boolean isCriticalHit(@NotNull Player attacker) {
        // 暴击判定：玩家在空中（下落后或跳跃中）
        // 使用isOnGround检测（已弃用但仍可用）
        boolean onGround = attacker.isOnGround();
        
        // 不在地面上
        if (!onGround) {
            // Y方向速度小于一定值说明是下落中或刚跳起
            double yVelocity = attacker.getVelocity().getY();
            return yVelocity <= 0.1 && yVelocity >= -0.5;
        }
        
        return false;
    }
}
