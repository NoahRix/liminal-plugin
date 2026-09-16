package org.derpcraft.liminal.config;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A single loot-table entry: one material item with an amount range and the
 * probability that it is rolled into a spawned loot container.
 *
 * <p>When a loot container spawns on a level's floor, every entry of that
 * level's loot table rolls independently: if {@link #chance} passes, a stack
 * of the entry's material with a random amount between {@link #min} and
 * {@link #max} is added to the container.</p>
 *
 * @param material the item material placed in the container
 * @param min      minimum stack amount (inclusive)
 * @param max      maximum stack amount (inclusive)
 * @param chance   probability (0.0&ndash;1.0) that this entry is included
 */
public record LootEntry(Material material, int min, int max, double chance) {

    /**
     * Returns the number of items to add for this entry.
     *
     * @param random the random source
     * @return a random amount between {@link #min} and {@link #max} (inclusive)
     */
    public int rollAmount(java.util.Random random) {
        if (max <= min) return min;
        return min + random.nextInt(max - min + 1);
    }

    /**
     * Parses a raw config value (a list of mappings) into loot entries.
     *
     * @param raw      the raw YAML value (a list of maps, or anything else)
     * @param fallback the entries to use when the value is not a list
     * @return the parsed entries, or the fallback
     */
    public static List<LootEntry> parseList(Object raw, List<LootEntry> fallback) {
        if (!(raw instanceof List<?> list)) return fallback;

        List<LootEntry> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;

            Object rawMaterial = map.get("material");
            Material material = rawMaterial == null ? null
                    : Material.matchMaterial(String.valueOf(rawMaterial).trim());
            if (material == null) continue;

            int min = map.get("min") instanceof Number n ? n.intValue() : 1;
            int max = map.get("max") instanceof Number n ? n.intValue() : min;
            double chance = map.get("chance") instanceof Number n ? n.doubleValue() : 1.0;
            out.add(new LootEntry(material, min, max, chance));
        }
        return out;
    }

    /**
     * Fills an inventory from a loot table, rolling every entry independently
     * against its chance.
     *
     * @param inventory the inventory to fill
     * @param entries   the loot-table entries
     * @param random    the random source
     */
    public static void fillInventory(Inventory inventory, List<LootEntry> entries, Random random) {
        for (LootEntry entry : entries) {
            if (random.nextDouble() >= entry.chance()) continue;
            int amount = entry.rollAmount(random);
            if (amount <= 0) continue;
            inventory.addItem(new ItemStack(entry.material(), amount));
        }
    }
}
