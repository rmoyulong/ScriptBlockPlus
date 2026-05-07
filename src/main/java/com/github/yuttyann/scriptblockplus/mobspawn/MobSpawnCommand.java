package com.github.yuttyann.scriptblockplus.mobspawn;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import com.github.yuttyann.scriptblockplus.ScriptBlock;
import com.github.yuttyann.scriptblockplus.mobspawn.MobSpawnData.MobEntry;

import java.util.Collection;

/**
 * 副本生物召唤点命令处理器
 * 
 * 命令：
 * /mobspawn create <名称>                          - 创建召唤点（站在方块上或看向方块）
 * /mobspawn remove                                 - 移除召唤点（看向方块）
 * /mobspawn addmob <怪物类型> [权重] [概率%] [自定义名] - 添加怪物配置
 * /mobspawn removemob <索引>                        - 移除怪物配置
 * /mobspawn listmobs                               - 列出当前召唤点的怪物配置
 * /mobspawn set radius <数值>                       - 设置检测半径
 * /mobspawn set interval <秒数>                     - 设置生成间隔
 * /mobspawn set mincount <数值>                     - 设置最小生成数
 * /mobspawn set maxcount <数值>                     - 设置最大生成数
 * /mobspawn set maxmobs <数值>                      - 设置最大生物数量
 * /mobspawn list                                   - 列出所有召唤点
 * /mobspawn info                                   - 查看当前召唤点详情
 * /mobspawn killmobs                               - 击杀当前召唤点的所有生物
 * /mobspawn reload                                 - 重载配置
 * /mobspawn save                                   - 保存数据
 */
public class MobSpawnCommand implements CommandExecutor {

    @SuppressWarnings("unused")
    private final ScriptBlock plugin;
    private final MobSpawnManager manager;

    private static final String ADMIN_PERMISSION = "scriptblockplus.mobspawn.admin";

    public MobSpawnCommand(ScriptBlock plugin, MobSpawnManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "[MobSpawn] You don't have permission!");
            sender.sendMessage(ChatColor.GRAY + "Required: " + ADMIN_PERMISSION);
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create":
                handleCreate(sender, args);
                break;
            case "remove":
                handleRemove(sender);
                break;
            case "addmob":
                handleAddMob(sender, args);
                break;
            case "removemob":
                handleRemoveMob(sender, args);
                break;
            case "listmobs":
                handleListMobs(sender);
                break;
            case "set":
                handleSet(sender, args);
                break;
            case "list":
                handleList(sender);
                break;
            case "info":
                handleInfo(sender);
                break;
            case "killmobs":
                handleKillMobs(sender);
                break;
            case "reload":
                manager.reload();
                sender.sendMessage(ChatColor.GREEN + "[MobSpawn] Configuration reloaded!");
                break;
            case "save":
                manager.saveData();
                sender.sendMessage(ChatColor.GREEN + "[MobSpawn] Data saved!");
                break;
            default:
                sendHelp(sender);
        }

        return true;
    }

    // ==================== 创建 ====================

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "[MobSpawn] Player only!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "[MobSpawn] Usage: /mobspawn create <名称>");
            return;
        }

        Player player = (Player) sender;
        String name = args[1];

        // 优先使用玩家站立的方块，否则使用看向的方块
        Block targetBlock = player.getLocation().subtract(0, 1, 0).getBlock();
        if (targetBlock == null || targetBlock.getType() == Material.AIR) {
            targetBlock = player.getTargetBlockExact(5);
        }

        if (targetBlock == null) {
            sender.sendMessage(ChatColor.RED + "[MobSpawn] Cannot determine target block!");
            return;
        }

        Location location = targetBlock.getLocation();

        if (manager.isSpawnPoint(location)) {
            sender.sendMessage(ChatColor.RED + "[MobSpawn] This block already has a spawn point!");
            return;
        }

        boolean success = manager.createSpawnPoint(location, name);

        if (success) {
            player.sendMessage(ChatColor.GREEN + "[MobSpawn] ✓ Spawn point created: " + ChatColor.GOLD + name);
            player.sendMessage(ChatColor.GRAY + "   Location: " + location.getBlockX() + ", " +
                    location.getBlockY() + ", " + location.getBlockZ());
            player.sendMessage(ChatColor.YELLOW + "   Next: /mobspawn addmob <怪物类型> to add mobs");
        } else {
            player.sendMessage(ChatColor.RED + "[MobSpawn] ✗ Failed to create spawn point!");
        }
    }

    // ==================== 移除 ====================

    private void handleRemove(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "[MobSpawn] Player only!");
            return;
        }

        Player player = (Player) sender;
        MobSpawnData data = getTargetSpawnPoint(player);

        if (data == null) return;

        boolean success = manager.removeSpawnPoint(data.getLocation());

        if (success) {
            player.sendMessage(ChatColor.GREEN + "[MobSpawn] ✓ Spawn point removed: " + data.getSpawnName());
        } else {
            player.sendMessage(ChatColor.RED + "[MobSpawn] ✗ Failed to remove!");
        }
    }

    // ==================== 添加怪物 ====================

    private void handleAddMob(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "[MobSpawn] Player only!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "[MobSpawn] Usage: /mobspawn addmob <怪物类型> [权重] [概率%] [自定义名]");
            sender.sendMessage(ChatColor.GRAY + "   Vanilla: /mobspawn addmob ZOMBIE 1 100 §c僵尸");
            sender.sendMessage(ChatColor.GRAY + "   MythicMobs: /mobspawn addmob mm:SkeletonKing 1 100");
            return;
        }

        Player player = (Player) sender;
        MobSpawnData data = getTargetSpawnPoint(player);

        if (data == null) return;

        String typeArg = args[1];

        // 解析可选参数
        double weight = args.length >= 3 ? parseDouble(args[2], 1.0) : 1.0;
        double probability = args.length >= 4 ? parseDouble(args[3], 100.0) : 100.0;
        String customName = args.length >= 5 ? args[4] : null;

        MobEntry entry;

        // 判断是否为 MythicMobs 怪物（mm:<mobId> 格式）
        if (typeArg.toLowerCase().startsWith("mm:") && typeArg.length() > 3) {
            String mobId = typeArg.substring(3);

            // 检查 MythicMobs 是否可用
            if (!manager.isMythicMobsEnabled()) {
                sender.sendMessage(ChatColor.RED + "[MobSpawn] MythicMobs plugin is not installed or not enabled!");
                sender.sendMessage(ChatColor.GRAY + "   Please install MythicMobs to use mm:<mobId> format.");
                return;
            }

            entry = new MobEntry(mobId, weight, probability, customName, true);
            data.addMobEntry(entry);
            manager.saveData();

            player.sendMessage(ChatColor.GREEN + "[MobSpawn] ✓ Added MythicMobs mob: " + ChatColor.LIGHT_PURPLE + "mm:" + mobId);
            player.sendMessage(ChatColor.GRAY + "   Weight: " + weight + ", Probability: " + probability + "%");
            if (customName != null) {
                player.sendMessage(ChatColor.GRAY + "   Custom Name: " + customName.replace('&', '\u00a7'));
            }
        } else {
            // 原生 Bukkit EntityType
            EntityType entityType;
            try {
                entityType = EntityType.valueOf(typeArg.toUpperCase());
            } catch (IllegalArgumentException e) {
                sender.sendMessage(ChatColor.RED + "[MobSpawn] Invalid entity type: " + typeArg);
                sender.sendMessage(ChatColor.GRAY + "   Vanilla types: ZOMBIE, SKELETON, CREEPER, SPIDER, ENDERMAN, etc.");
                sender.sendMessage(ChatColor.GRAY + "   MythicMobs format: mm:<mobId> (e.g. mm:SkeletonKing)");
                return;
            }

            if (!entityType.isAlive() || entityType == EntityType.PLAYER) {
                sender.sendMessage(ChatColor.RED + "[MobSpawn] Only living entities (not PLAYER) are allowed!");
                return;
            }

            entry = new MobEntry(entityType, weight, probability, customName);
            data.addMobEntry(entry);
            manager.saveData();

            player.sendMessage(ChatColor.GREEN + "[MobSpawn] ✓ Added mob: " + ChatColor.GOLD + entityType.name());
            player.sendMessage(ChatColor.GRAY + "   Weight: " + weight + ", Probability: " + probability + "%");
            if (customName != null) {
                player.sendMessage(ChatColor.GRAY + "   Custom Name: " + customName.replace('&', '\u00a7'));
            }
        }
    }

    // ==================== 移除怪物配置 ====================

    private void handleRemoveMob(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "[MobSpawn] Player only!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "[MobSpawn] Usage: /mobspawn removemob <索引>");
            return;
        }

        Player player = (Player) sender;
        MobSpawnData data = getTargetSpawnPoint(player);

        if (data == null) return;

        int index;
        try {
            index = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "[MobSpawn] Invalid index: " + args[1]);
            return;
        }

        if (index < 0 || index >= data.getMobEntries().size()) {
            sender.sendMessage(ChatColor.RED + "[MobSpawn] Index out of range! (0-" + (data.getMobEntries().size() - 1) + ")");
            return;
        }

        MobEntry removed = data.getMobEntries().get(index);
        data.removeMobEntry(index);
        manager.saveData();

        player.sendMessage(ChatColor.GREEN + "[MobSpawn] ✓ Removed mob at index " + index + ": " + removed.getMobIdentifier());
    }

    // ==================== 列出怪物配置 ====================

    private void handleListMobs(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "[MobSpawn] Player only!");
            return;
        }

        Player player = (Player) sender;
        MobSpawnData data = getTargetSpawnPoint(player);

        if (data == null) return;

        java.util.List<MobEntry> entries = data.getMobEntries();

        if (entries.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "[MobSpawn] No mobs configured. Use /mobspawn addmob to add.");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "═══ Mobs at " + data.getSpawnName() + " (" + entries.size() + ") ═══");
        for (int i = 0; i < entries.size(); i++) {
            MobEntry entry = entries.get(i);
            String typeDisplay = entry.isMythicMob()
                    ? ChatColor.LIGHT_PURPLE + "mm:" + entry.getMythicMobId()
                    : ChatColor.WHITE + entry.getEntityType().name();
            String nameStr = entry.getCustomName() != null ? " §7\"" + entry.getCustomName().replace('&', '\u00a7') + "\"" : "";
            player.sendMessage(String.format("§7%d) %s §7(w=%.1f, p=%.0f%%)%s",
                    i, typeDisplay, entry.getWeight(), entry.getSpawnProbability(), nameStr));
        }
    }

    // ==================== 设置参数 ====================

    private void handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "[MobSpawn] Player only!");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "[MobSpawn] Usage: /mobspawn set <参数> <值>");
            sender.sendMessage(ChatColor.GRAY + "   Parameters: radius, interval, mincount, maxcount, maxmobs");
            return;
        }

        Player player = (Player) sender;
        MobSpawnData data = getTargetSpawnPoint(player);

        if (data == null) return;

        String param = args[1].toLowerCase();
        double value = parseDouble(args[2], -1);

        if (value < 0) {
            sender.sendMessage(ChatColor.RED + "[MobSpawn] Invalid value: " + args[2]);
            return;
        }

        switch (param) {
            case "radius":
                data.setDetectionRadius(value);
                sender.sendMessage(ChatColor.GREEN + "[MobSpawn] ✓ Detection radius set to " + value);
                break;
            case "interval":
                data.setSpawnIntervalMs((long) (value * 1000));
                sender.sendMessage(ChatColor.GREEN + "[MobSpawn] ✓ Spawn interval set to " + (long) value + " seconds");
                break;
            case "mincount":
                data.setMinSpawnCount((int) value);
                sender.sendMessage(ChatColor.GREEN + "[MobSpawn] ✓ Min spawn count set to " + (int) value);
                break;
            case "maxcount":
                data.setMaxSpawnCount((int) value);
                sender.sendMessage(ChatColor.GREEN + "[MobSpawn] ✓ Max spawn count set to " + (int) value);
                break;
            case "maxmobs":
                data.setMaxMobCount((int) value);
                sender.sendMessage(ChatColor.GREEN + "[MobSpawn] ✓ Max mob count set to " + (int) value);
                break;
            default:
                sender.sendMessage(ChatColor.RED + "[MobSpawn] Unknown parameter: " + param);
                sender.sendMessage(ChatColor.GRAY + "   Available: radius, interval, mincount, maxcount, maxmobs");
                return;
        }

        manager.saveData();
    }

    // ==================== 列表 ====================

    private void handleList(CommandSender sender) {
        Collection<MobSpawnData> points = manager.getAllSpawnPoints();

        if (points.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "[MobSpawn] No spawn points configured.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "═══ Mob Spawn Points (" + points.size() + ") ═══");

        for (MobSpawnData data : points) {
            Location loc = data.getLocation();
            String status = data.isActive() ? "§a● Active" : "§7○ Inactive";
            int mobCount = data.getMobEntries().size();

            sender.sendMessage(String.format("§7▸ §f%s %s at §e%d,%d,%d §7| Mobs: %d §7| Radius: %.0f §7| Interval: %ds",
                    data.getSpawnName(),
                    status,
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                    mobCount,
                    data.getDetectionRadius(),
                    data.getSpawnIntervalMs() / 1000));
        }
    }

    // ==================== 详情 ====================

    private void handleInfo(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "[MobSpawn] Player only!");
            return;
        }

        Player player = (Player) sender;
        MobSpawnData data = getTargetSpawnPoint(player);

        if (data == null) return;

        Location loc = data.getLocation();

        player.sendMessage(ChatColor.GOLD + "═══ Spawn Point Info ═══");
        player.sendMessage("§7Name: §f" + data.getSpawnName());
        player.sendMessage("§7ID: §f" + data.getSpawnId().toString().substring(0, 8));
        player.sendMessage("§7Location: §e" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
        player.sendMessage("§7Status: " + (data.isActive() ? "§aActive" : "§7Inactive"));
        player.sendMessage("§7Detection Radius: §f" + data.getDetectionRadius());
        player.sendMessage("§7Spawn Interval: §f" + data.getSpawnIntervalMs() / 1000 + " seconds");
        player.sendMessage("§7Spawn Count: §f" + data.getMinSpawnCount() + "~" + data.getMaxSpawnCount());
        player.sendMessage("§7Max Mob Count: §f" + data.getMaxMobCount());
        player.sendMessage("§7Configured Mobs: §f" + data.getMobEntries().size());
        player.sendMessage("§7Current Nearby Mobs: §f" + manager.countAllNearbyMobs(loc, data.getDetectionRadius()));
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Quick Actions:");
        player.sendMessage("§7/mobspawn addmob <type> §f- Add mob");
        player.sendMessage("§7/mobspawn listmobs §f- List mobs");
        player.sendMessage("§7/mobspawn killmobs §f- Kill all spawned mobs");
    }

    // ==================== 击杀生物 ====================

    private void handleKillMobs(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "[MobSpawn] Player only!");
            return;
        }

        Player player = (Player) sender;
        MobSpawnData data = getTargetSpawnPoint(player);

        if (data == null) return;

        manager.killSpawnedMobs(data);
        player.sendMessage(ChatColor.GREEN + "[MobSpawn] ✓ All spawned mobs killed at " + data.getSpawnName());
    }

    // ==================== 工具方法 ====================

    /**
     * 获取玩家看向或站立的召唤点
     */
    private MobSpawnData getTargetSpawnPoint(Player player) {
        // 优先查找站立的方块
        Block standBlock = player.getLocation().subtract(0, 1, 0).getBlock();
        MobSpawnData data = manager.getSpawnPoint(standBlock.getLocation());

        if (data == null) {
            // 查看向的方块
            Block targetBlock = player.getTargetBlockExact(5);
            if (targetBlock != null) {
                data = manager.getSpawnPoint(targetBlock.getLocation());
            }
        }

        if (data == null) {
            player.sendMessage(ChatColor.RED + "[MobSpawn] No spawn point found! Stand on or look at the spawn block.");
        }

        return data;
    }

    private double parseDouble(String str, double def) {
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "═══ Mob Spawn Commands ═══");
        sender.sendMessage("");
        sender.sendMessage("§6Management:");
        sender.sendMessage("§7/mobspawn create <名称> §f- Create spawn point");
        sender.sendMessage("§7/mobspawn remove §f- Remove spawn point");
        sender.sendMessage("§7/mobspawn list §f- List all spawn points");
        sender.sendMessage("§7/mobspawn info §f- Show spawn point info");
        sender.sendMessage("");
        sender.sendMessage("§6Mob Configuration:");
        sender.sendMessage("§7/mobspawn addmob <类型> [权重] [概率%] [名称] §f- Add mob");
        sender.sendMessage("§7  §8Vanilla: /ms addmob ZOMBIE 1 100 §c僵尸");
        sender.sendMessage("§7  §8MythicMobs: /ms addmob mm:SkeletonKing 1 100");
        sender.sendMessage("§7/mobspawn removemob <索引> §f- Remove mob config");
        sender.sendMessage("§7/mobspawn listmobs §f- List mob configs");
        sender.sendMessage("");
        sender.sendMessage("§6Parameters:");
        sender.sendMessage("§7/mobspawn set radius <数值> §f- Detection radius (default: 5)");
        sender.sendMessage("§7/mobspawn set interval <秒数> §f- Spawn interval (default: 15)");
        sender.sendMessage("§7/mobspawn set mincount <数值> §f- Min spawn count (default: 1)");
        sender.sendMessage("§7/mobspawn set maxcount <数值> §f- Max spawn count (default: 3)");
        sender.sendMessage("§7/mobspawn set maxmobs <数值> §f- Max mob limit (default: 10)");
        sender.sendMessage("");
        sender.sendMessage("§6Admin:");
        sender.sendMessage("§7/mobspawn killmobs §f- Kill all spawned mobs");
        sender.sendMessage("§7/mobspawn reload §f- Reload configuration");
        sender.sendMessage("§7/mobspawn save §f- Save data");
    }
}
