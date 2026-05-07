package com.github.yuttyann.scriptblockplus.mobspawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;

import java.util.*;

/**
 * 副本生物召唤点数据模型
 * 
 * 每个召唤点配置：
 * - 位置（方块/坐标）
 * - 可生成的怪物类型列表
 * - 检测半径（默认5m）
 * - 生成间隔（默认15秒）
 * - 每次生成数量范围（默认1~3只）
 * - 最大生物数量上限（默认10只）
 */
public class MobSpawnData {

    private final UUID spawnId;
    private final Location location;
    private String spawnName;

    // 怪物配置列表
    private List<MobEntry> mobEntries;

    // 生成参数
    private double detectionRadius;     // 检测玩家/生物的半径（方块数）
    private long spawnIntervalMs;       // 生成间隔（毫秒）
    private int minSpawnCount;          // 每次最小生成数量
    private int maxSpawnCount;          // 每次最大生成数量
    private int maxMobCount;           // 区域内最大生物数量

    // 运行时状态
    private transient boolean isActive;            // 是否有玩家在附近（正在生成）
    private transient long lastSpawnTime;          // 上次生成时间
    private transient boolean firstSpawnDone;       // 是否已完成首次生成

    public MobSpawnData(Location location, String spawnName) {
        this.spawnId = UUID.randomUUID();
        this.location = location;
        this.spawnName = spawnName;
        this.mobEntries = new ArrayList<>();
        this.detectionRadius = 5.0;
        this.spawnIntervalMs = 15000L; // 15秒
        this.minSpawnCount = 1;
        this.maxSpawnCount = 3;
        this.maxMobCount = 10;
        this.isActive = false;
        this.lastSpawnTime = 0;
        this.firstSpawnDone = false;
    }

    // ==================== 内部类：怪物配置条目 ====================

    /**
     * 单个怪物配置
     * 
     * 支持两种模式：
     * - 原生模式：使用 Bukkit EntityType（如 ZOMBIE、SKELETON）
     * - MythicMobs模式：使用 mm:<mobId> 格式（如 mm:SkeletonKing）
     */
    public static class MobEntry {
        private EntityType entityType;       // 原生怪物类型（可为null，当使用MythicMobs时）
        private String mythicMobId;          // MythicMobs怪物ID（可为null，当使用原生类型时）
        private String customName;          // 自定义名称（可为null）
        private double weight;              // 权重（用于随机选择）
        private double spawnProbability;    // 生成概率 0~100

        // ==================== 构造方法 ====================

        /** 原生怪物类型构造 */
        public MobEntry(EntityType entityType, double weight, double spawnProbability) {
            this.entityType = entityType;
            this.mythicMobId = null;
            this.weight = weight;
            this.spawnProbability = spawnProbability;
            this.customName = null;
        }

        /** 原生怪物类型构造（带自定义名称） */
        public MobEntry(EntityType entityType, double weight, double spawnProbability, String customName) {
            this.entityType = entityType;
            this.mythicMobId = null;
            this.weight = weight;
            this.spawnProbability = spawnProbability;
            this.customName = customName;
        }

        /** MythicMobs怪物构造 */
        public MobEntry(String mythicMobId, double weight, double spawnProbability, String customName, boolean isMythic) {
            this.entityType = null;
            this.mythicMobId = mythicMobId;
            this.weight = weight;
            this.spawnProbability = spawnProbability;
            this.customName = customName;
        }

        // ==================== 判断方法 ====================

        /** 是否为MythicMobs怪物 */
        public boolean isMythicMob() {
            return mythicMobId != null && !mythicMobId.isEmpty();
        }

        /** 获取显示用的怪物标识（用于命令输出等） */
        public String getMobIdentifier() {
            return isMythicMob() ? "mm:" + mythicMobId : (entityType != null ? entityType.name() : "UNKNOWN");
        }

        // ==================== Getter/Setter ====================

        public EntityType getEntityType() { return entityType; }
        public void setEntityType(EntityType entityType) { this.entityType = entityType; }

        public String getMythicMobId() { return mythicMobId; }
        public void setMythicMobId(String mythicMobId) { this.mythicMobId = mythicMobId; }

        public double getWeight() { return weight; }
        public void setWeight(double weight) { this.weight = weight; }

        public double getSpawnProbability() { return spawnProbability; }
        public void setSpawnProbability(double spawnProbability) { this.spawnProbability = spawnProbability; }

        public String getCustomName() { return customName; }
        public void setCustomName(String customName) { this.customName = customName; }

        // ==================== 序列化 ====================

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            if (isMythicMob()) {
                map.put("mythicMobId", mythicMobId);
            } else if (entityType != null) {
                map.put("entityType", entityType.name());
            }
            map.put("weight", weight);
            map.put("spawnProbability", spawnProbability);
            if (customName != null) {
                map.put("customName", customName);
            }
            return map;
        }

        public static MobEntry fromMap(Map<String, Object> map) {
            try {
                double weight = toDouble(map.get("weight"), 1.0);
                double prob = toDouble(map.get("spawnProbability"), 100.0);
                String name = (String) map.get("customName");

                // 优先解析 MythicMobs ID
                String mmId = (String) map.get("mythicMobId");
                if (mmId != null && !mmId.isEmpty()) {
                    return new MobEntry(mmId, weight, prob, name, true);
                }

                // 回退到原生 EntityType
                String typeName = (String) map.get("entityType");
                if (typeName != null && typeName.startsWith("mm:")) {
                    // 兼容旧格式：entityType 字段存储了 mm:xxx
                    return new MobEntry(typeName.substring(3), weight, prob, name, true);
                }
                EntityType type = EntityType.valueOf(typeName);
                return new MobEntry(type, weight, prob, name);
            } catch (Exception e) {
                return null;
            }
        }

        private static double toDouble(Object val, double def) {
            if (val == null) return def;
            if (val instanceof Number) return ((Number) val).doubleValue();
            try { return Double.parseDouble(val.toString()); } catch (Exception e) { return def; }
        }
    }

    // ==================== Getter/Setter ====================

    public UUID getSpawnId() { return spawnId; }
    public Location getLocation() { return location; }
    public String getSpawnName() { return spawnName; }
    public void setSpawnName(String spawnName) { this.spawnName = spawnName; }

    public List<MobEntry> getMobEntries() { return Collections.unmodifiableList(mobEntries); }

    public void setMobEntries(List<MobEntry> entries) {
        this.mobEntries = entries != null ? new ArrayList<>(entries) : new ArrayList<>();
    }

    public void addMobEntry(MobEntry entry) {
        this.mobEntries.add(entry);
    }

    public void removeMobEntry(int index) {
        if (index >= 0 && index < mobEntries.size()) {
            mobEntries.remove(index);
        }
    }

    public double getDetectionRadius() { return detectionRadius; }
    public void setDetectionRadius(double detectionRadius) { this.detectionRadius = detectionRadius; }

    public long getSpawnIntervalMs() { return spawnIntervalMs; }
    public void setSpawnIntervalMs(long spawnIntervalMs) { this.spawnIntervalMs = spawnIntervalMs; }

    public int getMinSpawnCount() { return minSpawnCount; }
    public void setMinSpawnCount(int minSpawnCount) { this.minSpawnCount = minSpawnCount; }

    public int getMaxSpawnCount() { return maxSpawnCount; }
    public void setMaxSpawnCount(int maxSpawnCount) { this.maxSpawnCount = maxSpawnCount; }

    public int getMaxMobCount() { return maxMobCount; }
    public void setMaxMobCount(int maxMobCount) { this.maxMobCount = maxMobCount; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { this.isActive = active; }

    public long getLastSpawnTime() { return lastSpawnTime; }
    public void setLastSpawnTime(long lastSpawnTime) { this.lastSpawnTime = lastSpawnTime; }

    public boolean isFirstSpawnDone() { return firstSpawnDone; }
    public void setFirstSpawnDone(boolean firstSpawnDone) { this.firstSpawnDone = firstSpawnDone; }

    /**
     * 检查是否应该进行下一轮生成
     * - 首次生成：玩家进入范围后立即执行
     * - 后续生成：距离上次生成 >= spawnIntervalMs
     */
    public boolean shouldSpawn() {
        if (!isActive) return false;
        if (!firstSpawnDone) return true;
        return System.currentTimeMillis() - lastSpawnTime >= spawnIntervalMs;
    }

    /**
     * 标记生成完成
     */
    public void markSpawned() {
        this.lastSpawnTime = System.currentTimeMillis();
        if (!firstSpawnDone) {
            this.firstSpawnDone = true;
        }
    }

    /**
     * 玩家离开范围时重置状态（下次进入重新首次生成）
     */
    public void resetSpawnState() {
        this.isActive = false;
        this.firstSpawnDone = false;
        this.lastSpawnTime = 0;
    }

    // ==================== 序列化 ====================

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("spawnId", spawnId.toString());
        map.put("world", location.getWorld().getName());
        map.put("x", location.getX());
        map.put("y", location.getY());
        map.put("z", location.getZ());
        map.put("spawnName", spawnName);
        map.put("detectionRadius", detectionRadius);
        map.put("spawnIntervalMs", spawnIntervalMs);
        map.put("minSpawnCount", minSpawnCount);
        map.put("maxSpawnCount", maxSpawnCount);
        map.put("maxMobCount", maxMobCount);

        List<Map<String, Object>> entriesList = new ArrayList<>();
        for (MobEntry entry : mobEntries) {
            entriesList.add(entry.toMap());
        }
        map.put("mobEntries", entriesList);

        return map;
    }

    @SuppressWarnings("unchecked")
    public static MobSpawnData fromMap(Map<String, Object> map) {
        if (map == null) return null;

        try {
            String worldName = (String) map.get("world");
            if (worldName == null) return null;

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                Bukkit.getLogger().warning("[MobSpawn] World '" + worldName + "' not loaded, skipping spawn point");
                return null;
            }

            double x = toDouble(map.get("x"), 0.0);
            double y = toDouble(map.get("y"), 0.0);
            double z = toDouble(map.get("z"), 0.0);

            Location loc = new Location(world, x, y, z);
            String name = (String) map.get("spawnName");
            if (name == null) name = "unnamed";

            MobSpawnData data = new MobSpawnData(loc, name);

            data.detectionRadius = toDouble(map.get("detectionRadius"), 5.0);
            data.spawnIntervalMs = (long) toDouble(map.get("spawnIntervalMs"), 15000.0);
            data.minSpawnCount = (int) toDouble(map.get("minSpawnCount"), 1.0);
            data.maxSpawnCount = (int) toDouble(map.get("maxSpawnCount"), 3.0);
            data.maxMobCount = (int) toDouble(map.get("maxMobCount"), 10.0);

            // 解析怪物列表
            List<Object> rawEntries = (List<Object>) map.get("mobEntries");
            if (rawEntries != null) {
                for (Object obj : rawEntries) {
                    if (obj instanceof Map) {
                        MobEntry entry = MobEntry.fromMap((Map<String, Object>) obj);
                        if (entry != null) {
                            data.mobEntries.add(entry);
                        }
                    }
                }
            }

            return data;
        } catch (Exception e) {
            Bukkit.getLogger().warning("[MobSpawn] Failed to deserialize spawn data: " + e.getMessage());
            return null;
        }
    }

    private static double toDouble(Object val, double def) {
        if (val == null) return def;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return def; }
    }

    @Override
    public String toString() {
        return "MobSpawnData{" +
                "name='" + spawnName + '\'' +
                ", loc=" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() +
                ", mobs=" + mobEntries.size() +
                ", active=" + isActive +
                '}';
    }
}
