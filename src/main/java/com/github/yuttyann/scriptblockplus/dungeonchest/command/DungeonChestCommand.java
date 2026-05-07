package com.github.yuttyann.scriptblockplus.dungeonchest.command;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.github.yuttyann.scriptblockplus.ScriptBlock;
import com.github.yuttyann.scriptblockplus.dungeonchest.DungeonChestConfig;
import com.github.yuttyann.scriptblockplus.dungeonchest.DungeonChestData;
import com.github.yuttyann.scriptblockplus.dungeonchest.DungeonChestManager;

import java.util.Collection;
import java.util.stream.Collectors;

public class DungeonChestCommand implements CommandExecutor {

    private final ScriptBlock plugin;
    private final DungeonChestManager manager;
    
    // ★★★ 管理员权限节点 ★★★
    private static final String ADMIN_PERMISSION = "scriptblockplus.dungeonchest.admin";

    public DungeonChestCommand(ScriptBlock plugin, DungeonChestManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // ★★★ 所有 /dchest 指令都需要管理员权限 ★★★
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "[DungeonChest] You don't have permission to use this command!");
            sender.sendMessage(ChatColor.GRAY + "Required permission: " + ADMIN_PERMISSION);
            return true;
        }
        
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                handleCreate(sender, args);
                break;
            case "remove":
                handleRemove(sender);
                break;
            case "edit":
            case "gui":
                handleEditGUI(sender);
                break;
            case "list":
                handleList(sender);
                break;
            case "info":
                handleInfo(sender);
                break;
            case "save":
                handleSave(sender);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "refresh":
                handleRefresh(sender);
                break;
            default:
                sendHelp(sender);
        }

        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "[DungeonChest] This command can only be used by players!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "[DungeonChest] Usage: /dungeonchest create <config-name>");
            return;
        }

        Player player = (Player) sender;
        String configName = args[1];

        if (!manager.getConfigManager().hasConfig(configName)) {
            player.sendMessage(ChatColor.RED + "[DungeonChest] Config '" + configName + "' not found!");
            player.sendMessage(ChatColor.YELLOW + "Available configs: " + 
                    String.join(", ", manager.getConfigManager().getAllConfigs().keySet()));
            return;
        }

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || targetBlock.getType() != Material.CHEST) {
            player.sendMessage(ChatColor.RED + "[DungeonChest] You must be looking at a chest (within 5 blocks)!");
            return;
        }

        Location location = targetBlock.getLocation();

        if (manager.isDungeonChest(location)) {
            player.sendMessage(ChatColor.RED + "[DungeonChest] This chest is already a dungeon chest!");
            return;
        }

        boolean success = manager.createChest(location, configName);

        if (success) {
            player.sendMessage(ChatColor.GREEN + "[DungeonChest] ✓ Successfully created dungeon chest!");
            player.sendMessage(ChatColor.GRAY + "   Config: " + ChatColor.GOLD + configName);
            player.sendMessage(ChatColor.GRAY + "   Location: " + location.getBlockX() + ", " + 
                    location.getBlockY() + ", " + location.getBlockZ());
            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "   Next: Use /dc edit to add items to this chest");
        } else {
            player.sendMessage(ChatColor.RED + "[DungeonChest] ✗ Failed to create dungeon chest!");
        }
    }

    private void handleRemove(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "[DungeonChest] This command can only be used by players!");
            return;
        }

        Player player = (Player) sender;
        Block targetBlock = player.getTargetBlockExact(5);

        if (targetBlock == null || targetBlock.getType() != Material.CHEST) {
            player.sendMessage(ChatColor.RED + "[DungeonChest] You must be looking at a chest (within 5 blocks)!");
            return;
        }

        Location location = targetBlock.getLocation();

        if (!manager.isDungeonChest(location)) {
            player.sendMessage(ChatColor.YELLOW + "[DungeonChest] This is not a dungeon chest.");
            return;
        }

        boolean success = manager.removeChest(location);

        if (success) {
            player.sendMessage(ChatColor.GREEN + "[DungeonChest] ✓ Dungeon chest removed successfully!");
        } else {
            player.sendMessage(ChatColor.RED + "[DungeonChest] ✗ Failed to remove dungeon chest!");
        }
    }

    /**
     * Open GUI editor for the targeted chest
     */
    private void handleEditGUI(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "[DungeonChest] This command can only be used by players!");
            return;
        }

        Player player = (Player) sender;
        Block targetBlock = player.getTargetBlockExact(5);

        if (targetBlock == null || targetBlock.getType() != Material.CHEST) {
            player.sendMessage(ChatColor.RED + "[DungeonChest] You must be looking at a chest (within 5 blocks)!");
            return;
        }

        Location location = targetBlock.getLocation();
        DungeonChestData chestData = manager.getChestAt(location);

        if (chestData == null) {
            player.sendMessage(ChatColor.YELLOW + "[DungeonChest] This is not a dungeon chest. Create one first with /dc create <config>");
            return;
        }

        // Open GUI editor
        manager.openGUIEditor(player, location);
    }

    private void handleList(CommandSender sender) {
        Collection<DungeonChestData> chests = manager.getAllChests();

        if (chests.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "[DungeonChest] No active dungeon chests.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "═══ Dungeon Chests v2.0 (" + chests.size() + ") ═══");

        for (DungeonChestData chest : chests) {
            Location loc = chest.getLocation();
            
            String status;
            if (!chest.isTriggered()) {
                status = "§7⏸ Idle (waiting for player)";
            } else if (chest.needsRefreshing()) {
                status = "§c⚡ Refreshing now";
            } else {
                long remainingMs = chest.getRemainingRefreshTimeMs();
                status = String.format("§e⏱ %ds remaining", remainingMs / 1000);
            }
            
            int itemCount = chest.getSerializedItems() != null ? chest.getSerializedItems().size() : 0;
            
            sender.sendMessage(String.format(
                "§7▸ §f[%s] %s at §e%d,%d,%d §7| Items: %d",
                chest.getConfigName(),
                status,
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                itemCount
            ));
        }
    }

    private void handleInfo(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "[DungeonChest] This command can only be used by players!");
            return;
        }

        Player player = (Player) sender;
        Block targetBlock = player.getTargetBlockExact(5);

        if (targetBlock == null || targetBlock.getType() != Material.CHEST) {
            player.sendMessage(ChatColor.RED + "[DungeonChest] You must be looking at a chest (within 5 blocks)!");
            return;
        }

        Location location = targetBlock.getLocation();
        DungeonChestData chestData = manager.getChestAt(location);

        if (chestData == null) {
            player.sendMessage(ChatColor.YELLOW + "[DungeonChest] This is not a dungeon chest.");
            return;
        }

        DungeonChestConfig.ChestConfig config = manager.getConfigManager().getConfig(chestData.getConfigName());

        player.sendMessage(ChatColor.GOLD + "═══ Dungeon Chest Info ═══");
        player.sendMessage("§7ID: §f" + chestData.getChestId());
        player.sendMessage("§7Config: §e" + chestData.getConfigName());
        player.sendMessage("§7Display Name: §f" + (config != null ? config.getDisplayName() : "N/A"));
        player.sendMessage("§7Status: " + (chestData.needsRefreshing() ? "§cRefreshing" : "§aReady"));
        
        if (config != null) {
            player.sendMessage(String.format("§7Refresh Interval: §f%d seconds", config.getRefreshInterval() / 1000));
            player.sendMessage("§7Permission: §f" + (config.getPermission().isEmpty() ? "None" : config.getPermission()));
        }

        long remainingRefreshMs = chestData.getRemainingRefreshTimeMs();
        String refreshStatus;
        if (!chestData.isTriggered()) {
            refreshStatus = "Idle (waiting for player)";
        } else if (remainingRefreshMs <= 0) {
            refreshStatus = "Now";
        } else {
            refreshStatus = (remainingRefreshMs / 1000) + "s";
        }
        player.sendMessage("§7Next Refresh: §f" + refreshStatus);
        
        int itemcount = chestData.getSerializedItems() != null ? chestData.getSerializedItems().size() : 0;
        player.sendMessage("§7Configured Items: §f" + itemcount);
        player.sendMessage("§7Players Opened: §f" + chestData.getPlayerLastOpen().size());

        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Quick Actions:");
        player.sendMessage("§7• /dc edit §f- Open item editor for this chest");
        player.sendMessage("§7• /dc refresh §f- Force immediate refresh");
    }

    /**
     * Manual save command
     */
    private void handleSave(CommandSender sender) {
        // ★★★ 权限已在主方法检查，此处简化 ★★★
        long startTime = System.currentTimeMillis();
        manager.saveChestData();
        long duration = System.currentTimeMillis() - startTime;

        sender.sendMessage(ChatColor.GREEN + "[DungeonChest] ✓ Data saved successfully! (" + duration + "ms)");
    }

    private void handleReload(CommandSender sender) {
        // ★★★ 权限已在主方法检查，此处简化 ★★★
        manager.reload();
        sender.sendMessage(ChatColor.GREEN + "[DungeonChest] ✓ Configuration and data reloaded successfully!");
    }

    private void handleRefresh(CommandSender sender) {
        // ★★★ 权限已在主方法检查，此处简化 ★★★
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "[DungeonChest] This command can only be used by players!");
            return;
        }

        Player player = (Player) sender;
        Block targetBlock = player.getTargetBlockExact(5);

        if (targetBlock == null || targetBlock.getType() != Material.CHEST) {
            player.sendMessage(ChatColor.RED + "[DungeonChest] You must be looking at a chest (within 5 blocks)!");
            return;
        }

        Location location = targetBlock.getLocation();
        DungeonChestData chestData = manager.getChestAt(location);

        if (chestData == null) {
            player.sendMessage(ChatColor.RED + "[DungeonChest] This is not a dungeon chest!");
            return;
        }

        // Force refresh
        manager.refreshChest(chestData);
        player.sendMessage(ChatColor.GREEN + "[DungeonChest] ✓ Chest refreshed immediately!");
        player.sendMessage(ChatColor.GRAY + "   Cooldown has been reset for all players.");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "═══ Dungeon Chest Commands v2.0 ═══");
        sender.sendMessage("");
        sender.sendMessage("§6Management Commands:");
        sender.sendMessage("§7/dc create <config> §f- Create dungeon chest (looking at chest)");
        sender.sendMessage("§7/dc remove §f- Remove dungeon chest (looking at chest)");
        sender.sendMessage("§7/dc edit §f- §a★ Open GUI editor to set items ★");
        sender.sendMessage("§7/dc save §f- Save data to disk (admin)");
        sender.sendMessage("");
        sender.sendMessage("§6Information Commands:");
        sender.sendMessage("§7/dc list §f- List all active dungeon chests");
        sender.sendMessage("§7/dc info §f- Show detailed info about targeted chest");
        sender.sendMessage("");
        sender.sendMessage("§6Admin Commands:");
        sender.sendMessage("§7/dc reload §f- Reload configuration (admin)");
        sender.sendMessage("§7/dc refresh §f- Force refresh targeted chest (admin)");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "Tip: Use /dc edit to visually configure items with full NBT support!");
    }
}
