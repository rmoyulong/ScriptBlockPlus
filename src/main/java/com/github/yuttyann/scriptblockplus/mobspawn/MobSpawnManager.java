package com.github.yuttyann.scriptblockplus.mobspawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.github.yuttyann.scriptblockplus.ScriptBlock;
import com.github.yuttyann.scriptblockplus.FoliaCompat;
import com.github.yuttyann.scriptblockplus.mobspawn.MobSpawnData.MobEntry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 副本生物召唤管理器
 * 
 * 核心功能：
 * 1. 管理所有召唤点的生命周期
 * 2. 定时检测玩家是否在召唤点附近
 * 3. 当玩家在附近时自动生成配置的副本怪
 * 4. 每次生成1~3只，间隔15秒，首次生成无读秒
 * 5. 检测范围内生物数量，超过10只则取消下一轮生成
 * 6. 数据持久化到JSON文件
 */
public class MobSpawnManager {

    private final ScriptBlock plugin;
    private final Map<Location, MobSpawnData> activeSpawnPoints;
    private final Gson gson;

    private File dataFile;
    private int checkTaskId = -1;

    // 运行时追踪：每个召唤点当前已生成的实体UUID
    private final Map<Location, Set<UUID>> spawnedEntities;

    // 标签前缀：用于标识此插件生成的生物
    public static final String ENTITY_TAG = "SBP_MobSpawn";

    // MythicMobs 集成
    private boolean mythicMobsEnabled = false;
    private Object mythicMobsAPI = null; // io.lumine.mythic.bukkit.MythicBukkit instanced

    public MobSpawnManager(ScriptBlock plugin) {
        this.plugin = plugin;
        this.activeSpawnPoints = new ConcurrentHashMap<>();
        this.spawnedEntities = new ConcurrentHashMap<>();
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        initialize();
    }

    private void initialize() {
        File dataDir = new File(plugin.getDataFolder(), "mobspawn");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        dataFile = new File(dataDir, "data.json");

        // 检测 MythicMobs 插件
        detectMythicMobs();

        loadData();
        startCheckTask();

        plugin.getLogger().info("[MobSpawn] System initialized with " + activeSpawnPoints.size() + " spawn points" +
                (mythicMobsEnabled ? " [MythicMobs: Enabled]" : " [MythicMobs: Not found]"));
    }

    /**
     * 检测 MythicMobs 插件是否已安装
     */
    private void detectMythicMobs() {
        Plugin mmPlugin = Bukkit.getPluginManager().getPlugin("MythicMobs");
        if (mmPlugin != null && mmPlugin.isEnabled()) {
            try {
                // 尝试获取 MythicMobs API 实例
                Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
                mythicMobsAPI = mythicBukkitClass.getMethod("inst").invoke(null);
                mythicMobsEnabled = true;
                plugin.getLogger().info("[MobSpawn] MythicMobs v" + mmPlugin.getDescription().getVersion() + " detected, API enabled");
            } catch (Exception e) {
                // API 不可用，但插件存在，使用控制台命令回退
                mythicMobsEnabled = true;
                mythicMobsAPI = null;
                plugin.getLogger().info("[MobSpawn] MythicMobs detected, using console command fallback");
            }
        } else {
            mythicMobsEnabled = false;
            mythicMobsAPI = null;
        }
    }

    /**
     * 检查 MythicMobs 是否可用
     */
    public boolean isMythicMobsEnabled() {
        return mythicMobsEnabled;
    }

    // ==================== 数据持久化 ====================

    private void loadData() {
        if (!dataFile.exists()) {
            plugin.getLogger().info("[MobSpawn] No existing data file, starting fresh");
            return;
        }

        try (FileReader reader = new FileReader(dataFile)) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dataList = gson.fromJson(reader, List.class);
            if (dataList != null) {
                int successCount = 0;
                for (Map<String, Object> map : dataList) {
                    try {
                        MobSpawnData data = MobSpawnData.fromMap(map);
                        if (data != null) {
                            activeSpawnPoints.put(data.getLocation(), data);
                            spawnedEntities.put(data.getLocation(), ConcurrentHashMap.newKeySet());
                            successCount++;
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "[MobSpawn] Failed to parse spawn data", e);
                    }
                }
                plugin.getLogger().info("[MobSpawn] Loaded " + successCount + "/" + dataList.size() + " spawn points");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[MobSpawn] Failed to load data", e);
        }
    }

    public synchronized void saveData() {
        if (activeSpawnPoints.isEmpty()) {
            return;
        }

        File tempFile = new File(dataFile.getAbsolutePath() + ".tmp");

        try {
            List<Map<String, Object>> dataList = new ArrayList<>();
            for (MobSpawnData data : activeSpawnPoints.values()) {
                dataList.add(data.toMap());
            }

            String json = gson.toJson(dataList);

            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(json);
                writer.flush();
            }

            if (dataFile.exists()) {
                dataFile.delete();
            }

            if (!tempFile.renameTo(dataFile)) {
                try (FileWriter directWriter = new FileWriter(dataFile, false)) {
                    directWriter.write(json);
                    directWriter.flush();
                }
                if (tempFile.exists()) {
                    tempFile.delete();
                }
            }

            plugin.getLogger().fine("[MobSpawn] Data saved (" + activeSpawnPoints.size() + " spawn points)");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[MobSpawn] Failed to save data", e);
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    // ==================== 核心：定时检测任务 ====================

    /**
     * 启动定时检测任务
     * 每秒（20tick）检查一次所有召唤点
     */
    private void startCheckTask() {
        checkTaskId = ScriptBlock.getScheduler().run(() -> {
            checkAllSpawnPoints();
        }, 0L, 20L).getTaskId();
    }

    /**
     * 检查所有召唤点
     * 1. 检测玩家是否在附近
     * 2. 判断是否需要生成
     * 3. 检测生物数量上限
     */
    private void checkAllSpawnPoints() {
        for (MobSpawnData spawnData : activeSpawnPoints.values()) {
            try {
                Location loc = spawnData.getLocation();
                if (loc.getWorld() == null) continue;

                // 检测附近是否有玩家
                boolean hasPlayerNearby = hasPlayerNearby(spawnData);

                if (hasPlayerNearby && !spawnData.isActive()) {
                    // 玩家刚进入范围 -> 激活并立即首次生成
                    spawnData.setActive(true);
                    plugin.getLogger().fine("[MobSpawn] Player detected near " + spawnData.getSpawnName() + ", activating");
                } else if (!hasPlayerNearby && spawnData.isActive()) {
                    // 玩家离开范围 -> 停止生成，重置状态
                    spawnData.resetSpawnState();
                    plugin.getLogger().fine("[MobSpawn] No players near " + spawnData.getSpawnName() + ", deactivating");
                }

                if (spawnData.isActive() && spawnData.shouldSpawn()) {
                    // 在区域线程中执行生成
                    FoliaCompat.runAtLocation(plugin, loc, () -> {
                        performSpawn(spawnData);
                    }, 1L);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[MobSpawn] Error checking spawn point " + spawnData.getSpawnName(), e);
            }
        }
    }

    /**
     * 检测召唤点附近是否有玩家
     */
    private boolean hasPlayerNearby(MobSpawnData spawnData) {
        Location center = spawnData.getLocation();
        World world = center.getWorld();
        if (world == null) return false;

        double radius = spawnData.getDetectionRadius();
        double radiusSq = radius * radius;

        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(center) <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    // ==================== 核心生成逻辑 ====================

    /**
     * 执行一次怪物生成
     * 
     * 流程：
     * 1. 清理已死亡/消失的实体引用
     * 2. 统计当前范围内的生物数量
     * 3. 如果超过上限则取消生成
     * 4. 根据权重随机选择怪物类型
     * 5. 生成1~3只怪物（支持原生EntityType和MythicMobs）
     * 6. 标记生成完成
     */
    private void performSpawn(MobSpawnData spawnData) {
        Location center = spawnData.getLocation();
        World world = center.getWorld();
        if (world == null) return;

        // 1. 清理无效的实体引用
        cleanupDeadEntities(spawnData);

        // 2. 统计范围内由本插件生成的生物数量
        int currentMobCount = countSpawnedMobs(spawnData);
        plugin.getLogger().fine("[MobSpawn] " + spawnData.getSpawnName() + " has " + currentMobCount + "/" + spawnData.getMaxMobCount() + " mobs");

        // 3. 数量验证：如果范围内生物 >= 上限，取消本轮生成
        if (currentMobCount >= spawnData.getMaxMobCount()) {
            plugin.getLogger().fine("[MobSpawn] " + spawnData.getSpawnName() + " mob limit reached (" + currentMobCount + "), skipping spawn");
            spawnData.markSpawned();
            return;
        }

        // 4. 计算本次生成数量
        int min = spawnData.getMinSpawnCount();
        int max = spawnData.getMaxSpawnCount();
        int spawnCount = min + (int) (Math.random() * (max - min + 1));

        // 确保不超过上限
        int remaining = spawnData.getMaxMobCount() - currentMobCount;
        spawnCount = Math.min(spawnCount, remaining);
        if (spawnCount <= 0) {
            spawnData.markSpawned();
            return;
        }

        // 5. 生成怪物
        Set<UUID> entitySet = spawnedEntities.computeIfAbsent(center, k -> ConcurrentHashMap.newKeySet());
        int actuallySpawned = 0;

        for (int i = 0; i < spawnCount; i++) {
            MobEntry entry = selectRandomMob(spawnData);
            if (entry == null) continue;

            // 概率判定
            if (Math.random() * 100 > entry.getSpawnProbability()) {
                continue;
            }

            // 在召唤点附近随机偏移位置生成
            Location spawnLoc = getRandomSpawnLocation(center, spawnData.getDetectionRadius());

            try {
                Entity entity = null;

                if (entry.isMythicMob()) {
                    // MythicMobs 怪物生成
                    entity = spawnMythicMob(entry.getMythicMobId(), spawnLoc);
                } else if (entry.getEntityType() != null) {
                    // 原生 Bukkit 怪物生成
                    entity = world.spawnEntity(spawnLoc, entry.getEntityType());
                }

                if (entity != null && entity instanceof LivingEntity) {
                    LivingEntity living = (LivingEntity) entity;

                    // 设置自定义名称（仅对原生怪物设置，MythicMobs怪物由配置控制）
                    if (!entry.isMythicMob() && entry.getCustomName() != null && !entry.getCustomName().isEmpty()) {
                        living.setCustomName(entry.getCustomName().replace('&', '\u00a7'));
                        living.setCustomNameVisible(true);
                    }

                    // 添加标签用于识别
                    living.addScoreboardTag(ENTITY_TAG);
                    living.addScoreboardTag(ENTITY_TAG + "_" + spawnData.getSpawnId().toString().substring(0, 8));
                }

                if (entity != null) {
                    entitySet.add(entity.getUniqueId());
                    actuallySpawned++;
                }
            } catch (Exception e) {
                String mobId = entry.isMythicMob() ? "mm:" + entry.getMythicMobId() : entry.getEntityType().name();
                plugin.getLogger().log(Level.WARNING, "[MobSpawn] Failed to spawn " + mobId, e);
            }
        }

        // 6. 标记生成完成
        spawnData.markSpawned();

        if (actuallySpawned > 0) {
            plugin.getLogger().fine(String.format("[MobSpawn] Spawned %d mobs at %s (total: %d)",
                    actuallySpawned, spawnData.getSpawnName(), currentMobCount + actuallySpawned));
        }
    }

    /**
     * 生成 MythicMobs 怪物
     * 
     * 优先使用 MythicMobs API，如果 API 不可用则回退到控制台命令
     */
    private Entity spawnMythicMob(String mobId, Location location) {
        // 方法1：使用 MythicMobs API（反射调用，避免硬依赖）
        if (mythicMobsAPI != null) {
            try {
                // MythicBukkit.inst().getMobManager().spawnMob(mobId, location)
                Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
                Object mobManager = mythicBukkitClass.getMethod("getMobManager").invoke(mythicMobsAPI);
                
                // spawnMob 返回 ActiveMob 对象
                Class<?> mobManagerClass = Class.forName("io.lumine.mythic.core.mobs.MobManager");
                Object activeMob = mobManagerClass.getMethod("spawnMob", String.class, Location.class)
                        .invoke(mobManager, mobId, location);
                
                if (activeMob != null) {
                    // ActiveMob.getEntity().getBukkitEntity() 获取 Bukkit 实体
                    Class<?> activeMobClass = Class.forName("io.lumine.mythic.core.mobs.ActiveMob");
                    Object mythicEntity = activeMobClass.getMethod("getEntity").invoke(activeMob);
                    
                    if (mythicEntity != null) {
                        Class<?> mythicEntityClass = Class.forName("io.lumine.mythic.core.mobs.MythicEntity");
                        // 尝试获取 Bukkit 实体
                        try {
                            Object bukkitEntity = mythicEntityClass.getMethod("getBukkitEntity").invoke(mythicEntity);
                            if (bukkitEntity instanceof Entity) {
                                return (Entity) bukkitEntity;
                            }
                        } catch (NoSuchMethodException e) {
                            // 回退：通过 getEntity() 获取
                        }
                        
                        // 另一种方式：MythicEntity 可能实现了 Bukkit 的 Entity 接口
                        if (mythicEntity instanceof Entity) {
                            return (Entity) mythicEntity;
                        }
                    }
                }
                
                plugin.getLogger().fine("[MobSpawn] MythicMobs API spawned " + mobId + " but couldn't get Bukkit entity, falling back to tag search");
            } catch (Exception e) {
                plugin.getLogger().fine("[MobSpawn] MythicMobs API call failed for " + mobId + ": " + e.getMessage() + ", falling back to console command");
            }
        }

        // 方法2：使用控制台命令回退
        if (mythicMobsEnabled) {
            return spawnMythicMobViaCommand(mobId, location);
        }

        plugin.getLogger().warning("[MobSpawn] MythicMobs is not available! Cannot spawn mm:" + mobId);
        return null;
    }

    /**
     * 通过控制台命令生成 MythicMobs 怪物
     * 命令格式：mm mobs spawn <mobId> <x> <y> <z> <world>
     */
    private Entity spawnMythicMobViaCommand(String mobId, Location location) {
        World world = location.getWorld();
        if (world == null) return null;

        // 在生成前记录范围内的实体UUID
        Set<UUID> beforeEntities = new HashSet<>();
        double radiusSq = 4.0 * 4.0;
        for (Entity e : world.getEntities()) {
            if (e instanceof LivingEntity && !(e instanceof Player)) {
                if (e.getLocation().distanceSquared(location) <= radiusSq) {
                    beforeEntities.add(e.getUniqueId());
                }
            }
        }

        // 构建包含完整位置信息的命令
        // 格式: /mm m spawn <mobId> <amount> <world,x,y,z,yaw,pitch>
        // 或: /mm m spawn <mobId> <amount> -p <player> (在玩家位置生成)
        String worldName = world.getName();
        String cleanMobId = mobId.replace("<", "").replace(">", "");

        plugin.getLogger().info("[MobSpawn] Attempting to spawn mm:" + cleanMobId + " at world: " + worldName);

        // 方法1: 尝试通过有权限的玩家执行命令 (MythicMobs 在玩家执行时能正确处理位置)
        // 查找附近有 OP 权限的玩家
        Player privilegedPlayer = findPrivilegedPlayer(location, 50.0);
        if (privilegedPlayer != null) {
            // 使用 -s 参数静默执行，避免刷屏
            String cmd = String.format("mm m spawn -s %s 1 %s,%.5f,%.5f,%.5f,0.0,0.0",
                    cleanMobId, worldName, location.getX(), location.getY(), location.getZ());
            plugin.getLogger().info("[MobSpawn] Executing via privileged player " + privilegedPlayer.getName() + ": " + cmd);

            // 临时给予 OP 权限执行命令（参考 ScriptBlock @bypassop 逻辑）
            boolean wasOp = privilegedPlayer.isOp();
            boolean commandResult = false;
            try {
                if (!wasOp) {
                    privilegedPlayer.setOp(true);
                }
                commandResult = Bukkit.dispatchCommand(privilegedPlayer, cmd);
            } finally {
                if (!wasOp) {
                    privilegedPlayer.setOp(false);
                }
            }

            if (commandResult) {
                plugin.getLogger().info("[MobSpawn] Command executed successfully via privileged player");
                // 通过延迟查找来追踪生成的实体
                return findSpawnedEntityWithDelay(location, beforeEntities, world);
            }
        }

        // 方法2: 通过控制台执行 (作为备用)
        plugin.getLogger().info("[MobSpawn] No player nearby, trying console command");
        String[] commands = {
            String.format("mm m spawn %s 1 %s,%.5f,%.5f,%.5f,0.0,0.0",
                    cleanMobId, worldName, location.getX(), location.getY(), location.getZ()),
            String.format("mm mobs spawn %s %s,%.5f,%.5f,%.5f,0.0,0.0",
                    cleanMobId, worldName, location.getX(), location.getY(), location.getZ())
        };

        boolean commandSucceeded = false;
        for (String cmd : commands) {
            plugin.getLogger().info("[MobSpawn] Trying console command: " + cmd);
            boolean result = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            if (result) {
                plugin.getLogger().info("[MobSpawn] Console command succeeded");
                commandSucceeded = true;
                break;
            }
        }

        if (!commandSucceeded) {
            plugin.getLogger().warning("[MobSpawn] Command dispatch returned false for: " + mobId);
        }

        // 查找新生成的实体（在范围内但之前不存在的）
        // 延迟1tick后查找更可靠，但在同一tick也尝试查找
        for (Entity e : world.getEntities()) {
            if (e instanceof LivingEntity && !(e instanceof Player)) {
                if (e.getLocation().distanceSquared(location) <= radiusSq && !beforeEntities.contains(e.getUniqueId())) {
                    return e;
                }
            }
        }

        // 如果同一tick没找到，延迟查找
        final Location loc = location;
        FoliaCompat.runAtLocation(plugin, loc, () -> {
            // 尝试在延迟后标记实体
            World w = loc.getWorld();
            if (w == null) return;
            for (Entity e : w.getEntities()) {
                if (e instanceof LivingEntity && !(e instanceof Player)) {
                    if (e.getLocation().distanceSquared(loc) <= radiusSq && !beforeEntities.contains(e.getUniqueId())) {
                        // 找到了新生成的实体，但无法返回给调用者
                        // 已在 performSpawn 中添加了标签，这里无需额外操作
                        break;
                    }
                }
            }
        }, 2L);

        return null;
    }

    /**
     * 查找指定位置附近有权限的玩家（优先选择有 OP 权限的玩家）
     */
    private Player findPrivilegedPlayer(Location location, double maxDistance) {
        World world = location.getWorld();
        if (world == null) return null;

        double maxDistSq = maxDistance * maxDistance;
        Player privileged = null;
        double privilegedDistSq = Double.MAX_VALUE;
        boolean foundOp = false;

        for (Player player : world.getPlayers()) {
            double distSq = player.getLocation().distanceSquared(location);
            if (distSq > maxDistSq) continue;

            if (player.isOp()) {
                // 优先选择最近的 OP 玩家
                if (!foundOp || distSq < privilegedDistSq) {
                    privileged = player;
                    privilegedDistSq = distSq;
                    foundOp = true;
                }
            } else if (!foundOp && privileged == null) {
                // 如果还没找到 OP 玩家，先记录最近的非 OP 玩家作为备用
                privileged = player;
                privilegedDistSq = distSq;
            }
        }
        return privileged;
    }

    /**
     * 立即查找新生成的实体
     */
    private Entity findSpawnedEntityImmediate(Location location, Set<UUID> beforeEntities, World world) {
        double radiusSq = 4.0 * 4.0;
        for (Entity e : world.getEntities()) {
            if (e instanceof LivingEntity && !(e instanceof Player)) {
                if (e.getLocation().distanceSquared(location) <= radiusSq && !beforeEntities.contains(e.getUniqueId())) {
                    return e;
                }
            }
        }
        return null;
    }

    /**
     * 延迟查找新生成的实体
     */
    private Entity findSpawnedEntityWithDelay(Location location, Set<UUID> beforeEntities, World world) {
        double radiusSq = 4.0 * 4.0;
        for (Entity e : world.getEntities()) {
            if (e instanceof LivingEntity && !(e instanceof Player)) {
                if (e.getLocation().distanceSquared(location) <= radiusSq && !beforeEntities.contains(e.getUniqueId())) {
                    return e;
                }
            }
        }

        // 如果立即查找没找到，延迟查找
        final Location loc = location;
        final Set<UUID> before = beforeEntities;
        FoliaCompat.runAtLocation(plugin, loc, () -> {
            World w = loc.getWorld();
            if (w == null) return;
            for (Entity e : w.getEntities()) {
                if (e instanceof LivingEntity && !(e instanceof Player)) {
                    if (e.getLocation().distanceSquared(loc) <= 4.0 * 4.0 && !before.contains(e.getUniqueId())) {
                        LivingEntity living = (LivingEntity) e;
                        living.addScoreboardTag(ENTITY_TAG);
                        break;
                    }
                }
            }
        }, 2L);

        return null;
    }

    /**
     * 根据权重随机选择一个怪物类型
     */
    private MobEntry selectRandomMob(MobSpawnData spawnData) {
        List<MobEntry> entries = spawnData.getMobEntries();
        if (entries.isEmpty()) return null;

        double totalWeight = entries.stream().mapToDouble(MobEntry::getWeight).sum();
        if (totalWeight <= 0) return entries.get(0);

        double random = Math.random() * totalWeight;
        double current = 0;
        for (MobEntry entry : entries) {
            current += entry.getWeight();
            if (random <= current) {
                return entry;
            }
        }
        return entries.get(entries.size() - 1);
    }

    /**
     * 在中心点附近随机偏移位置生成
     */
    private Location getRandomSpawnLocation(Location center, double radius) {
        double angle = Math.random() * 2 * Math.PI;
        double distance = Math.random() * radius;

        double offsetX = Math.cos(angle) * distance;
        double offsetZ = Math.sin(angle) * distance;

        Location spawnLoc = center.clone().add(offsetX, 0, offsetZ);

        // 确保生成位置在合理的高度（尝试找安全位置）
        World world = spawnLoc.getWorld();
        if (world != null) {
            int blockX = spawnLoc.getBlockX();
            int blockZ = spawnLoc.getBlockZ();

            // 从上往下找安全的Y坐标
            for (int y = Math.min(spawnLoc.getBlockY() + 5, world.getHighestBlockYAt(blockX, blockZ) + 1);
                 y >= spawnLoc.getBlockY() - 5; y--) {
                Location testLoc = new Location(world, blockX + 0.5, y, blockZ + 0.5);
                if (isSafeSpawnLocation(testLoc)) {
                    return testLoc;
                }
            }
        }

        // 回退到原始位置
        return center.clone().add(0.5, 0, 0.5);
    }

    /**
     * 检查位置是否安全可生成
     */
    private boolean isSafeSpawnLocation(Location loc) {
        if (loc.getWorld() == null) return false;
        org.bukkit.block.Block feet = loc.getBlock();
        org.bukkit.block.Block head = feet.getRelative(0, 1, 0);
        org.bukkit.block.Block below = feet.getRelative(0, -1, 0);

        // 脚下和头部必须是空气/可穿过的，下面必须是固体
        return !feet.getType().isSolid() && !head.getType().isSolid() && below.getType().isSolid();
    }

    // ==================== 生物数量统计 ====================

    /**
     * 清理已死亡/消失的实体引用
     */
    private void cleanupDeadEntities(MobSpawnData spawnData) {
        Set<UUID> entitySet = spawnedEntities.get(spawnData.getLocation());
        if (entitySet == null) return;

        World world = spawnData.getLocation().getWorld();
        if (world == null) return;

        entitySet.removeIf(uuid -> {
            // 检查实体是否还存在
            for (Entity entity : world.getEntities()) {
                if (entity.getUniqueId().equals(uuid)) {
                    return false; // 还活着
                }
            }
            return true; // 已不存在，移除引用
        });
    }

    /**
     * 统计召唤点范围内由本插件生成的存活生物数量
     * 
     * 使用双重验证：
     * 1. 通过spawnedEntities追踪的UUID
     * 2. 通过实体标签（scoreboard tag）验证
     */
    private int countSpawnedMobs(MobSpawnData spawnData) {
        Location center = spawnData.getLocation();
        World world = center.getWorld();
        if (world == null) return 0;

        double radius = spawnData.getDetectionRadius();
        double radiusSq = radius * radius;
        int count = 0;

        // 方法1：通过UUID追踪计数
        Set<UUID> entitySet = spawnedEntities.get(center);
        Set<UUID> aliveUuids = new HashSet<>();

        for (Entity entity : world.getEntities()) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                if (entity.getLocation().distanceSquared(center) <= radiusSq) {
                    // 检查标签是否属于此召唤点
                    String spawnTag = ENTITY_TAG + "_" + spawnData.getSpawnId().toString().substring(0, 8);
                    if (entity.getScoreboardTags().contains(spawnTag)) {
                        count++;
                        aliveUuids.add(entity.getUniqueId());
                    }
                }
            }
        }

        // 更新追踪集合（移除已死亡的）
        if (entitySet != null) {
            entitySet.retainAll(aliveUuids);
        }

        return count;
    }

    /**
     * 统计范围内所有非玩家生物数量（通用检测）
     * 作为备用验证手段
     */
    public int countAllNearbyMobs(Location center, double radius) {
        World world = center.getWorld();
        if (world == null) return 0;

        double radiusSq = radius * radius;
        int count = 0;

        for (Entity entity : world.getEntities()) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                if (entity.getLocation().distanceSquared(center) <= radiusSq) {
                    count++;
                }
            }
        }
        return count;
    }

    // ==================== API 方法 ====================

    /**
     * 创建新的召唤点
     */
    public boolean createSpawnPoint(Location location, String spawnName) {
        if (activeSpawnPoints.containsKey(location)) {
            return false;
        }

        MobSpawnData data = new MobSpawnData(location, spawnName);
        activeSpawnPoints.put(location, data);
        spawnedEntities.put(location, ConcurrentHashMap.newKeySet());
        saveData();

        plugin.getLogger().info("[MobSpawn] Created spawn point: " + spawnName);
        return true;
    }

    /**
     * 移除召唤点
     */
    public boolean removeSpawnPoint(Location location) {
        MobSpawnData removed = activeSpawnPoints.remove(location);
        if (removed != null) {
            // 清理该召唤点生成的所有生物
            killSpawnedMobs(removed);
            spawnedEntities.remove(location);
            saveData();

            plugin.getLogger().info("[MobSpawn] Removed spawn point: " + removed.getSpawnName());
            return true;
        }
        return false;
    }

    /**
     * 击杀指定召唤点的所有已生成生物
     */
    public void killSpawnedMobs(MobSpawnData spawnData) {
        Set<UUID> entitySet = spawnedEntities.get(spawnData.getLocation());
        if (entitySet == null) return;

        World world = spawnData.getLocation().getWorld();
        if (world == null) return;

        String spawnTag = ENTITY_TAG + "_" + spawnData.getSpawnId().toString().substring(0, 8);

        for (Entity entity : world.getEntities()) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                if (entity.getScoreboardTags().contains(spawnTag)) {
                    ((LivingEntity) entity).setHealth(0);
                }
            }
        }

        entitySet.clear();
    }

    public MobSpawnData getSpawnPoint(Location location) {
        return activeSpawnPoints.get(location);
    }

    public boolean isSpawnPoint(Location location) {
        return activeSpawnPoints.containsKey(location);
    }

    public Collection<MobSpawnData> getAllSpawnPoints() {
        return Collections.unmodifiableCollection(activeSpawnPoints.values());
    }

    public ScriptBlock getPlugin() {
        return plugin;
    }

    // ==================== 关闭/重载 ====================

    public void shutdown() {
        if (checkTaskId != -1) {
            try {
                Bukkit.getScheduler().cancelTask(checkTaskId);
            } catch (Exception ignored) {}
        }

        saveData();
        activeSpawnPoints.clear();
        spawnedEntities.clear();
        mythicMobsAPI = null;
        mythicMobsEnabled = false;

        plugin.getLogger().info("[MobSpawn] System shutdown complete");
    }

    public void reload() {
        shutdown();

        // 重新初始化
        File dataDir = new File(plugin.getDataFolder(), "mobspawn");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        dataFile = new File(dataDir, "data.json");

        detectMythicMobs();
        loadData();
        startCheckTask();

        plugin.getLogger().info("[MobSpawn] Configuration reloaded with " + activeSpawnPoints.size() + " spawn points" +
                (mythicMobsEnabled ? " [MythicMobs: Enabled]" : " [MythicMobs: Not found]"));
    }
}
