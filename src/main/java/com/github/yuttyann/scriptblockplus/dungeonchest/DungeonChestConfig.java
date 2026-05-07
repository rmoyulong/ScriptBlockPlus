package com.github.yuttyann.scriptblockplus.dungeonchest;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import com.github.yuttyann.scriptblockplus.ScriptBlock;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class DungeonChestConfig {

    private final ScriptBlock plugin;
    private File configFile;
    private FileConfiguration config;
    private final Map<String, ChestConfig> chestConfigs;

    public static class ChestConfig {
        private final String name;
        private final String displayName;
        private final long refreshInterval; // milliseconds
        private final int maxItems;
        private final LootTable lootTable;
        private final boolean announceRefresh;
        private final String permission;

        public ChestConfig(String name, String displayName, long refreshInterval, int maxItems,
                          LootTable lootTable, boolean announceRefresh, String permission) {
            this.name = name;
            this.displayName = displayName;
            this.refreshInterval = refreshInterval;
            this.maxItems = maxItems;
            this.lootTable = lootTable;
            this.announceRefresh = announceRefresh;
            this.permission = permission;
        }

        public String getName() {
            return name;
        }

        public String getDisplayName() {
            return displayName;
        }

        public long getRefreshInterval() {
            return refreshInterval;
        }

        public int getMaxItems() {
            return maxItems;
        }

        public LootTable getLootTable() {
            return lootTable;
        }

        public boolean shouldAnnounceRefresh() {
            return announceRefresh;
        }

        public String getPermission() {
            return permission;
        }
    }

    public DungeonChestConfig(ScriptBlock plugin) {
        this.plugin = plugin;
        this.chestConfigs = new HashMap<>();
        loadConfig();
    }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "dungeonchests.yml");

        // ★★★ 修复问题：不要使用 saveResource() 覆盖用户配置 ★★★
        // 如果配置文件不存在，从 jar 内置资源复制到数据目录
        // 但如果存在，则保持用户配置不变
        if (!configFile.exists()) {
            // 检查 jar 内是否有内置资源
            InputStream defaultStream = plugin.getResource("dungeonchests.yml");
            if (defaultStream != null) {
                // 从 jar 复制到数据目录（只在首次创建时）
                try {
                    configFile.getParentFile().mkdirs();
                    Files.copy(defaultStream, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("[DungeonChest] Created default configuration file");
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "[DungeonChest] Failed to create config file", e);
                } finally {
                    try {
                        defaultStream.close();
                    } catch (IOException ignored) {}
                }
            } else {
                // jar 内没有内置资源，创建空配置文件
                try {
                    configFile.getParentFile().mkdirs();
                    configFile.createNewFile();
                    plugin.getLogger().warning("[DungeonChest] No default config found, created empty config");
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "[DungeonChest] Failed to create empty config", e);
                }
            }
        }

        // 加载配置文件
        config = YamlConfiguration.loadConfiguration(configFile);
        parseConfigs();
    }

    private void parseConfigs() {
        chestConfigs.clear();

        ConfigurationSection chestsSection = config.getConfigurationSection("chests");
        if (chestsSection == null) return;

        for (String key : chestsSection.getKeys(false)) {
            try {
                ConfigurationSection section = chestsSection.getConfigurationSection(key);
                if (section == null) continue;

                String name = key;
                String displayName = section.getString("display-name", key);
                long refreshInterval = section.getLong("refresh-interval", 300) * 1000L; // Convert seconds to ms
                int maxItems = section.getInt("max-items", 8);
                boolean announceRefresh = section.getBoolean("announce-refresh", true);
                String permission = section.getString("permission", "");

                LootTable lootTable = LootTable.fromConfig(name, section.getConfigurationSection("loot-table"));
                if (lootTable == null) {
                    plugin.getLogger().warning("Invalid loot table for chest config: " + name);
                    continue;
                }

                ChestConfig chestConfig = new ChestConfig(
                        name, displayName, refreshInterval, maxItems,
                        lootTable, announceRefresh, permission
                );

                chestConfigs.put(name, chestConfig);
                plugin.getLogger().info("Loaded dungeon chest config: " + name +
                        " (refresh: " + (refreshInterval / 1000) + "s, items: " + maxItems + ")");

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to parse chest config: " + key, e);
            }
        }
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save dungeon chest config", e);
        }
    }

    public ChestConfig getConfig(String name) {
        return chestConfigs.get(name);
    }

    public Map<String, ChestConfig> getAllConfigs() {
        return new HashMap<>(chestConfigs);
    }

    public boolean hasConfig(String name) {
        return chestConfigs.containsKey(name);
    }

    public FileConfiguration getRawConfig() {
        return config;
    }
}
