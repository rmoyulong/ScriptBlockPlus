package com.github.yuttyann.scriptblockplus;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

public class FoliaCompat {
    private static Boolean folia = null;
    private static boolean initialized = false;
    
    private static final String FOLIA_CLASS_NAME = "io.papermc.paper.threadedregions.RegionizedServer";

    /**
     * ★★★ 核心检测：判断是否为 Folia 服务器 ★★★
     * 
     * 多重检测：
     * 1. 检查 RegionizedServer 类是否存在
     * 2. 缓存检测结果
     */
    public static synchronized boolean isFolia() {
        if (folia == null) {
            try {
                Class.forName(FOLIA_CLASS_NAME);
                folia = true;
                
                if (!initialized) {
                    initialized = true;
                    Bukkit.getLogger().info("[ScriptBlockPlus] ✓ Detected Folia server - Using RegionScheduler API [FORCED MODE]");
                    logServerDetails();
                }
            } catch (ClassNotFoundException e) {
                folia = false;
                
                if (!initialized) {
                    initialized = true;
                    Bukkit.getLogger().info("[ScriptBlockPlus] ✓ Detected non-Folia server (Spigot/Paper) - Using Bukkit scheduler");
                }
            }
        }
        return folia;
    }

    /**
     * 记录服务器详情
     */
    private static void logServerDetails() {
        try {
            String serverVersion = Bukkit.getVersion();
            String bukkitVersion = Bukkit.getBukkitVersion();
            
            Bukkit.getLogger().info(String.format(
                "[ScriptBlockPlus] Server Details:\n" +
                "  - Version: %s\n" +
                "  - Bukkit API: %s\n" +
                "  - Java: %s",
                serverVersion,
                bukkitVersion,
                System.getProperty("java.version")
            ));
        } catch (Exception e) {
            // 忽略日志错误
        }
    }

    public static String getSchedulerMode() {
        return isFolia() ? "Folia (RegionScheduler) [FORCED]" : "Bukkit";
    }

    /*
     ★★★ 在指定位置执行任务 ★★★
      
     Folia 上：强制使用 RegionScheduler（失败则致命错误）
     非 Folia：使用 Bukkit 调度器
     延迟必须 > 0（GHolo: if(ticks <= 0) return run(...)）
    */
    public static void runAtLocation(Plugin plugin, Location location, Runnable task, long delayTicks) {
        if (isFolia()) {
            try {
                Method getRegionScheduler = Bukkit.class.getMethod("getRegionScheduler");
                Object scheduler = getRegionScheduler.invoke(null);
                
                final long actualDelay = delayTicks > 0 ? delayTicks : 1L;
                
                Method runDelayed = scheduler.getClass().getMethod(
                    "runDelayed", 
                    Plugin.class, 
                    Location.class,
                    Consumer.class,
                    long.class
                );
                
                Consumer<Object> wrappedTask = t -> task.run();
                runDelayed.invoke(scheduler, plugin, location, wrappedTask, actualDelay);
                
            } catch (Exception e) {
                throwFatalError(plugin, "runAtLocation", location.toString(), e);
            }
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    public static void runAtLocationRepeated(Plugin plugin, Location location, Runnable task, long delayTicks, long periodTicks) {
        if (isFolia()) {
            try {
                Method getRegionScheduler = Bukkit.class.getMethod("getRegionScheduler");
                Object scheduler = getRegionScheduler.invoke(null);
                
                final long actualDelay = delayTicks > 0 ? delayTicks : 1L;
                
                Method runAtFixedRate = scheduler.getClass().getMethod(
                    "runAtFixedRate", 
                    Plugin.class, 
                    Location.class,
                    Consumer.class,
                    long.class,
                    long.class
                );
                
                Consumer<Object> wrappedTask = t -> task.run();
                runAtFixedRate.invoke(scheduler, plugin, location, wrappedTask, actualDelay, periodTicks);
                
            } catch (Exception e) {
                throwFatalError(plugin, "runAtLocationRepeated", location.toString(), e);
            }
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
    }

    public static void runAtEntity(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (isFolia()) {
            throwFatalError(plugin, "runAtEntity", entity.getClass().getSimpleName(), 
                new UnsupportedOperationException("Entity scheduler not yet implemented for compile-time compatibility"));
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    public static void runAtEntityRepeated(Plugin plugin, Entity entity, Runnable task, long delayTicks, long periodTicks) {
        if (isFolia()) {
            throwFatalError(plugin, "runAtEntityRepeated", entity.getClass().getSimpleName(),
                new UnsupportedOperationException("Entity scheduler not yet implemented for compile-time compatibility"));
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
    }

    @SuppressWarnings("unchecked")
    public static CompletableFuture<Void> teleport(Player player, Location location) {
        if (isFolia()) {
            try {
                Method teleportAsync = player.getClass().getMethod(
                    "teleportAsync", 
                    Location.class, 
                    PlayerTeleportEvent.TeleportCause.class
                );
                return (CompletableFuture<Void>) teleportAsync.invoke(player, location, PlayerTeleportEvent.TeleportCause.PLUGIN);
            } catch (Exception e) {
                player.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN);
                return CompletableFuture.completedFuture(null);
            }
        } else {
            player.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN);
            return CompletableFuture.completedFuture(null);
        }
    }

    public static void runGlobal(Plugin plugin, Runnable task, long delayTicks) {
        if (isFolia()) {
            try {
                Method getGlobalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");
                Object scheduler = getGlobalRegionScheduler.invoke(null);
                
                if (delayTicks > 0) {
                    Method runDelayed = scheduler.getClass().getMethod(
                        "runDelayed", 
                        Plugin.class, 
                        Consumer.class,
                        long.class
                    );
                    
                    Consumer<Object> wrappedTask = t -> task.run();
                    runDelayed.invoke(scheduler, plugin, wrappedTask, delayTicks);
                } else {
                    Method execute = scheduler.getClass().getMethod(
                        "execute", 
                        Plugin.class, 
                        Runnable.class
                    );
                    execute.invoke(scheduler, plugin, task);
                }
            } catch (Exception e) {
                throwFatalError(plugin, "runGlobal", "global region", e);
            }
        } else {
            if (delayTicks > 0) {
                Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            } else {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        }
    }

    public static void runGlobalRepeated(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (isFolia()) {
            try {
                Method getGlobalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");
                Object scheduler = getGlobalRegionScheduler.invoke(null);
                
                final long actualDelay = delayTicks > 0 ? delayTicks : 1L;
                
                Method runAtFixedRate = scheduler.getClass().getMethod(
                    "runAtFixedRate", 
                    Plugin.class, 
                    Consumer.class,
                    long.class,
                    long.class
                );
                
                Consumer<Object> wrappedTask = t -> task.run();
                runAtFixedRate.invoke(scheduler, plugin, wrappedTask, actualDelay, periodTicks);
                
            } catch (Exception e) {
                throwFatalError(plugin, "runGlobalRepeated", "global region", e);
            }
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
    }

    public static void runAsync(Plugin plugin, Runnable task, long delayTicks) {
        if (isFolia()) {
            try {
                Method getAsyncScheduler = Bukkit.class.getMethod("getAsyncScheduler");
                Object scheduler = getAsyncScheduler.invoke(null);
                
                Consumer<Object> wrappedTask = t -> task.run();
                
                if (delayTicks > 0) {
                    Method runDelayed = scheduler.getClass().getMethod(
                        "runDelayed", 
                        Plugin.class, 
                        Consumer.class,
                        long.class,
                        java.util.concurrent.TimeUnit.class
                    );
                    runDelayed.invoke(scheduler, plugin, wrappedTask, delayTicks * 50L, java.util.concurrent.TimeUnit.MILLISECONDS);
                } else {
                    Method runNow = scheduler.getClass().getMethod(
                        "runNow", 
                        Plugin.class, 
                        Consumer.class
                    );
                    runNow.invoke(scheduler, plugin, wrappedTask);
                }
            } catch (Exception e) {
                throwFatalError(plugin, "runAsync", "async", e);
            }
        } else {
            if (delayTicks > 0) {
                Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
            } else {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            }
        }
    }

    public static void cancelTask(Plugin plugin, int taskId) {
        if (isFolia()) {
            try {
                Method getGlobalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");
                Object scheduler = getGlobalRegionScheduler.invoke(null);

                Method cancel = scheduler.getClass().getMethod("cancel", Plugin.class, int.class);
                cancel.invoke(scheduler, plugin, taskId);
            } catch (Exception e) {
                try {
                    Bukkit.getScheduler().cancelTask(taskId);
                } catch (Exception ignored) {}
            }
        } else {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    /**
     * 抛出致命错误（Folia API 失败时）
     * ⚠️ 阻止插件继续运行，避免静默回退到 Bukkit 导致线程死锁
     */
    private static void throwFatalError(Plugin plugin, String method, String target, Exception e) {
        String errorMsg = String.format(
            "[FoliaCompat] ✗ FATAL ERROR in %s(%s)\n" +
            "  Error: %s\n" +
            "  Server Mode: %s\n" +
            "\n" +
            "⚠️ CRITICAL: Cannot use Folia API on detected Folia server!\n" +
            "The plugin will NOT fall back to Bukkit to prevent thread deadlock.\n" +
            "\n" +
            "Possible causes:\n" +
            "  1. Server version mismatch with Paper API dependency in pom.xml\n" +
            "  2. Plugin loaded on non-Folia server but incorrectly detected as Folia\n" +
            "  3. Conflicting plugin modifying server internals\n" +
            "\n" +
            "Action required:\n" +
            "  - Verify running on actual Folia/Paper 1.21.1+ server\n" +
            "  - Check pom.xml paper-api version matches server version\n" +
            "  - Review startup logs for detection warnings",
            method,
            target,
            e.getMessage(),
            getSchedulerMode()
        );
        
        plugin.getLogger().log(Level.SEVERE, errorMsg);
        e.printStackTrace();
        
        throw new RuntimeException("[FoliaCompat] Fatal: Folia API unavailable on Folia server!", e);
    }
}
