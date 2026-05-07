package com.github.yuttyann.scriptblockplus.dungeonchest;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class LootTable {

    private final String name;
    private final List<LootItem> lootItems;
    private final int totalWeight;
    private final Random random;

    public LootTable(String name) {
        this.name = name;
        this.lootItems = new ArrayList<>();
        this.totalWeight = 0;
        this.random = new Random();
    }

    public void addItem(LootItem item) {
        lootItems.add(item);
    }

    public void recalculateWeights() {
        int total = 0;
        for (LootItem item : lootItems) {
            total += item.getWeight();
        }
    }

    public List<ItemStack> generateLoots(int slotCount) {
        List<ItemStack> loots = new ArrayList<>();
        
        List<LootItem> shuffledItems = new ArrayList<>(lootItems);
        Collections.shuffle(shuffledItems, random);

        for (LootItem lootItem : shuffledItems) {
            if (loots.size() >= slotCount) break;

            ItemStack item = lootItem.generateItem(random);
            if (item != null) {
                loots.add(item);
            }
        }

        Collections.shuffle(loots, random);
        return loots;
    }

    public String getName() {
        return name;
    }

    public List<LootItem> getLootItems() {
        return Collections.unmodifiableList(lootItems);
    }

    public static LootTable fromConfig(String name, ConfigurationSection section) {
        if (section == null) return null;

        LootTable table = new LootTable(name);

        if (section.isList("items")) {
            List<?> items = section.getList("items");
            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    ConfigurationSection itemSection = section.getConfigurationSection("items." + i);
                    if (itemSection != null) {
                        LootItem item = LootItem.fromConfig(itemSection);
                        if (item != null) {
                            table.addItem(item);
                        }
                    }
                }
            }
        }

        table.recalculateWeights();
        return table;
    }
}
